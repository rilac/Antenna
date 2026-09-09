package ssafy.a507.backend.domain.research.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import jakarta.persistence.EntityManager;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Transactional;
import ssafy.a507.backend.common.ai.AiClient;
import ssafy.a507.backend.common.ai.AiException;
import ssafy.a507.backend.common.error.BusinessException;
import ssafy.a507.backend.common.error.ErrorCode;
import ssafy.a507.backend.domain.research.dto.ResearchPointItemResponse;
import ssafy.a507.backend.domain.research.dto.ResearchPointListResponse;

/**
 * 조회가 곧 생성이다 (2026-09-09). 사용자가 연 종목만, 최신 거래일에 한 번 — 두 번째 요청부터는 GMS 를
 * 부르지 않고 DB 에서 나가야 한다. GMS 는 스텁이다.
 */
@SpringBootTest(properties = "app.ai.api-key=test-key")
@Transactional
@DisplayName("투자 포인트 요청 시점 생성")
class ResearchPointOnDemandTest {

    private static final String SAMSUNG = "005930";
    private static final LocalDate TARGET = LocalDate.of(2026, 9, 2);
    private static final String REPLY =
            "[{\"kind\":\"POSITIVE\",\"body\":\"긍정 한 건.\",\"documentId\":null}]";

    @Autowired ResearchPointService pointService;
    @Autowired EntityManager em;

    @MockitoBean AiClient aiClient;

    @BeforeEach
    void setUp() {
        em.createNativeQuery("INSERT INTO stocks (code, name, listed) VALUES (?, ?, TRUE)")
                .setParameter(1, SAMSUNG)
                .setParameter(2, "삼성전자")
                .executeUpdate();
        for (int i = 0; i < 3; i++) {
            em.createNativeQuery(
                            "INSERT INTO daily_quotes (stock_code, trade_date, close, volume, collected_at)"
                                    + " VALUES (?, ?, ?, ?, ?)")
                    .setParameter(1, SAMSUNG)
                    .setParameter(2, TARGET.minusDays(2 - i))
                    .setParameter(3, new BigDecimal(70000 + i * 100))
                    .setParameter(4, 10_000_000L)
                    .setParameter(5, Instant.now())
                    .executeUpdate();
        }
        em.flush();
    }

    @Test
    @DisplayName("date 없이 부르면 최신 거래일 포인트가 없을 때 만들고, 두 번째부터는 DB 에서 준다")
    void 없으면_만들고_있으면_읽는다() {
        given(aiClient.complete(anyString(), anyString())).willReturn(REPLY);

        ResearchPointListResponse first = pointService.points(SAMSUNG, null);
        ResearchPointListResponse second = pointService.points(SAMSUNG, null);

        assertThat(first.targetDate()).isEqualTo(TARGET);
        assertThat(first.positive()).extracting(ResearchPointItemResponse::body).containsExactly("긍정 한 건.");
        assertThat(second.positive()).hasSize(1);
        verify(aiClient, times(1)).complete(anyString(), anyString());
    }

    @Test
    @DisplayName("date 를 주면 읽기만 한다 — 지난 날짜를 보려는 요청이 생성을 일으키지 않는다")
    void 날짜_지정은_읽기만() {
        ResearchPointListResponse response = pointService.points(SAMSUNG, TARGET.minusDays(1));

        assertThat(response.positive()).isEmpty();
        verify(aiClient, never()).complete(anyString(), anyString());
    }

    @Test
    @DisplayName("생성이 실패하면 503 POINT_GENERATION_FAILED — 빈 3열로 주면 화면이 '없음'으로 읽는다")
    void 생성_실패는_503() {
        given(aiClient.complete(anyString(), anyString())).willThrow(new AiException("GMS 오류"));

        assertThatThrownBy(() -> pointService.points(SAMSUNG, null))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.POINT_GENERATION_FAILED);
    }
}
