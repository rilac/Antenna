package ssafy.a507.backend.domain.season.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import jakarta.persistence.EntityManager;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;
import ssafy.a507.backend.domain.season.entity.Season;
import ssafy.a507.backend.domain.season.service.SeasonCreateService.SeasonSpec;

/**
 * 시즌 생성 — 실제 과거 시세 구간을 game_day 로 옮기는 부분의 검증.
 *
 * <p>확인하는 것은 다섯이다. 가격이 원천과 같은가 · 실제 날짜가 game_day 로 바뀌었는가 ·
 * 워밍업이 game_day 0 이하로 앞에 붙는가 · 종목이 시총 순으로 뽑히고 종목명이 실명인가 ·
 * 구간에 구멍이 있거나 우선주이거나 상장주식수가 없는 종목이 걸러지는가.
 *
 * <p>기준 데이터 — 보통주 넷(A0010~A0040)과 우선주 하나(A0015), 다른 업종 하나(B0010),
 * 상장주식수가 없는 하나(C0010). 영업일은 2024-01-02 부터 닷새이고 주말(1/6·1/7)은 없다.
 * A0040 만 사흘째 시세가 빠져 있다. 첫날 시총(종가 × 주식수)은 A0040 > A0030 > B0010 >
 * A0020 > A0010 순이다.
 */
@SpringBootTest
@Transactional
class SeasonCreateServiceTest {

    private static final String SECTOR = "테스트업종";
    private static final String OTHER_SECTOR = "다른업종";
    private static final List<LocalDate> DAYS = List.of(
            LocalDate.of(2024, 1, 2),
            LocalDate.of(2024, 1, 3),
            LocalDate.of(2024, 1, 4),
            LocalDate.of(2024, 1, 5),
            LocalDate.of(2024, 1, 8));

    @Autowired SeasonCreateService createService;
    @Autowired EntityManager em;

    @BeforeEach
    void setUp() {
        insertStock("A0010", SECTOR, 100L);
        insertStock("A0020", SECTOR, 200L);
        insertStock("A0030", SECTOR, 300L);
        insertStock("A0040", SECTOR, 400L);
        // A0015 는 A0010 의 우선주다(끝자리가 0 이 아니다). 후보에 들면 안 된다.
        insertStock("A0015", SECTOR, 100L);
        insertStock("B0010", OTHER_SECTOR, 50L);
        // 상장주식수가 없으면 시총을 매길 수 없다. 후보에 들면 안 된다.
        insertStock("C0010", SECTOR, null);

        for (int i = 0; i < DAYS.size(); i++) {
            LocalDate day = DAYS.get(i);
            insertQuote("A0010", day, 1000 + i);
            insertQuote("A0020", day, 2000 + i);
            insertQuote("A0030", day, 3000 + i);
            insertQuote("A0015", day, 1500 + i);
            insertQuote("B0010", day, 9000 + i);
            insertQuote("C0010", day, 8000 + i);
            // A0040 은 사흘째가 빠진다 — 구간에 구멍이 있는 종목이다.
            if (i != 2) {
                insertQuote("A0040", day, 4000 + i);
            }
        }
        em.flush();
        em.clear();
    }

    @Test
    @DisplayName("게임일 가격이 원천 일봉과 같고, 순서가 영업일 순서다")
    void 가격을_그대로_옮긴다() {
        Season season = createService.create(spec(LocalDate.of(2024, 1, 2), 4, 5, 42L));

        List<String> codes = realCodes(season.getId());
        assertThat(codes).containsExactlyInAnyOrder("A0030", "B0010", "A0020", "A0010");

        for (String code : codes) {
            assertThat(gameDays(season.getId(), code)).containsExactly(1, 2, 3, 4, 5);
            // 값 자체가 원천이다 — 합성하지 않는다.
            assertThat(closes(season.getId(), code)).containsExactly(sourceCloses(code));
        }
    }

    @Test
    @DisplayName("종목명은 실명이다 — 가명을 붙이지 않는다")
    void 종목명은_실명이다() {
        Season season = createService.create(spec(DAYS.get(0), 2, 5, 42L));

        assertThat(displayNames(season.getId())).containsExactlyInAnyOrder("A0030종목", "B0010종목");
    }

