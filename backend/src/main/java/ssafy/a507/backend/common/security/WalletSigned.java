package ssafy.a507.backend.common.security;

/**
 * 지갑 서명이 붙는 요청 DTO가 구현한다.
 *
 * <p>payload 조립은 DTO마다 다르고(금액·대상이 요청마다 다르니까) 검증은 SignatureGuard가 독점한다.
 * 이 분리가 없으면 컨트롤러마다 검증이 흩어져 ANT-AUTH-06의 취지가 죽는다.
 */
public interface WalletSigned {

    /** 0x + r(32) s(32) v(1) = 130 hex. */
    String signature();

    /**
     * 실제로 서명된 문자열. <b>서버가 body 필드로 직접 조립한다</b> —
     * 클라이언트가 보낸 payload 문자열을 그대로 받으면, payload에는 A를 적고
     * 처리용 필드에는 B를 넣는 변조가 가능해진다.
     */
    String signingPayload(String nonce, long chainId);
}
