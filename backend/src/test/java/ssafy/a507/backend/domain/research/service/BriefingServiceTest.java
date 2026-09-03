package ssafy.a507.backend.domain.research.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import jakarta.persistence.EntityManager;
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
import ssafy.a507.backend.domain.research.dto.BriefingDetailResponse;
import ssafy.a507.backend.domain.research.dto.BriefingItemResponse;
import ssafy.a507.backend.domain.research.entity.AiBriefing;
import ssafy.a507.backend.domain.research.repository.AiBriefingRepository;

/** AI 브리핑 조회 — 기본 최신·필터·404. 키 없는 기본 컨텍스트라 생성 배치의 건너뛰기도 여기서 본다. */
@SpringBootTest
@Transactional
@DisplayName("AI 브리핑 조회")
class BriefingServiceTest {

    private static final String SAMSUNG = "005930";
    private static final LocalDate D1 = LocalDate.of(2026, 9, 1);
    private static final LocalDate D2 = LocalDate.of(2026, 9, 2);

    @Autowired BriefingService briefingService;
    @Autowired BriefingGenerationService generationService;
    @Autowired AiBriefingRepository aiBriefingRepository;
    @Autowired StockRepository stockRepository;
    @Autowired EntityManager em;

    @BeforeEach
    void setUp() {
        em.createNativeQuery("INSERT INTO stocks (code, name, listed) VALUES (?, ?, TRUE)")
                .setParameter(1, SAMSUNG)
                .setParameter(2, "삼성전자")
                .executeUpdate();
        save(AiBriefing.Scope.MARKET, null, D1, "시장 1일");
        save(AiBriefing.Scope.STOCK, SAMSUNG, D1, "삼성 1일");
        save(AiBriefing.Scope.MARKET, null, D2, "시장 2일");
        save(AiBriefing.Scope.STOCK, SAMSUNG, D2, "삼성 2일");
        em.flush();
    }

    @Test
    @DisplayName("date 가 없으면 가장 최신 영업일 하루치만 내려준다")
    void 기본_최신() {
        var items = briefingService.list(null, null, null).items();

        assertThat(items).extracting(BriefingItemResponse::headline).containsExactly("시장 2일", "삼성 2일");
        assertThat(items).allSatisfy(i -> assertThat(i.targetDate()).isEqualTo(D2));
        assertThat(items.get(0).stockCode()).as("MARKET 은 종목이 없다").isNull();
        assertThat(items.get(1).stockCode()).isEqualTo(SAMSUNG);
    }

    @Test
    @DisplayName("scope·stockCode·date 로 좁힌다")
    void 필터() {
        assertThat(briefingService.list(AiBriefing.Scope.MARKET, null, null).items())
                .extracting(BriefingItemResponse::headline).containsExactly("시장 2일");
        assertThat(briefingService.list(AiBriefing.Scope.STOCK, SAMSUNG, D1).items())
                .extracting(BriefingItemResponse::headline).containsExactly("삼성 1일");
        assertThat(briefingService.list(null, null, LocalDate.of(2026, 8, 1)).items()).isEmpty();
    }

    @Test
    @DisplayName("없는 종목은 404 STOCK_NOT_FOUND")
    void 종목_없음() {
        assertThatThrownBy(() -> briefingService.list(null, "000000", null))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.STOCK_NOT_FOUND);
    }

    @Test
    @DisplayName("상세는 본문까지, 없는 id 는 404 BRIEFING_NOT_FOUND")
    void 상세() {
        Long id = briefingService.list(AiBriefing.Scope.STOCK, SAMSUNG, D2).items().get(0).id();

        BriefingDetailResponse detail = briefingService.detail(id);
        assertThat(detail.headline()).isEqualTo("삼성 2일");
        assertThat(detail.body()).isEqualTo("본문");
        assertThat(detail.targetDate()).isEqualTo(D2);

        assertThatThrownBy(() -> briefingService.detail(Long.MAX_VALUE))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.BRIEFING_NOT_FOUND);
    }

    @Test
    @DisplayName("AI_API_KEY 가 없으면 생성 배치는 아무것도 하지 않는다")
    void 키_없음() {
        assertThat(generationService.generate()).isZero();
        assertThat(aiBriefingRepository.count()).isEqualTo(4);
    }

    private void save(AiBriefing.Scope scope, String stockCode, LocalDate date, String headline) {
        aiBriefingRepository.save(AiBriefing.of(
                scope,
                stockCode == null ? null : stockRepository.getReferenceById(stockCode),
                date,
                headline,
                "본문",
                "v1"));
    }
}
