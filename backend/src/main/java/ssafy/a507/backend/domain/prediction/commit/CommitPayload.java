package ssafy.a507.backend.domain.prediction.commit;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import ssafy.a507.backend.common.error.BusinessException;
import ssafy.a507.backend.common.error.ErrorCode;
import ssafy.a507.backend.domain.prediction.entity.Prediction;

/**
 * 커밋 문자열의 재료와 조립 규칙 (ANT-PRED-02, 결정 B2).
 *
 * <p>해시 대상은 JSON 이 아니라 <b>줄 구분 문자열</b>이다. Jackson 과 {@code JSON.stringify} 는 키 순서·공백·숫자 표기가 달라
 * 서로 다른 바이트를 만들 수 있고, 커밋은 "누구나 재계산" 이 존재 이유라 바이트 단위로 고정돼야 한다. 서명(§1.2)이 같은 이유로
 * 줄 문자열을 택했으니 그 판단을 재사용한다.
 *
 * <pre>
 * antenna:commit:v1
 * stockCode=005930
 * direction=UP
 * targetPrice=82000.00
 * horizon=30
 * noteHash=0x…
 * </pre>
 *
 * <ul>
 *   <li>구분은 {@code \n} 하나, 마지막 줄 뒤 개행 없음, UTF-8.
 *   <li>{@code salt=} 줄은 <b>없다</b>. {@code noteHash} 가 noteSalt 의 난수성을 물려받아 그 역할을 겸한다(09-09 결정).
 *   <li>{@code createdAt} 도 없다. 서버 시각이라 검증자가 재현할 수 없고, "이때 있었다" 는 앵커 블록이 증명한다.
 *   <li>첫 줄이 서명 문자열({@code antenna:prediction:v1})과 다르다 — 서명이 커밋으로, 커밋이 서명으로 오인·재사용되지 않게.
 * </ul>
 *
 * <p>{@link #lines()} 를 따로 노출하는 이유: PRED-01 의 서명 문자열이 같은 다섯 줄을 그대로 쓴다. 두 군데서 따로 {@code targetPrice}
 * 를 문자열로 만들면 {@code 82000} 과 {@code 82000.00} 이 갈려 서명은 통과하고 해시는 안 맞는 사고가 난다. <b>포맷 규칙은 여기 한 곳.</b>
 */
public record CommitPayload(
        String stockCode, Prediction.Direction direction, BigDecimal targetPrice, short horizon, String noteHash) {

    public static final String HEADER = "antenna:commit:v1";

    public CommitPayload {
        // 예측은 실전(REAL) 전용이라 종목코드가 없는 커밋은 없다(명세 POST /predictions).
        if (stockCode == null || stockCode.isBlank()) {
            throw new IllegalArgumentException("커밋에 종목코드가 없다");
        }
        if (direction == null || targetPrice == null || noteHash == null) {
            throw new IllegalArgumentException("커밋 재료가 비었다: direction/targetPrice/noteHash");
        }
    }

    public static CommitPayload of(Prediction prediction, String noteHash) {
        return new CommitPayload(
                prediction.getStock() == null ? null : prediction.getStock().getCode(),
                prediction.getDirection(),
                prediction.getTargetPrice(),
                prediction.getHorizon(),
                noteHash);
    }

    /** 필드 줄 다섯 개. 서명 문자열(PRED-01)도 이 줄을 그대로 쓴다. */
    public List<String> lines() {
        return List.of(
                "stockCode=" + stockCode,
                "direction=" + direction.name(),
                "targetPrice=" + formatPrice(targetPrice),
                "horizon=" + horizon,
                "noteHash=" + noteHash);
    }

    /** 해시 대상 문자열 전체. */
    public String canonical() {
        return HEADER + "\n" + String.join("\n", lines());
    }

    /**
     * 항상 소수 둘째 자리, 지수 표기 없음 — DB {@code numeric(14,2)} 와 같은 모양. 셋째 자리 이하가 있으면 <b>반올림하지 않고 거절</b>한다.
     * 조용히 반올림하면 프론트가 서명한 값과 서버가 해시한 값이 달라진다.
     */
    static String formatPrice(BigDecimal price) {
        try {
            return price.setScale(2, RoundingMode.UNNECESSARY).toPlainString();
        } catch (ArithmeticException e) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST, "targetPrice");
        }
    }
}
