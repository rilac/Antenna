package ssafy.a507.backend.domain.ranking.service;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.persistence.EntityManager;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;
import ssafy.a507.backend.domain.common.Track;
import ssafy.a507.backend.domain.ranking.dto.MyRankResponse;
import ssafy.a507.backend.domain.ranking.dto.RankingItemResponse;

/**
 * ANT-RANK-01 — 랭킹 스냅샷 배치(B3)의 AC 검증.
 *
 * <p>예측 등록·판정 API(ANT-PRED-01·04)가 아직 없어 {@code predictions} 는 네이티브 INSERT 로
 * 만든다. cron 은 {@code "-"} 로 꺼서 스케줄러가 끼어들지 않게 한다.
 */
@SpringBootTest(properties = "app.ranking.snapshot-cron=-")
@Transactional
class RankingSnapshotServiceTest {

    private static final String SAMSUNG = "005930";

    @Autowired
    RankingSnapshotService snapshots;

    /** 왕복 검증용 — 배치가 쓴 키를 조회가 찾는지 보려면 조회 쪽도 있어야 한다. */
    @Autowired
    RankingQueryService queries;

    @Autowired
    EntityManager em;

    @BeforeEach
    void setUp() {
        insertStock(SAMSUNG, "삼성전자");
    }

    @Test
    @DisplayName("리플레이 실적이 실전 랭킹에 섞이지 않는다 — AC 요구 검증")
    void 트랙_미혼합() {
        Long realOnly = insertUser("실전만");
        Long replayOnly = insertUser("리플레이만");
        // 실전에서 20건 전부 적중
        insertJudged(realOnly, "REAL", 20, 20, "0.500");
        // 리플레이에서 20건 전부 적중 — 실전 랭킹에 나오면 안 된다.
        // REPLAY 는 stock_code 가 아니라 season_ticker_id 를 쓴다(체크 제약 ck_predictions_target_matches_track).
        insertReplayJudged(replayOnly, insertSeasonTicker(), 20, 20, "0.500");

        snapshots.runOnce();

        assertThat(userIdsOf("REAL", "ALL")).containsExactly(realOnly);
        assertThat(userIdsOf("REPLAY", "ALL")).containsExactly(replayOnly);
    }

    @Test
    @DisplayName("순위는 1부터 빈틈·중복 없이 이어지고 점수 내림차순이다")
    void 순위_밀도() {
        Long good = insertUser("잘하는사람");
        Long mid = insertUser("보통");
        Long poor = insertUser("못하는사람");
        insertJudged(good, "REAL", 20, 18, "1.000");
        insertJudged(mid, "REAL", 20, 12, "3.000");
        insertJudged(poor, "REAL", 20, 4, "6.000");

        snapshots.runOnce();

        List<Object[]> rows = rankRows("REAL", "ALL");
        assertThat(rows).hasSize(3);
        assertThat(rows.stream().map(r -> ((Number) r[1]).intValue())).containsExactly(1, 2, 3);
        assertThat(rows.stream().map(r -> ((Number) r[0]).longValue()))
                .containsExactly(good, mid, poor);
    }

    @Test
    @DisplayName("표본이 적으면 가중치가 깎인다 — 3건 만점자가 20건 90%보다 낮다")
    void 표본_가중치() {
        Long lucky = insertUser("세건만맞춘사람");
        Long steady = insertUser("스무건꾸준한사람");
        insertJudged(lucky, "REAL", 3, 3, "0.100");
        insertJudged(steady, "REAL", 20, 18, "1.000");

        snapshots.runOnce();

        assertThat(userIdsOf("REAL", "ALL")).containsExactly(steady, lucky);
    }

    @Test
    @DisplayName("닉네임이 NULL 인 계정은 집계에서 뺀다")
    void 닉네임_없는_계정() {
        Long named = insertUser("이름있음");
        Long anonymous = insertUserWithoutNickname();
        insertJudged(named, "REAL", 5, 3, "2.000");
        insertJudged(anonymous, "REAL", 20, 20, "0.100");

        snapshots.runOnce();

        assertThat(userIdsOf("REAL", "ALL")).containsExactly(named);
    }

