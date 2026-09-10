package ssafy.a507.backend.domain.research.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;

import jakarta.persistence.EntityManager;
import java.time.Instant;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import ssafy.a507.backend.common.ai.GpuAiClient;
import ssafy.a507.backend.domain.market.repository.StockRepository;
import ssafy.a507.backend.domain.research.entity.NewsSignal;
import ssafy.a507.backend.domain.research.entity.ResearchDocument;
import ssafy.a507.backend.domain.research.repository.NewsSignalRepository;

/**
 * 관련도 판정 배치를 <b>운영과 같은 조건</b>에서 본다 — 테스트 트랜잭션 없이.
 *
 * <p>{@link NewsRelevanceServiceTest} 는 클래스에 {@code @Transactional} 이 붙어 있어 세션이 회차
 * 내내 열려 있다. 운영의 밤 배치는 그렇지 않다({@code spring.jpa.open-in-view: false}, 회차에
 * 트랜잭션 없음). 그 차이가 두 가지를 가렸다.
 *
 * <ul>
 *   <li>재료를 읽을 때 {@code document.getStock().getName()} 이 LazyInitializationException 으로
 *       터진다 — 첫 기사에서 회차가 통째로 죽는다.
 *   <li>세대가 올라간 재판정이 분리(detached) 엔티티 변경이라 DB 에 남지 않는다.
 * </ul>
 *
 * <p>그래서 이 클래스에는 {@code @Transactional} 을 붙이지 않는다. 붙이는 순간 다시 가려진다.
 * 대신 심기와 치우기를 {@link TransactionTemplate} 으로 직접 한다.
 */
@SpringBootTest
@DisplayName("뉴스 관련도 판정 — 배치 실행 조건")
class NewsRelevanceBatchTest {

    private static final String CODE = "111111";
    private static final String BALL = "두산 선발 최승용, 3이닝 4실점으로 강판";

    @Autowired NewsRelevanceService relevanceService;
    @Autowired NewsSignalRepository newsSignalRepository;
    @Autowired StockRepository stockRepository;
    @Autowired EntityManager em;
    @Autowired PlatformTransactionManager txManager;

    @MockitoBean GpuAiClient gpuAiClient;

    private TransactionTemplate tx;

    @BeforeEach
    void setUp() {
        tx = new TransactionTemplate(txManager);
        tx.executeWithoutResult(status -> {
            em.createNativeQuery("INSERT INTO stocks (code, name, listed) VALUES (?, ?, TRUE)")
                    .setParameter(1, CODE)
                    .setParameter(2, "두산")
                    .executeUpdate();
            em.flush();
        });
        given(gpuAiClient.isConfigured()).willReturn(true);
        given(gpuAiClient.model()).willReturn("antenna");
        given(gpuAiClient.promptVersion()).willReturn("v1");
    }

    @AfterEach
    void tearDown() {
        tx.executeWithoutResult(status -> {
            em.createNativeQuery("DELETE FROM news_signals WHERE document_id IN"
                            + " (SELECT id FROM research_documents WHERE external_id LIKE 'batch-%')")
                    .executeUpdate();
            em.createNativeQuery("DELETE FROM research_documents WHERE external_id LIKE 'batch-%'")
                    .executeUpdate();
            em.createNativeQuery("DELETE FROM stocks WHERE code = ?").setParameter(1, CODE).executeUpdate();
        });
    }

    @Test
    @DisplayName("종목이 달린 기사를 판정한다 — 회차에 트랜잭션이 없어도 종목명을 읽는다")
    void 종목명_지연로딩() {
        seedNews("batch-ball", BALL, true);
        given(gpuAiClient.complete(anyString(), anyString())).willReturn("무관");

        assertThat(relevanceService.judgeRecent()).isEqualTo(1);

        assertThat(newsSignalRepository.findAll())
                .filteredOn(s -> !s.isRelevant())
                .singleElement()
                .satisfies(s -> assertThat(s.getPromptVersion()).isEqualTo("v1"));
    }

    @Test
    @DisplayName("종목명을 프롬프트에 넣는다 — 회사가 빠지면 이름만 같은 기사를 가릴 수 없다")
    void 종목명_전달() {
        seedNews("batch-ball", BALL, true);
        given(gpuAiClient.complete(anyString(), anyString())).willReturn("무관");

        relevanceService.judgeRecent();

        org.mockito.ArgumentCaptor<String> input = org.mockito.ArgumentCaptor.forClass(String.class);
        org.mockito.Mockito.verify(gpuAiClient).complete(anyString(), input.capture());
        assertThat(input.getValue()).contains("회사: 두산").contains("3이닝");
    }

    @Test
    @DisplayName("종목이 없는 시장 기사도 대상이다 — 종목을 함께 읽느라 걸러지면 안 된다")
    void 시장_기사도_판정() {
        seedNews("batch-market", "코스피 3390 마감", false);
        given(gpuAiClient.complete(anyString(), anyString())).willReturn("관련");

        assertThat(relevanceService.judgeRecent()).isEqualTo(1);

        org.mockito.ArgumentCaptor<String> input = org.mockito.ArgumentCaptor.forClass(String.class);
        org.mockito.Mockito.verify(gpuAiClient).complete(anyString(), input.capture());
        assertThat(input.getValue()).contains("회사: (시장 전체)");
    }

    @Test
    @DisplayName("세대가 올라간 재판정이 DB 에 남는다 — 분리 엔티티라 변경만으로는 저장되지 않는다")
    void 재판정_저장() {
        Long documentId = seedNews("batch-biz", "두산에너빌리티, 체코 원전 수주", true);
        given(gpuAiClient.complete(anyString(), anyString())).willReturn("관련");
        relevanceService.judgeRecent();

        given(gpuAiClient.promptVersion()).willReturn("v2");
        given(gpuAiClient.complete(anyString(), anyString())).willReturn("무관");
        assertThat(relevanceService.judgeRecent()).isEqualTo(1);

        assertThat(newsSignalRepository.findAll()).hasSize(1);
        assertThat(newsSignalRepository.findByDocument_Id(documentId)).get().satisfies(s -> {
            assertThat(s.getPromptVersion()).as("세대가 덮였나").isEqualTo("v2");
            assertThat(s.isRelevant()).as("판정이 덮였나").isFalse();
        });
    }

    /** @return 문서 id · {@code withStock} 이 false 면 시장 전체 기사다 */
    private Long seedNews(String externalId, String title, boolean withStock) {
        return tx.execute(status -> {
            ResearchDocument document = ResearchDocument.collected(
                    withStock ? stockRepository.getReferenceById(CODE) : null,
                    ResearchDocument.Source.NEWS,
                    externalId,
                    title,
                    "https://news.example.com/" + externalId,
                    "발췌",
                    Instant.now().minusSeconds(3600));
            em.persist(document);
            em.flush();
            return document.getId();
        });
    }
}
