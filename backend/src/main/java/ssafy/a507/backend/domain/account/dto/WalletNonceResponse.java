package ssafy.a507.backend.domain.account.dto;

/**
 * 1회성 nonce와 서명에 쓸 체인 번호.
 *
 * <p>chainId를 같이 내리는 이유: 프론트가 이 값을 지갑에서 읽으면 <b>사용자가 고른 네트워크</b> 값이
 * 와서 서버가 조립한 payload와 어긋난다. 그러면 복원 주소가 달라져 원인 없는 401이 난다.
 * nonce를 받는 시점이 곧 payload 조립 직전이라 여기 같이 실어 보내는 게 왕복도 안 늘린다.
 */
public record WalletNonceResponse(String nonce, long chainId) {}
