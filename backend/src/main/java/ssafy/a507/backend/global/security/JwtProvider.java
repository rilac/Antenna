package ssafy.a507.backend.global.security;

import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.MACSigner;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/** Access token 발급 전용. 검증은 리소스 서버가 {@link SecurityConfig} 의 JwtDecoder 로 처리한다. */
@Component
public class JwtProvider {

    private final MACSigner signer;
    private final Duration accessTtl;

    public JwtProvider(
            @Value("${app.auth.jwt-secret}") String secret,
            @Value("${app.auth.access-ttl-minutes}") long accessTtlMinutes)
            throws JOSEException {
        this.signer = new MACSigner(secret.getBytes(java.nio.charset.StandardCharsets.UTF_8));
        this.accessTtl = Duration.ofMinutes(accessTtlMinutes);
    }

    /** sub 는 users.id, role 은 인가 판단용. */
    public String issueAccessToken(Long userId, String role) {
        Instant now = Instant.now();
        JWTClaimsSet claims =
                new JWTClaimsSet.Builder()
                        .subject(String.valueOf(userId))
                        .claim("role", role)
                        .issueTime(Date.from(now))
                        .expirationTime(Date.from(now.plus(accessTtl)))
                        .build();
        SignedJWT jwt = new SignedJWT(new JWSHeader(JWSAlgorithm.HS256), claims);
        try {
            jwt.sign(signer);
        } catch (JOSEException e) {
            throw new IllegalStateException("access token 서명 실패", e);
        }
        return jwt.serialize();
    }
}
