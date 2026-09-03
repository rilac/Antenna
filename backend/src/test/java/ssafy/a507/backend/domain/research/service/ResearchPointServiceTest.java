package ssafy.a507.backend.domain.research.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import jakarta.persistence.EntityManager;
import java.time.Instant;
import java.time.LocalDate;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;
import ssafy.a507.backend.common.error.BusinessException;
import ssafy.a507.backend.common.error.ErrorCode;
import ssafy.a507.backend.domain.market.repository.StockRepository;
import ssafy.a507.backend.domain.research.dto.ResearchPointItemResponse;
import ssafy.a507.backend.domain.research.dto.ResearchPointListResponse;
import ssafy.a507.backend.domain.research.entity.ResearchDocument;
import ssafy.a507.backend.domain.research.entity.ResearchPoint;
import ssafy.a507.backend.domain.research.repository.ResearchPointRepository;

/** 긍정·위험·확인 포인트 조회 — 3열 분류·출처 태그·기본 최신·404. */
@SpringBootTest
@Transactional
@DisplayName("리서치 포인트 조회")
class ResearchPointServiceTest {

    private static final String SAMSUNG = "005930";
    private static final LocalDate D1 = LocalDate.of(2026, 9, 1);
    private static final LocalDate D2 = LocalDate.of(2026, 9, 2);

    @Autowired ResearchPointService researchPointService;
    @Autowired ResearchPointRepository researchPointRepository;
    @Autowired StockRepository stockRepository;
    @Autowired EntityManager em;

    private Long documentId;

    @BeforeEach
    void setUp() {
        em.createNativeQuery("INSERT INTO stocks (code, name, listed) VALUES (?, ?, TRUE)")
                .setParameter(1, SAMSUNG)
                .setParameter(2, "삼성전자")
                .executeUpdate();
        ResearchDocument document = ResearchDocument.collected(
                stockRepository.getReferenceById(SAMSUNG),
                ResearchDocument.Source.DART,
                "d-1",
                "반기보고서",
                "https://dart.example.com/d-1",
                null,
                Instant.parse("2026-09-01T06:00:00Z"));
        em.persist(document);
        documentId = document.getId();

        save(D1, ResearchPoint.Kind.POSITIVE, "1일 긍정", null);
        save(D2, ResearchPoint.Kind.POSITIVE, "2일 긍정", document);
        save(D2, ResearchPoint.Kind.RISK, "2일 위험", null);
        em.flush();
    }

    @Test
    @DisplayName("date 가 없으면 포인트가 있는 가장 최근 영업일치를 3열로 내려준다")
    void 기본_최신() {
        ResearchPointListResponse response = researchPointService.points(SAMSUNG, null);

        assertThat(response.positive()).extracting(ResearchPointItemResponse::body).containsExactly("2일 긍정");
        assertThat(response.risk()).extracting(ResearchPointItemResponse::body).containsExactly("2일 위험");
        assertThat(response.check()).as("빈 열도 배열로 내려간다").isEmpty();
    }

    @Test
    @DisplayName("연결 문서에서 documentId 와 출처 태그를 파생한다 — 문서가 없으면 둘 다 null")
    void 출처_태그() {
        ResearchPointListResponse response = researchPointService.points(SAMSUNG, D2);

        assertThat(response.positive()).singleElement().satisfies(p -> {
            assertThat(p.documentId()).isEqualTo(documentId);
            assertThat(p.source()).isEqualTo(ResearchDocument.Source.DART);
        });
        assertThat(response.risk()).singleElement().satisfies(p -> {
            assertThat(p.documentId()).isNull();
            assertThat(p.source()).isNull();
        });
    }

    @Test
    @DisplayName("date 를 주면 그 날짜만 본다")
    void 날짜_지정() {
        ResearchPointListResponse response = researchPointService.points(SAMSUNG, D1);

        assertThat(response.positive()).extracting(ResearchPointItemResponse::body).containsExactly("1일 긍정");
    }

    @Test
    @DisplayName("아직 포인트가 없는 종목은 200 + 빈 3열 — '종목 없음'과 구분한다")
    void 포인트_없음() {
        em.createNativeQuery("INSERT INTO stocks (code, name, listed) VALUES ('000660', 'SK하이닉스', TRUE)")
                .executeUpdate();
        em.flush();

        ResearchPointListResponse response = researchPointService.points("000660", null);

        assertThat(response.positive()).isEmpty();
        assertThat(response.risk()).isEmpty();
        assertThat(response.check()).isEmpty();
    }

    @Test
    @DisplayName("없는 종목은 STOCK_NOT_FOUND")
    void 없는_종목() {
        assertThatThrownBy(() -> researchPointService.points("999999", null))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.STOCK_NOT_FOUND);
    }

    private void save(LocalDate targetDate, ResearchPoint.Kind kind, String body, ResearchDocument document) {
        researchPointRepository.save(ResearchPoint.of(
                stockRepository.getReferenceById(SAMSUNG), targetDate, kind, body, document));
    }
}
