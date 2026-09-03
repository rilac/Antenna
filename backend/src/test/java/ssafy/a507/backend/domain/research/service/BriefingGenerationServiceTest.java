package ssafy.a507.backend.domain.research.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import jakarta.persistence.EntityManager;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Transactional;
import ssafy.a507.backend.common.ai.AiClient;
import ssafy.a507.backend.common.ai.AiException;
import ssafy.a507.backend.domain.market.repository.StockRepository;
import ssafy.a507.backend.domain.research.entity.AiBriefing;
import ssafy.a507.backend.domain.research.repository.AiBriefingRepository;

/**
 * 배치 B6 의 브리핑 갈래를 본다.
 *
 * <p>두 가지가 중심이다. <b>수치는 코드가 계산해 프롬프트에 넣는다</b> — 모델이 받은 재료에 등락률·
 * 부채비율이 이미 들어 있어야 한다. <b>D16</b> — 확률·가능성 수치가 든 응답은 저장되지 않아야 한다.
 *
 * <p>GMS 는 스텁이다 — 테스트는 외부와 통신하지 않는다.
 */
@SpringBootTest(properties = {"app.ai.api-key=test-key", "app.ai.prompt-version=v1"})
@Transactional
@DisplayName("AI 브리핑 생성 (B6)")
class BriefingGenerationServiceTest {

    private static final String SAMSUNG = "005930";
    private static final LocalDate TARGET = LocalDate.of(2026, 9, 2);
    private static final String REPLY = "헤드라인 한 줄\n\n본문 첫 문단.\n\n본문 둘째 문단.";

    @Autowired BriefingGenerationService generationService;
    @Autowired AiBriefingRepository aiBriefingRepository;
    @Autowired StockRepository stockRepository;
    @Autowired EntityManager em;

    @MockitoBean AiClient aiClient;

    @BeforeEach
    void setUp() {
        em.createNativeQuery("INSERT INTO stocks (code, name, listed) VALUES (?, ?, TRUE)")
                .setParameter(1, SAMSUNG)
                .setParameter(2, "삼성전자")
                .executeUpdate();
        em.flush();
    }

    @Test
    @DisplayName("등락률·거래량·52주·재무·뉴스를 코드가 계산해 재료로 넣고, 첫 줄을 헤드라인으로 저장한다")
    void 재료_주입() {
        seedQuotes(25);
        seedIndex();
        seedFinancial(2025, 1_000_000_000_000L, 100_000_000_000L, 20_000_000_000L, 100_000_000_000L, 200_000_000_000L);
        seedFinancial(2024, 900_000_000_000L, 90_000_000_000L, 18_000_000_000L, 90_000_000_000L, 180_000_000_000L);
        given(aiClient.complete(anyString(), anyString())).willReturn(REPLY);

        assertThat(generationService.generate()).isEqualTo(2);
        em.flush();

        ArgumentCaptor<String> input = ArgumentCaptor.forClass(String.class);
        verify(aiClient, times(2)).complete(anyString(), input.capture());
        String market = input.getAllValues().get(0);
        String stock = input.getAllValues().get(1);

        assertThat(market).contains("코스피 종가 3200", "1영업일 +0.31%", "상승 상위: 삼성전자 +0.14%");
        assertThat(stock)
                .contains("종목: 삼성전자 (005930)")
                .contains("종가 72400원")
                .contains("1영업일 +0.14%", "5영업일 +0.70%", "20영업일 +2.84%")
                .contains("최근 20영업일 평균의 1.1배")
                .contains("52주 종가 최고 72400원 · 최저 70000원 · 52주 범위 내 위치 100% (0%=최저, 100%=최고)")
                .contains("연간 재무(CFS, 억원): 2025년 매출 10000 영업이익 1000 순이익 200; 2024년")
                .contains("부채비율 50.0% · ROE 10.0% (2025년 기준)")
                .contains("최근 뉴스 요약: (없음)");

        List<AiBriefing> saved = aiBriefingRepository.findAll();
        assertThat(saved).hasSize(2);
        assertThat(saved).allSatisfy(b -> {
            assertThat(b.getTargetDate()).isEqualTo(TARGET);
            assertThat(b.getHeadline()).isEqualTo("헤드라인 한 줄");
            assertThat(b.getBody()).isEqualTo("본문 첫 문단.\n\n본문 둘째 문단.");
            assertThat(b.getPromptVersion()).isEqualTo("v1");
        });
        assertThat(saved).filteredOn(b -> b.getScope() == AiBriefing.Scope.MARKET)
                .singleElement().extracting(AiBriefing::getStock).isNull();
        assertThat(saved).filteredOn(b -> b.getScope() == AiBriefing.Scope.STOCK)
                .singleElement().satisfies(b -> assertThat(b.getStock().getCode()).isEqualTo(SAMSUNG));
    }

