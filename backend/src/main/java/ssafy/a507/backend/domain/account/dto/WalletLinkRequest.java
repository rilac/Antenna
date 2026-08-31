package ssafy.a507.backend.domain.account.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import java.util.Locale;
import ssafy.a507.backend.common.security.SignatureScope;
import ssafy.a507.backend.common.security.WalletSigned;

/**
 * 지갑 연동 요청. nonce는 본문에 없다 — 서버가 (userId, scope)로 Redis에서 꺼내 payload를 재조립한다.
 * 클라이언트가 되돌려 보낼 필요가 없고, 그만큼 변조할 수 있는 면도 줄어든다.
 */
public record WalletLinkRequest(
        @NotBlank(message = "지갑 주소를 입력해주세요.")
                @Pattern(
                        regexp = "^0x[0-9a-fA-F]{40}$",
                        message = "지갑 주소 형식이 올바르지 않습니다.")
                String address,
        @NotBlank(message = "서명이 필요합니다.")
                @Pattern(
                        regexp = "^0x[0-9a-fA-F]{130}$",
                        message = "서명 형식이 올바르지 않습니다.")
                String signature)
        implements WalletSigned {

    @Override
    public SignatureScope scope() {
        return SignatureScope.WALLET_LINK;
    }

    /**
     * 프론트도 이 문자열을 그대로 만들어 personal_sign 해야 한다.
     * 줄바꿈·순서·소문자 중 하나만 어긋나도 복원 주소가 달라져 401이 난다.
     */
    @Override
    public String signingPayload(String nonce, long chainId) {
        return "antenna:"
                + scope().tag()
                + ":v1\n"
                + "address="
                + address.toLowerCase(Locale.ROOT)
                + "\n"
                + "chainId="
                + chainId
                + "\n"
                + "nonce="
                + nonce;
    }
}
