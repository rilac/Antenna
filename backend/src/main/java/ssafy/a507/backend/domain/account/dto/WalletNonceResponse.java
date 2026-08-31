package ssafy.a507.backend.domain.account.dto;

/** 지갑 연동 서명에 쓸 1회성 nonce. 5분 안에 쓰지 않으면 만료된다. */
public record WalletNonceResponse(String nonce) {}
