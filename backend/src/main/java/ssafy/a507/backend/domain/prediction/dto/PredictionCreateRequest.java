package ssafy.a507.backend.domain.prediction.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.util.List;
import ssafy.a507.backend.common.security.SignatureScope;
import ssafy.a507.backend.common.security.WalletSigned;
import ssafy.a507.backend.domain.prediction.commit.CommitHashes;
import ssafy.a507.backend.domain.prediction.commit.CommitPayload;
import ssafy.a507.backend.domain.prediction.entity.Prediction;

/**
 * 예측 등록 요청 (ANT-PRED-01, 명세 v0.38 {@code POST /predictions}).
 *
 * <p>여기서 잡는 건 <b>모양</b>뿐이다. 종목 존재·직전 종가와의 모순·근거 포인트 소속·슬롯은 서비스가 본다 —
 * DB 를 봐야 아는 것들이라서다. 형식 오류는 여기서 400 으로 끝나고, 그러면 nonce 를 태우지 않는다.
 *
 * <p>{@code noteSalt} 는 이 시스템의 유일한 난수다(ANT-PRED-02). 클라이언트가 만들어 보내고 서버는 형식만 본다.
 */
public record PredictionCreateRequest(
        @NotBlank(message = "종목코드를 입력해주세요.")
                @Pattern(regexp = "^\\d{6}$", message = "종목코드는 숫자 6자리입니다.")
                String stockCode,
        @NotNull(message = "방향(UP/DOWN)을 선택해주세요.") Prediction.Direction direction,
        @NotNull(message = "목표가를 입력해주세요.")
                @DecimalMin(value = "0.01", message = "목표가는 0보다 커야 합니다.")
                @Digits(integer = 12, fraction = 2, message = "목표가는 소수 둘째 자리까지만 입력할 수 있습니다.")
                BigDecimal targetPrice,
        @NotNull(message = "예측 기간을 선택해주세요.") Short horizon,
        @NotBlank(message = "근거를 입력해주세요.")
                @Size(max = 5000, message = "근거는 5000자까지 입력할 수 있습니다.")
                String note,
        @NotBlank(message = "noteSalt 가 필요합니다.")
                @Pattern(regexp = "^[0-9a-f]{64}$", message = "noteSalt 는 소문자 64자리 hex 여야 합니다.")
                String noteSalt,
        List<@NotNull(message = "근거 포인트 id 가 비어 있습니다.") Long> evidencePointIds,
        @NotBlank(message = "서명이 필요합니다.")
                @Pattern(regexp = "^0x[0-9a-fA-F]{130}$", message = "서명 형식이 올바르지 않습니다.")
                String signature)
        implements WalletSigned {

    @Override
    public SignatureScope scope() {
        return SignatureScope.PREDICTION;
    }

    /**
     * 서명 문자열 (결정 B3). 가운데 다섯 줄은 커밋 문자열과 <b>같은 코드</b>({@link CommitPayload#lines()})로 만든다 —
     * 포맷 규칙이 두 군데면 {@code 82000} 과 {@code 82000.00} 이 갈려 서명은 통과하고 해시는 안 맞는 사고가 난다.
     *
     * <pre>
     * antenna:prediction:v1
     * stockCode=… / direction=… / targetPrice=… / horizon=… / noteHash=…
     * chainId=…
     * nonce=…
     * </pre>
     */
    @Override
    public String signingPayload(String nonce, long chainId) {
        return "antenna:" + scope().tag() + ":v1\n"
                + String.join("\n", commitPayload().lines())
                + "\nchainId=" + chainId
                + "\nnonce=" + nonce;
    }

    /** 커밋 재료. noteHash 는 여기서 한 번 계산해 서명 문자열과 봉인이 같은 값을 쓴다. */
    public CommitPayload commitPayload() {
        return new CommitPayload(
                stockCode, direction, targetPrice, horizon, CommitHashes.noteHash(note, noteSalt));
    }

    /** null 도 빈 목록으로 — 근거는 선택이다. */
    public List<Long> evidencePointIdsOrEmpty() {
        return evidencePointIds == null ? List.of() : evidencePointIds;
    }
}
