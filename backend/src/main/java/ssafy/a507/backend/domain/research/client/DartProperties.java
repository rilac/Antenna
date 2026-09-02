package ssafy.a507.backend.domain.research.client;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * DART 전자공시 OpenAPI 설정 (ANT-RESEARCH-01).
 *
 * <p>주소는 문서로 확정된 값이라 환경마다 달라지지 않는다 — 상수로 두고 {@code .env} 로는
 * 인증키만 받는다(공공데이터포털·SSAFY SSO 와 같은 규칙).
 *
 * @param apiKey open.dart.fss.or.kr 발급 40자리 hex · 비어 있으면 수집을 아예 돌리지 않는다
 * @param baseUrl OpenAPI 기본 주소
 * @param lookbackDays 공시 목록을 되돌아보며 훑을 구간(일) · 접수번호가 멱등 키라 겹쳐 받아도
 *     중복이 생기지 않는다. 연휴와 실패 회차를 함께 덮을 만큼 넉넉히 둔다
 * @param financialYear 재무 수집 기준 연도 · 0 이면 "작년"을 쓴다. 한 번 호출에 3개년이 오므로
 *     이 값 하나로 3년치가 채워진다
 */
@ConfigurationProperties(prefix = "app.dart")
public record DartProperties(String apiKey, String baseUrl, int lookbackDays, int financialYear) {

    private static final String DEFAULT_BASE_URL = "https://opendart.fss.or.kr/api";
    private static final int DEFAULT_LOOKBACK_DAYS = 7;

    public DartProperties {
        if (baseUrl == null || baseUrl.isBlank()) {
            baseUrl = DEFAULT_BASE_URL;
        }
        if (lookbackDays <= 0) {
            lookbackDays = DEFAULT_LOOKBACK_DAYS;
        }
        if (financialYear < 0) {
            financialYear = 0;
        }
    }

    /**
     * 키가 없으면 수집을 열지 않는다. DART 는 키가 틀려도 HTTP 200 에 {@code status:"013"} 같은
     * 오류 코드를 실어 보내므로, 키 없이 돌리면 매 회차가 조용히 빈손으로 끝난다.
     */
    public boolean isConfigured() {
        return apiKey != null && !apiKey.isBlank();
    }
}
