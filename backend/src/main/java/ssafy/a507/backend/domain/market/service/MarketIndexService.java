package ssafy.a507.backend.domain.market.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Limit;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ssafy.a507.backend.common.error.BusinessException;
import ssafy.a507.backend.common.error.ErrorCode;
import ssafy.a507.backend.domain.market.dto.MarketIndexItemResponse;
import ssafy.a507.backend.domain.market.dto.MarketIndexListResponse;
import ssafy.a507.backend.domain.market.entity.IndexQuote;
import ssafy.a507.backend.domain.market.entity.IndexQuote.IndexCode;
import ssafy.a507.backend.domain.market.repository.IndexQuoteRepository;

/**
 * 시장 Overview 조회(ANT-DATA-04). 홈의 지수 카드 하나가 이 API 하나로 그려진다.
 *
 * <p>등락률은 포털이 주는 {@code fltRt} 를 저장하지 않고 우리가 가진 두 점으로 다시 낸다 —
 * 환율에는 그런 값이 없어 어차피 계산해야 하고, 세 지수가 같은 식으로 나와야 화면이 한 규칙으로
 * 읽힌다. 점이 하나뿐이면 0 이다. 없는 값을 지어내지 않는다.
 *
 * <p>{@code days} 는 달력 날짜가 아니라 점의 수다. 미니차트는 점 30개를 원하지, "최근 30일 안의
 * 영업일 21개"를 원하지 않는다. 종목 시세({@code /stocks/{code}/prices})가 달력 구간을 받는 것과
 * 다른 이유가 그것이다.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class MarketIndexService {

    private static final int DEFAULT_DAYS = 30;

    /** 미니차트가 그릴 수 있는 상한. 넘게 달라고 하면 자르되 오류로 두지는 않는다. */
    private static final int MAX_DAYS = 365;

    private static final int RATE_SCALE = 2;
    private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);

    private final IndexQuoteRepository indexQuoteRepository;

    public MarketIndexListResponse indices(Integer days) {
        int points = points(days);

        List<MarketIndexItemResponse> items = new ArrayList<>();
        for (IndexCode code : IndexCode.values()) {
            List<IndexQuote> latestFirst =
                    indexQuoteRepository.findByIndexCodeOrderByTradeDateDesc(code, Limit.of(points));
            if (latestFirst.isEmpty()) {
                // 아직 한 점도 없다. 빈 값을 내려 두면 화면이 그것을 진짜 0 으로 읽는다.
                continue;
            }

            List<BigDecimal> series = new ArrayList<>(latestFirst.size());
            for (int i = latestFirst.size() - 1; i >= 0; i--) {
                series.add(latestFirst.get(i).getClose());
            }
            BigDecimal close = latestFirst.get(0).getClose();
            BigDecimal previous = latestFirst.size() > 1 ? latestFirst.get(1).getClose() : null;

            items.add(new MarketIndexItemResponse(
                    code.name(), close, changeRate(close, previous), series));
        }
        return new MarketIndexListResponse(items);
    }

    private static int points(Integer days) {
        if (days == null) {
            return DEFAULT_DAYS;
        }
        if (days <= 0) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST, "days");
        }
        return Math.min(days, MAX_DAYS);
    }

    /** (close − previous) / previous × 100, 소수 둘째 자리 반올림. 전일이 없으면 0. */
    static BigDecimal changeRate(BigDecimal close, BigDecimal previous) {
        if (previous == null || previous.signum() == 0) {
            return BigDecimal.ZERO.setScale(RATE_SCALE, RoundingMode.HALF_UP);
        }
        return close.subtract(previous)
                .multiply(HUNDRED)
                .divide(previous, RATE_SCALE, RoundingMode.HALF_UP);
    }
}
