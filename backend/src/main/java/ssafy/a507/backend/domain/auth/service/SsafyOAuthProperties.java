package ssafy.a507.backend.domain.auth.service;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * SSAFY SSO 설정. 개발자센터가 발급하는 값이라 코드에 상수로 박지 않는다.
 *
 * <p>인증 서버(토큰 발급)와 사용자 API(userInfo)가 문서상 서로 다른 서버라 주소를 각각 받는다.
 */
@ConfigurationProperties(prefix = "app.auth.ssafy")
public record SsafyOAuthProperties(
        String clientId,
        String clientSecret,
        String tokenUri,
        String userInfoUri,
        /** userInfo 응답에서 불변 ID 가 담긴 필드명. 구글의 sub 에 해당한다. */
        String userNameAttribute,
        /** userInfo 응답에서 이메일이 담긴 필드명. 표시용이라 비어 있어도 된다. */
        String emailAttribute) {

    /**
     * 값이 하나라도 비면 로그인을 열지 않는다.
     *
     * <p>반쯤 설정된 채로 열어 두면 사용자는 동의까지 마치고 나서야 실패한다 — 아예 막고
     * 501 을 돌려주는 편이 원인이 분명하다. email 은 표시용이라 필수에서 뺀다.
     */
    public boolean isConfigured() {
        return hasText(clientId)
                && hasText(clientSecret)
                && hasText(tokenUri)
                && hasText(userInfoUri)
                && hasText(userNameAttribute);
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
