package ssafy.a507.backend.domain.account.dto;

/** 연동된 지갑 주소. 소문자로 정규화된 값이다. */
public record WalletLinkResponse(String walletAddress) {}