    @Test
    @DisplayName("D30 필터는 settle_date 기준으로 갈린다")
    void 최근_30일() {
        Long recent = insertUser("최근판정");
        Long old = insertUser("오래된판정");
        insertJudgedOn(recent, "REAL", LocalDate.now().minusDays(3), 5, 5, "0.500");
        insertJudgedOn(old, "REAL", LocalDate.now().minusDays(90), 5, 5, "0.500");

        snapshots.runOnce();

        assertThat(userIdsOf("REAL", "ALL")).containsExactlyInAnyOrder(recent, old);
        assertThat(userIdsOf("REAL", "30D")).containsExactly(recent);
    }

    @Test
    @DisplayName("섹터 조합은 stocks.sector 의 KRX 업종명으로 돈다 — 판정분이 있는 업종만")
    void 섹터_조합() {
        insertStockWithSector("000660", "SK하이닉스", "전기·전자");
        insertStockWithSector("051910", "LG화학", "화학");
        Long chip = insertUser("반도체투자자");
        Long chem = insertUser("화학투자자");
        insertJudgedOnStock(chip, "000660", LocalDate.now().minusDays(2), 10, 9, "1.000");
        insertJudgedOnStock(chem, "051910", LocalDate.now().minusDays(2), 10, 3, "5.000");

        snapshots.runOnce();

        // 업종별로 그 업종 예측을 한 사람만 들어간다.
        assertThat(userIdsOf("REAL", "ALL:SEC:전기·전자")).containsExactly(chip);
        assertThat(userIdsOf("REAL", "ALL:SEC:화학")).containsExactly(chem);
        // 기간 × 섹터 조합도 함께 만든다.
        assertThat(userIdsOf("REAL", "30D:SEC:전기·전자")).containsExactly(chip);
        // 전체 필터에는 둘 다 있고, 판정분이 없는 업종은 필터 자체가 생기지 않는다.
        assertThat(userIdsOf("REAL", "ALL")).containsExactlyInAnyOrder(chip, chem);
        assertThat(userIdsOf("REAL", "ALL:SEC:제약")).isEmpty();
    }

    @Test
    @DisplayName("리플레이는 시즌마다 필터가 갈린다 — 다른 시즌 실적이 섞이지 않는다")
    void 시즌_조합() {
        Long seasonA = insertSeason();
        Long seasonB = insertSeason();
        Long playerA = insertUser("A시즌참가자");
        Long playerB = insertUser("B시즌참가자");
        insertReplayJudged(playerA, insertSeasonTicker(seasonA), 10, 9, "1.000");
        insertReplayJudged(playerB, insertSeasonTicker(seasonB), 10, 3, "5.000");

        snapshots.runOnce();

        assertThat(userIdsOf("REPLAY", "SEASON:" + seasonA)).containsExactly(playerA);
        assertThat(userIdsOf("REPLAY", "SEASON:" + seasonB)).containsExactly(playerB);
        // 시즌을 가르지 않은 ALL 은 그대로 둘 다 담는다 — seasonId 없이 오는 조회가 쓰는 키다.
        assertThat(userIdsOf("REPLAY", "ALL")).containsExactlyInAnyOrder(playerA, playerB);
        // 예측이 달리지 않은 시즌은 필터 자체가 생기지 않는다.
        assertThat(userIdsOf("REPLAY", "SEASON:" + insertSeason())).isEmpty();
    }

    @Test
    @DisplayName("왕복 — 배치가 쓴 필터 키를 조회가 실제로 찾아낸다")
    void 배치_조회_왕복() {
        Long seasonId = insertSeason();
        Long player = insertUser("리플레이참가자");
        insertReplayJudged(player, insertSeasonTicker(seasonId), 10, 7, "2.000");
        Long real = insertUser("실전참가자");
        insertJudged(real, "REAL", 10, 7, "2.000");

        snapshots.runOnce();
        em.flush();
        em.clear();

        /* 배치와 조회가 각자 필터 키를 만들기 때문에, 둘을 따로 검증하면 키가 어긋나도 드러나지 않는다
           — 어긋난 결과가 예외가 아니라 빈 목록이라서다. 여기서만 두 쪽을 이어 붙여 확인한다. */
        assertThat(queries.list(Track.REPLAY, null, null, seasonId, null, null, null).items())
                .extracting(RankingItemResponse::userId)
                .containsExactly(player);
        assertThat(queries.myRank(player, Track.REPLAY, seasonId)).isPresent();
        assertThat(queries.list(Track.REAL, "D30", null, null, null, null, null).items())
                .extracting(RankingItemResponse::userId)
                .containsExactly(real);
    }

