package ssafy.a507.backend.domain.auth.service;

import com.nimbusds.jwt.SignedJWT;
import java.text.ParseException;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.server.ResponseStatusException;

/** 프론트가 받은 인가 코드를 구글 토큰 엔드포인트에서 교환하고 계정 식별자를 꺼낸다. */
@Component
public class GoogleOAuthClient {

    /** 프로바이더가 발급한 불변 식별자(sub)와 표시용 이메일. */
    public record GoogleAccount(String providerUserId, String email) {}

    private final ClientRegistration registration;
    private final RestClient restClient = RestClient.create();

    public GoogleOAuthClient(ClientRegistrationRepository registrations) {
        this.registration = registrations.findByRegistrationId("google");
    }

    public GoogleAccount exchange(String code, String redirectUri) {
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
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "인가 코드 교환 실패");
        }

        Object idToken = body == null ? null : body.get("id_token");
        if (idToken == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "구글 id_token 없음");
        }
        // TLS 로 구글 토큰 엔드포인트에서 직접 받은 값이라 서명 재검증 없이 클레임만 읽는다.
        try {
            var claims = SignedJWT.parse(idToken.toString()).getJWTClaimsSet();
            String sub = claims.getSubject();
            if (sub == null) {
                throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "구글 sub 없음");
            }
            return new GoogleAccount(sub, claims.getStringClaim("email"));
        } catch (ParseException e) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "구글 id_token 파싱 실패");
        }
    }
}
