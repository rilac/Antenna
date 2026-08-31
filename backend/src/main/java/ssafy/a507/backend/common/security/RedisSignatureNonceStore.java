package ssafy.a507.backend.common.security;

import java.time.Duration;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

/**
 * nonce는 5분 뒤 Redis가 알아서 지운다. AOF가 꺼져 있어 재시작 시 유실되지만,
 * 그때는 "서명을 다시 받으세요"로 끝나는 값이라 영속성이 필요 없다.
 */
@Component
@RequiredArgsConstructor
public class RedisSignatureNonceStore implements SignatureNonceStore {

    private static final String KEY_PREFIX = "sig:nonce:";
    private static final Duration TTL = Duration.ofMinutes(5);

    private final StringRedisTemplate redisTemplate;

    @Override
    public String issue(Long userId, SignatureScope scope) {
        String nonce = UUID.randomUUID().toString();
        redisTemplate.opsForValue().set(key(userId, scope), nonce, TTL);
        return nonce;
    }

    /**
     * GETDEL 한 번으로 읽고 지운다.
     * 조회와 삭제를 두 명령으로 나누면 동시에 들어온 2건이 둘 다 조회를 통과한다.
     */
    @Override
    public String consume(Long userId, SignatureScope scope) {
        return redisTemplate.opsForValue().getAndDelete(key(userId, scope));
    }

    /** Redis에는 WHERE가 없다. 조회 조건을 키 이름에 그대로 박아 넣는다. */
    private String key(Long userId, SignatureScope scope) {
        return KEY_PREFIX + userId + ":" + scope.tag();
    }
}