    @Test
    @DisplayName("직전 순위를 prev_rank 로 옮긴다 — 첫 회차는 NULL 이다")
    void 직전_순위_이월() {
        Long first = insertUser("일등");
        Long second = insertUser("이등");
        insertJudged(first, "REAL", 20, 20, "0.500");
        insertJudged(second, "REAL", 20, 10, "3.000");

        // 첫 회차는 비교할 지난 스냅샷이 없다.
        snapshots.runOnce();
        assertThat(prevRankOf("REAL", "ALL", first)).isNull();
        assertThat(prevRankOf("REAL", "ALL", second)).isNull();

        // 다음 영업일 회차는 어제 순위를 담고 있어야 한다.
        하루_지나간다();
        snapshots.runOnce();
        assertThat(prevRankOf("REAL", "ALL", first)).isEqualTo(1);
        assertThat(prevRankOf("REAL", "ALL", second)).isEqualTo(2);
    }

    @Test
    @DisplayName("같은 날 재실행해도 변동이 지워지지 않는다 — 직전 회차의 prev_rank 를 물려받는다")
    void 같은_날_재실행() {
        Long fading = insertUser("떨어질사람2");
        Long rising = insertUser("올라갈사람2");
        insertJudged(fading, "REAL", 20, 20, "0.500");
        insertJudged(rising, "REAL", 20, 10, "3.000");
        snapshots.runOnce();

        // 다음 영업일, 순위가 뒤집힌 회차 — 여기서 prev_rank 가 1·2 로 박힌다.
        하루_지나간다();
        insertJudged(fading, "REAL", 20, 0, "8.000");
        snapshots.runOnce();
        assertThat(prevRankOf("REAL", "ALL", rising)).isEqualTo(2);

        /* 같은 날 한 번 더 돈다(실패 회차 재시도). 지금 순위를 직전 값으로 담으면 변동이 0 으로
           지워진다 — 재시도했다는 이유로 화면의 ▲▼ 가 사라지면 안 된다. */
        snapshots.runOnce();
        em.flush();
        em.clear();

        assertThat(prevRankOf("REAL", "ALL", rising)).isEqualTo(2);
        assertThat(queries.myRank(rising, Track.REAL, null)).get().extracting(MyRankResponse::delta)
                .isEqualTo(1);
    }

    @Test
    @DisplayName("순위가 오르면 delta 가 양수다 — 순위는 숫자가 작을수록 위라 부호가 뒤집힌다")
    void 순위_변동_부호() {
        Long fading = insertUser("떨어질사람");
        Long rising = insertUser("올라갈사람");
        insertJudged(fading, "REAL", 20, 20, "0.500");
        insertJudged(rising, "REAL", 20, 10, "3.000");

        snapshots.runOnce();
        assertThat(userIdsOf("REAL", "ALL")).containsExactly(fading, rising);

        // 다음 영업일, 앞사람이 20건을 내리 틀려 순위가 뒤집힌다.
        하루_지나간다();
        insertJudged(fading, "REAL", 20, 0, "8.000");
        snapshots.runOnce();
        em.flush();
        em.clear();

        assertThat(userIdsOf("REAL", "ALL")).containsExactly(rising, fading);
        // 2등 → 1등은 +1, 1등 → 2등은 -1 이다.
        assertThat(queries.myRank(rising, Track.REAL, null)).get().extracting(MyRankResponse::delta)
                .isEqualTo(1);
        assertThat(queries.myRank(fading, Track.REAL, null)).get().extracting(MyRankResponse::delta)
                .isEqualTo(-1);
    }

    @Test
    @DisplayName("새로 진입한 회원은 delta 가 0 이다 — 비교할 지난 순위가 없다")
    void 신규_진입자() {
        Long veteran = insertUser("기존참가자");
        insertJudged(veteran, "REAL", 20, 15, "2.000");
        snapshots.runOnce();

        하루_지나간다();
        Long rookie = insertUser("신규참가자");
        insertJudged(rookie, "REAL", 20, 20, "0.500");
        snapshots.runOnce();
        em.flush();
        em.clear();

        assertThat(userIdsOf("REAL", "ALL")).containsExactly(rookie, veteran);
        assertThat(prevRankOf("REAL", "ALL", rookie)).isNull();
        // null 이 아니라 0 이다 — 화면이 delta !== 0 으로 ▲▼ 를 감춘다.
        assertThat(queries.myRank(rookie, Track.REAL, null)).get().extracting(MyRankResponse::delta)
                .isEqualTo(0);
    }

