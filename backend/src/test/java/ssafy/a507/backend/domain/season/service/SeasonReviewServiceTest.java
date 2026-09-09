package ssafy.a507.backend.domain.season.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import ssafy.a507.backend.common.ai.AiClient;
import ssafy.a507.backend.common.ai.AiProperties;

/** 키가 없으면 GMS 를 부르지 않는다 — 로컬·CI 에서 종료 흐름이 막히지 않아야 한다(ANT-SEASON-09). */
class SeasonReviewServiceTest {

    @Test
    @DisplayName("키가 비어 있으면 복기는 null 이고 GMS 를 부르지 않는다")
    void 키_없으면_null() {
        AiClient client = mock(AiClient.class);
        SeasonReviewService service =
                new SeasonReviewService(client, new AiProperties("  ", null, null, null));

        assertThat(service.hasKey()).isFalse();
        assertThat(service.review("재료")).isNull();
        then(client).should(never()).complete(anyString(), anyString());
    }

    @Test
    @DisplayName("키가 있으면 지시문과 재료로 한 번 부르고 본문을 그대로 돌려준다")
    void 키_있으면_한_번_부른다() {
        AiClient client = mock(AiClient.class);
        given(client.complete(SeasonReviewService.INSTRUCTION, "재료")).willReturn("잘한 판단: …");
        SeasonReviewService service =
                new SeasonReviewService(client, new AiProperties("k", null, null, "v1"));

        assertThat(service.review("재료")).isEqualTo("잘한 판단: …");
        assertThat(service.promptVersion()).isEqualTo("v1");
    }
}
