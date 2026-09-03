package ssafy.a507.backend.domain.research.service;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import ssafy.a507.backend.domain.market.entity.DailyQuote;
import ssafy.a507.backend.domain.market.entity.Stock;
import ssafy.a507.backend.domain.market.repository.DailyQuoteRepository;
import ssafy.a507.backend.domain.market.repository.StockRepository;
import ssafy.a507.backend.domain.research.entity.CorpFinancial;
import ssafy.a507.backend.domain.research.repository.CorpFinancialRepository;

/**
 * 배치 B6 가 프롬프트에 넣는 종목 재료 — 브리핑(ANT-RESEARCH-03)과 포인트(-04)가 함께 쓴다.
 *
 * <p><b>수치는 전부 여기서 계산한다.</b> 등락률·거래량 배수·부채비율을 모델에게 계산시키면 자릿수를
 * 틀린 문장이 그대로 화면에 나가고, 검산할 방법이 없다. 모델은 주어진 수치를 서술만 한다.
 *
 * <p>따로 뺀 이유는 "그날의 대상 종목"과 "종목 수치"가 두 생성기에서 같아야 하기 때문이다. 각자
 * 들고 있으면 한쪽 라벨만 고쳐지고 두 화면이 서로 다른 숫자를 말하게 된다.
 */
@Component
@RequiredArgsConstructor
class StockMaterials {

    /**
     * 한 회차의 종목 상한. 수집 범위가 KOSPI 300 이라 평소엔 걸리지 않는 안전판이다 — 범위를
     * 전 종목으로 넓히면 하루 2,500콜이 되어 한도가 하루에 끝난다.
     */
    static final int MAX_STOCKS_PER_RUN = 400;

    /** 52주 고저 계산 구간. 달력 1년이면 영업일 ~245개다. */
    private static final int LOOKBACK_YEARS = 1;

    private final StockRepository stockRepository;
    private final DailyQuoteRepository dailyQuoteRepository;
    private final CorpFinancialRepository corpFinancialRepository;

    /**
     * 생성 대상 = 기준일에 시세가 있는 상장 종목.
     *
     * <p>{@code stocks} 표는 시가총액 상위 300 이 날마다 들고나며 코드가 쌓이고 {@code listed} 가
     * false 로 내려가는 일이 없어, 표 전체를 대상으로 잡으면 옛 멤버까지 헛돌고 상한에 밀려 정작
     * 오늘 멤버(코드 큰 종목)가 빠진다. 기준일 일봉이 있는 종목이 그날의 300 이다.
     */
    List<Stock> quotedOn(LocalDate targetDate) {
        Map<String, Stock> listed = stockRepository.findAll().stream()
                .filter(Stock::isListed)
                .collect(Collectors.toMap(Stock::getCode, Function.identity()));
        if (listed.isEmpty()) {
            return List.of();
        }
        return dailyQuoteRepository.findByTradeDateAndStock_CodeIn(targetDate, listed.keySet()).stream()
                .map(q -> listed.get(q.getStock().getCode()))
                .sorted(Comparator.comparing(Stock::getCode))
                .limit(MAX_STOCKS_PER_RUN)
                .toList();
    }

    /**
     * 종목 수치 재료 — 시세 등락·거래량·52주 위치 + 3개년 재무.
     *
     * @return 기준일 시세가 없으면 null. 재료 없이 만들면 지어낸 글이 된다.
     */
    String facts(Stock stock, LocalDate targetDate) {
        List<DailyQuote> quotes = dailyQuoteRepository.findByStock_CodeAndTradeDateBetweenOrderByTradeDate(
                stock.getCode(), targetDate.minusYears(LOOKBACK_YEARS), targetDate);
        if (quotes.size() < 2 || !quotes.get(quotes.size() - 1).getTradeDate().equals(targetDate)) {
            return null;
        }
        // 최신이 앞에 오도록 뒤집는다 — 지수와 같은 계산기를 쓴다.
        List<BigDecimal> closes = new ArrayList<>(quotes.stream().map(DailyQuote::getClose).toList());
        Collections.reverse(closes);

        StringBuilder sb = new StringBuilder();
        sb.append("종목: ").append(stock.getName()).append(" (").append(stock.getCode()).append(")\n");
        sb.append("기준일: ").append(targetDate).append('\n');
        sb.append("종가 ").append(plain(closes.get(0)))
                .append("원 · 1영업일 ").append(rate(closes, 1))
                .append(" · 5영업일 ").append(rate(closes, 5))
                .append(" · 20영업일 ").append(rate(closes, 20))
                .append('\n');
        appendVolume(sb, quotes);
        append52Week(sb, quotes, closes.get(0));
        appendFinancials(sb, stock.getCode());
        return sb.toString();
    }

