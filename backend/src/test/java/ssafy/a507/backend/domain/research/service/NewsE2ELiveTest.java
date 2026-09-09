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
import ssafy.a507.backend.domain.research.client.NaverNewsClient;
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
            "app.naver-news.display-per-stock=50"
        })
@DisplayName("뉴스 수집·요약 실호출 E2E")
class NewsE2ELiveTest {

    private static final String SAMSUNG = "005930";

    @Autowired NaverNewsClient newsClient;
    @Autowired NewsIngestService ingestService;
    @Autowired ResearchDocumentService documentService;
    @Autowired ResearchDocumentRepository researchDocumentRepository;
    @Autowired EntityManager em;
    @Autowired PlatformTransactionManager txManager;

    @BeforeEach
    void setUp() {
        assumeThat(System.getenv("NAVER_API_KEY_ID")).as("NAVER_API_KEY_ID 없음").isNotBlank();
        new TransactionTemplate(txManager).executeWithoutResult(status -> {
            em.createNativeQuery("INSERT INTO stocks (code, name, listed) VALUES (?, ?, TRUE)")
                    .setParameter(1, SAMSUNG)
                    .setParameter(2, "삼성전자")
                    .executeUpdate();
            em.flush();
        });
    }

    @Test
    @DisplayName("수집 → 조회가 실제로 이어진다")
    void 전체_파이프라인() {
        // 필터 통과율을 먼저 본다. 종목명 검색 결과 중 제목에 종목명이 든 것은 2026-09-03
        // 실측으로 15% 안팎이라, display 를 작게 잡으면 어느 날은 0건이 정상이다.
        var raw = newsClient.searchLatest("삼성전자");
        long kept = raw.stream().filter(item -> NewsIngestService.mentions(item.title(), "삼성전자")).count();
        System.out.println("[E2E] 검색 " + raw.size() + "건 중 제목 필터 통과 " + kept + "건");
        assertThat(raw).as("검색 자체가 비면 자격증명·주소 문제다").isNotEmpty();

        long t0 = System.currentTimeMillis();
        int collected = ingestService.ingestNews();
        System.out.println("[E2E] 뉴스 수집 " + collected + "건 (" + (System.currentTimeMillis() - t0) + "ms)");
        assertThat(collected).as("50건 중 제목에 종목명 든 기사가 하나도 없을 리 없다").isPositive();

        researchDocumentRepository.findAll().stream()
                .limit(3)
                .forEach(document -> System.out.println(
                        "[E2E]   " + document.getPublishedAt() + " | " + document.getTitle()));

        var page = documentService.documents(SAMSUNG, ResearchDocument.Source.NEWS, null, 5);
        System.out.println("[E2E] 조회 " + page.items().size() + "건 · hasNext=" + page.hasNext());
        assertThat(page.items())
                .isNotEmpty()
                .allSatisfy(item -> assertThat(item.originUrl()).startsWith("http"));
        assertThat(page.items())
                .extracting(ResearchDocumentItemResponse::snippet)
                .anySatisfy(snippet -> assertThat(snippet).isNotBlank().doesNotContain("<b>"));
    }
}
