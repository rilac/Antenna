package ssafy.a507.backend.common.ai;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 자체 서빙(교내 GPU) LLM 설정. {@code app.ai} 와 별개로 둔다.
 *
 * <p><b>둘로 나눈 이유는 품질과 한도가 반대라서다.</b> 투자 포인트·시장 브리핑·복기는 문장을
 * 만드는 일이라 상용 모델이 낫고 호출 수가 적다({@code app.ai}). 뉴스 관련도 판정은 기사 한 건에
 * 한 콜이라 하루 1,000콜을 넘겨 무료 티어 한도를 넘는데, 판정 자체는 짧은 이진 답이라 자체 서빙
 * 모델로 충분하다({@code app.ai.gpu}).
 *
 * <p>키가 비어 있으면 관련도 배치를 아예 돌리지 않는다 — 그 환경에서는 재료 선정이
 * {@code NewsIngestService.isNoise} 정규식으로 떨어진다.
 *
 * @param apiKey vLLM {@code --api-key} 로 건 값 · 비어 있으면 판정 배치를 건너뛴다
 * @param baseUrl OpenAI Chat Completions 호환 주소(vLLM 은 {@code .../v1})
 * @param model vLLM {@code --served-model-name} · 모델을 바꿔도 이 이름을 고정하면 설정이 안 바뀐다
 * @param promptVersion 판정에 남기는 세대 태그 · 지시문을 고치면 올린다. 옛 값 행은 다시 판정된다
 */
@ConfigurationProperties(prefix = "app.ai.gpu")
public record GpuAiProperties(
        String apiKey,
        String baseUrl,
        String model,
        String promptVersion) {

    private static final String DEFAULT_MODEL = "antenna";
    private static final String DEFAULT_PROMPT_VERSION = "v1";

    public GpuAiProperties {
        if (apiKey != null) {
            apiKey = apiKey.trim();
        }
        if (model == null || model.isBlank()) {
            model = DEFAULT_MODEL;
        }
        if (promptVersion == null || promptVersion.isBlank()) {
            promptVersion = DEFAULT_PROMPT_VERSION;
        }
    }

    public boolean isConfigured() {
        return apiKey != null && !apiKey.isBlank() && baseUrl != null && !baseUrl.isBlank();
    }

    /** {@link AiClient} 가 읽는 모양. 키가 없을 때는 부르지 않으므로 base-url 기본값은 그쪽에 맡긴다. */
    AiProperties asAiProperties() {
        return new AiProperties(apiKey, baseUrl, model, promptVersion);
    }
}
