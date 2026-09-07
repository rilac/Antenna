package ssafy.a507.backend.domain.season.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import jakarta.persistence.EntityManager;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.LinkedHashMap;
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
 * <p>확인하는 것은 넷이다. 가격이 원천과 같은가 · 실제 날짜가 game_day 로 바뀌었는가 ·
 * 구간에 구멍이 있는 종목이 걸러지는가 · 같은 seed 가 같은 종목을 뽑는가.
 *
 * <p>기준 데이터 — 섹터 "테스트업종" 에 보통주 넷(A0010~A0040)과 우선주 하나(A0015).
 * 영업일은 2024-01-02 부터 닷새이고 주말(1/6·1/7)은 없다. A0040 만 사흘째 시세가 빠져 있다.
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
        insertStock("A0010", SECTOR);
        insertStock("A0020", SECTOR);
        insertStock("A0030", SECTOR);
        insertStock("A0040", SECTOR);
        // A0015 는 A0010 의 우선주다(끝자리가 0 이 아니다). 후보에 들면 안 된다.
        insertStock("A0015", SECTOR);
        insertStock("B0010", OTHER_SECTOR);

        for (int i = 0; i < DAYS.size(); i++) {
            LocalDate day = DAYS.get(i);
            insertQuote("A0010", day, 1000 + i);
            insertQuote("A0020", day, 2000 + i);
            insertQuote("A0030", day, 3000 + i);
            insertQuote("A0015", day, 1500 + i);
            insertQuote("B0010", day, 9000 + i);
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
        Season season = createService.create(spec(LocalDate.of(2024, 1, 2), 3, 5, 42L));

        // 어느 가명이 어느 종목인지는 seed 가 정한다 — 가명을 고정해 두고 값을 비교하면
        // 셔플 결과에 따라 붙었다 떨어졌다 하는 테스트가 된다.
        Map<String, String> aliases = aliasToCode(season.getId());
        assertThat(aliases).hasSize(3);

        aliases.forEach((alias, code) -> {
            assertThat(gameDays(season.getId(), alias)).containsExactly(1, 2, 3, 4, 5);
            // 값 자체가 원천이다 — 합성하지 않는다.
            assertThat(closes(season.getId(), alias)).containsExactly(sourceCloses(code));
        });
    }

    @Test
    @DisplayName("요청한 기준일이 영업일이 아니면 그다음 영업일부터 시작한다")
    void 휴일은_다음_영업일로_밀린다() {
        // 2024-01-06·07 은 주말이라 일봉이 없다. 1/6 을 넣으면 1/8 이 첫 게임일이다.
        Season season = createService.create(spec(LocalDate.of(2024, 1, 6), 1, 1, 42L));

        assertThat(season.getBaseDate()).isEqualTo(LocalDate.of(2024, 1, 8));

        String alias = aliasToCode(season.getId()).keySet().iterator().next();
        assertThat(gameDays(season.getId(), alias)).containsExactly(1);
    }

    @Test
    @DisplayName("구간에 시세가 빠진 종목은 뽑지 않는다")
    void 구멍_있는_종목은_후보가_아니다() {
        // 후보는 A0010~A0030 셋뿐이다(A0040 은 구멍, A0015 는 우선주). 넷을 달라고 하면 만들지 않는다.
        assertThatThrownBy(() -> createService.create(spec(DAYS.get(0), 4, 5, 42L)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("구간 전체 시세가 있는 종목이 3 개");
    }

    @Test
    @DisplayName("같은 seed 는 같은 종목을 뽑는다 — 대회 공정성의 근거다")
    void seed_가_같으면_같은_종목이다() {
        Season first = createService.create(spec(DAYS.get(0), 2, 5, 7L));
        Season second = createService.create(spec(DAYS.get(0), 2, 5, 7L));

        assertThat(realCodes(first.getId())).isEqualTo(realCodes(second.getId()));
    }

    @Test
    @DisplayName("섹터 밖 종목은 섞이지 않는다")
    void 섹터로만_뽑는다() {
        Season season = createService.create(spec(DAYS.get(0), 3, 5, 42L));

        assertThat(realCodes(season.getId())).containsExactlyInAnyOrder("A0010", "A0020", "A0030");
    }

    @Test
    @DisplayName("우선주는 뽑지 않는다 — 보통주와 같이 움직여 분산이 무의미해진다")
    void 우선주는_후보가_아니다() {
        Season season = createService.create(spec(DAYS.get(0), 3, 5, 42L));

        assertThat(realCodes(season.getId())).doesNotContain("A0015");
    }

    @Test
    @DisplayName("수집된 영업일보다 긴 시즌은 만들지 않는다 — 짧은 시즌을 조용히 만들지 않는다")
    void 영업일이_부족하면_만들지_않는다() {
        assertThatThrownBy(() -> createService.create(spec(DAYS.get(0), 2, 6, 42L)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("5 일만 수집돼 있다");
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

    /** 그 종목의 원천 종가 다섯 개. 종목마다 1000·2000·3000 대에서 하루 1 씩 오른다. */
    private static BigDecimal[] sourceCloses(String code) {
        int base = Integer.parseInt(code.substring(1, 4)) * 1000;
        BigDecimal[] closes = new BigDecimal[DAYS.size()];
        for (int i = 0; i < closes.length; i++) {
            closes[i] = new BigDecimal(base + i).setScale(2);
        }
        return closes;
    }

    @SuppressWarnings("unchecked")
    private List<Object[]> prices(Long seasonId, String displayName) {
        return em.createNativeQuery(
                        """
                        SELECT p.game_day, p.close FROM season_prices p
                        JOIN season_tickers t ON t.id = p.ticker_id
                        WHERE t.season_id = ? AND t.display_name = ?
                        ORDER BY p.game_day
                        """)
                .setParameter(1, seasonId)
                .setParameter(2, displayName)
                .getResultList();
    }

    private List<BigDecimal> closes(Long seasonId, String displayName) {
        return prices(seasonId, displayName).stream()
                .map(row -> ((BigDecimal) row[1]).setScale(2))
                .toList();
    }

    private List<Integer> gameDays(Long seasonId, String displayName) {
        return prices(seasonId, displayName).stream()
                .map(row -> ((Number) row[0]).intValue())
                .toList();
    }

    /** 가명 → 실제 종목코드. 정답 표라 응답에는 안 나가지만 검증에는 필요하다. */
    @SuppressWarnings("unchecked")
    private Map<String, String> aliasToCode(Long seasonId) {
        List<Object[]> rows = em.createNativeQuery(
                        """
                        SELECT display_name, real_stock_code FROM season_tickers
                        WHERE season_id = ? ORDER BY display_name
                        """)
                .setParameter(1, seasonId)
                .getResultList();
        Map<String, String> aliases = new LinkedHashMap<>();
        for (Object[] row : rows) {
            aliases.put((String) row[0], (String) row[1]);
        }
        return aliases;
    }

    @SuppressWarnings("unchecked")
    private List<String> realCodes(Long seasonId) {
        return em.createNativeQuery(
                        """
                        SELECT real_stock_code FROM season_tickers
                        WHERE season_id = ? ORDER BY display_name
                        """)
                .setParameter(1, seasonId)
                .getResultList();
    }

    private void insertStock(String code, String sector) {
        em.createNativeQuery(
                        """
                        INSERT INTO stocks (code, name, market, sector, listed)
                        VALUES (?, ?, 'KOSPI', ?, true)
                        """)
                .setParameter(1, code)
                .setParameter(2, code + "종목")
                .setParameter(3, sector)
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