    @Test
    @DisplayName("같은 기준일·같은 세대가 이미 있으면 다시 부르지 않는다 — 주말에 도는 회차가 한도를 태우지 않는다")
    void 재실행_멱등() {
        seedQuotes(3);
        seedIndex();
        given(aiClient.complete(anyString(), anyString())).willReturn(REPLY);

        generationService.generate();
        em.flush();
        int second = generationService.generate();

        assertThat(second).isZero();
        verify(aiClient, times(2)).complete(anyString(), anyString());
    }

    @Test
    @DisplayName("세대가 뒤처진 행은 제자리에서 다시 쓴다 — id 가 유지된다")
    void 세대_재생성() {
        seedQuotes(3);
        seedIndex();
        AiBriefing old = aiBriefingRepository.save(AiBriefing.of(
                AiBriefing.Scope.STOCK, stockRepository.getReferenceById(SAMSUNG), TARGET, "옛 제목", "옛 본문", "v0"));
        AiBriefing oldMarket = aiBriefingRepository.save(AiBriefing.of(
                AiBriefing.Scope.MARKET, null, TARGET, "옛 시장", "옛 본문", "v0"));
        em.flush();
        given(aiClient.complete(anyString(), anyString())).willReturn(REPLY);

        assertThat(generationService.generate()).isEqualTo(2);
        em.flush();

        assertThat(aiBriefingRepository.findAll()).hasSize(2)
                .extracting(AiBriefing::getId).containsExactlyInAnyOrder(old.getId(), oldMarket.getId());
        assertThat(aiBriefingRepository.findById(old.getId()).orElseThrow())
                .satisfies(b -> {
                    assertThat(b.getHeadline()).isEqualTo("헤드라인 한 줄");
                    assertThat(b.getPromptVersion()).isEqualTo("v1");
                });
    }

    @Test
    @DisplayName("D16 — 확률·가능성 수치가 든 응답은 버린다")
    void D16_가드() {
        seedQuotes(3);
        given(aiClient.complete(anyString(), contains("종목:"))).willReturn("제목\n\n단기 하락 68% 전망이다.");

        assertThat(generationService.generate()).isZero();
        assertThat(aiBriefingRepository.findAll()).isEmpty();
    }

    @Test
    @DisplayName("D16 패턴 — 방향+확률, 확률+수치, 방향+수치는 잡고, 사실 서술과 뉴스 속 '확률'은 통과시킨다")
    void D16_패턴() {
        var p = BriefingGenerationService.FORBIDDEN;
        assertThat(p.matcher("상승 확률이 높다").find()).isTrue();
        assertThat(p.matcher("오를 가능성이 60%다").find()).isTrue();
        assertThat(p.matcher("반등할 가능성은 커 보인다").find()).isTrue();
        assertThat(p.matcher("확률 70%").find()).isTrue();
        assertThat(p.matcher("하락 68%").find()).isTrue();
        assertThat(p.matcher("5영업일간 3.2% 하락했다").find()).isFalse();
        assertThat(p.matcher("부채비율은 50.0%다").find()).isFalse();
        assertThat(p.matcher("금리 인하 확률이 높아졌다는 보도가 있었다").find()).isFalse();
        assertThat(p.matcher("확인해 볼 가능성 2가지가 있다").find()).isFalse();
    }

    @Test
    @DisplayName("본문 없이 헤드라인 한 줄만 오면 버린다 — 저장하면 현 세대로 굳어 다시 안 만든다")
    void 본문_없음() {
        seedQuotes(3);
        given(aiClient.complete(anyString(), anyString())).willReturn("헤드라인만 왔다.");

        assertThat(generationService.generate()).isZero();
        assertThat(aiBriefingRepository.findAll()).isEmpty();
    }

    @Test
    @DisplayName("기준일 뒤에 나온 뉴스는 재료에 넣지 않는다 — D-1 시세에 D 기사가 붙으면 수치와 요약이 어긋난다")
    void 뉴스_기준일_경계() {
        seedQuotes(3);
        seedNews("n-old", "기준일 기사 요약.", TARGET.atTime(15, 0).atZone(java.time.ZoneId.of("Asia/Seoul")).toInstant());
        seedNews("n-new", "다음 날 기사 요약.", TARGET.plusDays(1).atTime(9, 0).atZone(java.time.ZoneId.of("Asia/Seoul")).toInstant());
        given(aiClient.complete(anyString(), anyString())).willReturn(REPLY);

        generationService.generate();

        ArgumentCaptor<String> input = ArgumentCaptor.forClass(String.class);
        verify(aiClient).complete(anyString(), input.capture());
        assertThat(input.getValue()).contains("- 2026-09-02 · 기준일 기사 요약.").doesNotContain("다음 날 기사 요약.");
    }

