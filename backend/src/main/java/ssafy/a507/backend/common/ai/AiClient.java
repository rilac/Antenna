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
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/**
 * OpenAI Chat Completions 호환 호출. 기본은 SSAFY GMS(OpenAI 프록시)이고, 같은 규격을 받는
 * 제공자(Gemini 의 OpenAI 호환 엔드포인트 등)는 {@code app.ai.base-url}·키·모델만 바꾸면 그대로 붙는다.
 *
 * <p>인터페이스를 두지 않았다 — 구현이 하나뿐이고, 제공자가 바뀌어도 바뀌는 것은 설정값뿐이다.
 * 호출부는 {@link #complete} 하나만 안다.
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
     * 규칙 메시지 역할. OpenAI 는 GPT-5 계열에서 {@code developer} 를 권하지만 {@code system} 도
     * 그대로 받아 같은 뜻으로 다룬다. {@code developer} 는 OpenAI 전용이라 다른 제공자가 거부한다 —
     * 어디서나 통하는 쪽을 쓴다.
     */
    private static final String ROLE_SYSTEM = "system";

    /**
     * 429(분당 한도) 뒤 한 번 쉬는 시간. 무료 티어는 15 RPM 안팎인데 호출 하나가 3~4초라 경계에 걸린다.
     * 한 번 쉬고 다시 부르면 넘기고, 그래도 막히면 그 건은 포기한다 — 두 번째부터는 일일 한도일 수 있다.
     */
    private static final Duration RATE_LIMIT_BACKOFF = Duration.ofSeconds(5);

    private static final String ROLE_USER = "user";

    /** 응답을 읽기만 하는 용도라 앱의 직렬화 설정을 물려받지 않는다(DartClient 와 같은 이유). */
    private final ObjectMapper objectMapper = new ObjectMapper();

    private final RestClient restClient;
    private final AiProperties properties;
    private final Duration rateLimitBackoff;

    @Autowired
    public AiClient(RestClient.Builder restClientBuilder, AiProperties properties) {
        this(restClientBuilder.requestFactory(timeoutAwareFactory()).build(), properties, RATE_LIMIT_BACKOFF);
    }

    /** 테스트에서 스텁 RestClient 를 끼우고 429 대기를 0 으로 두는 통로. */
    AiClient(RestClient restClient, AiProperties properties, Duration rateLimitBackoff) {
        this.restClient = restClient;
        this.properties = properties;
        this.rateLimitBackoff = rateLimitBackoff;
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
            return send(body);
        } catch (HttpClientErrorException.TooManyRequests e) {
            sleep(rateLimitBackoff);
            try {
                return send(body);
            } catch (HttpClientErrorException.TooManyRequests again) {
                throw new AiException("GMS 호출 실패 — " + again.getMessage(), again);
            }
        }
    }

    private JsonNode send(String body) {
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
        } catch (HttpClientErrorException.TooManyRequests e) {
            throw e;
        } catch (HttpClientErrorException.Unauthorized e) {
            // 토큰 소진도 401 로 온다 — {"message":"[GMS 에러] This GMS key has no token left"}.
            throw new AiKeyRejectedException("GMS 키 거부(401) — " + e.getResponseBodyAsString(), e);
        } catch (RestClientException e) {
            throw new AiException("GMS 호출 실패 — " + e.getMessage(), e);
        } catch (JsonProcessingException e) {
            throw new AiException("GMS 응답을 읽지 못했다 — " + e.getMessage(), e);
        }
    }

    private String requestBody(String instruction, String input) {
        try {
            // 사실 서술만 시키는 용도라 표집을 끈다. 기본값(모델마다 0.7 안팎)으로 뽑으면 자체 서빙
            // 양자화 모델이 숫자를 바꿔 쓰거나 없는 사실을 덧붙이는 빈도가 눈에 띄게 는다.
            return objectMapper.writeValueAsString(Map.of(
                    "model",
                    properties.model(),
                    "temperature",
                    0,
                    "messages",
                    List.of(
                            Map.of("role", ROLE_SYSTEM, "content", instruction),
                            Map.of("role", ROLE_USER, "content", input))));
        } catch (JsonProcessingException e) {
            throw new AiException("GMS 요청 본문을 만들지 못했다", e);
        }
    }

    private static void sleep(Duration duration) {
        try {
            Thread.sleep(duration.toMillis());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new AiException("429 대기 중 중단됐다", e);
        }
    }
}
