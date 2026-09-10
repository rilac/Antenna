package ssafy.a507.backend.domain.prediction.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.LocalDate;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import ssafy.a507.backend.domain.account.entity.User;
import ssafy.a507.backend.domain.account.repository.UserRepository;
import ssafy.a507.backend.domain.common.Track;
import ssafy.a507.backend.domain.market.entity.Stock;
import ssafy.a507.backend.domain.market.repository.StockRepository;
import ssafy.a507.backend.domain.prediction.entity.Prediction;
import ssafy.a507.backend.domain.prediction.repository.PredictionRepository;
import ssafy.a507.backend.domain.ranking.entity.BatchRun;
import ssafy.a507.backend.domain.ranking.repository.BatchRunRepository;

/**
 * ANT-PRED-03·04 — 판정 배치 B2 한 회차. H2.
 *
 * <p><b>{@code @Transactional} 을 일부러 붙이지 않았다.</b> 이 배치의 AC 가 "예측 단위 트랜잭션(전체 롤백 금지)" 인데,
 * 테스트가 바깥 트랜잭션을 열면 서비스의 {@code @Transactional} 이 거기 참여해 버려서 한 건의 실패가 테스트 트랜잭션 전체를
 * rollback-only 로 만든다 — 검증하려는 성질이 테스트 환경 때문에 사라진다. 대신 앞뒤로 직접 지운다.
 *
 * <p>날짜는 고정 상수다. 배치가 대상을 날짜가 아니라 시세 유무로 고르기 때문에(plan §설계 ②) 과거 날짜를 써도 결정적으로 돈다.
 * 2026-01-05 는 월요일, 01-10·01-11 은 토·일, 01-12 는 월요일이다.
 */
@SpringBootTest
@DisplayName("판정 배치 B2")
class PredictionSettlementRunnerTest {

    private static final String STOCK = "999901";
    private static final String OTHER_STOCK = "999902";

    private static final LocalDate MON = LocalDate.of(2026, 1, 5);
    private static final LocalDate SAT = LocalDate.of(2026, 1, 10);
    private static final LocalDate SUN = LocalDate.of(2026, 1, 11);
    private static final LocalDate NEXT_MON = LocalDate.of(2026, 1, 12);
    /** 시세가 절대 없는 미래 — "보류" 를 만들 때 쓴다. */
    private static final LocalDate FAR_FUTURE = LocalDate.of(2030, 1, 7);

    private static final LocalDate BUSINESS_DATE = LocalDate.of(2026, 1, 13);

    @Autowired PredictionSettlementRunner runner;
    @Autowired PredictionRepository predictions;
    @Autowired BatchRunRepository batchRuns;
    @Autowired UserRepository users;
    @Autowired StockRepository stocks;
    @Autowired JdbcTemplate jdbc;

    private long userId;

    @BeforeEach
    void setUp() {
        clean();
        userId = users.save(User.create()).getId();
        insertStock(STOCK);
        insertStock(OTHER_STOCK);
    }

    @AfterEach
    void tearDown() {
        clean();
    }

    // ── ① 기준가 확정 ────────────────────────────────────────────────

    @Test
    @DisplayName("기준일 종가가 있으면 기준가를 채우고 OPEN 으로 올린다")
    void 기준가_확정() {
        insertQuote(STOCK, MON, "80000");
        long id = givenBase(MON, FAR_FUTURE, Prediction.Direction.UP, "82000");

        runner.run(BUSINESS_DATE);

        Prediction p = predictions.findById(id).orElseThrow();
        assertThat(p.getStatus()).isEqualTo(Prediction.Status.OPEN);
        assertThat(p.getBasePrice()).isEqualByComparingTo("80000");
        assertThat(batchRun().getOpenedCount()).isEqualTo(1);
    }

    @Test
    @DisplayName("시세가 아직 없으면 보류한다 — 오판보다 지연")
    void 시세_없으면_보류() {
        long id = givenBase(FAR_FUTURE, FAR_FUTURE, Prediction.Direction.UP, "82000");

        runner.run(BUSINESS_DATE);

        Prediction p = predictions.findById(id).orElseThrow();
        assertThat(p.getStatus()).isEqualTo(Prediction.Status.BASE);
        assertThat(p.getBasePrice()).isNull();
        assertThat(batchRun().getOpenedCount()).isZero();
    }

