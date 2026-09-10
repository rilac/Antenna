package ssafy.a507.backend.domain.ranking.repository;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;
import ssafy.a507.backend.domain.common.Track;

/**
 * 랭킹 스냅샷 배치(B3, ANT-RANK-01)의 집계·적재 담당.
 *
 * <p><b>왜 JPA 가 아니라 JDBC 인가.</b> 하는 일이 "필터 하나당 그룹 집계 한 번 + 결과 전량 재작성"
 * 이다. 엔티티를 한 건씩 올렸다 내리는 것이 아니라 집계 결과를 통째로 갈아 끼우는 쪽이라, 같은
 * 이유로 JDBC 를 쓴 {@link ssafy.a507.backend.domain.market.repository.MarketUpsertRepository}
 * 와 결을 맞췄다.
 *
 * <p><b>왜 upsert 가 아니라 delete + insert 인가.</b> 전량 재계산이라 이번 회차에 자격을 잃은
 * 회원의 행이 남으면 안 된다. upsert 로는 그 행을 지울 수 없어 "지난 회차 순위가 유령으로 남는"
 * 상태가 된다. 한 트랜잭션 안에서 지우고 넣으므로 읽는 쪽은 MVCC 로 이전 스냅샷을 계속 본다.
 */
@Repository
@RequiredArgsConstructor
public class RankingAggregateRepository {

    /** 회차를 "같은 날" 로 묶는 기준. 배치가 영업일 기준이라 서버 시간대와 무관하게 KST 로 센다. */
    private static final ZoneId KST = ZoneId.of("Asia/Seoul");

    private final JdbcTemplate jdbcTemplate;

    /** 집계 한 줄. 점수·순위는 서비스가 매긴다 — 산식 상수를 SQL 에 묻어두면 바꿀 때 찾기 어렵다. */
    public record Aggregate(long userId, int doneCount, int hitCount, BigDecimal avgError) {}

    /**
     * 판정이 끝난 예측만 회원별로 모은다.
     *
     * <p>닉네임이 NULL 인 계정은 뺀다 — {@code AuthService} 가 닉네임 없이 가입시키고 온보딩에서
     * 확정하므로, 넣으면 랭킹과 홈 위젯에 이름 빈 줄이 뜬다.
     *
     * <p><b>오차는 절대값으로 평균낸다</b>({@code AVG(ABS(error_rate))}). ERD·명세 어디에도
     * {@code predictions.error_rate} 의 부호 규칙이 없고 판정 배치(ANT-PRED-04)가 아직 없어,
     * 목표가를 밑돈 예측이 음수로 들어올 수 있다. 부호를 그대로 평균내면 +5% 와 −5% 가 상쇄돼
     * 평균 0 — 크게 빗나간 사람이 목표가 정확도 만점을 받는다. 점수 계산 뒤에 절대값을 씌우는
     * 것으로는 못 막는다(이미 상쇄된 뒤다). 화면의 "평균 오차" 도 절대오차 평균이 맞는 값이다.
     *
     * @param sector KRX 업종명. null 이면 업종을 가리지 않는다
     * @param settledFrom 이 날짜 이후 판정분만. null 이면 전체 기간
     * @param seasonId 리플레이 시즌. null 이면 시즌을 가리지 않는다. 시즌마다 종목도 기간도 다르므로
     *     가르지 않으면 비교가 성립하지 않는 실적이 한 필터에 섞인다
     */
    @Transactional(readOnly = true)
    public List<Aggregate> aggregate(
            Track track, String sector, LocalDate settledFrom, Long seasonId) {
        StringBuilder sql = new StringBuilder(
                """
                SELECT p.user_id,
                       COUNT(*)                                        AS done_count,
                       SUM(CASE WHEN p.status = 'HIT' THEN 1 ELSE 0 END) AS hit_count,
                       AVG(ABS(p.error_rate))                          AS avg_error
                  FROM predictions p
                  JOIN users u ON u.id = p.user_id
                 WHERE p.track = ?
                   AND p.status IN ('HIT', 'MISS')
                   AND u.nickname IS NOT NULL
                """);
        List<Object> args = new ArrayList<>();
        args.add(track.name());
        if (sector != null) {
            sql.append("   AND EXISTS (SELECT 1 FROM stocks s WHERE s.code = p.stock_code AND s.sector = ?)\n");
            args.add(sector);
        }
        if (settledFrom != null) {
            sql.append("   AND p.settle_date >= ?\n");
            args.add(settledFrom);
        }
        if (seasonId != null) {
            sql.append(
                    "   AND EXISTS (SELECT 1 FROM season_tickers st WHERE st.id = p.season_ticker_id AND st.season_id = ?)\n");
            args.add(seasonId);
        }
        sql.append(" GROUP BY p.user_id");

        return jdbcTemplate.query(
                sql.toString(),
                (rs, n) -> new Aggregate(
                        rs.getLong("user_id"),
                        rs.getInt("done_count"),
                        rs.getInt("hit_count"),
                        rs.getBigDecimal("avg_error")),
                args.toArray());
    }

