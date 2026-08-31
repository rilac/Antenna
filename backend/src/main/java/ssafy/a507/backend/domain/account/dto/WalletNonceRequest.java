package ssafy.a507.backend.domain.account.dto;

import jakarta.validation.constraints.NotNull;
import ssafy.a507.backend.common.security.SignatureScope;

/**
 * nonce를 어느 칸에 발급할지 고른다. 지갑 연동뿐 아니라 온체인 동반 요청 전부가 이 창구를 쓴다.
 * 칸이 나뉘어 있어 예측 등록 서명을 띄워 둔 채 구독 결제를 시작해도 서로를 죽이지 않는다.
 */
public record WalletNonceRequest(@NotNull(message = "서명 용도를 지정해주세요.") SignatureScope scope) {}
