package ssafy.a507.backend.global.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.charset.StandardCharsets;
import javax.crypto.spec.SecretKeySpec;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;

/** 발급한 access token 을 리소스 서버 디코더가 그대로 읽는지 확인한다. */
class JwtProviderTest {

    private static final String SECRET = "antenna-test-secret-key-at-least-32-bytes-long";

    private static NimbusJwtDecoder decoder(String secret) {
        return NimbusJwtDecoder.withSecretKey(
                        new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"))
                .macAlgorithm(MacAlgorithm.HS256)
                .build();
    }

    @Test
    void 발급한_토큰의_sub와_role을_디코더가_읽는다() throws Exception {
        String token = new JwtProvider(SECRET, 30).issueAccessToken(42L, "USER");

        Jwt decoded = decoder(SECRET).decode(token);

        assertThat(decoded.getSubject()).isEqualTo("42");
        assertThat(decoded.getClaimAsString("role")).isEqualTo("USER");
        assertThat(decoded.getExpiresAt()).isAfter(decoded.getIssuedAt());
    }

    @Test
    void 다른_키로_서명된_토큰은_거부된다() throws Exception {
        String token = new JwtProvider("another-secret-key-at-least-32-bytes-long!", 30)
                .issueAccessToken(42L, "USER");

        assertThatThrownBy(() -> decoder(SECRET).decode(token)).isInstanceOf(JwtException.class);
    }

    @Test
    void 만료된_토큰은_거부된다() throws Exception {
        String token = new JwtProvider(SECRET, -1).issueAccessToken(42L, "USER");

        assertThatThrownBy(() -> decoder(SECRET).decode(token)).isInstanceOf(JwtException.class);
    }
}
