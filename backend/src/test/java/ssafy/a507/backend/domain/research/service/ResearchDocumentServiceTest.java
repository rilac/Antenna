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
import ssafy.a507.backend.domain.research.entity.NewsSignal;
import ssafy.a507.backend.domain.research.repository.NewsSignalRepository;
import ssafy.a507.backend.domain.research.repository.ResearchDocumentRepository;

/**
 * 종목별 뉴스·공시 조회 — 커서 페이징 · 원천 필터 · 무관 기사 제외.
 *
 * <p>목록도 투자 포인트 재료와 같은 규칙으로 거른다 — 판정이 있으면 그것을, 없으면 정규식을 따른다.
 * 이름만 같은 스포츠 구단 기사가 종목 화면에 그대로 뜨던 것을 막는다.
 */
@SpringBootTest
@Transactional
@DisplayName("리서치 문서 조회")
class ResearchDocumentServiceTest {

    private static final String SAMSUNG = "005930";

    @Autowired ResearchDocumentService documentService;
    @Autowired EntityManager em;
    @Autowired StockRepository stockRepository;
    @Autowired ResearchDocumentRepository researchDocumentRepository;
    @Autowired NewsSignalRepository newsSignalRepository;

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
    @DisplayName("이름만 같은 스포츠 기사는 목록에서 뺀다 — 판정이 없으면 정규식으로 떨어진다")
    void 무관_기사_제외() {
        save(ResearchDocument.Source.NEWS, "n-ball", "한화 강백호, 두산전 2점 아치…시즌 30홈런");
        save(ResearchDocument.Source.NEWS, "n-biz", "삼성전자, 신형 메모리 양산 시작");

        assertThat(documentService.documents(SAMSUNG, null, null, 20).items())
                .extracting(ResearchDocumentItemResponse::title)
                .containsExactly("삼성전자, 신형 메모리 양산 시작");
    }

    @Test
    @DisplayName("판정이 정규식보다 앞선다 — 정규식이 버릴 사업 기사도 관련이면 보인다")
    void 판정_우선() {
        ResearchDocument merger = save(
                ResearchDocument.Source.NEWS, "n-merger", "엔씨소프트, NC다이노스 구단 지분 매각 검토");
        ResearchDocument sports = save(
                ResearchDocument.Source.NEWS, "n-sports", "삼성전자 창단 기념 사내 야구대회 열려");
        judge(merger, true);
        judge(sports, false);

        assertThat(documentService.documents(SAMSUNG, null, null, 20).items())
                .extracting(ResearchDocumentItemResponse::title)
                .containsExactly("엔씨소프트, NC다이노스 구단 지분 매각 검토");
    }

    @Test
    @DisplayName("공시는 거르지 않는다 — 회사가 스스로 낸 것이라 무관할 수 없다")
    void 공시는_그대로() {
        save(ResearchDocument.Source.DART, "d-1", "주요사항보고서(타법인 주식 취득결정) 야구단 인수");

        assertThat(documentService.documents(SAMSUNG, null, null, 20).items()).hasSize(1);
    }

    @Test
    @DisplayName("한 장이 통째로 무관이어도 다음 기사까지 읽어 채운다 — 빈 장을 내려보내지 않는다")
    void 무관이_많아도_장을_채운다() {
        for (int i = 1; i <= 12; i++) {
            save(ResearchDocument.Source.NEWS, "n-ball" + i, "두산 선발 최승용 " + i + "이닝 4실점 강판");
        }
        save(ResearchDocument.Source.NEWS, "n-biz", "삼성전자, 신형 메모리 양산 시작");

        ResearchDocumentListResponse page = documentService.documents(SAMSUNG, null, null, 5);

        assertThat(page.items())
                .extracting(ResearchDocumentItemResponse::title)
                .containsExactly("삼성전자, 신형 메모리 양산 시작");
        assertThat(page.hasNext()).isFalse();
        assertThat(page.nextCursor()).isNull();
    }

    @Test
    @DisplayName("없는 종목은 404 다")
    void 없는_종목() {
        assertThatThrownBy(() -> documentService.documents("999999", null, null, 20))
                .isInstanceOf(BusinessException.class);
    }

    private void save(ResearchDocument.Source source, String externalId) {
        save(source, externalId, "제목 " + externalId);
    }

    private ResearchDocument save(ResearchDocument.Source source, String externalId, String title) {
        ResearchDocument document = researchDocumentRepository.save(ResearchDocument.collected(
                stockRepository.getReferenceById(SAMSUNG),
                source,
                externalId,
                title,
                "https://news.example.com/" + externalId,
                "발췌",
                Instant.now().minus(1, ChronoUnit.HOURS)));
        em.flush();
        return document;
    }

    private void judge(ResearchDocument document, boolean relevant) {
        newsSignalRepository.save(NewsSignal.judged(document, relevant, "antenna", "v1"));
        em.flush();
    }
}