    @Test
    @DisplayName("첫 게임일 시가총액 큰 순서로 상한까지 뽑는다 — 업종은 거르지 않는다")
    void 시총_순으로_뽑는다() {
        // A0040 이 가장 크지만 구멍이 있어 빠지고, 다음이 A0030(900,000) · B0010(450,000) 이다.
        Season season = createService.create(spec(DAYS.get(0), 2, 5, 42L));

        assertThat(realCodes(season.getId())).containsExactly("A0030", "B0010");
    }

    @Test
    @DisplayName("워밍업 — 첫 게임일 앞의 영업일이 game_day 0 이하로 붙는다")
    void 워밍업이_앞에_붙는다() {
        // 1/4 부터 사흘. 앞의 1/2·1/3 이 워밍업이라 game_day 는 -1·0 이다.
        Season season = createService.create(spec(LocalDate.of(2024, 1, 4), 1, 3, 42L));

        assertThat(season.getBaseDate()).isEqualTo(LocalDate.of(2024, 1, 4));
        assertThat(season.getLengthDays()).isEqualTo(3);
        assertThat(gameDays(season.getId(), "A0030")).containsExactly(-1, 0, 1, 2, 3);
        assertThat(closes(season.getId(), "A0030")).containsExactly(sourceCloses("A0030"));
    }

    @Test
    @DisplayName("요청한 기준일이 영업일이 아니면 그다음 영업일부터 시작한다")
    void 휴일은_다음_영업일로_밀린다() {
        // 2024-01-06·07 은 주말이라 일봉이 없다. 1/6 을 넣으면 1/8 이 첫 게임일이다.
        Season season = createService.create(spec(LocalDate.of(2024, 1, 6), 2, 1, 42L));

        assertThat(season.getBaseDate()).isEqualTo(LocalDate.of(2024, 1, 8));
        // 워밍업 나흘(1/2~1/5)이 앞에 붙는다. 1/8 하루짜리 구간이라 구멍 있는 A0040 도 후보가
        // 되어 1등으로 뽑히는데, 그쪽은 워밍업 1/4 봉이 비어 있다 — 구멍 검사는 플레이 구간만
        // 본다. 열이 온전한 2등 A0030 으로 확인한다.
        assertThat(realCodes(season.getId())).containsExactly("A0040", "A0030");
        assertThat(gameDays(season.getId(), "A0030")).containsExactly(-3, -2, -1, 0, 1);
        assertThat(gameDays(season.getId(), "A0040")).containsExactly(-3, -2, 0, 1);
    }

    @Test
    @DisplayName("구간에 시세가 빠진 종목은 뽑지 않는다")
    void 구멍_있는_종목은_후보가_아니다() {
        Season season = createService.create(spec(DAYS.get(0), 10, 5, 42L));

        assertThat(realCodes(season.getId())).doesNotContain("A0040");
    }

    @Test
    @DisplayName("우선주와 상장주식수 없는 종목은 뽑지 않는다")
    void 우선주와_주식수_없는_종목은_후보가_아니다() {
        Season season = createService.create(spec(DAYS.get(0), 10, 5, 42L));

        assertThat(realCodes(season.getId())).doesNotContain("A0015", "C0010");
    }

    @Test
    @DisplayName("종목 수는 상한이다 — 후보가 적으면 있는 만큼 만든다")
    void 종목_수는_상한이다() {
        Season season = createService.create(spec(DAYS.get(0), 10, 5, 42L));

        assertThat(realCodes(season.getId())).hasSize(4);
    }

