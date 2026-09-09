package ssafy.a507.backend.domain.research.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import jakarta.persistence.EntityManager;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;
import ssafy.a507.backend.common.error.BusinessException;
import ssafy.a507.backend.domain.market.repository.StockRepository;
import ssafy.a507.backend.domain.research.dto.ResearchDocumentItemResponse;
import ssafy.a507.backend.domain.research.dto.ResearchDocumentListResponse;
import ssafy.a507.backend.domain.research.entity.ResearchDocument;
import ssafy.a507.backend.domain.research.repository.ResearchDocumentRepository;

/** 종목별 뉴스·공시 조회 — 커서 페이징과 원천 필터. */
@SpringBootTest
@Transactional
@DisplayName("리서치 문서 조회")
class ResearchDocumentServiceTest {

    private static final String SAMSUNG = "005930";

    @Autowired ResearchDocumentService documentService;
    @Autowired EntityManager em;
    @Autowired StockRepository stockRepository;
    @Autowired ResearchDocumentRepository researchDocumentRepository;

    @BeforeEach
    void setUp() {
        em.createNativeQuery("INSERT INTO stocks (code, name, listed) VALUES (?, ?, TRUE)")
                .setParameter(1, SAMSUNG)
                .setParameter(2, "삼성전자")
                .executeUpdate();
        em.flush();
    }

    @Test
    @DisplayName("커서로 이어 받으면 건너뛰거나 겹치지 않는다")
    void 커서_페이징() {
        for (int i = 1; i <= 5; i++) {
            save(ResearchDocument.Source.NEWS, "n" + i);
        }

        ResearchDocumentListResponse first = documentService.documents(SAMSUNG, null, null, 2);
        assertThat(first.items()).hasSize(2);
        assertThat(first.hasNext()).isTrue();

        ResearchDocumentListResponse second =
                documentService.documents(SAMSUNG, null, first.nextCursor(), 2);
        assertThat(second.items()).hasSize(2);

        assertThat(first.items()).extracting(ResearchDocumentItemResponse::id)
                .doesNotContainAnyElementsOf(
                        second.items().stream().map(ResearchDocumentItemResponse::id).toList());
    }

    @Test
    @DisplayName("마지막 장에서는 커서가 비고 hasNext 가 false 다")
    void 마지막_장() {
        save(ResearchDocument.Source.NEWS, "n1");

        ResearchDocumentListResponse page = documentService.documents(SAMSUNG, null, null, 20);

        assertThat(page.items()).hasSize(1);
        assertThat(page.hasNext()).isFalse();
        assertThat(page.nextCursor()).isNull();
    }

    @Test
    @DisplayName("source 를 주면 그 원천만, 빼면 섞어서 내려준다")
    void 원천_필터() {
        save(ResearchDocument.Source.NEWS, "n1");
        save(ResearchDocument.Source.DART, "d1");

        assertThat(documentService.documents(SAMSUNG, null, null, 20).items()).hasSize(2);
        assertThat(documentService
                        .documents(SAMSUNG, ResearchDocument.Source.DART, null, 20)
                        .items())
                .singleElement()
                .extracting(ResearchDocumentItemResponse::source)
                .isEqualTo(ResearchDocument.Source.DART);
    }

    @Test
    @DisplayName("발췌를 그대로 내려보낸다 — AI 요약을 기다리지 않는다")
    void 발췌가_보인다() {
        save(ResearchDocument.Source.NEWS, "n1");

        assertThat(documentService.documents(SAMSUNG, null, null, 20).items())
                .singleElement()
                .satisfies(item -> {
                    assertThat(item.snippet()).isEqualTo("발췌");
                    assertThat(item.title()).isNotBlank();
                    assertThat(item.originUrl()).isNotBlank();
                });
    }

    @Test
    @DisplayName("없는 종목은 404 다")
    void 없는_종목() {
        assertThatThrownBy(() -> documentService.documents("999999", null, null, 20))
                .isInstanceOf(BusinessException.class);
    }

    private void save(ResearchDocument.Source source, String externalId) {
        researchDocumentRepository.save(ResearchDocument.collected(
                stockRepository.getReferenceById(SAMSUNG),
                source,
                externalId,
                "제목 " + externalId,
                "https://news.example.com/" + externalId,
                "발췌",
                Instant.now().minus(1, ChronoUnit.HOURS)));
        em.flush();
    }
}
