package ssafy.a507.backend.common.security;

/**
 * 지갑 서명의 1회성 nonce 보관소.
 * 인터페이스로 둔 이유: 운영은 Redis지만 테스트는 도커 없이 돌아야 한다(CLAUDE.md).
 */
public interface SignatureNonceStore {

    /** userId당 1개만 살아 있다. 재발급하면 이전 nonce는 죽는다 — nonce 비축을 막는다. */
    String issue(Long userId);

    /** 읽으면서 지운다. 없거나 이미 쓰였거나 만료면 null. */
    String consume(Long userId);
}
