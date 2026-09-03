package ssafy.a507.backend.domain.market.client;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 한국수출입은행 환율 Open API 설정(ANT-DATA-04).
 *
 * <p>주소는 2025-06-25 부터 {@code oapi.koreaexim.go.kr} 다. 옛 {@code www.} 도메인은
 * 2026-04-30 에 끊겼으니 되돌리지 말 것. 주소는 문서로 확정된 값이라 환경변수로 받지 않고,
 * .env 로는 인증키만 받는다(포털 키와 같은 규칙).
 *
 * @param baseUrl 요청 주소 전체 · 경로까지 포함한다(이 API 는 오퍼레이션이 하나뿐이다)
 * @param authKey 수출입은행이 발급한 인증키 · 비어 있으면 환율 수집을 아예 돌리지 않는다
 */
@ConfigurationProperties(prefix = "app.market-data.fx")
public record KoreaeximProperties(String baseUrl, String authKey) {

    private static final String DEFAULT_BASE_URL =
            "https://oapi.koreaexim.go.kr/site/program/financial/exchangeJSON";

    public KoreaeximProperties {
        if (baseUrl == null || baseUrl.isBlank()) {
            baseUrl = DEFAULT_BASE_URL;
        }
    }

    /**
     * 인증키가 없으면 수집을 열지 않는다. 키 없이 부르면 result 3 이 매번 돌아와 실패 로그만
     * 쌓인다 — 건너뛰고 로그 한 줄을 남기는 편이 낫다.
     */
    public boolean isConfigured() {
        return authKey != null && !authKey.isBlank();
    }
}