    private static void appendVolume(StringBuilder sb, List<DailyQuote> quotes) {
        DailyQuote last = quotes.get(quotes.size() - 1);
        if (last.getVolume() == null) {
            return;
        }
        List<Long> prior = quotes.subList(Math.max(0, quotes.size() - 21), quotes.size() - 1).stream()
                .map(DailyQuote::getVolume)
                .filter(v -> v != null && v > 0)
                .toList();
        if (prior.isEmpty()) {
            return;
        }
        double avg = prior.stream().mapToLong(Long::longValue).average().orElse(0);
        BigDecimal multiple = BigDecimal.valueOf(last.getVolume() / avg).setScale(1, RoundingMode.HALF_UP);
        sb.append("거래량 ").append(last.getVolume()).append("주 · 최근 20영업일 평균의 ")
                .append(multiple).append("배\n");
    }

    private static void append52Week(StringBuilder sb, List<DailyQuote> quotes, BigDecimal close) {
        BigDecimal high = quotes.stream().map(DailyQuote::getClose).max(Comparator.naturalOrder()).orElse(close);
        BigDecimal low = quotes.stream().map(DailyQuote::getClose).min(Comparator.naturalOrder()).orElse(close);
        sb.append("52주 종가 최고 ").append(plain(high)).append("원 · 최저 ").append(plain(low)).append("원");
        if (high.compareTo(low) > 0) {
            BigDecimal position = close.subtract(low)
                    .multiply(BigDecimal.valueOf(100))
                    .divide(high.subtract(low), 0, RoundingMode.HALF_UP);
            // "저점 대비 N%" 라고 쓰면 모델이 "최저가보다 N% 높다"로 읽는다(2026-09-03 실호출에서 확인).
            // 0~100 사이 위치임을 라벨에 박는다.
            sb.append(" · 52주 범위 내 위치 ").append(position).append("% (0%=최저, 100%=최고)");
        }
        sb.append('\n');
    }

    private void appendFinancials(StringBuilder sb, String stockCode) {
        List<CorpFinancial> rows =
                corpFinancialRepository.findByStockCodeOrderByFiscalYearDescQuarterDesc(stockCode).stream()
                        .filter(f -> f.getQuarter() == CorpFinancial.ANNUAL_QUARTER)
                        .limit(3)
                        .toList();
        if (rows.isEmpty()) {
            sb.append("재무: (없음)\n");
            return;
        }
        sb.append("연간 재무(").append(rows.get(0).getFsDiv()).append(", 억원): ");
        for (CorpFinancial f : rows) {
            sb.append(f.getFiscalYear()).append("년 매출 ").append(eok(f.getRevenue()))
                    .append(" 영업이익 ").append(eok(f.getOperatingProfit()))
                    .append(" 순이익 ").append(eok(f.getNetIncome()))
                    .append("; ");
        }
        sb.setLength(sb.length() - 2);
        sb.append('\n');
        CorpFinancial latest = rows.get(0);
        if (latest.getTotalEquity() == null || latest.getTotalEquity().signum() <= 0) {
            return;
        }
        List<String> ratios = new ArrayList<>();
        if (latest.getTotalLiabilities() != null) {
            ratios.add("부채비율 " + ratio(latest.getTotalLiabilities(), latest.getTotalEquity()) + "%");
        }
        if (latest.getNetIncome() != null) {
            ratios.add("ROE " + ratio(latest.getNetIncome(), latest.getTotalEquity()) + "%");
        }
        if (!ratios.isEmpty()) {
            sb.append(String.join(" · ", ratios)).append(" (").append(latest.getFiscalYear()).append("년 기준)\n");
        }
    }

    // ── 계산기 ───────────────────────────────────────────────

    /** {@code closes} 는 최신이 앞. {@code back} 영업일 전 대비 등락률, 그만큼 없으면 "-". */
    static String rate(List<BigDecimal> closes, int back) {
        if (closes.size() <= back) {
            return "-";
        }
        BigDecimal r = changeRate(closes.get(0), closes.get(back));
        return r == null ? "-" : pct(r);
    }

    static BigDecimal changeRate(BigDecimal now, BigDecimal before) {
        if (now == null || before == null || before.signum() == 0) {
            return null;
        }
        return now.subtract(before)
                .multiply(BigDecimal.valueOf(100))
                .divide(before, 2, RoundingMode.HALF_UP);
    }

    static String pct(BigDecimal rate) {
        return (rate.signum() > 0 ? "+" : "") + rate.setScale(2, RoundingMode.HALF_UP).toPlainString() + "%";
    }

    static String plain(BigDecimal value) {
        return value.stripTrailingZeros().toPlainString();
    }

    private static String eok(BigInteger won) {
        return won == null ? "-" : won.divide(BigInteger.valueOf(100_000_000L)).toString();
    }

    private static BigDecimal ratio(BigInteger numerator, BigInteger denominator) {
        return new BigDecimal(numerator)
                .multiply(BigDecimal.valueOf(100))
                .divide(new BigDecimal(denominator), 1, RoundingMode.HALF_UP);
    }
}
