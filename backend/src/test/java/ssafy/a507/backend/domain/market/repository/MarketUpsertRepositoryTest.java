package ssafy.a507.backend.domain.market.repository;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.Limit;
import org.springframework.jdbc.core.JdbcTemplate;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import ssafy.a507.backend.domain.market.dto.DailyQuoteUpsert;
import ssafy.a507.backend.domain.market.dto.IndexQuoteUpsert;
import ssafy.a507.backend.domain.market.dto.StockUpsert;
import ssafy.a507.backend.domain.market.entity.DailyQuote;
import ssafy.a507.backend.domain.market.entity.IndexQuote;
import ssafy.a507.backend.domain.market.entity.IndexQuote.IndexCode;
import ssafy.a507.backend.domain.market.entity.Stock;

/**
 * ANT-DATA-01 의 인수 조건 — 같은 날짜를 다시 수집해도 중복·변형이 없어야 한다.
 *
 * <p>다른 테스트와 달리 H2 를 쓰지 않는다. 멱등성을 떠받치는 것이 PostgreSQL 의
 * {@code ON CONFLICT} 이고 H2 는 그 문법을 지원하지 않아, H2 로 통과시키면 정작 검증하려던
 * 것을 검증하지 못한다. 이미지는 docker-compose 가 쓰는 것과 같은 태그라 배포 러너에 이미
 * 받아져 있고, 도커가 없는 곳에서는 {@code disabledWithoutDocker} 로 건너뛴다.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(MarketUpsertRepository.class)
@Testcontainers(disabledWithoutDocker = true)
class MarketUpsertRepositoryTest {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:18-alpine");

    private static final String SAMSUNG = "005930";
    private static final LocalDate TRADE_DATE = LocalDate.of(2026, 8, 28);
    private static final Instant FIRST_RUN = Instant.parse("2026-08-28T04:00:00Z");
    private static final Instant SECOND_RUN = Instant.parse("2026-08-28T05:30:00Z");

    @Autowired MarketUpsertRepository marketUpsertRepository;
    @Autowired DailyQuoteRepository dailyQuoteRepository;
    @Autowired StockRepository stockRepository;
    @Autowired IndexQuoteRepository indexQuoteRepository;
    @Autowired JdbcTemplate jdbcTemplate;

    /** JDBC 로 쓴 값을 JPA 로 읽으려면 영속성 컨텍스트를 비워야 한다 — 안 그러면 캐시된 옛 행이 온다. */
    @PersistenceContext EntityManager entityManager;

    // ── 지수·환율 (ANT-DATA-04) ─────────────────────────────

    @Test
    @DisplayName("지수·환율은 (지수, 영업일) 로 멱등하다 — 다시 받으면 종가만 바뀌고 환율 소수 넷째 자리가 남는다")
    void 지수_재수집이_멱등하다() {
        marketUpsertRepository.upsertIndexQuotes(List.of(
                new IndexQuoteUpsert(IndexCode.KOSPI, TRADE_DATE, new BigDecimal("6835.8")),
                new IndexQuoteUpsert(IndexCode.USDKRW, TRADE_DATE, new BigDecimal("1385.1425"))));
        // 다음 회차가 같은 날을 다시 받았다 — 코스피 종가가 정정됐다.
        marketUpsertRepository.upsertIndexQuotes(List.of(
                new IndexQuoteUpsert(IndexCode.KOSPI, TRADE_DATE, new BigDecimal("6850.1"))));
        entityManager.clear();

        assertThat(indexQuoteRepository.count()).isEqualTo(2);

        IndexQuote kospi = indexQuoteRepository
                .findByIndexCodeOrderByTradeDateDesc(IndexCode.KOSPI, Limit.of(1)).get(0);
        assertThat(kospi.getClose()).isEqualByComparingTo("6850.1");

        IndexQuote usd = indexQuoteRepository
                .findByIndexCodeOrderByTradeDateDesc(IndexCode.USDKRW, Limit.of(1)).get(0);
        assertThat(usd.getClose()).isEqualByComparingTo("1385.1425");
        assertThat(usd.getClose().scale()).as("환율은 numeric(14,4) 그대로다").isEqualTo(4);
    }

    // ── 종목·일봉 (ANT-DATA-01) ─────────────────────────────

    @Test
    @DisplayName("같은 날짜를 다시 수집해도 행이 늘지 않고 값만 최신으로 바뀐다")
    void 같은_날짜_재수집이_멱등하다() {
        givenStock(SAMSUNG, "삼성전자", Stock.Market.KOSPI);

        marketUpsertRepository.upsertDailyQuotes(List.of(quote(SAMSUNG, "70000")), FIRST_RUN);
        marketUpsertRepository.upsertDailyQuotes(List.of(quote(SAMSUNG, "71500")), SECOND_RUN);
        entityManager.clear();

        assertThat(dailyQuoteRepository.count()).isOne();

        DailyQuote saved = findQuote(SAMSUNG);
        assertThat(saved.getClose()).isEqualByComparingTo("71500");
        assertThat(saved.getCollectedAt())
                .as("수집 시각도 최신 회차로 바뀌어야 장애 추적이 된다")
                .isEqualTo(SECOND_RUN);
    }

    @Test
    @DisplayName("한 회차 안에 같은 종목·날짜가 두 번 들어와도 문장이 깨지지 않는다")
    void 한_배치_안의_중복도_흡수한다() {
        givenStock(SAMSUNG, "삼성전자", Stock.Market.KOSPI);

        // 포털 응답은 페이지 경계에서 같은 행을 다시 주는 일이 있다.
        marketUpsertRepository.upsertDailyQuotes(
                List.of(quote(SAMSUNG, "70000"), quote(SAMSUNG, "71500")), FIRST_RUN);
        entityManager.clear();

        assertThat(dailyQuoteRepository.count()).isOne();
        assertThat(findQuote(SAMSUNG).getClose()).isEqualByComparingTo("71500");
    }

    @Test
    @DisplayName("가격은 numeric(14,2) 그대로 — 반올림도 부동소수점 오차도 없다")
    void 가격이_소수점까지_보존된다() {
        givenStock(SAMSUNG, "삼성전자", Stock.Market.KOSPI);

        marketUpsertRepository.upsertDailyQuotes(
                List.of(new DailyQuoteUpsert(
                        SAMSUNG,
                        TRADE_DATE,
                        new BigDecimal("1234.56"),
                        new BigDecimal("99999999999.99"),
                        new BigDecimal("0.01"),
                        new BigDecimal("1234.56"),
                        12_345_678L)),
                FIRST_RUN);
        entityManager.clear();

        DailyQuote saved = findQuote(SAMSUNG);
        assertThat(saved.getClose()).isEqualByComparingTo("1234.56");
        assertThat(saved.getHigh()).isEqualByComparingTo("99999999999.99");
        assertThat(saved.getLow()).isEqualByComparingTo("0.01");
        assertThat(saved.getClose().scale())
                .as("double 로 오갔다면 스케일이 유지되지 않는다")
                .isEqualTo(2);
    }

    @Test
    @DisplayName("수집분에 없는 종목의 행과 과거 시세는 그대로 남는다")
    void 상장폐지_종목의_행을_지우지_않는다() {
        givenStock("900000", "폐지예정", Stock.Market.KOSDAQ);
        marketUpsertRepository.upsertDailyQuotes(List.of(quote("900000", "1200")), FIRST_RUN);

        // 다음 회차 응답에는 이 종목이 아예 없다 — 상장폐지된 뒤의 모습이다.
        givenStock(SAMSUNG, "삼성전자", Stock.Market.KOSPI);
        marketUpsertRepository.upsertDailyQuotes(List.of(quote(SAMSUNG, "70000")), SECOND_RUN);
        entityManager.clear();

        assertThat(stockRepository.count()).isEqualTo(2);
        assertThat(dailyQuoteRepository.count()).isEqualTo(2);
        assertThat(findQuote("900000").getClose())
                .as("폐지 종목의 과거 시세는 판정 근거로 남아 있어야 한다")
                .isEqualByComparingTo("1200");
    }

    @Test
    @DisplayName("종목 재수집은 이름만 갱신하고 섹터·시장은 지키다")
    void 종목_재수집이_기존_값을_지우지_않는다() {
        givenStock(SAMSUNG, "삼성전자", Stock.Market.KOSPI);
        // 섹터의 원천은 시세 API 가 아니라 KRX 업종분류 CSV 다. 그 시드를 흉내 낸다.
        jdbcTemplate.update("UPDATE stocks SET sector = ? WHERE code = ?", "반도체", SAMSUNG);

        // 시장 값이 비어 온 회차. 이름만 바뀌었다.
        marketUpsertRepository.upsertStocks(List.of(new StockUpsert(SAMSUNG, "삼성전자우", null, null)));
        entityManager.clear();

        Stock saved = stockRepository.findById(SAMSUNG).orElseThrow();
        assertThat(saved.getName()).isEqualTo("삼성전자우");
        assertThat(saved.getSector()).as("시세 수집이 CSV 시드를 덮으면 안 된다").isEqualTo("반도체");
        assertThat(saved.getMarket()).as("값이 안 온 회차가 이미 아는 시장을 지우면 안 된다").isEqualTo(Stock.Market.KOSPI);
        assertThat(saved.isListed()).isTrue();
    }

    @Test
    @DisplayName("상장주식수는 값이 온 회차만 덮는다 — 비어 온 회차가 이미 아는 값을 지우면 안 된다")
    void 상장주식수를_적재하고_빈_회차가_지우지_않는다() {
        marketUpsertRepository.upsertStocks(
                List.of(new StockUpsert(SAMSUNG, "삼성전자", Stock.Market.KOSPI, 5_969_782_550L)));
        marketUpsertRepository.upsertStocks(
                List.of(new StockUpsert(SAMSUNG, "삼성전자", Stock.Market.KOSPI, null)));
        entityManager.clear();

        assertThat(stockRepository.findById(SAMSUNG).orElseThrow().getListedShares())
                .isEqualTo(5_969_782_550L);
    }

    // ── PER·PBR 파생 (ANT-DATA-03) ──────────────────────────

    @Test
    @DisplayName("PER·PBR 은 종목별 마지막 종가 × 상장주식수를 최신 연간 순이익·자본총계로 나눈 값이다")
    void 밸류에이션을_다시_계산한다() {
        marketUpsertRepository.upsertStocks(List.of(
                new StockUpsert(SAMSUNG, "삼성전자", Stock.Market.KOSPI, 5_969_782_550L),
                new StockUpsert("000001", "적자기업", Stock.Market.KOSPI, 1_000L),
                new StockUpsert("000002", "재무없음", Stock.Market.KOSPI, 1_000L),
                new StockUpsert("000003", "달러재무", Stock.Market.KOSPI, 1_000L),
                new StockUpsert("000004", "주식수없음", Stock.Market.KOSPI, null)));
        marketUpsertRepository.upsertDailyQuotes(List.of(
                quote(SAMSUNG, "70000"),
                quote("000001", "1000"),
                quote("000002", "1000"),
                quote("000003", "1000"),
                quote("000004", "1000")), FIRST_RUN);
        // 다음 날 종가 — 종목별 마지막 종가가 기준이다.
        marketUpsertRepository.upsertDailyQuotes(List.of(new DailyQuoteUpsert(
                SAMSUNG, TRADE_DATE.plusDays(1), null, null, null, new BigDecimal("71500"), null)), SECOND_RUN);
        // 삼성: 최신 연도(2024)만 쓴다. 2023 은 무시.
        givenFinancial(SAMSUNG, 2023, "KRW", "15000000000000", "360000000000000");
        givenFinancial(SAMSUNG, 2024, "KRW", "34451351000000", "400000000000000");
        givenFinancial("000001", 2024, "KRW", "-5", "2000");
        givenFinancial("000003", 2024, "USD", "100", "2000");
        givenFinancial("000004", 2024, "KRW", "100", "2000");
        // 지난 회차의 값이 남아 있다 — 재료가 사라지면 지워져야 한다.
        jdbcTemplate.update("UPDATE stocks SET per = 9.99, pbr = 9.99 WHERE code = '000002'");

        marketUpsertRepository.refreshValuations();
        entityManager.clear();

        Stock samsung = stockRepository.findById(SAMSUNG).orElseThrow();
        // 71500 × 5,969,782,550 = 426,839,452,325,000 → ÷ 34,451,351,000,000 = 12.389…
        assertThat(samsung.getPer()).isEqualByComparingTo("12.39");
        assertThat(samsung.getPbr()).isEqualByComparingTo("1.07");

        Stock loss = stockRepository.findById("000001").orElseThrow();
        assertThat(loss.getPer()).as("적자면 PER 은 의미가 없다").isNull();
        assertThat(loss.getPbr()).isEqualByComparingTo("500.00");

        Stock noFinancial = stockRepository.findById("000002").orElseThrow();
        assertThat(noFinancial.getPer()).as("재무가 없으면 옛 값을 지운다").isNull();
        assertThat(noFinancial.getPbr()).isNull();

        Stock usd = stockRepository.findById("000003").orElseThrow();
        assertThat(usd.getPer()).as("원화 종가를 달러 재무로 나누면 안 된다").isNull();
        assertThat(usd.getPbr()).isNull();

        Stock noShares = stockRepository.findById("000004").orElseThrow();
        assertThat(noShares.getPer()).isNull();
        assertThat(noShares.getPbr()).isNull();
    }

    private void givenFinancial(String code, int year, String currency, String netIncome, String equity) {
        jdbcTemplate.update(
                """
                INSERT INTO corp_financials
                    (stock_code, fiscal_year, quarter, fs_div, currency, net_income, total_equity, updated_at)
                VALUES (?, ?, 4, 'CFS', ?, ?, ?, CURRENT_TIMESTAMP)
                """,
                code, year, currency, new BigDecimal(netIncome), new BigDecimal(equity));
    }

    private void givenStock(String code, String name, Stock.Market market) {
        marketUpsertRepository.upsertStocks(List.of(new StockUpsert(code, name, market, null)));
    }

    private DailyQuoteUpsert quote(String code, String close) {
        return new DailyQuoteUpsert(
                code,
                TRADE_DATE,
                new BigDecimal("69000"),
                new BigDecimal("71600"),
                new BigDecimal("68800"),
                new BigDecimal(close),
                12_345_678L);
    }

    private DailyQuote findQuote(String code) {
        return dailyQuoteRepository.findByStock_CodeAndTradeDate(code, TRADE_DATE).orElseThrow();
    }
}