    @Test
    @DisplayName("기준일이 휴장이면 그 이후 첫 거래일 종가를 쓰되, base_date·settle_date 는 그대로 둔다")
    void 기준일_휴장() {
        insertQuote(STOCK, NEXT_MON, "81000"); // 토요일(SAT)에는 시세가 없다
        long id = givenBase(SAT, FAR_FUTURE, Prediction.Direction.UP, "82000");

        runner.run(BUSINESS_DATE);

        Prediction p = predictions.findById(id).orElseThrow();
        assertThat(p.getBasePrice()).isEqualByComparingTo("81000");
        // 등록 때 약속한 날짜는 움직이지 않는다 — 옮기면 사용자가 본 D-day 와 만기일이 바뀐다
        assertThat(p.getBaseDate()).isEqualTo(SAT);
        assertThat(p.getSettleDate()).isEqualTo(FAR_FUTURE);
    }

    // ── ② 만기 판정 ─────────────────────────────────────────────────

    @Test
    @DisplayName("방향을 맞히면 HIT — 오차는 목표가 대비로 따로 적힌다")
    void 판정_HIT() {
        insertQuote(STOCK, MON, "85000");
        long id = givenOpen(MON, Prediction.Direction.UP, "82000", "80000");

        runner.run(BUSINESS_DATE);

        Prediction p = predictions.findById(id).orElseThrow();
        assertThat(p.getStatus()).isEqualTo(Prediction.Status.HIT);
        assertThat(p.getSettlePrice()).isEqualByComparingTo("85000");
        assertThat(p.getErrorRate()).isEqualByComparingTo("3.659");
        BatchRun run = batchRun();
        assertThat(run.getVerifiedCount()).isEqualTo(1);
        assertThat(run.getHitCount()).isEqualTo(1);
    }

    @Test
    @DisplayName("방향을 틀리면 MISS — 판정 건수에는 들어가고 적중 건수에는 안 들어간다")
    void 판정_MISS() {
        insertQuote(STOCK, MON, "79000");
        long id = givenOpen(MON, Prediction.Direction.UP, "82000", "80000");

        runner.run(BUSINESS_DATE);

        assertThat(predictions.findById(id).orElseThrow().getStatus()).isEqualTo(Prediction.Status.MISS);
        BatchRun run = batchRun();
        assertThat(run.getVerifiedCount()).isEqualTo(1);
        assertThat(run.getHitCount()).isZero();
    }

    @Test
    @DisplayName("보합(기준가 == 종가)은 UP·DOWN 둘 다 HIT")
    void 판정_보합() {
        insertQuote(STOCK, MON, "80000");
        long up = givenOpen(MON, Prediction.Direction.UP, "82000", "80000");
        long down = givenOpen(MON, Prediction.Direction.DOWN, "78000", "80000");

        runner.run(BUSINESS_DATE);

        assertThat(predictions.findById(up).orElseThrow().getStatus()).isEqualTo(Prediction.Status.HIT);
        assertThat(predictions.findById(down).orElseThrow().getStatus()).isEqualTo(Prediction.Status.HIT);
        assertThat(batchRun().getHitCount()).isEqualTo(2);
    }

    @Test
    @DisplayName("만기일이 휴장이면 그 이후 첫 거래일 종가로 판정하고, 실제로 쓴 날짜를 settled_on 에 남긴다")
    void 만기일_휴장() {
        insertQuote(STOCK, NEXT_MON, "85000"); // 일요일(SUN)에는 시세가 없다
        long id = givenOpen(SUN, Prediction.Direction.UP, "82000", "80000");

        runner.run(BUSINESS_DATE);

        Prediction p = predictions.findById(id).orElseThrow();
        assertThat(p.getStatus()).isEqualTo(Prediction.Status.HIT);
        assertThat(p.getSettlePrice()).isEqualByComparingTo("85000");
        // 검산 ③단계의 sourceUrl 이 이 날짜로 열린다. settle_date(일요일)로 열면 포털이 빈 응답을 준다
        assertThat(p.getSettledOn()).isEqualTo(NEXT_MON);
        assertThat(p.getSettleDate()).isEqualTo(SUN);
    }

    @Test
    @DisplayName("한 회차 안에서 기준가 확정과 만기 판정이 이어진다 — ① 이 OPEN 으로 올린 건을 ② 가 같은 회차에 집는다")
    void 한_회차에_확정과_판정() {
        insertQuote(STOCK, MON, "80000");
        insertQuote(STOCK, NEXT_MON, "88000");
        long id = givenBase(MON, NEXT_MON, Prediction.Direction.UP, "82000");

        runner.run(BUSINESS_DATE);

        Prediction p = predictions.findById(id).orElseThrow();
        assertThat(p.getBasePrice()).isEqualByComparingTo("80000");
        assertThat(p.getStatus()).isEqualTo(Prediction.Status.HIT);
        BatchRun run = batchRun();
        assertThat(run.getOpenedCount()).isEqualTo(1);
        assertThat(run.getVerifiedCount()).isEqualTo(1);
    }

