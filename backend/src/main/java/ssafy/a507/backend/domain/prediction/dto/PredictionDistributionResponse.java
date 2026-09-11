package ssafy.a507.backend.domain.prediction.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * GET /api/v1/stocks/{code}/predictions/distribution 200 응답 — 종목 상세(B-03)의 예측 호가창 (ANT-PRED-07).
 *
 * <p><b>개인이 없다.</b> 예측 id·작성자·목표가 원본을 싣지 않는다. 있으면 구간을 되짚어 누가 어디에 걸었는지
 * 복원할 수 있고, 종목 상세에서 개별 예측을 뺀 이유가 사라진다.
 *
 * <p>{@code basePrice} 는 전일 종가다(현재가가 아니다, §7 legal). 종가가 아직 없는 종목은 {@code basePrice·asOf} 가
 * null 이고 {@code buckets} 가 비어 있다 — 기준 없이 구간을 지어내지 않는다. {@code total} 은 그때도 센 값이다.
 *
 * @param total 서버가 센 판정 대기 수. 화면이 구간을 더해 쓰지 않는다
 * @param buckets 싼 쪽에서 비싼 쪽 순서
 */
public record PredictionDistributionResponse(
        String stockCode,
        BigDecimal basePrice,
        LocalDate asOf,
        int stepPct,
        long total,
        List<Bucket> buckets) {

    /**
     * 한 구간 [fromPrice, toPrice). 양 끝은 열린 구간이라 한쪽이 null 이다.
     *
     * <p>경계 가격을 서버가 주는 이유: 화면이 %로 다시 계산하면 반올림이 갈려 "전일 종가" 와 0% 경계가 어긋난다.
     * 그리고 서버는 <b>이 가격으로</b> 셌다 — 표시된 경계와 센 기준이 같다.
     */
    public record Bucket(Integer fromPct, Integer toPct, BigDecimal fromPrice, BigDecimal toPrice, long count) {}
}
