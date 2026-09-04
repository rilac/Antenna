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
import ssafy.a507.backend.domain.market.dto.IndexQuoteUpsert;
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
     * market·listed_shares 는 COALESCE 로 덮는다 — 값이 안 온 회차가 이미 아는 값을 지우면 안 된다.
     */
    private static final String UPSERT_STOCK =
            """
            INSERT INTO stocks (code, name, market, listed, listed_shares)
            VALUES (?, ?, ?, true, ?)
            ON CONFLICT (code) DO UPDATE SET
                name = EXCLUDED.name,
                market = COALESCE(EXCLUDED.market, stocks.market),
                listed_shares = COALESCE(EXCLUDED.listed_shares, stocks.listed_shares)
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

    /** 지수·환율은 종가 하나뿐이다. 같은 (지수, 영업일)이 다시 오면 값만 덮는다. */
    private static final String UPSERT_INDEX_QUOTE =
            """
            INSERT INTO index_quotes (index_code, trade_date, close)
            VALUES (?, ?, ?)
            ON CONFLICT (index_code, trade_date) DO UPDATE SET
                close = EXCLUDED.close
            """;

    /**
     * PER = 종가 × 상장주식수 / 순이익, PBR = 종가 × 상장주식수 / 자본총계. 종가는 종목별 마지막
     * 영업일, 재무는 최신 연간(사업보고서) 한 해다. 순이익·자본총계가 0 이하(적자·자본잠식)면 그
     * 지표만 null — 음수 PER 은 "싸다" 로 읽혀 더 해롭다. 원화 종가를 달러 재무로 나누면 안 되므로
     * 통화가 KRW 가 아닌 재무는 없는 것으로 본다. 재료가 사라진 종목은 옛 값이 지워진다 — 전 종목을
     * 매번 다시 쓰기 때문이다(300 행이라 값싸다).
     *
     * <p>UPDATE … FROM 이 아니라 MERGE 인 이유 — H2 가 전자를 지원하지 않아 SpringBootTest 에서
     * 깨진다. MERGE 는 PostgreSQL 15+ 와 H2 양쪽에서 같은 문장으로 돈다.
     */
    private static final String REFRESH_VALUATIONS =
            """
            MERGE INTO stocks s
            USING (
                SELECT st.code,
                       CASE WHEN f.net_income > 0
                            THEN ROUND(q.close * st.listed_shares / f.net_income, 2) END AS per,
                       CASE WHEN f.total_equity > 0
                            THEN ROUND(q.close * st.listed_shares / f.total_equity, 2) END AS pbr
                FROM stocks st
                LEFT JOIN daily_quotes q
                       ON q.stock_code = st.code
                      AND q.trade_date = (SELECT MAX(trade_date) FROM daily_quotes
                                          WHERE stock_code = st.code)
                LEFT JOIN corp_financials f
                       ON f.stock_code = st.code
                      AND f.quarter = 4
                      AND COALESCE(f.currency, 'KRW') = 'KRW'
                      AND f.fiscal_year = (SELECT MAX(fiscal_year) FROM corp_financials
                                           WHERE stock_code = st.code AND quarter = 4)
            ) v ON v.code = s.code
            WHEN MATCHED THEN UPDATE SET per = v.per, pbr = v.pbr
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
                        ps.setObject(4, row.listedShares(), Types.BIGINT);
                    }

                    @Override
                    public int getBatchSize() {
                        return rows.size();
                    }
                });
        return rows.size();
    }

    /**
     * 전 종목의 PER·PBR 파생 컬럼을 다시 쓴다. 일봉 13:00 회차와 DART 연간 재무 회차 뒤에 부른다 —
     * 두 재료 중 하나라도 바뀌면 값이 바뀐다.
     *
     * @return 다시 쓴 종목 수(전 종목)
     */
    @Transactional
    public int refreshValuations() {
        return jdbcTemplate.update(REFRESH_VALUATIONS);
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

    /**
     * 지수·환율 종가를 적재한다(ANT-DATA-04). 일봉과 같은 이유로 JDBC upsert 다 —
     * 대리키 PK + UQ(index_code, trade_date) 라 {@code save()} 로는 멱등해지지 않는다.
     *
     * @return DB 로 보낸 행 수
     */
    @Transactional
    public int upsertIndexQuotes(List<IndexQuoteUpsert> rows) {
        if (rows.isEmpty()) {
            return 0;
        }

        jdbcTemplate.batchUpdate(
                UPSERT_INDEX_QUOTE,
                new BatchPreparedStatementSetter() {
                    @Override
                    public void setValues(PreparedStatement ps, int i) throws SQLException {
                        IndexQuoteUpsert row = rows.get(i);
                        ps.setString(1, row.indexCode().name());
                        ps.setObject(2, row.tradeDate(), Types.DATE);
                        ps.setObject(3, row.close(), Types.NUMERIC);
                    }

                    @Override
                    public int getBatchSize() {
                        return rows.size();
                    }
                });
        return rows.size();
    }
}
