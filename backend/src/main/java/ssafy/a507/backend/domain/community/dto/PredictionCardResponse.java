package ssafy.a507.backend.domain.community.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.math.BigDecimal;
import ssafy.a507.backend.domain.prediction.entity.Prediction;

/**
 * 글에 붙은 예측 카드.
 *
 * <p><b>명세에 없는 조합을 여기서 정했다.</b> §예측 공개 규칙은 미판정(BASE·OPEN) 예측을
 * 작성자·구독자 외에는 403 으로 막으라고 한다. 그런데 피드 글은 전체 공개다. 미구독자가
 * 미판정 예측이 붙은 글을 열었다고 글 전체를 403 으로 막을 수는 없다.
 *
 * <p>그래서 리포트 카드와 같은 방식을 쓴다 — 카드만 {@code locked: true} 로 내리고 종목과
 * 방향까지만 보여준다. 목표가·기대 수익 같은 값은 null 로 빼서 응답에서 사라진다. 이 조합은
 * 팀 확인이 필요하다(Jira S15P21A507-70 코멘트).
 *
 * <p>판정 완료(HIT·MISS)는 전체 공개이므로 잠기지 않는다.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record PredictionCardResponse(
        Long id,
        AuthorResponse author,
        String stockCode,
        String stockName,
        Prediction.Direction direction,
        BigDecimal targetPrice,
        Prediction.Status status,
        boolean locked) {

    public static PredictionCardResponse of(Prediction prediction, boolean locked) {
        return new PredictionCardResponse(
                prediction.getId(),
                AuthorResponse.from(prediction.getUser()),
                prediction.getStock() == null ? null : prediction.getStock().getCode(),
                prediction.getStock() == null ? null : prediction.getStock().getName(),
                prediction.getDirection(),
                locked ? null : prediction.getTargetPrice(),
                prediction.getStatus(),
                locked);
    }

    /** 미판정 예측은 비공개다 — 작성자와 구독자만 전문을 본다. */
    public static boolean isGated(Prediction prediction) {
        return prediction.getStatus() == Prediction.Status.BASE
                || prediction.getStatus() == Prediction.Status.OPEN;
    }
}
