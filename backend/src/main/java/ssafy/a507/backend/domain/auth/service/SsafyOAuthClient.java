package ssafy.a507.backend.domain.auth.service;

import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;
import ssafy.a507.backend.common.error.BusinessException;
import ssafy.a507.backend.common.error.ErrorCode;
import ssafy.a507.backend.domain.account.entity.UserOauth;

/**
 * SSAFY SSO. 구글과 달리 토큰 응답에 id_token 이 없어 두 번 나간다 — 인가 코드로 access token 을
 * 받고, 그 토큰으로 사용자 API 의 userInfo 를 읽는다(개발자센터 연동 흐름 6~9단계).
 *
 * <p>응답 필드명은 프로바이더마다 달라 설정으로 받는다. 값이 비어 있으면 로그인을 501 로 막는다.
 */
@Slf4j
@Component
@EnableConfigurationProperties(SsafyOAuthProperties.class)
public class SsafyOAuthClient implements OAuthClient {

    private final SsafyOAuthProperties properties;
    private final RestClient restClient = RestClient.create();

    public SsafyOAuthClient(SsafyOAuthProperties properties) {
        this.properties = properties;
    }

    @Override
    public UserOauth.Provider provider() {
        return UserOauth.Provider.SSAFY;
    }

    @Override
    public OAuthAccount exchange(String code, String redirectUri) {
        if (!properties.isConfigured()) {
            throw new BusinessException(ErrorCode.PROVIDER_NOT_SUPPORTED);
        }

        Map<?, ?> userInfo = requestUserInfo(requestAccessToken(code, redirectUri));

        String providerUserId = attribute(userInfo, properties.userNameAttribute());
        if (providerUserId == null) {
            // 매칭 키가 없으면 같은 사람인지 확인할 방법이 없다. 로그인시키면 안 된다.
            log.warn("SSAFY 사용자 정보에 {} 가 없다", properties.userNameAttribute());
            throw new BusinessException(ErrorCode.UNAUTHENTICATED);
        }
        return new OAuthAccount(providerUserId, attribute(userInfo, properties.emailAttribute()));
    }

    private String requestAccessToken(String code, String redirectUri) {
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("grant_type", "authorization_code");
        form.add("code", code);
        form.add("client_id", properties.clientId());
        form.add("client_secret", properties.clientSecret());
        form.add("redirect_uri", redirectUri);

        Map<?, ?> body;
        try {
            body =
                    restClient
                            .post()
                            .uri(properties.tokenUri())
                            .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                            .body(form)
                            .retrieve()
                            .body(Map.class);
        } catch (RestClientResponseException e) {
            // invalid_grant(코드 무효·만료·redirect_uri 불일치)가 대부분이다.
            log.warn("SSAFY 인가 코드 교환 실패", e);
            throw new BusinessException(ErrorCode.INVALID_REQUEST);
        }

        Object accessToken = body == null ? null : body.get("access_token");
        if (accessToken == null) {
            log.warn("SSAFY 토큰 응답에 access_token 이 없다");
            throw new BusinessException(ErrorCode.UNAUTHENTICATED);
        }
        return accessToken.toString();
    }

    private Map<?, ?> requestUserInfo(String accessToken) {
        Map<?, ?> body;
        try {
            body =
                    restClient
                            .get()
                            .uri(properties.userInfoUri())
                            .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                            // 본문 없는 GET 이라 contentType() 을 못 쓴다.
                            // 문서가 명시한 헤더라 값만 직접 실어 보낸다.
                            .header(
                                    HttpHeaders.CONTENT_TYPE,
                                    MediaType.APPLICATION_FORM_URLENCODED_VALUE)
                            .retrieve()
                            .body(Map.class);
        } catch (RestClientResponseException e) {
            log.warn("SSAFY 사용자 정보 조회 실패", e);
            throw new BusinessException(ErrorCode.UNAUTHENTICATED);
        }
        if (body == null) {
            log.warn("SSAFY 사용자 정보 응답이 비어 있다");
            throw new BusinessException(ErrorCode.UNAUTHENTICATED);
        }
        return body;
    }

    /** 설정된 필드명으로 값을 꺼낸다. 필드명이 비었거나(email) 응답에 없으면 null. */
    private String attribute(Map<?, ?> source, String name) {
        if (name == null || name.isBlank()) {
            return null;
        }
        Object value = source.get(name);
        if (value == null || value.toString().isBlank()) {
            return null;
        }
        return value.toString();
    }
}
