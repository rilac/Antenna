package ssafy.a507.backend.domain.auth.service;

import com.nimbusds.jwt.SignedJWT;
import java.text.ParseException;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;
import ssafy.a507.backend.common.error.BusinessException;
import ssafy.a507.backend.common.error.ErrorCode;
import ssafy.a507.backend.domain.account.entity.UserOauth;

/** 프론트가 받은 인가 코드를 구글 토큰 엔드포인트에서 교환하고 계정 식별자를 꺼낸다. */
@Slf4j
@Component
public class GoogleOAuthClient implements OAuthClient {

    private final ClientRegistration registration;
    private final RestClient restClient = RestClient.create();

    public GoogleOAuthClient(ClientRegistrationRepository registrations) {
        this.registration = registrations.findByRegistrationId("google");
    }

    @Override
    public UserOauth.Provider provider() {
        return UserOauth.Provider.GOOGLE;
    }

    @Override
    public OAuthAccount exchange(String code, String redirectUri) {
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("code", code);
        form.add("client_id", registration.getClientId());
        form.add("client_secret", registration.getClientSecret());
        form.add("redirect_uri", redirectUri);
        form.add("grant_type", "authorization_code");

        Map<?, ?> body;
        try {
            body =
                    restClient
                            .post()
                            .uri(registration.getProviderDetails().getTokenUri())
                            .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                            .body(form)
                            .retrieve()
                            .body(Map.class);
        } catch (RestClientResponseException e) {
            // invalid_grant(코드 무효·만료·redirect_uri 불일치)가 대부분이다.
            log.warn("구글 인가 코드 교환 실패", e);
            throw new BusinessException(ErrorCode.INVALID_REQUEST);
        }

        Object idToken = body == null ? null : body.get("id_token");
        if (idToken == null) {
            // 아래 세 갈래가 한 code 로 합쳐지므로 어느 지점이었는지는 로그로만 남는다.
            log.warn("구글 토큰 응답에 id_token 이 없다");
            throw new BusinessException(ErrorCode.UNAUTHENTICATED);
        }
        // TLS 로 구글 토큰 엔드포인트에서 직접 받은 값이라 서명 재검증 없이 클레임만 읽는다.
        try {
            var claims = SignedJWT.parse(idToken.toString()).getJWTClaimsSet();
            String sub = claims.getSubject();
            if (sub == null) {
                log.warn("구글 id_token 에 sub 가 없다");
                throw new BusinessException(ErrorCode.UNAUTHENTICATED);
            }
            return new OAuthAccount(sub, claims.getStringClaim("email"));
        } catch (ParseException e) {
            log.warn("구글 id_token 파싱 실패", e);
            throw new BusinessException(ErrorCode.UNAUTHENTICATED);
        }
    }
}
