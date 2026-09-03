package ssafy.a507.backend.domain.research.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import jakarta.persistence.EntityManager;
import java.time.Instant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Transactional;
import ssafy.a507.backend.common.ai.AiClient;
import ssafy.a507.backend.common.ai.AiException;
import ssafy.a507.backend.domain.market.repository.StockRepository;
import ssafy.a507.backend.domain.research.entity.ResearchDocument;
import ssafy.a507.backend.domain.research.repository.ResearchDocumentRepository;

/**
 * 배치 B6 의 뉴스 갈래를 본다.
 *
 * <p><b>세대 태그가 이 배치의 중심이다.</b> {@code prompt_version} 이 없으면 프롬프트를 고친
 * 뒤 전부 다시 돌리는 수밖에 없고(비용), 반대로 태그를 붙이고도 조건이 어긋나면 매 회차가
 * 같은 기사를 다시 요약한다(역시 비용). 두 방향을 함께 막는다.
 *
 * <p>GMS 는 스텁이다 — 테스트는 외부와 통신하지 않는다.
 */
@SpringBootTest(properties = {"app.ai.api-key=test-key", "app.ai.prompt-version=v1"})
@Transactional
@DisplayName("뉴스 요약 (B6)")
class DocumentSummaryServiceTest {

    private static final String SAMSUNG = "005930";

    @Autowired DocumentSummaryService summaryService;
    @Autowired EntityManager em;
    @Autowired StockRepository stockRepository;
    @Autowired ResearchDocumentRepository researchDocumentRepository;

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
    @DisplayName("요약과 세대 태그를 함께 남긴다")
    void 요약_생성() {
        save(ResearchDocument.Source.NEWS, "n1", "삼성전자 신공장", "발췌 본문");
        given(aiClient.complete(anyString(), anyString())).willReturn("요약 문장.");

        assertThat(summaryService.summarizeNews()).isEqualTo(1);
        em.flush();

        assertThat(researchDocumentRepository.findAll())
                .singleElement()
                .satisfies(document -> {
                    assertThat(document.getSummary()).isEqualTo("요약 문장.");
                    assertThat(document.getPromptVersion()).isEqualTo("v1");
                    assertThat(document.getSummarizedAt()).isNotNull();
                });
    }

    @Test
    @DisplayName("종목명·제목·발췌를 재료로 넣는다 — 제목만으로는 주어가 엉뚱해진다")
    void 프롬프트_재료() {
        save(ResearchDocument.Source.NEWS, "n1", "업계 1위, 신공장 착공", "발췌 본문");
        given(aiClient.complete(anyString(), anyString())).willReturn("요약 문장.");

        summaryService.summarizeNews();

        verify(aiClient).complete(anyString(), contains("삼성전자"));
        verify(aiClient).complete(anyString(), contains("발췌 본문"));
    }

    @Test
    @DisplayName("이미 현 세대인 기사는 다시 부르지 않는다")
    void 재실행_멱등() {
        save(ResearchDocument.Source.NEWS, "n1", "삼성전자 신공장", "발췌 본문");
        given(aiClient.complete(anyString(), anyString())).willReturn("요약 문장.");

        summaryService.summarizeNews();
        em.flush();
        int second = summaryService.summarizeNews();

        assertThat(second).as("두 번째 회차는 대상이 없다").isZero();
    }

    @Test
    @DisplayName("세대가 뒤처진 기사는 다시 생성한다")
    void 세대_재생성() {
        ResearchDocument document =
                save(ResearchDocument.Source.NEWS, "n1", "삼성전자 신공장", "발췌 본문");
        document.summarize("옛 요약", "v0");
        researchDocumentRepository.save(document);
        em.flush();
        given(aiClient.complete(anyString(), anyString())).willReturn("새 요약.");

        assertThat(summaryService.summarizeNews()).isEqualTo(1);
        em.flush();

        assertThat(researchDocumentRepository.findAll())
                .singleElement()
                .satisfies(d -> {
                    assertThat(d.getSummary()).isEqualTo("새 요약.");
                    assertThat(d.getPromptVersion()).isEqualTo("v1");
                });
    }

    @Test
    @DisplayName("DART 공시는 요약하지 않는다 — 보고서명이 곧 요약이라 넣어 봐야 제목을 바꿔 쓴다")
    void 공시는_제외() {
        save(ResearchDocument.Source.DART, "20260831000066", "주요사항보고서", null);

        assertThat(summaryService.summarizeNews()).isZero();
        verify(aiClient, never()).complete(anyString(), anyString());
    }

    @Test
    @DisplayName("한 건이 실패해도 나머지는 계속 간다")
    void 실패_격리() {
        save(ResearchDocument.Source.NEWS, "n1", "삼성전자 첫 기사", "발췌 1");
        save(ResearchDocument.Source.NEWS, "n2", "삼성전자 둘째 기사", "발췌 2");
        given(aiClient.complete(anyString(), contains("발췌 1")))
                .willThrow(new AiException("GMS 오류"));
        given(aiClient.complete(anyString(), contains("발췌 2"))).willReturn("요약 문장.");

        assertThat(summaryService.summarizeNews()).isEqualTo(1);
    }

    private ResearchDocument save(
            ResearchDocument.Source source, String externalId, String title, String snippet) {
        ResearchDocument document = ResearchDocument.collected(
                stockRepository.getReferenceById(SAMSUNG),
                source,
                externalId,
                title,
                "https://news.example.com/" + externalId,
                snippet,
                Instant.now());
        researchDocumentRepository.save(document);
        em.flush();
        return document;
    }
}
