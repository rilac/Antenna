package ssafy.a507.backend.domain.research.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assumptions.assumeThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;

import jakarta.persistence.EntityManager;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import ssafy.a507.backend.common.ai.AiClient;
import ssafy.a507.backend.domain.research.entity.ResearchDocument;
import ssafy.a507.backend.domain.research.entity.ResearchPoint;
import ssafy.a507.backend.domain.research.repository.ResearchPointRepository;

/**
 * GMS 를 실제로 <b>한 번</b> 부른다(종목 1개). 운영 DB 는 건드리지 않는다 — 재료는 H2 에 합성으로 넣는다.
 *
 * <p>확인하려는 것은 파서가 아니라 <b>모델의 실제 출력 모양</b>이다. 코드펜스를 붙이는지, 우리가
 * 준 목록 안에서만 {@code documentId} 를 고르는지, D16 문구가 얼마나 섞이는지는 목으로는 알 수
 * 없다. 그래서 스파이로 원문 응답을 그대로 찍어 둔다 — 호출 수는 그대로 1이다.
 *
 * <p>키가 없으면 조용히 건너뛴다. 한도를 나눠 쓰는 키라 자동으로 돌리지 않고 사람이 시킬 때만
 * {@code --tests '*E2ELiveTest*' --rerun} 으로 돈다.
 *
 * <p>Gradle 은 표준출력을 콘솔에 흘리지 않는다. 결과는
 * {@code build/test-results/test/TEST-*.xml} 에서 {@code [E2E]} 로 찾는다.
 */
@SpringBootTest(properties = "app.ai.api-key=${AI_API_KEY:}")
@DisplayName("리서치 포인트 실호출 E2E")
class PointE2ELiveTest {

    private static final String SAMSUNG = "005930";
    private static final LocalDate TARGET = LocalDate.of(2026, 9, 2);
    private static final ZoneId KST = ZoneId.of("Asia/Seoul");

    @Autowired ResearchPointGenerationService generationService;
    @Autowired ResearchPointService researchPointService;
    @Autowired ssafy.a507.backend.domain.market.repository.StockRepository stockRepository;
    @Autowired ResearchPointRepository researchPointRepository;
    @Autowired EntityManager em;
    @Autowired PlatformTransactionManager txManager;

    @MockitoSpyBean AiClient aiClient;

    private final List<Long> documentIds = new ArrayList<>();

    @BeforeEach
    void setUp() {
        assumeThat(System.getenv("AI_API_KEY")).as("AI_API_KEY 없음").isNotBlank();
        documentIds.clear();
        new TransactionTemplate(txManager).executeWithoutResult(status -> {
            em.createNativeQuery("INSERT INTO stocks (code, name, listed) VALUES (?, ?, TRUE)")
                    .setParameter(1, SAMSUNG)
                    .setParameter(2, "삼성전자")
                    .executeUpdate();
            for (int i = 0; i < 25; i++) {
                em.createNativeQuery(
                                "INSERT INTO daily_quotes (stock_code, trade_date, close, volume, collected_at)"
                                        + " VALUES (?, ?, ?, ?, ?)")
                        .setParameter(1, SAMSUNG)
                        .setParameter(2, TARGET.minusDays(24 - i))
                        .setParameter(3, new BigDecimal(70000 + i * 150))
                        .setParameter(4, 10_000_000L + i * 200_000L)
                        .setParameter(5, Instant.now())
                        .executeUpdate();
            }
            em.createNativeQuery(
                            "INSERT INTO corp_financials (stock_code, fiscal_year, quarter, fs_div, revenue,"
                                    + " operating_profit, net_income, total_liabilities, total_equity, updated_at)"
                                    + " VALUES (?, 2025, 4, 'CFS', ?, ?, ?, ?, ?, ?)")
                    .setParameter(1, SAMSUNG)
                    .setParameter(2, 300_000_000_000_000L)
                    .setParameter(3, 32_000_000_000_000L)
                    .setParameter(4, 34_000_000_000_000L)
                    .setParameter(5, 112_000_000_000_000L)
                    .setParameter(6, 400_000_000_000_000L)
                    .setParameter(7, Instant.now())
                    .executeUpdate();
            documentIds.add(seedDocument(
                    ResearchDocument.Source.NEWS,
                    "e2e-news-1",
                    "삼성전자, 신형 메모리 양산 시작",
                    "삼성전자가 차세대 고대역폭 메모리 양산에 들어갔다고 밝혔다. 주요 고객사 공급은 다음 분기부터다.",
                    TARGET.minusDays(1)));
            documentIds.add(seedDocument(
                    ResearchDocument.Source.DART,
                    "e2e-dart-1",
                    "주요사항보고서(유형자산 취득 결정)",
                    null,
                    TARGET.minusDays(3)));
            em.flush();
        });
    }

    @Test
    @DisplayName("포인트가 실제로 생성되고 3열로 조회된다")
    void 생성_조회() {
        // 호출은 한 번 그대로 나가고, 오가는 원문만 옆에서 찍는다.
        given(aiClient.complete(anyString(), anyString())).willAnswer(invocation -> {
            System.out.println("[E2E] --- 프롬프트 재료 ---");
            System.out.println("[E2E] " + invocation.getArgument(1, String.class).replace("\n", "\n[E2E] "));
            String raw = (String) invocation.callRealMethod();
            System.out.println("[E2E] --- 원문 응답 ---");
            System.out.println("[E2E] " + raw.replace("\n", "\n[E2E] "));
            return raw;
        });

        long t0 = System.currentTimeMillis();
        int written = generationService.generate(stockRepository.findById(SAMSUNG).orElseThrow());
        System.out.println("[E2E] 포인트 " + written + "건 (" + (System.currentTimeMillis() - t0) + "ms)");
        assertThat(written).as("전부 버려졌으면 0 — 원문 응답을 본다").isPositive();

        // findAll 은 document 를 프록시로 준다 — 트랜잭션 밖이라 조회 API 와 같은 fetch join 을 쓴다.
        for (ResearchPoint p : researchPointRepository.findOn(SAMSUNG, TARGET)) {
            System.out.println("[E2E] " + p.getKind() + " | doc="
                    + (p.getDocument() == null ? "-" : p.getDocument().getId() + "/" + p.getDocument().getSource())
                    + " | " + p.getBody());
            assertThat(p.getBody()).isNotBlank();
            assertThat(p.getBody().length()).isLessThanOrEqualTo(300);
            assertThat(BriefingGenerationService.FORBIDDEN.matcher(p.getBody()).find())
                    .as("D16 위반은 저장 전에 걸러진다")
                    .isFalse();
            assertThat(p.getDocument() == null || documentIds.contains(p.getDocument().getId()))
                    .as("우리가 준 문서만 가리킨다")
                    .isTrue();
        }

        var response = researchPointService.points(SAMSUNG, null);
        System.out.println("[E2E] 3열 — 긍정 " + response.positive().size()
                + " · 위험 " + response.risk().size()
                + " · 확인 " + response.check().size());
        assertThat(response.positive().size() + response.risk().size() + response.check().size())
                .isEqualTo(written);
    }

    private Long seedDocument(
            ResearchDocument.Source source, String externalId, String title, String snippet, LocalDate publishedOn) {
        ResearchDocument document = ResearchDocument.collected(
                em.getReference(ssafy.a507.backend.domain.market.entity.Stock.class, SAMSUNG),
                source,
                externalId,
                title,
                "https://example.com/" + externalId,
                snippet,
                publishedOn.atTime(15, 0).atZone(KST).toInstant());
        em.persist(document);
        em.flush();
        return document.getId();
    }
}
