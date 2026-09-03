package ssafy.a507.backend.common.ai;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.http.HttpClient;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/**
 * SSAFY GMS(OpenAI 프록시) 호출.
 *
 * <p>인터페이스를 두지 않았다 — 구현이 하나뿐이고, 제공자가 바뀌어도 바뀌는 것은 이 클래스
 * 안의 주소와 본문 모양뿐이다. 호출부는 {@link #complete} 하나만 안다.
 *
 * <p><b>요청 팩터리에 타임아웃을 건다.</b> 생성 배치는 스케줄러 스레드에서 도는데 그 스레드가
 * 하나뿐이다. 응답이 오지 않는 호출 하나가 다른 배치(일봉·공시 수집)를 함께 세운다.
 *
 * <p>응답은 레코드로 바로 바인딩하지 않고 트리로 읽는다. OpenAI 응답은 필드가 계속 늘어나는
 * 데다, 오류일 때는 {@code choices} 대신 {@code error} 가 오므로 봉투부터 확인해야 한다.
 */
@Slf4j
@Component
@EnableConfigurationProperties(AiProperties.class)
public class AiClient {

    /** 연결까지 기다리는 시간. */
    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(10);

    /** 응답을 다 받기까지 기다리는 시간. 생성은 수 초가 정상이라 넉넉히 준다. */
    private static final Duration READ_TIMEOUT = Duration.ofSeconds(120);

    /**
     * GPT-5 계열은 {@code system} 이 아니라 {@code developer} 역할을 쓴다 — GMS 문서 예시도
     * 이 값이다.
     */
    private static final String ROLE_DEVELOPER = "developer";

    private static final String ROLE_USER = "user";

    /** 응답을 읽기만 하는 용도라 앱의 직렬화 설정을 물려받지 않는다(DartClient 와 같은 이유). */
    private final ObjectMapper objectMapper = new ObjectMapper();

    private final RestClient restClient;
    private final AiProperties properties;

    @Autowired
    public AiClient(RestClient.Builder restClientBuilder, AiProperties properties) {
        this(restClientBuilder.requestFactory(timeoutAwareFactory()).build(), properties);
    }

    /** 테스트에서 스텁 RestClient 를 끼우는 통로. */
    AiClient(RestClient restClient, AiProperties properties) {
        this.restClient = restClient;
        this.properties = properties;
    }

    private static JdkClientHttpRequestFactory timeoutAwareFactory() {
        JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory(
                HttpClient.newBuilder().connectTimeout(CONNECT_TIMEOUT).build());
        factory.setReadTimeout(READ_TIMEOUT);
        return factory;
    }

    /**
     * 한 번의 생성. 대화 이력을 쌓지 않는다 — 배치 생성은 건마다 독립이고, 이력을 들고
     * 다니면 앞 종목의 문맥이 다음 종목 문장에 새어 든다.
     *
     * @param instruction 지켜야 할 규칙(문체·금지 표현·길이)
     * @param input 이번 건의 재료
     * @return 생성된 본문 · 공백만 오면 {@link AiException}
     */
    public String complete(String instruction, String input) {
        String body = requestBody(instruction, input);
        JsonNode root = post(body);

        JsonNode error = root.path("error");
        if (!error.isMissingNode() && !error.isNull()) {
            throw new AiException("GMS 오류 — " + error.path("message").asText(error.toString()));
        }

        String content = root.path("choices").path(0).path("message").path("content").asText("");
        if (content.isBlank()) {
            // 길이 제한에 걸려 본문 없이 끊긴 경우가 여기로 온다. 빈 문자열을 저장하면
            // "요약 완료"로 보여서 다시 생성되지 않는다.
            throw new AiException(
                    "GMS 응답에 본문이 없다 — finish_reason="
                            + root.path("choices").path(0).path("finish_reason").asText("?"));
        }
        return content.trim();
    }

    private JsonNode post(String body) {
        try {
            String response = restClient
                    .post()
                    .uri(properties.baseUrl() + "/chat/completions")
                    .header("Authorization", "Bearer " + properties.apiKey())
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(body)
                    .retrieve()
                    .body(String.class);
            return objectMapper.readTree(response == null ? "" : response);
        } catch (RestClientException e) {
            throw new AiException("GMS 호출 실패 — " + e.getMessage(), e);
        } catch (JsonProcessingException e) {
            throw new AiException("GMS 응답을 읽지 못했다 — " + e.getMessage(), e);
        }
    }

    private String requestBody(String instruction, String input) {
        try {
            return objectMapper.writeValueAsString(Map.of(
                    "model",
                    properties.model(),
                    "messages",
                    List.of(
                            Map.of("role", ROLE_DEVELOPER, "content", instruction),
                            Map.of("role", ROLE_USER, "content", input))));
        } catch (JsonProcessingException e) {
            throw new AiException("GMS 요청 본문을 만들지 못했다", e);
        }
    }
}
