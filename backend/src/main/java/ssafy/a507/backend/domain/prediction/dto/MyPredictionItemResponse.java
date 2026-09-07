package ssafy.a507.backend.domain.prediction.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import ssafy.a507.backend.domain.market.entity.Stock;
import ssafy.a507.backend.domain.prediction.entity.Prediction;

/**
 * GET /api/v1/predictions/me 의 목록 항목 (ANT-PRED-06, 화면 C-02).
 *
 * <p>명세의 아홉 필드에 {@code stockName} 을 더했다 — 목록에서 종목코드만 보여줄 수는 없고,
 * 화면이 코드마다 종목 조회를 한 번 더 하는 것을 막는다(프론트 요청, 2026-09-07).
 *
 * <p>{@code status} 는 엔티티의 네 값({@code BASE·OPEN·HIT·MISS})을 그대로 내린다.
 * 필터 파라미터의 {@code PENDING} 은 BASE·OPEN 을 묶은 조회용 어휘일 뿐이고 저장된 상태가 아니다.
 */
public record MyPredictionItemResponse(
        long id,
        String stockCode,
        String stockName,
        Prediction.Direction direction,
        BigDecimal targetPrice,
        short horizon,
        Prediction.Status status,
        Integer dday,
        BigDecimal errorRate,
        LocalDate settleDate) {

    public static MyPredictionItemResponse of(Prediction p, LocalDate today) {
        Stock stock = p.getStock();
        return new MyPredictionItemResponse(
                p.getId(),
                stock == null ? null : stock.getCode(),
                stock == null ? null : stock.getName(),
                p.getDirection(),
                p.getTargetPrice(),
                p.getHorizon(),
                p.getStatus(),
                dday(p, today),
                p.getErrorRate(),
                p.getSettleDate());
    }

    /**
     * 만기까지 남은 일수. 판정된 건과 만기일이 아직 없는 건(REAL 은 기준가 배치가 채우기 전)은 null 이다 —
     * 0 으로 채우면 화면이 "오늘 만기" 로 읽는다.
     */
    private static Integer dday(Prediction p, LocalDate today) {
        if (p.getSettleDate() == null
                || p.getStatus() == Prediction.Status.HIT
                || p.getStatus() == Prediction.Status.MISS) {
            return null;
        }
        return (int) ChronoUnit.DAYS.between(today, p.getSettleDate());
    }
}