    @Test
    @DisplayName("전량 재작성 — 자격을 잃은 회원의 지난 순위가 남지 않는다")
    void 재실행_안전() {
        Long stays = insertUser("남는사람");
        insertJudged(stays, "REAL", 10, 6, "2.000");
        // 지난 회차의 유령 행을 직접 심어 둔다.
        Long gone = insertUser("사라질사람");
        em.createNativeQuery(
                        """
                        INSERT INTO rankings
                          (track, filter_key, user_id, score, hit_rate, avg_error, done_count, "rank", computed_at)
                        VALUES ('REAL', 'ALL', ?, 99.000, 99.00, 0.100, 50, 1, CURRENT_TIMESTAMP)
                        """)
                .setParameter(1, gone)
                .executeUpdate();

        snapshots.runOnce();
        assertThat(userIdsOf("REAL", "ALL")).containsExactly(stays);

        // 두 번 돌려도 결과가 같다.
        snapshots.runOnce();
        assertThat(userIdsOf("REAL", "ALL")).containsExactly(stays);
        assertThat(rankRows("REAL", "ALL")).hasSize(1);
    }

    /* ── 도우미 ───────────────────────────────────────── */

    /**
     * 지금 있는 스냅샷을 하루 전 것으로 만든다. 배치는 영업일마다 한 번 도는데 테스트는 한
     * 순간에 두 번 부르므로, 이걸 끼워 넣지 않으면 "어제 → 오늘" 이 아니라 "같은 날 재시도" 가
     * 된다. 둘은 {@code prev_rank} 를 다르게 다룬다.
     */
    private void 하루_지나간다() {
        em.flush();
        // 한 회차의 행들은 산출 시각이 모두 같아서 한 값으로 밀어도 된다.
        // INTERVAL 구문은 H2 가 못 읽어 바인딩으로 넣는다.
        em.createNativeQuery("UPDATE rankings SET computed_at = ?")
                .setParameter(
                        1,
                        java.sql.Timestamp.from(
                                java.time.Instant.now().minus(1, java.time.temporal.ChronoUnit.DAYS)))
                .executeUpdate();
        em.clear();
    }

    /** 직전 스냅샷 순위. 첫 회차이거나 그때 이 필터에 없었으면 NULL 이다. */
    private Integer prevRankOf(String track, String filterKey, Long userId) {
        em.flush();
        em.clear();
        Number value = (Number) em.createNativeQuery(
                        """
                        SELECT prev_rank FROM rankings
                         WHERE track = ? AND filter_key = ? AND user_id = ?
                        """)
                .setParameter(1, track)
                .setParameter(2, filterKey)
                .setParameter(3, userId)
                .getSingleResult();
        return value == null ? null : value.intValue();
    }

    private List<Long> userIdsOf(String track, String filterKey) {
        return rankRows(track, filterKey).stream()
                .map(r -> ((Number) r[0]).longValue())
                .toList();
    }

    @SuppressWarnings("unchecked")
    private List<Object[]> rankRows(String track, String filterKey) {
        em.flush();
        em.clear();
        return em.createNativeQuery(
                        """
                        SELECT user_id, "rank", score FROM rankings
                         WHERE track = ? AND filter_key = ? ORDER BY "rank"
                        """)
                .setParameter(1, track)
                .setParameter(2, filterKey)
                .getResultList();
    }

    private void insertJudged(Long userId, String track, int total, int hits, String avgError) {
        insertJudgedOn(userId, track, LocalDate.now().minusDays(1), total, hits, avgError);
    }

    private void insertJudgedOn(
            Long userId, String track, LocalDate settleDate, int total, int hits, String avgError) {
        for (int i = 0; i < total; i++) {
            em.createNativeQuery(
                            """
                            INSERT INTO predictions
                              (user_id, track, stock_code, direction, target_price, horizon,
                               status, settle_date, error_rate, created_at, updated_at)
                            VALUES (?, ?, ?, 'UP', 70000, 7, ?, ?, ?, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                            """)
                    .setParameter(1, userId)
                    .setParameter(2, track)
                    .setParameter(3, SAMSUNG)
                    .setParameter(4, i < hits ? "HIT" : "MISS")
                    .setParameter(5, settleDate)
                    .setParameter(6, new java.math.BigDecimal(avgError))
                    .executeUpdate();
        }
    }