    /**
     * 랭킹을 낼 업종 목록. {@code stocks.sector} 의 실제 값(KRX 업종명)을 그대로 쓴다 —
     * 종목 탐색의 섹터 칩({@code GET /stocks/sectors})과 같은 어휘여야 앱 안에서 섹터 목록이
     * 두 벌이 되지 않는다.
     *
     * <p>업종이 비어 있는 종목은 빠진다. 판정된 예측이 하나도 없는 업종까지 도는 것을 막으려고
     * predictions 를 걸어 좁힌다 — 26개 업종을 전부 돌면 빈 필터 스냅샷만 늘어난다.
     */
    @Transactional(readOnly = true)
    public List<String> sectorsWithJudgedPredictions(Track track) {
        return jdbcTemplate.queryForList(
                """
                SELECT DISTINCT s.sector
                  FROM predictions p
                  JOIN stocks s ON s.code = p.stock_code
                 WHERE p.track = ?
                   AND p.status IN ('HIT', 'MISS')
                   AND s.sector IS NOT NULL
                 ORDER BY s.sector
                """,
                String.class,
                track.name());
    }

    /**
     * 랭킹을 낼 리플레이 시즌 목록. 판정된 리플레이 예측이 달린 시즌만 돈다 — 업종과 같은 이유로,
     * 예측이 하나도 없는 시즌까지 돌면 빈 필터 스냅샷만 늘어난다.
     *
     * <p>{@code predictions.season_ticker_id} 가 시즌 종목을 가리키고 시즌은 그 위에 있다.
     * REAL 예측은 이 컬럼이 비어 있어 조인에서 자연히 빠진다.
     */
    @Transactional(readOnly = true)
    public List<Long> seasonsWithJudgedPredictions(Track track) {
        return jdbcTemplate.queryForList(
                """
                SELECT DISTINCT st.season_id
                  FROM predictions p
                  JOIN season_tickers st ON st.id = p.season_ticker_id
                 WHERE p.track = ?
                   AND p.status IN ('HIT', 'MISS')
                 ORDER BY st.season_id
                """,
                Long.class,
                track.name());
    }

    /** 한 줄 = 한 회원의 최종 랭킹 값. 서비스가 점수 순으로 정렬해 순위를 붙인 결과다. */
    public record Row(
            long userId,
            int rank,
            BigDecimal score,
            BigDecimal hitRate,
            BigDecimal avgError,
            int doneCount) {}

    /**
     * 필터 하나를 통째로 갈아 끼운다. 호출자의 트랜잭션 안에서 돌아야 한다 — 지운 뒤 넣기 전에
     * 커밋되면 그 순간 조회가 빈 랭킹을 본다.
     *
     * <p><b>지우기 전에 지금 순위를 읽어 {@code prev_rank} 로 옮겨 담는다</b>(ANT-RANK-05).
     * 전량 재작성이라 이 자리를 놓치면 직전 순위를 되찾을 곳이 없다. 이전 회차에 없던 회원은
     * NULL 로 남고 조회가 그것을 변동 없음으로 읽는다.
     */
    public void replaceFilter(
            Track track, String filterKey, List<Row> rows, java.time.Instant computedAt) {
        Map<Long, Integer> previousRanks = previousRanks(track, filterKey, computedAt);

        jdbcTemplate.update(
                "DELETE FROM rankings WHERE track = ? AND filter_key = ?", track.name(), filterKey);
        if (rows.isEmpty()) {
            return;
        }
        jdbcTemplate.batchUpdate(
                """
                INSERT INTO rankings
                  (track, filter_key, user_id, score, hit_rate, avg_error, done_count, "rank", prev_rank, computed_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """,
                rows,
                rows.size(),
                (ps, row) -> {
                    ps.setString(1, track.name());
                    ps.setString(2, filterKey);
                    ps.setLong(3, row.userId());
                    ps.setBigDecimal(4, row.score());
                    ps.setBigDecimal(5, row.hitRate());
                    ps.setBigDecimal(6, row.avgError());
                    ps.setInt(7, row.doneCount());
                    ps.setInt(8, row.rank());
                    // 없으면 NULL 이다 — 0 을 넣으면 "0 등이었다" 가 되어 변동이 거꾸로 계산된다.
                    ps.setObject(9, previousRanks.get(row.userId()), java.sql.Types.INTEGER);
                    ps.setTimestamp(10, java.sql.Timestamp.from(computedAt));
                });
    }

    /**
     * 이번 회차가 덮어쓰기 직전의 순위. 회원 하나당 한 줄이라 UQ 가 키 중복을 막아 준다.
     *
     * <p><b>같은 날 두 번 돌면 지금 있는 행의 {@code prev_rank} 를 그대로 물려받는다.</b> 그러지
     * 않으면 재실행이 "오늘 아침 순위" 를 직전 값으로 담아 변동이 전부 0 으로 지워진다. 재실행
     * 안전은 이 배치의 AC 이고(실패한 회차는 다음 회차가 다시 계산한다), 재시도했다는 이유로
     * 화면의 ▲▼ 가 사라지면 안 된다. 날짜가 다르면 지금 순위가 곧 직전 순위다.
     */
    private Map<Long, Integer> previousRanks(Track track, String filterKey, Instant computedAt) {
        LocalDate today = LocalDate.ofInstant(computedAt, KST);
        Map<Long, Integer> previous = new HashMap<>();
        jdbcTemplate.query(
                """
                SELECT user_id, "rank", prev_rank, computed_at FROM rankings
                 WHERE track = ? AND filter_key = ?
                """,
                rs -> {
                    boolean sameDay = today.equals(
                            LocalDate.ofInstant(rs.getTimestamp("computed_at").toInstant(), KST));
                    int carried = sameDay ? rs.getInt("prev_rank") : rs.getInt("rank");
                    // prev_rank 는 NULL 일 수 있다 — getInt 가 0 을 주므로 wasNull 로 갈라야 한다.
                    if (sameDay && rs.wasNull()) {
                        return;
                    }
                    previous.put(rs.getLong("user_id"), carried);
                },
                track.name(),
                filterKey);
        return previous;
    }
}