    @Test
    @DisplayName("기준일 시세가 없는 상장 종목은 대상에서 빠진다 — stocks 표에 쌓인 옛 멤버를 헛돌지 않는다")
    void 대상은_기준일_시세_있는_종목() {
        em.createNativeQuery("INSERT INTO stocks (code, name, listed) VALUES ('000020', '옛멤버', TRUE)").executeUpdate();
        seedQuotes(3);
        given(aiClient.complete(anyString(), anyString())).willReturn(REPLY);

        assertThat(generationService.generate()).isEqualTo(1);
        verify(aiClient).complete(anyString(), contains("종목: 삼성전자"));
        verify(aiClient, never()).complete(anyString(), contains("옛멤버"));
    }

    @Test
    @DisplayName("시장 브리핑이 실패해도 종목은 계속 간다")
    void 실패_격리() {
        seedQuotes(3);
        seedIndex();
        given(aiClient.complete(anyString(), contains("코스피"))).willThrow(new AiException("GMS 오류"));
        given(aiClient.complete(anyString(), contains("종목:"))).willReturn(REPLY);

        assertThat(generationService.generate()).isEqualTo(1);
    }

    @Test
    @DisplayName("기준일 시세가 없는 종목은 만들지 않는다 — 재료 없이 만들면 지어낸 글이다")
    void 시세_없음() {
        assertThat(generationService.generate()).isZero();
        verify(aiClient, never()).complete(anyString(), anyString());
    }

    // ── 재료 ─────────────────────────────────────────────────

    /** 종가 70000 부터 하루 100원씩 오르는 {@code days}개 일봉. 마지막 날이 기준일이다. */
    private void seedQuotes(int days) {
        for (int i = 0; i < days; i++) {
            LocalDate date = TARGET.minusDays(days - 1 - i);
            // 마지막 날 거래량만 1.1배로 튀운다.
            long volume = i == days - 1 ? 11_000_000L : 10_000_000L;
            em.createNativeQuery(
                            "INSERT INTO daily_quotes (stock_code, trade_date, close, volume, collected_at)"
                                    + " VALUES (?, ?, ?, ?, ?)")
                    .setParameter(1, SAMSUNG)
                    .setParameter(2, date)
                    .setParameter(3, new BigDecimal(70000 + i * 100))
                    .setParameter(4, volume)
                    .setParameter(5, Instant.now())
                    .executeUpdate();
        }
        em.flush();
    }

    /** 코스피 6점 — 3190 에서 기준일 3200 까지. 1영업일 +0.31%. */
    private void seedIndex() {
        for (int i = 0; i < 6; i++) {
            em.createNativeQuery("INSERT INTO index_quotes (index_code, trade_date, close) VALUES ('KOSPI', ?, ?)")
                    .setParameter(1, TARGET.minusDays(5 - i))
                    .setParameter(2, new BigDecimal(3150 + i * 10))
                    .executeUpdate();
        }
        em.flush();
    }

    private void seedNews(String externalId, String summary, Instant publishedAt) {
        var doc = ssafy.a507.backend.domain.research.entity.ResearchDocument.collected(
                stockRepository.getReferenceById(SAMSUNG),
                ssafy.a507.backend.domain.research.entity.ResearchDocument.Source.NEWS,
                externalId, "제목", "https://news.example.com/" + externalId, "발췌", publishedAt);
        doc.summarize(summary, "v1");
        em.persist(doc);
        em.flush();
    }

    private void seedFinancial(int year, long revenue, long op, long net, long liabilities, long equity) {
        em.createNativeQuery(
                        "INSERT INTO corp_financials (stock_code, fiscal_year, quarter, fs_div, revenue,"
                                + " operating_profit, net_income, total_liabilities, total_equity, updated_at)"
                                + " VALUES (?, ?, 4, 'CFS', ?, ?, ?, ?, ?, ?)")
                .setParameter(1, SAMSUNG)
                .setParameter(2, year)
                .setParameter(3, revenue)
                .setParameter(4, op)
                .setParameter(5, net)
                .setParameter(6, liabilities)
                .setParameter(7, equity)
                .setParameter(8, Instant.now())
                .executeUpdate();
        em.flush();
    }
}
