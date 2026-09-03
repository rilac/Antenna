package ssafy.a507.backend.domain.market.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
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
import org.springframework.data.domain.Limit;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import ssafy.a507.backend.domain.market.client.KoreaeximFxClient;
import ssafy.a507.backend.domain.market.client.KoreaeximProperties;
import ssafy.a507.backend.domain.market.client.MarketIndexProperties;
import ssafy.a507.backend.domain.market.client.PublicDataException;
import ssafy.a507.backend.domain.market.client.PublicDataIndexClient;
import ssafy.a507.backend.domain.market.client.PublicDataProperties;
import ssafy.a507.backend.domain.market.dto.IndexQuoteUpsert;
import ssafy.a507.backend.domain.market.entity.IndexQuote;
import ssafy.a507.backend.domain.market.entity.IndexQuote.IndexCode;
import ssafy.a507.backend.domain.market.repository.IndexQuoteRepository;
import ssafy.a507.backend.domain.market.repository.MarketUpsertRepository;

/**
 * ANT-DATA-04 의 인수 조건 — 지수·환율이 일 배치에 편입되고, 공휴일은 데이터 없음이 정상이다.
 *
 * <p>포털과 수출입은행만 가짜고 DB 는 진짜다. 멱등 upsert 가 PostgreSQL 문법에 걸려 있어
 * 적재 경로를 H2 로 확인하면 검증이 되지 않는다.
 *
 * <p>기준 주간 — 8/31(월) 9/1(화) 9/2(수) · 회차는 9/3(목)에 돈다 → 마지막 대상은 9/2.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({
    MarketUpsertRepository.class,
    IndexQuoteIngestService.class,
    IndexQuoteIngestIntegrationTest.PropertiesConfig.class
})
@Testcontainers(disabledWithoutDocker = true)
class IndexQuoteIngestIntegrationTest {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:18-alpine");

    private static final LocalDate FROM = LocalDate.of(2026, 8, 20);
    private static final int LOOKBACK_DAYS = 5;
    private static final LocalDate MON = LocalDate.of(2026, 8, 31);
    private static final LocalDate TUE = LocalDate.of(2026, 9, 1);
    private static final LocalDate WED = LocalDate.of(2026, 9, 2);
    private static final LocalDate TODAY = LocalDate.of(2026, 9, 3);

    @TestConfiguration
    static class PropertiesConfig {
        @Bean
        PublicDataProperties publicDataProperties() {
            return new PublicDataProperties("portal-key", null, 100, 10, null, 0);
        }

        @Bean
        MarketIndexProperties marketIndexProperties() {
            return new MarketIndexProperties(null, LOOKBACK_DAYS, FROM);
        }

        @Bean
        KoreaeximProperties koreaeximProperties() {
            return new KoreaeximProperties(null, "fx-key");
        }
    }

    @MockitoBean PublicDataIndexClient indexClient;
    @MockitoBean KoreaeximFxClient fxClient;

    @Autowired IndexQuoteIngestService service;
    @Autowired MarketUpsertRepository upsertRepository;
    @Autowired IndexQuoteRepository indexQuoteRepository;
    @Autowired PublicDataProperties publicDataProperties;
    @Autowired MarketIndexProperties marketIndexProperties;

    @PersistenceContext EntityManager entityManager;

    // ── 지수 ────────────────────────────────────────────────

    @Test
    @DisplayName("DB 가 비어 있으면 포털 제공 시작일부터 어제까지 한 번에 받는다")
    void 빈_DB_는_시작일부터_받는다() {
        given(indexClient.fetchRange(FROM, WED))
                .willReturn(List.of(
                        kospi(TUE, "6835.8"), kospi(WED, "6850.1"),
                        kosdaq(TUE, "821.25"), kosdaq(WED, "830.5")));

        service.ingestPending(TODAY);
        entityManager.clear();

        verify(indexClient).fetchRange(FROM, WED);
        assertThat(indexQuoteRepository.count()).isEqualTo(4);
        assertThat(latest(IndexCode.KOSPI).getClose()).isEqualByComparingTo("6850.1");
    }

    @Test
    @DisplayName("이미 받은 뒤에는 마지막 날짜에서 lookback 만큼만 되돌아가 다시 받는다")
    void 받은_뒤에는_창만_다시_받는다() {
        upsertRepository.upsertIndexQuotes(List.of(kospi(TUE, "6835.8")));
        given(indexClient.fetchRange(any(), any())).willReturn(List.of(kospi(WED, "6850.1")));

        service.ingestPending(TODAY);

        verify(indexClient).fetchRange(TUE.minusDays(LOOKBACK_DAYS), WED);
    }

    @Test
    @DisplayName("포털 호출이 실패해도 환율 수집은 계속된다")
    void 지수_실패가_환율을_막지_않는다() {
        upsertRepository.upsertIndexQuotes(List.of(kospi(WED, "6850.1")));
        given(indexClient.fetchRange(any(), any())).willThrow(new PublicDataException("포털 장애"));
        given(fxClient.fetchUsd(WED)).willReturn(Optional.of(new BigDecimal("1385.14")));

        service.ingestPending(TODAY);
        entityManager.clear();

        assertThat(latest(IndexCode.USDKRW).getClose()).isEqualByComparingTo("1385.14");
    }

    @Test
    @DisplayName("포털 키가 없으면 지수 수집을 열지 않는다")
    void 포털_키가_없으면_지수를_건너뛴다() {
        IndexQuoteIngestService withoutKey = new IndexQuoteIngestService(
                indexClient, fxClient, upsertRepository, indexQuoteRepository,
                new PublicDataProperties("", null, 100, 10, null, 0),
                marketIndexProperties,
                new KoreaeximProperties(null, "fx-key"));

        withoutKey.ingestPending(TODAY);

        verifyNoInteractions(indexClient);
    }

    // ── 환율 ────────────────────────────────────────────────

    @Test
    @DisplayName("환율은 코스피 거래일 중 빈 날만 최신부터 받는다 — 공휴일은 애초에 부르지 않는다")
    void 환율은_코스피_거래일의_빈_날만_받는다() {
        // 8/31 · 9/1 · 9/2 가 거래일이고 9/1 환율은 이미 있다.
        upsertRepository.upsertIndexQuotes(List.of(
                kospi(MON, "6800"), kospi(TUE, "6835.8"), kospi(WED, "6850.1"),
                new IndexQuoteUpsert(IndexCode.USDKRW, TUE, new BigDecimal("1380.2"))));
        given(indexClient.fetchRange(any(), any())).willReturn(List.of());
        given(fxClient.fetchUsd(WED)).willReturn(Optional.of(new BigDecimal("1385.14")));
        // 은행 휴무일 — 고시가 없다. 오류가 아니라 빈 값이다.
        given(fxClient.fetchUsd(MON)).willReturn(Optional.empty());

        service.ingestPending(TODAY);
        entityManager.clear();

        InOrder order = inOrder(fxClient);
        order.verify(fxClient).fetchUsd(WED);
        order.verify(fxClient).fetchUsd(MON);
        verify(fxClient, never()).fetchUsd(TUE);

        List<IndexQuote> usd = indexQuoteRepository.findByIndexCodeOrderByTradeDateDesc(
                IndexCode.USDKRW, Limit.of(10));
        assertThat(usd).extracting(IndexQuote::getTradeDate).containsExactly(WED, TUE);
        assertThat(usd.get(0).getClose()).isEqualByComparingTo("1385.14");
    }

    @Test
    @DisplayName("일 한도에 걸리면 그 회차를 멈춘다 — 받은 것까지는 남는다")
    void 환율_일_한도에_걸리면_멈춘다() {
        upsertRepository.upsertIndexQuotes(List.of(
                kospi(MON, "6800"), kospi(TUE, "6835.8"), kospi(WED, "6850.1")));
        given(indexClient.fetchRange(any(), any())).willReturn(List.of());
        given(fxClient.fetchUsd(WED)).willReturn(Optional.of(new BigDecimal("1385.14")));
        given(fxClient.fetchUsd(TUE))
                .willThrow(new KoreaeximFxClient.DailyLimitExceededException("한도 초과"));

        service.ingestPending(TODAY);
        entityManager.clear();

        verify(fxClient, never()).fetchUsd(MON);
        assertThat(latest(IndexCode.USDKRW).getTradeDate()).isEqualTo(WED);
    }

    @Test
    @DisplayName("하루 호출이 실패해도 나머지 날짜는 계속 받는다")
    void 환율_하루_실패가_나머지를_막지_않는다() {
        upsertRepository.upsertIndexQuotes(List.of(kospi(TUE, "6835.8"), kospi(WED, "6850.1")));
        given(indexClient.fetchRange(any(), any())).willReturn(List.of());
        given(fxClient.fetchUsd(WED)).willThrow(new PublicDataException("일시 장애"));
        given(fxClient.fetchUsd(TUE)).willReturn(Optional.of(new BigDecimal("1380.2")));

        service.ingestPending(TODAY);
        entityManager.clear();

        assertThat(latest(IndexCode.USDKRW).getTradeDate()).isEqualTo(TUE);
    }

    @Test
    @DisplayName("환율 키가 없으면 환율 수집만 건너뛴다 — 지수는 그대로 받는다")
    void 환율_키가_없으면_환율만_건너뛴다() {
        IndexQuoteIngestService withoutFxKey = new IndexQuoteIngestService(
                indexClient, fxClient, upsertRepository, indexQuoteRepository,
                publicDataProperties, marketIndexProperties, new KoreaeximProperties(null, ""));
        given(indexClient.fetchRange(any(), any())).willReturn(List.of(kospi(WED, "6850.1")));

        withoutFxKey.ingestPending(TODAY);

        verify(indexClient).fetchRange(FROM, WED);
        verifyNoInteractions(fxClient);
    }

    private static IndexQuoteUpsert kospi(LocalDate date, String close) {
        return new IndexQuoteUpsert(IndexCode.KOSPI, date, new BigDecimal(close));
    }

    private static IndexQuoteUpsert kosdaq(LocalDate date, String close) {
        return new IndexQuoteUpsert(IndexCode.KOSDAQ, date, new BigDecimal(close));
    }

    private IndexQuote latest(IndexCode code) {
        return indexQuoteRepository.findByIndexCodeOrderByTradeDateDesc(code, Limit.of(1)).get(0);
    }
}
