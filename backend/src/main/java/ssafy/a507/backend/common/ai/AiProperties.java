package ssafy.a507.backend.common.ai;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * SSAFY GMS 게이트웨이 설정.
 *
 * <p>리서치 전용이 아니다 — AI 브리핑(ANT-RESEARCH-03) · 투자 포인트(-04) ·
 * 복기 리포트(-708) · 모의투자 사건 요약이 모두 이 클라이언트를 쓴다. 그래서
 * {@code domain/research} 가 아니라 {@code common} 에 둔다.
 *
 * <p>GMS 는 OpenAI Chat Completions 를 그대로 프록시한다 — 요청·응답 모양이 OpenAI 규격과
 * 같고 경로만 게이트웨이 앞단이 다르다.
 *
 * @param apiKey GMS 발급 키({@code S15P…-UUID} 꼴) · 비어 있으면 생성 배치를 아예 돌리지 않는다
 * @param baseUrl 게이트웨이 기본 주소
 * @param model 모델 ID · 게이트웨이가 날짜 붙은 실제 버전으로 해석해 응답에 실어 준다
 * @param promptVersion 생성물에 남기는 세대 태그 · 프롬프트를 고치면 이 값을 올리고, 옛 값
 *     행만 골라 다시 생성한다
 */
@ConfigurationProperties(prefix = "app.ai")
public record AiProperties(
        String apiKey,
        String baseUrl,
        String model,
        String promptVersion) {

    private static final String DEFAULT_BASE_URL = "https://gms.ssafy.io/gmsapi/api.openai.com/v1";
    private static final String DEFAULT_MODEL = "gpt-5.4-mini";
    private static final String DEFAULT_PROMPT_VERSION = "v1";

    public AiProperties {
        if (apiKey != null) {
            // .env 를 윈도우에서 편집하면 캐리지리턴이 붙는다. 그대로 헤더에 실으면 요청이 깨진다.
            apiKey = apiKey.trim();
        }
        if (baseUrl == null || baseUrl.isBlank()) {
            baseUrl = DEFAULT_BASE_URL;
        }
        // 경로를 붙일 때 "/" 를 우리가 넣는다. 설정에 끝 슬래시가 있으면 "//" 가 된다.
        while (baseUrl.endsWith("/")) {
            baseUrl = baseUrl.substring(0, baseUrl.length() - 1);
        }
        if (model == null || model.isBlank()) {
            model = DEFAULT_MODEL;
        }
        if (promptVersion == null || promptVersion.isBlank()) {
            promptVersion = DEFAULT_PROMPT_VERSION;
        }
    }

    /** 키가 없으면 생성을 열지 않는다 — 키 없는 팀원의 로컬도 그대로 부팅된다(DART 와 같은 규칙). */
    public boolean isConfigured() {
        return apiKey != null && !apiKey.isBlank();
    }
}
