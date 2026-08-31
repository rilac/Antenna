package ssafy.a507.backend.common.security;

/**
 * 지갑 서명의 1회성 nonce 보관소.
 * 인터페이스로 둔 이유: 운영은 Redis지만 테스트는 도커 없이 돌아야 한다(CLAUDE.md).
 */
public interface SignatureNonceStore {

    /**
     * (userId, scope)당 1개만 살아 있다. 같은 칸에 재발급하면 이전 nonce는 죽는다 — nonce 비축을 막는다.
     * 칸을 scope로 나눈 덕에 예측 등록 서명을 띄워 둔 채 구독 결제를 시작해도 서로를 죽이지 않는다.
     */
    String issue(Long userId, SignatureScope scope);

    /** 읽으면서 지운다. 없거나 이미 쓰였거나 만료면 null. */
    String consume(Long userId, SignatureScope scope);
}
