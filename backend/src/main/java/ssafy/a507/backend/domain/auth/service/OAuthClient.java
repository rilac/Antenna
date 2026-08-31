package ssafy.a507.backend.domain.auth.service;

import ssafy.a507.backend.domain.account.entity.UserOauth;

/**
 * 프로바이더별 인가 코드 교환.
 *
 * <p>구글은 토큰 응답의 id_token 에서, SSAFY 는 user-info 응답에서 계정을 꺼내지만
 * AuthService 입장에서는 "코드를 주면 계정이 나온다"는 같은 계약이다. 프로바이더가
 * 늘어도 AuthService 는 그대로 두고 구현체만 추가한다.
 */
public interface OAuthClient {

    /** 프로바이더가 발급한 불변 식별자와 표시용 이메일. */
    record OAuthAccount(String providerUserId, String email) {}

    /** 이 구현체가 담당하는 프로바이더. AuthService 가 이 값으로 구현체를 고른다. */
    UserOauth.Provider provider();

    OAuthAccount exchange(String code, String redirectUri);
}
