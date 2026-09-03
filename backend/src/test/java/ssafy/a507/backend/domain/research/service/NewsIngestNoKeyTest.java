package ssafy.a507.backend.domain.research.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verifyNoInteractions;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Transactional;
import ssafy.a507.backend.common.ai.AiClient;
import ssafy.a507.backend.domain.research.client.NaverNewsClient;

/**
 * 자격증명이 없는 상태가 곧 검증 대상이다.
 *
 * <p>네이버 키와 GMS 키는 따로 논다 — 한쪽만 받은 환경에서 다른 쪽까지 멈추면 안 된다.
 * 기본 테스트 설정은 둘 다 비어 있고, 그때 수집도 요약도 외부를 부르지 않아야 한다.
 */
@SpringBootTest
@Transactional
@DisplayName("뉴스 수집·요약 — 키 없음")
class NewsIngestNoKeyTest {

    @Autowired NewsIngestService ingestService;
    @Autowired DocumentSummaryService summaryService;

    @MockitoBean NaverNewsClient newsClient;
    @MockitoBean AiClient aiClient;

    @Test
    @DisplayName("키가 비면 수집도 요약도 아무것도 부르지 않는다")
    void 키가_없으면_열지_않는다() {
        assertThat(ingestService.ingestNews()).isZero();
        assertThat(summaryService.summarizeNews()).isZero();

        verifyNoInteractions(newsClient);
        verifyNoInteractions(aiClient);
    }
}
