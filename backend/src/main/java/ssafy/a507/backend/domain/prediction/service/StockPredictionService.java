package ssafy.a507.backend.domain.prediction.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ssafy.a507.backend.common.error.BusinessException;
import ssafy.a507.backend.common.error.ErrorCode;
import ssafy.a507.backend.domain.market.entity.DailyQuote;
import ssafy.a507.backend.domain.market.repository.DailyQuoteRepository;
import ssafy.a507.backend.domain.market.repository.StockRepository;
import ssafy.a507.backend.domain.prediction.dto.PredictionDistributionResponse;
import ssafy.a507.backend.domain.prediction.dto.SettledTodayResponse;
import ssafy.a507.backend.domain.prediction.entity.Prediction;
import ssafy.a507.backend.domain.prediction.repository.PredictionRepository;

/**
 * 종목 상세(B-03)의 예측 블록 두 개 (ANT-PRED-07) — 호가창과 오늘 판정.
 *
 * <p>종목 상세는 2026-09-10 개편으로 개별 예측을 보여주지 않는다. 판정 대기는 구간별 인원만, 개인은 판정이 끝난
 * 오늘 건만 머리에 띄운다. 판정 후는 전체 공개라 여기엔 구독 판정이 없다.
 */
@Service
@RequiredArgsConstructor
public class StockPredictionService {

    /** 구간 폭 %. 좁히면(2%) 줄이 스물 가까이 되어 구간마다 한두 명씩 흩어지고 "어디에 몰렸나" 가 안 보인다. */
    static final int STEP_PCT = 5;

    /** 이 바깥은 양 끝의 열린 구간 하나로 몰아넣는다. 상·하한가가 없어 끝을 닫을 수 없다. */
    static final int EDGE_PCT = 20;

    /** "오늘" 은 장 기준이라 서버 시간대와 무관하게 KST 로 자른다. */
    private static final ZoneId KST = ZoneId.of("Asia/Seoul");

    private static final List<Prediction.Status> PENDING = List.of(Prediction.Status.BASE, Prediction.Status.OPEN);
    private static final List<Prediction.Status> JUDGED = List.of(Prediction.Status.HIT, Prediction.Status.MISS);

    private final StockRepository stocks;
    private final DailyQuoteRepository quotes;
    private final PredictionRepository predictions;

    /**
     * 판정 대기 예측의 목표가 분포. 끝난 예측은 세지 않는다 — 호가창은 지금 걸려 있는 물량이고 끝난 예측은 기록이다.
     *
     * <p><b>경계 가격을 먼저 정하고 그 가격으로 센다.</b> %로 구간을 정하고 가격을 따로 반올림해 내리면, 229,080원이
     * 화면에 "229,100 이상" 으로 그려진 구간 밖에 들어가 있는 모순이 생긴다. 경계가 한 벌이면 표시와 셈이 어긋날 수 없다.
     */
    @Transactional(readOnly = true)
    public PredictionDistributionResponse distribution(String code) {
        requireStock(code);
        List<BigDecimal> targets = predictions.findTargetPricesByStockAndStatusIn(code, PENDING);

        // 종가와 그 날짜를 한 행에서 꺼낸다(CorpInfoService.valuation 과 같은 이유) — 화면 머리의 "N일 종가" 와 같은 값.
        DailyQuote quote = quotes.findTopByStock_CodeOrderByTradeDateDesc(code).orElse(null);
        if (quote == null) {
            // 기준이 없으면 구간을 지어내지 않는다. 세는 것은 할 수 있으니 total 은 준다.
            return new PredictionDistributionResponse(code, null, null, STEP_PCT, targets.size(), List.of());
        }
        BigDecimal base = quote.getClose();

        // [null,-20) [-20,-15) … [15,20) [20,null) — 이웃 구간이 경계를 공유하므로 모든 목표가가 정확히 한 구간에 든다.
        List<Integer> lows = new ArrayList<>();
        List<Integer> highs = new ArrayList<>();
        lows.add(null);
        for (int pct = -EDGE_PCT; pct <= EDGE_PCT; pct += STEP_PCT) {
            lows.add(pct);
            highs.add(pct);
        }
        highs.add(null);

        List<PredictionDistributionResponse.Bucket> buckets = new ArrayList<>();
        for (int i = 0; i < lows.size(); i++) {
            Integer fromPct = lows.get(i);
            Integer toPct = highs.get(i);
            BigDecimal from = fromPct == null ? null : priceAt(base, fromPct);
            BigDecimal to = toPct == null ? null : priceAt(base, toPct);
            long count = targets.stream()
                    .filter(t -> (from == null || t.compareTo(from) >= 0) && (to == null || t.compareTo(to) < 0))
                    .count();
            buckets.add(new PredictionDistributionResponse.Bucket(fromPct, toPct, from, to, count));
        }
        return new PredictionDistributionResponse(
                code, base, quote.getTradeDate(), STEP_PCT, targets.size(), buckets);
    }

    /** 오늘(KST) 판정 배치가 HIT·MISS 로 확정한 이 종목의 예측. 기준이 updated_at 인 이유는 리포지토리 주석. */
    @Transactional(readOnly = true)
    public SettledTodayResponse settledToday(String code) {
        requireStock(code);
        LocalDate today = LocalDate.now(KST);
        Instant from = today.atStartOfDay(KST).toInstant();
        Instant to = today.plusDays(1).atStartOfDay(KST).toInstant();
        List<SettledTodayResponse.Item> items = predictions.findSettledBetween(code, JUDGED, from, to).stream()
                .map(SettledTodayResponse.Item::from)
                .toList();
        return new SettledTodayResponse(items);
    }

    /**
     * 전일 종가 대비 pct% 가격. 원 단위 반올림이다 — 100원 단위(프론트 목업)면 1,000원 종목의 경계가 전부 뭉개지고,
     * KRX 호가단위는 규정 개정마다 표가 바뀐다. 0% 는 종가 그대로라 "전일 종가" 표시와 어긋나지 않는다.
     */
    static BigDecimal priceAt(BigDecimal base, int pct) {
        return base.multiply(BigDecimal.valueOf(100L + pct)).divide(BigDecimal.valueOf(100), 0, RoundingMode.HALF_UP);
    }

    private void requireStock(String code) {
        if (!stocks.existsById(code)) {
            throw new BusinessException(ErrorCode.STOCK_NOT_FOUND, "code");
        }
    }
}
