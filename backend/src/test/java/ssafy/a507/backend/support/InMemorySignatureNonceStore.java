package ssafy.a507.backend.support;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import ssafy.a507.backend.common.security.SignatureNonceStore;
import ssafy.a507.backend.common.security.SignatureScope;

/**
 * 테스트용 nonce 저장소. 도커 없이 ./gradlew build가 돌아야 해서 Redis를 대신한다.
 * TTL은 흉내 내지 않는다 — 만료 동작은 실제 Redis로만 검증되고, 진행상황.md의 수동 항목이다.
 */
public class InMemorySignatureNonceStore implements SignatureNonceStore {

    private final Map<String, String> nonces = new ConcurrentHashMap<>();

    @Override
    public String issue(Long userId, SignatureScope scope) {
        String nonce = UUID.randomUUID().toString();
        nonces.put(key(userId, scope), nonce);
        return nonce;
    }

    /** remove가 원자적이라 GETDEL과 같은 성질을 갖는다. */
    @Override
    public String consume(Long userId, SignatureScope scope) {
        return nonces.remove(key(userId, scope));
    }

    private String key(Long userId, SignatureScope scope) {
        return userId + ":" + scope.tag();
    }
}