    @Test
    @DisplayName("수집된 영업일보다 긴 시즌은 만들지 않는다 — 짧은 시즌을 조용히 만들지 않는다")
    void 영업일이_부족하면_만들지_않는다() {
        assertThatThrownBy(() -> createService.create(spec(DAYS.get(0), 2, 6, 42L)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("5 일만 수집돼 있다");
    }

    @Test
    @DisplayName("시가총액을 매길 종목이 하나도 없으면 만들지 않는다")
    void 주식수가_전부_없으면_만들지_않는다() {
        em.createNativeQuery("UPDATE stocks SET listed_shares = NULL").executeUpdate();

        assertThatThrownBy(() -> createService.create(spec(DAYS.get(0), 2, 5, 42L)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("listed_shares 가 비어 있다");
    }

    @Test
    @DisplayName("지우면 시즌·종목·가격이 함께 사라진다")
    void 지우면_전부_사라진다() {
        Season season = createService.create(spec(DAYS.get(0), 2, 5, 42L));
        assertThat(count("season_prices")).isEqualTo(10);

        createService.delete(season.getId());

        assertThat(count("season_prices")).isZero();
        assertThat(count("season_tickers")).isZero();
        assertThat(count("seasons")).isZero();
    }

    private static SeasonSpec spec(LocalDate baseDate, int tickerCount, int lengthDays, long seed) {
        return new SeasonSpec(
                Season.Mode.PRACTICE,
                "테스트 시즌",
                "설명",
                SECTOR,
                baseDate,
                tickerCount,
                lengthDays,
                new BigDecimal("30000000"),
                seed);
    }

    /** 종목별 첫날 종가. setUp 의 insertQuote 와 같은 표다. */
    private static final Map<String, Integer> BASE_CLOSE = Map.of(
            "A0010", 1000, "A0020", 2000, "A0030", 3000, "A0040", 4000,
            "A0015", 1500, "B0010", 9000, "C0010", 8000);

    /** 그 종목의 원천 종가 다섯 개. 첫날 값에서 하루 1 씩 오른다. */
    private static BigDecimal[] sourceCloses(String code) {
        int base = BASE_CLOSE.get(code);
        BigDecimal[] closes = new BigDecimal[DAYS.size()];
        for (int i = 0; i < closes.length; i++) {
            closes[i] = new BigDecimal(base + i).setScale(2);
        }
        return closes;
    }

    @SuppressWarnings("unchecked")
    private List<Object[]> prices(Long seasonId, String code) {
        return em.createNativeQuery(
                        """
                        SELECT p.game_day, p.close FROM season_prices p
                        JOIN season_tickers t ON t.id = p.ticker_id
                        WHERE t.season_id = ? AND t.real_stock_code = ?
                        ORDER BY p.game_day
                        """)
                .setParameter(1, seasonId)
                .setParameter(2, code)
                .getResultList();
    }

    private List<BigDecimal> closes(Long seasonId, String code) {
        return prices(seasonId, code).stream()
                .map(row -> ((BigDecimal) row[1]).setScale(2))
                .toList();
    }

    private List<Integer> gameDays(Long seasonId, String code) {
        return prices(seasonId, code).stream()
                .map(row -> ((Number) row[0]).intValue())
                .toList();
    }

    /** 시즌 종목의 실제 코드. id 순 = 뽑힌 순(시총 순)이다. */
    @SuppressWarnings("unchecked")
    private List<String> realCodes(Long seasonId) {
        return em.createNativeQuery(
                        "SELECT real_stock_code FROM season_tickers WHERE season_id = ? ORDER BY id")
                .setParameter(1, seasonId)
                .getResultList();
    }

    @SuppressWarnings("unchecked")
    private List<String> displayNames(Long seasonId) {
        return em.createNativeQuery(
                        "SELECT display_name FROM season_tickers WHERE season_id = ? ORDER BY id")
                .setParameter(1, seasonId)
                .getResultList();
    }

    private long count(String table) {
        return ((Number) em.createNativeQuery("SELECT COUNT(*) FROM " + table).getSingleResult()).longValue();
    }

    private void insertStock(String code, String sector, Long listedShares) {
        em.createNativeQuery(
                        """
                        INSERT INTO stocks (code, name, market, sector, listed, listed_shares)
                        VALUES (?, ?, 'KOSPI', ?, true, ?)
                        """)
                .setParameter(1, code)
                .setParameter(2, code + "종목")
                .setParameter(3, sector)
                .setParameter(4, listedShares)
                .executeUpdate();
    }

    private void insertQuote(String code, LocalDate tradeDate, int close) {
        em.createNativeQuery(
                        """
                        INSERT INTO daily_quotes (stock_code, trade_date, open, high, low, close, volume, collected_at)
                        VALUES (?, ?, ?, ?, ?, ?, ?, CURRENT_TIMESTAMP)
                        """)
                .setParameter(1, code)
                .setParameter(2, tradeDate)
                .setParameter(3, new BigDecimal(close))
                .setParameter(4, new BigDecimal(close + 10))
                .setParameter(5, new BigDecimal(close - 10))
                .setParameter(6, new BigDecimal(close))
                .setParameter(7, 1000L)
                .executeUpdate();
    }
}
