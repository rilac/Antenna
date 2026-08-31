package ssafy.a507.backend.domain.auth.service;

import java.security.SecureRandom;
import java.time.Duration;
import java.util.Base64;
import java.util.Set;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

/**
 * Refresh 토큰 회전 관리.
 *
 * <p>키 3종 — {@code rt:{token}} 유효 토큰→userId, {@code rt:user:{userId}} 유저별 활성 토큰 집합, {@code
 * rt:used:{token}} 회전으로 폐기된 토큰. 폐기된 토큰이 다시 들어오면 탈취로 보고 해당 유저 토큰을 전부 지운다.
 */
@Component
public class RefreshTokenStore {

    /** 회전 성공 시 새로 발급된 토큰과 소유자. */
    public record Rotated(Long userId, String token) {}

    /** 재사용 탐지·만료 등 refresh 흐름이 끊긴 경우. 호출부가 401로 바꾼다. */
    public static class InvalidRefreshTokenException extends RuntimeException {
        public InvalidRefreshTokenException(String message) {
            super(message);
        }
    }

    private static final String TOKEN_KEY = "rt:";
    private static final String USER_KEY = "rt:user:";
    private static final String USED_KEY = "rt:used:";

    private final StringRedisTemplate redis;
    private final SecureRandom random = new SecureRandom();
    private final Duration ttl;

    public RefreshTokenStore(
            StringRedisTemplate redis, @Value("${app.auth.refresh-ttl-days}") long refreshTtlDays) {
        this.redis = redis;
        this.ttl = Duration.ofDays(refreshTtlDays);
    }

    public Duration ttl() {
        return ttl;
    }

    public String issue(Long userId) {
        byte[] bytes = new byte[32];
        random.nextBytes(bytes);
        String token = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        redis.opsForValue().set(TOKEN_KEY + token, String.valueOf(userId), ttl);
        redis.opsForSet().add(USER_KEY + userId, token);
        redis.expire(USER_KEY + userId, ttl);
        return token;
    }

    public Rotated rotate(String token) {
        String reusedBy = redis.opsForValue().get(USED_KEY + token);
        if (reusedBy != null) {
            revokeAll(Long.valueOf(reusedBy));
            throw new InvalidRefreshTokenException("폐기된 refresh 토큰 재사용");
        }

        String userId = redis.opsForValue().get(TOKEN_KEY + token);
        if (userId == null) {
            throw new InvalidRefreshTokenException("알 수 없거나 만료된 refresh 토큰");
        }

        redis.delete(TOKEN_KEY + token);
        redis.opsForSet().remove(USER_KEY + userId, token);
        redis.opsForValue().set(USED_KEY + token, userId, ttl);
        return new Rotated(Long.valueOf(userId), issue(Long.valueOf(userId)));
    }

    /** 로그아웃. 제시된 토큰만 지운다. */
    public void revoke(String token) {
        String userId = redis.opsForValue().get(TOKEN_KEY + token);
        if (userId == null) {
            return;
        }
        redis.delete(TOKEN_KEY + token);
        redis.opsForSet().remove(USER_KEY + userId, token);
        redis.opsForValue().set(USED_KEY + token, userId, ttl);
    }

    public void revokeAll(Long userId) {
        Set<String> tokens = redis.opsForSet().members(USER_KEY + userId);
        if (tokens != null) {
            tokens.forEach(t -> redis.delete(TOKEN_KEY + t));
        }
        redis.delete(USER_KEY + userId);
    }
}
