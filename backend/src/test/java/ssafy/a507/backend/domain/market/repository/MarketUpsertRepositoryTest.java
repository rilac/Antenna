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
import org.springframework.jdbc.core.JdbcTemplate;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import ssafy.a507.backend.domain.market.dto.DailyQuoteUpsert;
import ssafy.a507.backend.domain.market.dto.StockUpsert;
import ssafy.a507.backend.domain.market.entity.DailyQuote;
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
    @Autowired JdbcTemplate jdbcTemplate;

    /** JDBC 로 쓴 값을 JPA 로 읽으려면 영속성 컨텍스트를 비워야 한다 — 안 그러면 캐시된 옛 행이 온다. */
    @PersistenceContext EntityManager entityManager;

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
        marketUpsertRepository.upsertStocks(List.of(new StockUpsert(SAMSUNG, "삼성전자우", null)));
        entityManager.clear();

        Stock saved = stockRepository.findById(SAMSUNG).orElseThrow();
        assertThat(saved.getName()).isEqualTo("삼성전자우");
        assertThat(saved.getSector()).as("시세 수집이 CSV 시드를 덮으면 안 된다").isEqualTo("반도체");
        assertThat(saved.getMarket()).as("값이 안 온 회차가 이미 아는 시장을 지우면 안 된다").isEqualTo(Stock.Market.KOSPI);
        assertThat(saved.isListed()).isTrue();
    }

    private void givenStock(String code, String name, Stock.Market market) {
        marketUpsertRepository.upsertStocks(List.of(new StockUpsert(code, name, market)));
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
