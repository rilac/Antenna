package ssafy.a507.backend.domain.prediction.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import ssafy.a507.backend.domain.market.entity.Stock;
import ssafy.a507.backend.domain.prediction.entity.Prediction;

/**
 * GET /api/v1/channels/{userId}/predictions 목록 한 줄 (ANT-PRED-05, 화면 E-02).
 *
 * <p>잠긴 건도 목록에서 빼지 않는다. 종목은 전체 공개라 "이 사람이 이 종목에 걸었다" 까지는 보이고,
 * 판정 전 방향·목표가만 null 이 된다({@code locked: true}). 빼 버리면 채널이 실제보다 한가해 보이고 구독 유인이 사라진다.
 *
 * <p>필드 이름은 {@link MyPredictionItemResponse} 와 맞췄다(만기일은 {@code settleDate}). 같은 예측을 두 화면이
 * 다른 이름으로 받지 않게 한다.
 */
public record ChannelPredictionItemResponse(
        long id,
        String stockCode,
        String stockName,
        Prediction.Direction direction,
        BigDecimal targetPrice,
        short horizon,
        Prediction.Status status,
        Integer dday,
        BigDecimal errorRate,
        LocalDate settleDate,
        boolean locked) {

    public static ChannelPredictionItemResponse of(Prediction p, LocalDate today, boolean locked) {
        Stock stock = p.getStock();
        return new ChannelPredictionItemResponse(
                p.getId(),
                stock == null ? null : stock.getCode(),
                stock == null ? null : stock.getName(),
                locked ? null : p.getDirection(),
                locked ? null : p.getTargetPrice(),
                p.getHorizon(),
                p.getStatus(),
                MyPredictionItemResponse.dday(p, today),
                p.getErrorRate(),
                p.getSettleDate(),
                locked);
    }
}
