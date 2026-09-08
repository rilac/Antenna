package ssafy.a507.backend.domain.season.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import ssafy.a507.backend.domain.season.entity.SeasonTrade;

/**
 * 시즌 성적 계산 — 체결 내역을 첫날부터 다시 돌려 일별 자산 곡선을 만든다(ANT-SEASON-04).
 *
 * <p>DB 를 모르는 순수 함수다. 입력은 체결(오래된 순)과 종가표, 출력은 {@code season_results} 의 지표.
 *
 * <p><b>보유일.</b> 포지션이 0 에서 열린 게임일을 기억하고, 매도마다 (매도일 − 연 날) 을 샘플로 모은다.
 * 추가 매수는 연 날을 바꾸지 않는다 — 평단이 가중 평균이라 lot 을 나누지 않는 것과 같은 단순화다.
 */
public final class SeasonResultCalculator {

    private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);

    private SeasonResultCalculator() {}

    /** 지표 묶음. null 은 "정의할 수 없다" 다(매도가 없으면 승률·보유일, 손실이 없으면 손익비). */
    public record Result(
            BigDecimal finalAsset,
            BigDecimal returnRate,
            BigDecimal benchmarkReturn,
            BigDecimal maxDrawdown,
            BigDecimal winRate,
            BigDecimal profitFactor,
            BigDecimal avgHoldingDays) {}

    /** 게임일 d 의 종가. 그날 봉이 없으면 직전 종가를 쓴다(휴장 구멍은 워밍업에만 있지만 방어). */
    public interface Closes {
        BigDecimal close(Long tickerId, int gameDay);
    }

    /**
     * @param initialCash 시즌 출발 예수금
     * @param lengthDays 총 게임일 N. 자산 곡선은 D+1 … D+N 이다
     * @param trades 이 회차의 체결, 오래된 순
     * @param closes 거래한 종목의 종가
     * @param benchmarkStart 시즌 전 종목의 첫 게임일 종가(종목 id → 종가)
     * @param benchmarkEnd 시즌 전 종목의 마지막 게임일 종가
     */
    public static Result compute(
            BigDecimal initialCash,
            int lengthDays,
            List<SeasonTrade> trades,
            Closes closes,
            Map<Long, BigDecimal> benchmarkStart,
            Map<Long, BigDecimal> benchmarkEnd) {

        BigDecimal cash = initialCash;
        Map<Long, Integer> qty = new HashMap<>();
        Map<Long, Integer> openedDay = new HashMap<>();
        List<Integer> holdingDays = new ArrayList<>();
        int wins = 0;
        int sells = 0;
        BigDecimal grossProfit = BigDecimal.ZERO;
        BigDecimal grossLoss = BigDecimal.ZERO;

        BigDecimal peak = initialCash;
        BigDecimal maxDrawdown = BigDecimal.ZERO;
        BigDecimal equity = initialCash;
        int cursor = 0;

        for (int day = 1; day <= lengthDays; day++) {
            // 그날의 체결을 순서대로 반영한다. 체결가는 그날 종가라 자산은 그날 종가 기준이다.
            while (cursor < trades.size() && trades.get(cursor).getGameDay() <= day) {
                SeasonTrade tr = trades.get(cursor++);
                Long ticker = tr.getTicker().getId();
                BigDecimal amount = tr.getPrice().multiply(BigDecimal.valueOf(tr.getQty()));
                int held = qty.getOrDefault(ticker, 0);
                if (tr.getSide() == SeasonTrade.Side.BUY) {
                    cash = cash.subtract(amount);
                    if (held == 0) {
                        openedDay.put(ticker, tr.getGameDay());
                    }
                    qty.put(ticker, held + tr.getQty());
                } else {
                    cash = cash.add(amount);
                    qty.put(ticker, held - tr.getQty());
                    holdingDays.add(tr.getGameDay() - openedDay.getOrDefault(ticker, tr.getGameDay()));
                    sells++;
                    BigDecimal pnl = tr.getRealizedPnl() == null ? BigDecimal.ZERO : tr.getRealizedPnl();
                    if (pnl.signum() > 0) {
                        wins++;
                        grossProfit = grossProfit.add(pnl);
                    } else {
                        grossLoss = grossLoss.add(pnl.abs());
                    }
                }
            }

            equity = cash;
            for (Map.Entry<Long, Integer> h : qty.entrySet()) {
                if (h.getValue() > 0) {
                    equity = equity.add(closes.close(h.getKey(), day).multiply(BigDecimal.valueOf(h.getValue())));
                }
            }
            if (equity.compareTo(peak) > 0) {
                peak = equity;
            } else if (peak.signum() > 0) {
                BigDecimal dd = peak.subtract(equity).multiply(HUNDRED).divide(peak, 3, RoundingMode.HALF_UP);
                if (dd.compareTo(maxDrawdown) > 0) {
                    maxDrawdown = dd;
                }
            }
        }

        BigDecimal finalAsset = equity.setScale(2, RoundingMode.HALF_UP);
        BigDecimal returnRate = finalAsset.subtract(initialCash)
                .multiply(HUNDRED).divide(initialCash, 3, RoundingMode.HALF_UP);

        BigDecimal winRate = sells == 0 ? null
                : BigDecimal.valueOf(wins).multiply(HUNDRED).divide(BigDecimal.valueOf(sells), 2, RoundingMode.HALF_UP);
        BigDecimal profitFactor = sells == 0 || grossLoss.signum() == 0 ? null
                : grossProfit.divide(grossLoss, 3, RoundingMode.HALF_UP);
        BigDecimal avgHolding = holdingDays.isEmpty() ? null
                : BigDecimal.valueOf(holdingDays.stream().mapToInt(Integer::intValue).sum())
                        .divide(BigDecimal.valueOf(holdingDays.size()), 2, RoundingMode.HALF_UP);

        return new Result(finalAsset, returnRate, benchmark(benchmarkStart, benchmarkEnd),
                maxDrawdown, winRate, profitFactor, avgHolding);
    }

    /** 등가중 벤치마크 — 첫날·마지막날 둘 다 있는 종목의 (끝/처음 − 1) 평균, %. 없으면 null. */
    static BigDecimal benchmark(Map<Long, BigDecimal> start, Map<Long, BigDecimal> end) {
        BigDecimal sum = BigDecimal.ZERO;
        int n = 0;
        for (Map.Entry<Long, BigDecimal> s : start.entrySet()) {
            BigDecimal e = end.get(s.getKey());
            if (e == null || s.getValue().signum() == 0) {
                continue;
            }
            sum = sum.add(e.divide(s.getValue(), 8, RoundingMode.HALF_UP).subtract(BigDecimal.ONE));
            n++;
        }
        return n == 0 ? null : sum.multiply(HUNDRED).divide(BigDecimal.valueOf(n), 3, RoundingMode.HALF_UP);
    }
}