    // ── 실패·재실행 ──────────────────────────────────────────────────

    @Test
    @DisplayName("한 건이 터져도 나머지는 저장된다 — 회차 전체가 롤백되지 않는다(AC)")
    void 한_건_실패가_회차를_말아먹지_않는다() {
        insertQuote(STOCK, MON, "85000");
        insertQuote(OTHER_STOCK, MON, "85000");
        long broken = givenOpen(MON, Prediction.Direction.UP, "82000", "80000");
        long healthy = givenOpen(OTHER_STOCK, MON, Prediction.Direction.UP, "82000", "80000");
        // 오차 산식의 분모를 0 으로 만든다. 등록 API 는 0.01 이상을 강제하므로 정상 경로로는 나올 수 없는 값이다 —
        // "데이터가 깨져도 나머지는 간다" 를 확인하려는 것이다.
        jdbc.update("update predictions set target_price = 0 where id = ?", broken);

        runner.run(BUSINESS_DATE);

        assertThat(predictions.findById(broken).orElseThrow().getStatus()).isEqualTo(Prediction.Status.OPEN);
        assertThat(predictions.findById(healthy).orElseThrow().getStatus()).isEqualTo(Prediction.Status.HIT);
        BatchRun run = batchRun();
        assertThat(run.getVerifiedCount()).isEqualTo(1);
        assertThat(run.getStatus()).isEqualTo(BatchRun.PARTIAL);
    }

    @Test
    @DisplayName("같은 날 다시 돌려도 batch_runs 는 한 행이고 카운터는 누적된다")
    void 같은_날_재실행은_누적() {
        insertQuote(STOCK, MON, "80000");
        givenBase(MON, FAR_FUTURE, Prediction.Direction.UP, "82000");
        runner.run(BUSINESS_DATE);

        // 회차 사이에 새 예측이 하나 들어왔다
        givenBase(MON, FAR_FUTURE, Prediction.Direction.UP, "83000");
        runner.run(BUSINESS_DATE);

        assertThat(jdbc.queryForObject("select count(*) from batch_runs", Long.class)).isEqualTo(1L);
        assertThat(batchRun().getOpenedCount()).isEqualTo(2);
    }

    @Test
    @DisplayName("두 번째 회차에는 다시 집을 대상이 없다 — 재실행이 상태를 덧칠하지 않는다")
    void 멱등() {
        insertQuote(STOCK, MON, "85000");
        long id = givenOpen(MON, Prediction.Direction.UP, "82000", "80000");
        runner.run(BUSINESS_DATE);
        BigDecimal firstError = predictions.findById(id).orElseThrow().getErrorRate();

        runner.run(BUSINESS_DATE);

        Prediction p = predictions.findById(id).orElseThrow();
        assertThat(p.getStatus()).isEqualTo(Prediction.Status.HIT);
        assertThat(p.getErrorRate()).isEqualByComparingTo(firstError);
        // 두 번째 회차는 0 건이라 총합이 그대로다
        assertThat(batchRun().getVerifiedCount()).isEqualTo(1);
    }

    // ── ③ 리빌 ──────────────────────────────────────────────────────

    @Test
    @DisplayName("판정 직후 salt 가 공개된다 — 00:05 앵커 배치를 기다리지 않는다")
    void 판정_직후_리빌() {
        insertQuote(STOCK, MON, "85000");
        long id = givenOpen(MON, Prediction.Direction.UP, "82000", "80000");
        insertCommit(id);

        runner.run(BUSINESS_DATE);

        assertThat(jdbc.queryForObject(
                        "select count(*) from prediction_commits where prediction_id = ? and revealed_at is not null",
                        Long.class,
                        id))
                .isEqualTo(1L);
    }

    @Test
    @DisplayName("판정 전에는 salt 를 공개하지 않는다")
    void 판정_전에는_리빌하지_않는다() {
        long id = givenBase(FAR_FUTURE, FAR_FUTURE, Prediction.Direction.UP, "82000");
        insertCommit(id);

        runner.run(BUSINESS_DATE);

        assertThat(jdbc.queryForObject(
                        "select count(*) from prediction_commits where prediction_id = ? and revealed_at is null",
                        Long.class,
                        id))
                .isEqualTo(1L);
    }

