package ssafy.a507.backend.domain.research.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assumptions.assumeThat;

import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import ssafy.a507.backend.domain.research.dto.ResearchDocumentItemResponse;
import ssafy.a507.backend.domain.research.entity.ResearchDocument;
import ssafy.a507.backend.domain.research.repository.ResearchDocumentRepository;

/**
 * 네이버 뉴스 → H2 → GMS 요약 → 조회 API 까지 실제로 한 번 돌린다. 운영 DB 는 건드리지 않는다.
 *
 * <p>자격증명이 없으면 조용히 건너뛴다 — CI 와 키 없는 팀원의 로컬에서 빌드를 깨지 않는다.
 *
 * <p>Gradle 은 표준출력을 콘솔에 흘리지 않는다. 결과는
 * {@code build/test-results/test/TEST-*.xml} 에서 {@code [E2E]} 로 찾거나 IDE 에서 돌려 본다.
 */
@SpringBootTest(
        properties = {
            "app.naver-news.key-id=${NAVER_API_KEY_ID:}",
            "app.naver-news.key=${NAVER_API_KEY:}",
            "app.ai.api-key=${AI_API_KEY:}",
            "app.naver-news.display-per-stock=5"
        })
@DisplayName("뉴스 수집·요약 실호출 E2E")
class NewsE2ELiveTest {

    private static final String SAMSUNG = "005930";

    @Autowired NewsIngestService ingestService;
    @Autowired DocumentSummaryService summaryService;
    @Autowired ResearchDocumentService documentService;
    @Autowired ResearchDocumentRepository researchDocumentRepository;
    @Autowired EntityManager em;
    @Autowired PlatformTransactionManager txManager;

    @BeforeEach
    void setUp() {
        assumeThat(System.getenv("NAVER_API_KEY_ID")).as("NAVER_API_KEY_ID 없음").isNotBlank();
        assumeThat(System.getenv("AI_API_KEY")).as("AI_API_KEY 없음").isNotBlank();
        new TransactionTemplate(txManager).executeWithoutResult(status -> {
            em.createNativeQuery("INSERT INTO stocks (code, name, listed) VALUES (?, ?, TRUE)")
                    .setParameter(1, SAMSUNG)
                    .setParameter(2, "삼성전자")
                    .executeUpdate();
            em.flush();
        });
    }

    @Test
    @DisplayName("수집 → 요약 → 조회가 실제로 이어진다")
    void 전체_파이프라인() {
        long t0 = System.currentTimeMillis();
        int collected = ingestService.ingestNews();
        System.out.println("[E2E] 뉴스 수집 " + collected + "건 (" + (System.currentTimeMillis() - t0) + "ms)");
        assertThat(collected).as("삼성전자 뉴스가 하루도 없을 리 없다").isPositive();

        researchDocumentRepository.findAll().stream()
                .limit(3)
                .forEach(document -> System.out.println(
                        "[E2E]   " + document.getPublishedAt() + " | " + document.getTitle()));

        t0 = System.currentTimeMillis();
        int summarized = summaryService.summarizeNews();
        System.out.println("[E2E] 요약 생성 " + summarized + "건 (" + (System.currentTimeMillis() - t0) + "ms)");
        assertThat(summarized).isPositive();

        assertThat(researchDocumentRepository.findAll())
                .filteredOn(document -> document.getSummary() != null)
                .allSatisfy(document -> {
                    System.out.println("[E2E]   요약: " + document.getSummary());
                    assertThat(document.getPromptVersion()).isNotBlank();
                    assertThat(document.getSummary()).doesNotContain("<b>");
                });

        assertThat(summaryService.summarizeNews())
                .as("다시 돌려도 같은 세대는 건드리지 않는다")
                .isZero();

        var page = documentService.documents(SAMSUNG, ResearchDocument.Source.NEWS, null, 5);
        System.out.println("[E2E] 조회 " + page.items().size() + "건 · hasNext=" + page.hasNext());
        assertThat(page.items())
                .isNotEmpty()
                .allSatisfy(item -> assertThat(item.originUrl()).startsWith("http"));
        assertThat(page.items())
                .extracting(ResearchDocumentItemResponse::summary)
                .anySatisfy(summary -> assertThat(summary).isNotBlank());
    }
}
