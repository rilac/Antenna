package ssafy.a507.backend.domain.market.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import ssafy.a507.backend.domain.market.client.PublicDataException;
import ssafy.a507.backend.domain.market.client.PublicDataProperties;
import ssafy.a507.backend.domain.market.client.PublicDataStockClient;
import ssafy.a507.backend.domain.market.client.StockPriceRow;
import ssafy.a507.backend.domain.market.entity.DailyQuote;
import ssafy.a507.backend.domain.market.entity.IngestRun;
import ssafy.a507.backend.domain.market.entity.Stock;
import ssafy.a507.backend.domain.market.repository.DailyQuoteRepository;
import ssafy.a507.backend.domain.market.repository.IngestRunRepository;
import ssafy.a507.backend.domain.market.repository.MarketUpsertRepository;
import ssafy.a507.backend.domain.market.repository.StockRepository;

/**
 * ANT-DATA-02 의 인수 조건 — 회차 기록, 실패 후 재시도, 공휴일 정상 처리, 역주행 보정.
 *
 * <p>포털만 가짜고 DB 는 진짜다. 멱등 upsert 가 PostgreSQL 문법에 걸려 있어(ANT-DATA-01)
 * 적재 경로를 H2 로 확인하면 검증이 되지 않는다.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({
    MarketUpsertRepository.class,
    DailyQuoteIngestService.class,
    DailyQuoteIngestIntegrationTest.PropertiesConfig.class
})
@Testcontainers(disabledWithoutDocker = true)
class DailyQuoteIngestIntegrationTest {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:18-alpine");

    private static final LocalDate BASE_DATE = LocalDate.of(2026, 8, 28);
    private static final LocalDate MONDAY = LocalDate.of(2026, 8, 31);

    @TestConfiguration
    static class PropertiesConfig {
        @Bean
        PublicDataProperties publicDataProperties() {
            return new PublicDataProperties("test-key", null, 100, 10);
        }
    }

    @MockitoBean PublicDataStockClient client;

    @Autowired DailyQuoteIngestService service;
    @Autowired MarketUpsertRepository marketUpsertRepository;
    @Autowired IngestRunRepository ingestRunRepository;
    @Autowired DailyQuoteRepository dailyQuoteRepository;
    @Autowired StockRepository stockRepository;

    @PersistenceContext EntityManager entityManager;

    @Test
    @DisplayName("받아 온 하루치를 적재하고 회차를 SUCCESS 로 남긴다")
    void 하루치를_적재한다() {
        given(client.fetchDay(BASE_DATE))
                .willReturn(List.of(row("005930", "삼성전자", Stock.Market.KOSPI, "71500")));

        IngestRun run = service.ingestDate(BASE_DATE);
        entityManager.clear();

        assertThat(run.getStatus()).isEqualTo(IngestRun.Status.SUCCESS);
        assertThat(run.getQuoteCount()).isEqualTo(1);
        assertThat(run.getFinishedAt()).isNotNull();

        DailyQuote quote =
                dailyQuoteRepository.findByStock_CodeAndTradeDate("005930", BASE_DATE).orElseThrow();
        assertThat(quote.getClose()).isEqualByComparingTo("71500");
        assertThat(stockRepository.findById("005930"))
                .as("일봉의 FK 대상이라 종목 마스터가 먼저 들어가야 한다")
                .isPresent();
    }

    @Test
    @DisplayName("공휴일은 EMPTY 로 남는다 — 데이터가 없는 것은 실패가 아니다")
    void 공휴일을_정상_처리한다() {
        given(client.fetchDay(BASE_DATE)).willReturn(List.of());

        IngestRun run = service.ingestDate(BASE_DATE);
        entityManager.clear();

        assertThat(run.getStatus()).isEqualTo(IngestRun.Status.EMPTY);
        assertThat(run.getQuoteCount()).isZero();
        assertThat(run.getMessage()).isNull();
        assertThat(dailyQuoteRepository.count()).isZero();
    }

    @Test
    @DisplayName("실패한 날짜는 FAILED 로 남고 다음 회차가 같은 행을 성공으로 덮는다")
    void 실패하면_다음_회차가_다시_집는다() {
        given(client.fetchDay(BASE_DATE))
                .willThrow(new PublicDataException("포털이 오류를 돌려줬다 — resultCode=22"))
                .willReturn(List.of(row("005930", "삼성전자", Stock.Market.KOSPI, "71500")));

        IngestRun failed = service.ingestDate(BASE_DATE);

        assertThat(failed.getStatus()).isEqualTo(IngestRun.Status.FAILED);
        assertThat(failed.getMessage()).contains("resultCode=22");
        assertThat(failed.getAttemptCount()).isEqualTo(1);
        assertThat(failed.isCollected()).as("종결이 아니므로 다음 회차의 대상으로 남는다").isFalse();

        IngestRun retried = service.ingestDate(BASE_DATE);
        entityManager.clear();

        assertThat(retried.getStatus()).isEqualTo(IngestRun.Status.SUCCESS);
        assertThat(retried.getAttemptCount()).isEqualTo(2);
        assertThat(retried.getMessage()).as("성공하면 앞 회차의 실패 사유를 지운다").isNull();
        assertThat(ingestRunRepository.count()).as("날짜당 회차 기록은 하나다").isOne();
        assertThat(dailyQuoteRepository.count()).isOne();
    }

    /** 월요일 13시 회차. 주말은 부르지 않고, 이미 받은 날은 건너뛰고, 금요일치를 소급해 집는다. */
    @Test
    @DisplayName("창 안에서 못 받은 영업일만 오래된 순서로 집는다")
    void 누락_구간을_역주행으로_메운다() {
        given(client.fetchDay(any())).willReturn(List.of());
        seedCollected(LocalDate.of(2026, 8, 24));
        seedCollected(LocalDate.of(2026, 8, 25));
        seedCollected(LocalDate.of(2026, 8, 26));
        seedCollected(LocalDate.of(2026, 8, 27));

        service.ingestPending(MONDAY);

        InOrder inOrder = inOrder(client);
        inOrder.verify(client).fetchDay(LocalDate.of(2026, 8, 20));
        inOrder.verify(client).fetchDay(LocalDate.of(2026, 8, 21));
        inOrder.verify(client).fetchDay(LocalDate.of(2026, 8, 28));
        verify(client, times(3)).fetchDay(any());
    }

    @Test
    @DisplayName("인증키가 없으면 포털을 부르지 않고 회차도 남기지 않는다")
    void 키가_없으면_건너뛴다() {
        DailyQuoteIngestService unconfigured =
                new DailyQuoteIngestService(
                        client,
                        marketUpsertRepository,
                        ingestRunRepository,
                        new PublicDataProperties("", null, 100, 10));

        assertThat(unconfigured.ingestPending(MONDAY)).isEmpty();

        verifyNoInteractions(client);
        assertThat(ingestRunRepository.count()).isZero();
    }

    private void seedCollected(LocalDate baseDate) {
        IngestRun run = IngestRun.of(baseDate);
        run.start(Instant.parse("2026-08-28T04:00:00Z"));
        run.succeed(1, Instant.parse("2026-08-28T04:00:10Z"));
        ingestRunRepository.save(run);
    }

    private StockPriceRow row(String code, String name, Stock.Market market, String close) {
        return new StockPriceRow(
                code,
                name,
                market,
                BASE_DATE,
                new BigDecimal("71000"),
                new BigDecimal("71800"),
                new BigDecimal("70900"),
                new BigDecimal(close),
                12_345_678L);
    }
}