    // ── 트랙 ────────────────────────────────────────────────────────

    @Test
    @DisplayName("대상 조회가 트랙을 가른다 — REAL 예측은 REPLAY 스캔에 잡히지 않는다")
    void 트랙_구분() {
        long id = givenBase(MON, FAR_FUTURE, Prediction.Direction.UP, "82000");

        assertThat(predictions.findIdsByTrackAndStatus(Track.REAL, Prediction.Status.BASE))
                .contains(id);
        assertThat(predictions.findIdsByTrackAndStatus(Track.REPLAY, Prediction.Status.BASE))
                .isEmpty();
    }

    // ── 픽스처 ──────────────────────────────────────────────────────

    /** BASE 상태의 REAL 예측. horizon 은 REAL 제약(7·14·30·90) 안의 값이어야 한다. */
    private long givenBase(
            LocalDate baseDate, LocalDate settleDate, Prediction.Direction direction, String targetPrice) {
        return givenBase(STOCK, baseDate, settleDate, direction, targetPrice);
    }

    private long givenBase(
            String stockCode,
            LocalDate baseDate,
            LocalDate settleDate,
            Prediction.Direction direction,
            String targetPrice) {
        Stock stock = stocks.findById(stockCode).orElseThrow();
        Prediction p = Prediction.register(
                users.findById(userId).orElseThrow(),
                stock,
                direction,
                new BigDecimal(targetPrice),
                new BigDecimal("80000"),
                (short) 30,
                baseDate,
                settleDate);
        return predictions.save(p).getId();
    }

    /** 기준가까지 확정된 OPEN 예측. ① 을 거치지 않고 바로 판정 단계를 시험하려고 상태를 직접 세운다. */
    private long givenOpen(
            LocalDate settleDate, Prediction.Direction direction, String targetPrice, String basePrice) {
        return givenOpen(STOCK, settleDate, direction, targetPrice, basePrice);
    }

    private long givenOpen(
            String stockCode,
            LocalDate settleDate,
            Prediction.Direction direction,
            String targetPrice,
            String basePrice) {
        long id = givenBase(stockCode, MON, settleDate, direction, targetPrice);
        jdbc.update(
                "update predictions set status = 'OPEN', base_price = ? where id = ?",
                new BigDecimal(basePrice),
                id);
        return id;
    }

    private void insertStock(String code) {
        jdbc.update("INSERT INTO stocks (code, name, listed) VALUES (?, ?, true)", code, "테스트종목" + code);
    }

    private void insertQuote(String code, LocalDate tradeDate, String close) {
        jdbc.update(
                "INSERT INTO daily_quotes (stock_code, trade_date, close, collected_at)"
                        + " VALUES (?, ?, ?, CURRENT_TIMESTAMP)",
                code,
                tradeDate,
                new BigDecimal(close));
    }

    /** 리빌 대상이 되려면 커밋 행이 있어야 한다. 값은 형식만 맞으면 되고 내용은 이 테스트와 무관하다. */
    private void insertCommit(long predictionId) {
        jdbc.update(
                "INSERT INTO prediction_commits (prediction_id, commit_hash, salt) VALUES (?, ?, ?)",
                predictionId,
                "0x" + String.format("%064x", predictionId),
                "a".repeat(64));
    }

    private BatchRun batchRun() {
        return batchRuns.findByBusinessDate(BUSINESS_DATE).orElseThrow();
    }

    /**
     * 이 테스트는 트랜잭션 밖에서 돌아 스스로 지워야 한다. {@code predictions} 를 통째로 비우는 것이 안전한 이유는
     * 이 표에 쓰는 코드가 예측 등록 서비스 하나뿐이고 다른 테스트는 전부 트랜잭션 안에서 롤백되기 때문이다.
     * 시드가 쓰는 {@code stocks}·{@code daily_quotes} 는 내 종목코드만 지운다.
     */
    private void clean() {
        jdbc.update("delete from prediction_commits");
        jdbc.update("delete from prediction_evidences");
        jdbc.update("delete from prediction_notes");
        jdbc.update("delete from predictions");
        jdbc.update("delete from batch_runs");
        jdbc.update("delete from daily_quotes where stock_code in (?, ?)", STOCK, OTHER_STOCK);
        jdbc.update("delete from stocks where code in (?, ?)", STOCK, OTHER_STOCK);
    }
}
