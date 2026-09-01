package ssafy.a507.backend.domain.market.repository;

import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Types;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.BatchPreparedStatementSetter;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;
import ssafy.a507.backend.domain.market.dto.DailyQuoteUpsert;
import ssafy.a507.backend.domain.market.dto.StockUpsert;

/**
 * 공공데이터 수집분을 멱등하게 적재한다 — 같은 날짜를 몇 번 다시 수집해도 행이 늘지 않는다.
 *
 * <p><b>왜 JPA 가 아니라 JDBC 인가.</b> {@code daily_quotes} 는 대리키 PK + UQ(stock_code,
 * trade_date) 구조라(ERD 도메인 B) {@code save()} 로는 멱등해지지 않는다. 기존 행을 찾으려면
 * 날짜별 2,800여 건마다 SELECT 를 한 번씩 더 쏴야 하고, 그 SELECT 와 INSERT 사이에 재시도가
 * 끼면 중복이 들어간다. {@code ON CONFLICT} 는 그 판단을 DB 의 유니크 인덱스에 맡긴다.
 *
 * <p><b>왜 다중 VALUES 가 아니라 batchUpdate 인가.</b> 한 문장의 VALUES 목록 안에서 같은
 * 충돌 대상이 두 번 나오면 PostgreSQL 이 "cannot affect row a second time" 로 문장 전체를
 * 거절한다. 포털 응답은 페이지 경계에서 같은 (종목, 날짜)를 다시 주는 일이 있어, 행마다
 * 문장을 따로 실행하는 batchUpdate 여야 그 중복이 그냥 갱신으로 흡수된다.
 *
 * <p>호출 순서는 {@link #upsertStocks} → {@link #upsertDailyQuotes} 다. 일봉의 stock_code 가
 * {@code stocks} 를 참조하므로 종목이 먼저 들어가 있어야 한다.
 */
@Repository
@RequiredArgsConstructor
public class MarketUpsertRepository {

    /**
     * sector 와 listed 는 갱신하지 않는다. 섹터의 원천은 KRX 업종분류 CSV 이고, 상장 여부는
     * 3년치 백필이 옛 날짜의 폐지 종목을 다시 살려 놓지 않도록 수집이 건드리지 않는다.
     * market 은 COALESCE 로 덮는다 — 값이 안 온 회차가 이미 아는 시장을 지우면 안 된다.
     */
    private static final String UPSERT_STOCK =
            """
            INSERT INTO stocks (code, name, market, listed)
            VALUES (?, ?, ?, true)
            ON CONFLICT (code) DO UPDATE SET
                name = EXCLUDED.name,
                market = COALESCE(EXCLUDED.market, stocks.market)
            """;

    private static final String UPSERT_DAILY_QUOTE =
            """
            INSERT INTO daily_quotes
                (stock_code, trade_date, open, high, low, close, volume, collected_at)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?)
            ON CONFLICT (stock_code, trade_date) DO UPDATE SET
                open = EXCLUDED.open,
                high = EXCLUDED.high,
                low = EXCLUDED.low,
                close = EXCLUDED.close,
                volume = EXCLUDED.volume,
                collected_at = EXCLUDED.collected_at
            """;

    private final JdbcTemplate jdbcTemplate;

    /**
     * 종목 마스터를 적재한다. 처음 보는 종목은 상장 상태로 넣고, 이미 아는 종목은 이름·시장만
     * 맞춰 둔다. 행을 지우는 경로는 없다 — 상장폐지 종목의 과거 시세를 남겨 두기 위해서다.
     *
     * @return DB 로 보낸 행 수. {@code DO UPDATE} 에 조건이 없어 행마다 정확히 하나가 반영된다.
     */
    @Transactional
    public int upsertStocks(List<StockUpsert> rows) {
        if (rows.isEmpty()) {
            return 0;
        }

        jdbcTemplate.batchUpdate(
                UPSERT_STOCK,
                new BatchPreparedStatementSetter() {
                    @Override
                    public void setValues(PreparedStatement ps, int i) throws SQLException {
                        StockUpsert row = rows.get(i);
                        ps.setString(1, row.code());
                        ps.setString(2, row.name());
                        ps.setString(3, row.market() == null ? null : row.market().name());
                    }

                    @Override
                    public int getBatchSize() {
                        return rows.size();
                    }
                });
        return rows.size();
    }

    /**
     * 일봉을 적재한다. 같은 (종목, 영업일)이 다시 오면 값만 최신으로 덮는다.
     *
     * @param collectedAt 수집 시각 · 한 회차가 같은 값을 공유해야 장애 추적 때 회차 단위로 묶인다
     * @return DB 로 보낸 행 수
     */
    @Transactional
    public int upsertDailyQuotes(List<DailyQuoteUpsert> rows, Instant collectedAt) {
        if (rows.isEmpty()) {
            return 0;
        }

        // timestamptz 컬럼에 Instant 를 그대로 넘기면 드라이버가 세션 타임존으로 해석한다.
        // 오프셋을 명시해 서버 타임존과 무관하게 같은 시점이 되도록 고정한다.
        OffsetDateTime collectedAtUtc = collectedAt.atOffset(ZoneOffset.UTC);

        jdbcTemplate.batchUpdate(
                UPSERT_DAILY_QUOTE,
                new BatchPreparedStatementSetter() {
                    @Override
                    public void setValues(PreparedStatement ps, int i) throws SQLException {
                        DailyQuoteUpsert row = rows.get(i);
                        ps.setString(1, row.stockCode());
                        ps.setObject(2, row.tradeDate(), Types.DATE);
                        ps.setObject(3, row.open(), Types.NUMERIC);
                        ps.setObject(4, row.high(), Types.NUMERIC);
                        ps.setObject(5, row.low(), Types.NUMERIC);
                        ps.setObject(6, row.close(), Types.NUMERIC);
                        ps.setObject(7, row.volume(), Types.BIGINT);
                        ps.setObject(8, collectedAtUtc, Types.TIMESTAMP_WITH_TIMEZONE);
                    }

                    @Override
                    public int getBatchSize() {
                        return rows.size();
                    }
                });
        return rows.size();
    }
}
