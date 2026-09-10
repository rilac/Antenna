package ssafy.a507.backend.common.ai;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

/**
 * 자체 서빙(교내 GPU) 엔드포인트 호출. {@link AiClient} 를 감싸기만 한다.
 *
 * <p><b>상속이나 두 번째 {@code AiClient} 빈이 아니라 감싼 이유는 빈 타입을 갈라 두려고다.</b>
 * 같은 타입 빈이 둘이면 주입 지점마다 한정자가 필요해지고, 기존 테스트의
 * {@code @MockitoBean AiClient} 가 어느 쪽을 대체할지 정하지 못한다.
 *
 * <p>{@code app.ai.gpu.api-key}·{@code base-url} 이 없으면 {@link #isConfigured()} 가 false 다.
 * 부르는 쪽이 회차를 통째로 건너뛴다 — 로컬·CI 는 GPU 없이 그대로 돈다.
 */
@Slf4j
@Component
@EnableConfigurationProperties(GpuAiProperties.class)
public class GpuAiClient {

    private final AiClient delegate;
    private final GpuAiProperties properties;

    @Autowired
    public GpuAiClient(RestClient.Builder restClientBuilder, GpuAiProperties properties) {
        this(new AiClient(restClientBuilder, properties.asAiProperties()), properties);
    }

    /** 테스트에서 스텁 {@link AiClient} 를 끼우는 통로. */
    GpuAiClient(AiClient delegate, GpuAiProperties properties) {
        this.delegate = delegate;
        this.properties = properties;
    }

    public boolean isConfigured() {
        return properties.isConfigured();
    }

    public String promptVersion() {
        return properties.promptVersion();
    }

    public String model() {
        return properties.model();
    }

    /** 실패는 {@link AiException} 그대로 올린다 — 건별로 넘길지 회차를 접을지는 부른 쪽이 정한다. */
    public String complete(String instruction, String input) {
        return delegate.complete(instruction, input);
    }
}
