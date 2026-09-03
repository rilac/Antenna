package ssafy.a507.backend.domain.research.client;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 네이버 뉴스 검색 설정 (ANT-RESEARCH-02).
 *
 * <p><b>구 개발자센터 검색 API 가 아니다.</b> 네이버가 검색 API 를 NAVER API HUB(네이버
 * 클라우드 플랫폼)로 옮기면서 주소와 인증 헤더가 모두 바뀌었다 — {@code X-Naver-Client-Id}
 * 가 아니라 {@code X-NCP-APIGW-API-KEY-ID} 다. 옛 문서를 보고 헤더를 맞추면 인증이 통과되지
 * 않는다.
 *
 * @param keyId {@code X-NCP-APIGW-API-KEY-ID} · 비어 있으면 수집을 아예 돌리지 않는다
 * @param key {@code X-NCP-APIGW-API-KEY}
 * @param baseUrl API HUB 기본 주소
 * @param displayPerStock 종목당 한 회차에 받을 기사 수 · HUB 상한이 100 이다
 * @param lookbackDays 이 구간보다 오래된 기사는 버린다 · 검색은 최신순이라 대개 걸리지 않지만,
 *     뉴스가 뜸한 종목에서는 몇 달 전 기사가 딸려 온다
 */
@ConfigurationProperties(prefix = "app.naver-news")
public record NaverNewsProperties(
        String keyId, String key, String baseUrl, int displayPerStock, int lookbackDays) {

    private static final String DEFAULT_BASE_URL = "https://naverapihub.apigw.ntruss.com/search/v1";

    /** HUB 가 받는 {@code display} 상한. */
    public static final int MAX_DISPLAY = 100;

    private static final int DEFAULT_DISPLAY = 20;
    private static final int DEFAULT_LOOKBACK_DAYS = 14;

    public NaverNewsProperties {
        if (keyId != null) {
            keyId = keyId.trim();
        }
        if (key != null) {
            key = key.trim();
        }
        if (baseUrl == null || baseUrl.isBlank()) {
            baseUrl = DEFAULT_BASE_URL;
        }
        // 경로를 붙일 때 "/" 를 우리가 넣는다. 설정에 끝 슬래시가 있으면 "//" 가 된다.
        while (baseUrl.endsWith("/")) {
            baseUrl = baseUrl.substring(0, baseUrl.length() - 1);
        }
        if (displayPerStock <= 0) {
            displayPerStock = DEFAULT_DISPLAY;
        }
        if (displayPerStock > MAX_DISPLAY) {
            displayPerStock = MAX_DISPLAY;
        }
        if (lookbackDays <= 0) {
            lookbackDays = DEFAULT_LOOKBACK_DAYS;
        }
    }

    /** 자격증명 둘 다 있어야 부른다. 하나만 있으면 매 호출이 401 로 돌아온다. */
    public boolean isConfigured() {
        return keyId != null && !keyId.isBlank() && key != null && !key.isBlank();
    }
}
