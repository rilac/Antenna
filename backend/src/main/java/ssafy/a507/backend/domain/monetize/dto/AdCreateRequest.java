package ssafy.a507.backend.domain.monetize.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import ssafy.a507.backend.common.security.SignatureScope;
import ssafy.a507.backend.common.security.WalletSigned;

/**
 * POST /api/v1/ads 요청 (ANT-COMMUNITY-05).
 *
 * <p>배너 이미지는 {@code POST /uploads}(purpose=AD) 가 발급한 fileId 로만 받는다.
 * 외부 URL 을 받지 않는 이유는 ① 서버 프리뷰·렌더 시 SSRF ② 이미지 URL 이 추적 픽셀로 쓰여
 * 열람자 IP 가 광고주에게 수집됨 ③ 승인 후 URL 내용만 바꿔치기, 셋이다.
 *
 * <p>{@code linkUrl} 은 https 만 받는다. http 링크는 중간에서 갈아 끼울 수 있고, 배너는
 * 클릭을 유도하는 자리라 피싱 대상이 된다.
 */
public record AdCreateRequest(
        @NotBlank(message = "배너 이미지를 올려주세요.") String imageFileId,
        @NotBlank(message = "이동할 주소를 입력해주세요.")
                @Pattern(regexp = "^https://\\S+$", message = "https 주소만 등록할 수 있습니다.")
                String linkUrl,
        @NotNull(message = "노출 기간을 입력해주세요.")
                @Min(value = 1, message = "노출 기간은 하루 이상이어야 합니다.")
                Integer days,
        @NotBlank(message = "서명이 필요합니다.")
                @Pattern(regexp = "^0x[0-9a-fA-F]{130}$", message = "서명 형식이 올바르지 않습니다.")
                String signature)
        implements WalletSigned {

    @Override
    public SignatureScope scope() {
        return SignatureScope.AD;
    }

    /**
     * 지불 대상과 기간이 전부 들어간다. 하나라도 빠지면 사용자가 서명한 내용과 서버가 처리하는
     * 내용이 달라질 수 있다 — 예를 들어 days 가 빠지면 3일치 서명으로 30일치를 등록할 수 있다.
     */
    @Override
    public String signingPayload(String nonce, long chainId) {
        return "antenna:"
                + scope().tag()
                + ":v1\n"
                + "imageFileId="
                + imageFileId
                + "\n"
                + "linkUrl="
                + linkUrl
                + "\n"
                + "days="
                + days
                + "\n"
                + "chainId="
                + chainId
                + "\n"
                + "nonce="
                + nonce;
    }
}