    /** REPLAY 판정분. 대상이 종목이 아니라 시즌 종목이고 기간도 게임일이라 별 도우미가 필요하다. */
    private void insertReplayJudged(
            Long userId, Long tickerId, int total, int hits, String avgError) {
        for (int i = 0; i < total; i++) {
            em.createNativeQuery(
                            """
                            INSERT INTO predictions
                              (user_id, track, season_ticker_id, direction, target_price, horizon,
                               status, base_game_day, settle_game_day, error_rate, created_at, updated_at)
                            VALUES (?, 'REPLAY', ?, 'UP', 70000, 5, ?, 1, 6, ?, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                            """)
                    .setParameter(1, userId)
                    .setParameter(2, tickerId)
                    .setParameter(3, i < hits ? "HIT" : "MISS")
                    .setParameter(4, new java.math.BigDecimal(avgError))
                    .executeUpdate();
        }
    }

    /** 리플레이 예측을 매달 자리. 시즌 하나와 시즌 종목 하나만 만든다. */
    private Long insertSeasonTicker() {
        return insertSeasonTicker(insertSeason());
    }

    private Long insertSeason() {
        em.createNativeQuery(
                        """
                        INSERT INTO seasons
                          (mode, length_days, initial_cash, seed, current_day, status)
                        VALUES ('PRACTICE', 60, 10000000, 1, 10, 'RUNNING')
                        """)
                .executeUpdate();
        return ((Number) em.createNativeQuery("SELECT max(id) FROM seasons").getSingleResult())
                .longValue();
    }

    private Long insertSeasonTicker(Long seasonId) {
        em.createNativeQuery(
                        """
                        INSERT INTO season_tickers (season_id, display_name, real_stock_code, sector)
                        VALUES (?, 'A사', ?, '전기·전자')
                        """)
                .setParameter(1, seasonId)
                .setParameter(2, SAMSUNG)
                .executeUpdate();
        return ((Number) em.createNativeQuery("SELECT max(id) FROM season_tickers").getSingleResult())
                .longValue();
    }


    private void insertStockWithSector(String code, String name, String sector) {
        em.createNativeQuery(
                        "INSERT INTO stocks (code, name, listed, sector) VALUES (?, ?, true, ?)")
                .setParameter(1, code)
                .setParameter(2, name)
                .setParameter(3, sector)
                .executeUpdate();
    }

    private void insertJudgedOnStock(
            Long userId, String stockCode, LocalDate settleDate, int total, int hits, String avgError) {
        for (int i = 0; i < total; i++) {
            em.createNativeQuery(
                            """
                            INSERT INTO predictions
                              (user_id, track, stock_code, direction, target_price, horizon,
                               status, settle_date, error_rate, created_at, updated_at)
                            VALUES (?, 'REAL', ?, 'UP', 70000, 7, ?, ?, ?, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                            """)
                    .setParameter(1, userId)
                    .setParameter(2, stockCode)
                    .setParameter(3, i < hits ? "HIT" : "MISS")
                    .setParameter(4, settleDate)
                    .setParameter(5, new java.math.BigDecimal(avgError))
                    .executeUpdate();
        }
    }

    private void insertStock(String code, String name) {
        em.createNativeQuery("INSERT INTO stocks (code, name, listed) VALUES (?, ?, true)")
                .setParameter(1, code)
                .setParameter(2, name)
                .executeUpdate();
    }

    private Long insertUser(String nickname) {
        em.createNativeQuery("""
                        INSERT INTO users (nickname, role, status, created_at, updated_at)
                        VALUES (?, 'USER', 'ACTIVE', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                        """)
                .setParameter(1, nickname)
                .executeUpdate();
        return ((Number) em.createNativeQuery("SELECT id FROM users WHERE nickname = ?")
                        .setParameter(1, nickname)
                        .getSingleResult())
                .longValue();
    }

    private Long insertUserWithoutNickname() {
        em.createNativeQuery("""
                        INSERT INTO users (role, status, created_at, updated_at)
                        VALUES ('USER', 'ACTIVE', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                        """)
                .executeUpdate();
        return ((Number) em.createNativeQuery("SELECT max(id) FROM users").getSingleResult())
                .longValue();
    }
}
