package ssafy.a507.backend.common.ai;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

/**
 * GMS 응답의 실제 모양을 고정해 둔다. 2026-09-03 실호출로 확인한 형태다 — OpenAI Chat
 * Completions 규격 그대로이고, 모델 ID 는 요청에 보낸 {@code gpt-5.4-mini} 가 아니라 날짜가
 * 붙은 실제 버전으로 돌아온다.
 *
 * <p><b>빈 본문을 성공으로 넘기지 않는 것이 요점이다.</b> 길이 제한에 걸려 문장 없이 끊기면
 * {@code content} 가 빈 문자열로 온다. 그대로 저장하면 "요약 완료"로 보여서 다시 생성되지
 * 않고, 화면에는 빈 카드가 남는다.
 */
@DisplayName("GMS 호출")
class AiClientTest {

    private static final MediaType JSON =
            new MediaType(MediaType.APPLICATION_JSON, StandardCharsets.UTF_8);

    private static final String URL = "https://gms.ssafy.io/gmsapi/api.openai.com/v1/chat/completions";

    private RestClient.Builder builder;
    private MockRestServiceServer server;

    @BeforeEach
    void setUp() {
        builder = RestClient.builder();
        server = MockRestServiceServer.bindTo(builder).build();
    }

    private AiClient client() {
        return new AiClient(builder.build(), new AiProperties("gms-key", null, null, "v1"), Duration.ZERO);
    }

    @Test
    @DisplayName("생성 본문만 꺼내 온다")
    void 생성_성공() {
        server.expect(requestTo(URL))
                .andExpect(header("Authorization", "Bearer gms-key"))
                .andExpect(content().string(containsString("\"model\":\"gpt-5.4-mini\"")))
                .andExpect(content().string(containsString("\"role\":\"system\"")))
                .andRespond(withSuccess(
                        """
                        {"id":"chatcmpl-1","model":"gpt-5.4-mini-2026-03-17",
                         "choices":[{"index":0,"finish_reason":"stop",
                          "message":{"role":"assistant","content":"  요약 문장.  "}}]}
                        """,
                        JSON));

        assertThat(client().complete("규칙", "재료")).isEqualTo("요약 문장.");
        server.verify();
    }

    @Test
    @DisplayName("본문 없이 끊긴 응답은 실패로 다룬다 — 빈 요약을 저장하면 다시 만들어지지 않는다")
    void 빈_본문은_실패() {
        server.expect(requestTo(URL))
                .andRespond(withSuccess(
                        """
                        {"choices":[{"index":0,"finish_reason":"length",
                         "message":{"role":"assistant","content":""}}]}
                        """,
                        JSON));

        assertThatThrownBy(() -> client().complete("규칙", "재료"))
                .isInstanceOf(AiException.class)
                .hasMessageContaining("length");
    }

    @Test
    @DisplayName("오류 봉투를 성공으로 넘기지 않는다")
    void 오류_봉투() {
        server.expect(requestTo(URL))
                .andRespond(withSuccess(
                        """
                        {"error":{"message":"model not found","type":"invalid_request_error"}}
                        """,
                        JSON));

        assertThatThrownBy(() -> client().complete("규칙", "재료"))
                .isInstanceOf(AiException.class)
                .hasMessageContaining("model not found");
    }

    @Test
    @DisplayName("429 는 분당 한도다 — 한 번 쉬고 다시 부른다. 무료 티어(15 RPM 안팎)에서 건을 잃지 않으려고")
    void 한도_초과는_한_번_재시도() {
        server.expect(requestTo(URL)).andRespond(withStatus(HttpStatus.TOO_MANY_REQUESTS));
        server.expect(requestTo(URL))
                .andRespond(withSuccess(
                        """
                        {"choices":[{"index":0,"finish_reason":"stop",
                         "message":{"role":"assistant","content":"두 번째에 성공."}}]}
                        """,
                        JSON));

        assertThat(client().complete("규칙", "재료")).isEqualTo("두 번째에 성공.");
        server.verify();
    }

    @Test
    @DisplayName("두 번 연속 429 면 그 건은 포기한다 — 회차는 이어 간다")
    void 두_번_막히면_포기() {
        server.expect(requestTo(URL)).andRespond(withStatus(HttpStatus.TOO_MANY_REQUESTS));
        server.expect(requestTo(URL)).andRespond(withStatus(HttpStatus.TOO_MANY_REQUESTS));

        assertThatThrownBy(() -> client().complete("규칙", "재료"))
                .isInstanceOf(AiException.class)
                .isNotInstanceOf(AiKeyRejectedException.class)
                .hasMessageContaining("429");
    }

    @Test
    @DisplayName("401 은 키가 죽은 것이다(토큰 소진·잘못된 키) — 건너뛰기가 아니라 회차 중단 신호로 구분해 던진다")
    void 키_거부() {
        server.expect(requestTo(URL))
                .andRespond(withStatus(HttpStatus.UNAUTHORIZED)
                        .contentType(JSON)
                        .body("{\"message\":\"[GMS 에러] This GMS key has no token left\",\"statusCode\":401}"));

        assertThatThrownBy(() -> client().complete("규칙", "재료"))
                .isInstanceOf(AiKeyRejectedException.class)
                .hasMessageContaining("no token left");
    }
}
