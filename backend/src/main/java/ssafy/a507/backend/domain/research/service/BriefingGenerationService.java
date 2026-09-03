package ssafy.a507.backend.domain.research.service;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataAccessException;
import org.springframework.data.domain.Limit;
import org.springframework.stereotype.Service;
import ssafy.a507.backend.common.ai.AiClient;
import ssafy.a507.backend.common.ai.AiException;
import ssafy.a507.backend.common.ai.AiProperties;
import ssafy.a507.backend.domain.market.entity.DailyQuote;
import ssafy.a507.backend.domain.market.entity.IndexQuote;
import ssafy.a507.backend.domain.market.entity.Stock;
import ssafy.a507.backend.domain.market.repository.DailyQuoteRepository;
import ssafy.a507.backend.domain.market.repository.IndexQuoteRepository;
import ssafy.a507.backend.domain.market.repository.StockRepository;
import ssafy.a507.backend.domain.research.entity.AiBriefing;
import ssafy.a507.backend.domain.research.entity.CorpFinancial;
import ssafy.a507.backend.domain.research.entity.ResearchDocument;
import ssafy.a507.backend.domain.research.repository.AiBriefingRepository;
import ssafy.a507.backend.domain.research.repository.CorpFinancialRepository;
import ssafy.a507.backend.domain.research.repository.ResearchDocumentRepository;

/**
 * AI 브리핑 생성 — 배치 B6 의 브리핑 갈래 (ANT-RESEARCH-03).
 *
 * <p><b>수치는 전부 여기서 계산해 프롬프트에 넣는다.</b> 등락률·거래량 배수·부채비율을 모델에게
 * 계산시키면 자릿수를 틀린 문장이 그대로 화면에 나가고, 검산할 방법이 없다. 모델은 주어진
 * 수치를 서술만 한다.
 *
 * <p><b>D16 — 확률 수치 직접 제시 금지.</b> 프롬프트로 막고, 응답을 다시 검사해 걸리면 저장하지
 * 않는다. 프롬프트만 믿으면 열 번에 한 번은 "상승 가능성 60%" 같은 문장이 섞여 나온다.
 *
 * <p>기준일은 오늘이 아니라 <b>일봉이 들어온 마지막 영업일</b>이다. 주말·휴일에도 배치는 도는데
 * 그날은 새 시세가 없으니 금요일 브리핑이 이미 있으면(같은 세대) 건너뛴다 — 같은 글을 세 번
 * 만들어 한도를 태우지 않는다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class BriefingGenerationService {

    /**
     * 한 회차의 종목 상한. 수집 범위가 KOSPI 300 이라 평소엔 걸리지 않는 안전판이다 — 범위를
     * 전 종목으로 넓히면 하루 2,500콜이 되어 한도가 하루에 끝난다.
     *
     * <p>ponytail: 상수다. 종목을 골라 돌려야 할 만큼 한도가 빡빡해지면 관심 종목·상위 N 필터를
     * 설정으로 뺀다.
     */
    private static final int MAX_STOCKS_PER_RUN = 400;

    /** 52주 고저 계산 구간. 달력 1년이면 영업일 ~245개다. */
    private static final int LOOKBACK_YEARS = 1;

    /** 브리핑에 넣는 최근 뉴스 요약 수. 더 넣으면 프롬프트만 길어지고 문단은 늘지 않는다. */
    private static final int NEWS_PER_STOCK = 5;

    /** 시장 브리핑에 넣는 상승·하락 상위 종목 수. */
    private static final int MOVERS = 5;

    /**
     * D16 위반 패턴 셋 — ① 방향 + 확률·가능성("상승 확률", "오를 가능성이"), ② 확률·가능성 + 수치
     * ("가능성이 60%"), ③ 방향 + 수치("하락 68%"). "3.2% 하락" 같은 사실 서술(수치가 앞)은 통과한다
     * — 그건 우리가 준 값이다.
     *
     * <p>"확률" 단어 하나만으로는 막지 않는다. 뉴스 요약("금리 인하 확률이 높아졌다")이 재료로
     * 들어가면 모델이 그대로 옮기는데, 그걸 막으면 그 종목은 매일 호출하고 매일 버린다.
     */
    static final Pattern FORBIDDEN = Pattern.compile(
            "(상승|하락|오를|내릴|반등|급등|급락)[^.\\n]{0,12}(확률|가능성)"
                    + "|(확률|가능성)[^.\\n]{0,8}\\d+(\\.\\d+)?\\s*%"
                    + "|(상승|하락)\\s*\\d+(\\.\\d+)?\\s*%");

    private static final ZoneId KST = ZoneId.of("Asia/Seoul");

    private static final String INSTRUCTION =
            """
            너는 한국 주식 리서치 화면에 들어갈 AI 브리핑을 쓴다. 주어진 수치는 서버가 계산한 값이다.
            아래 규칙을 반드시 지켜라.

            - 첫 줄에 헤드라인 한 줄(40자 이내)을 쓰고, 빈 줄 뒤에 본문을 3~4개 문단으로 쓴다.
            - 한국어 평서문만 쓴다. 목록·머리말·마크다운·이모지를 쓰지 않는다. 전체 1500자 이내.
            - 주어진 수치와 뉴스 요약에 있는 사실만 쓴다. 새 수치를 계산하거나 지어내지 않는다.
            - 매수·매도 권유, 목표주가, 주가 방향 예측을 쓰지 않는다.
            - 상승·하락의 확률이나 가능성을 수치로 쓰지 않는다. "확률"이라는 단어를 쓰지 않는다.
            - 사실 서술과 "확인해 볼 점"까지만 쓴다. 판단은 읽는 사람에게 남긴다.
            """;

    private final AiClient aiClient;
    private final AiProperties aiProperties;
    private final AiBriefingRepository aiBriefingRepository;
    private final StockRepository stockRepository;
    private final DailyQuoteRepository dailyQuoteRepository;
    private final IndexQuoteRepository indexQuoteRepository;
    private final CorpFinancialRepository corpFinancialRepository;
    private final ResearchDocumentRepository researchDocumentRepository;

    /**
     * 시장 1건 + 상장 종목 전부. 없거나 세대가 뒤처진 것만 만든다.
     *
     * @return 이번 회차에 새로 만들거나 다시 쓴 건수
     */
    public int generate() {
        if (!aiProperties.isConfigured()) {
            log.info("[B6] AI_API_KEY 가 없어 브리핑 생성을 건너뛴다");
            return 0;
        }
        Optional<LocalDate> latest = dailyQuoteRepository.findLatestTradeDate();
        if (latest.isEmpty()) {
            log.warn("[B6] daily_quotes 가 비어 있어 브리핑 생성을 건너뛴다 — 일봉 수집이 먼저다");
            return 0;
        }
        LocalDate targetDate = latest.get();
        String promptVersion = aiProperties.promptVersion();

        List<Stock> stocks = stocksQuotedOn(targetDate);

        int written = 0;
        try {
            if (generateMarket(stocks, targetDate, promptVersion)) {
                written++;
            }
        } catch (AiException | DataAccessException e) {
            log.warn("[B6] 시장 브리핑 실패 — {}", e.getMessage());
        }
        for (Stock stock : stocks) {
            try {
                if (generateStock(stock, targetDate, promptVersion)) {
                    written++;
                }
            } catch (AiException | DataAccessException e) {
                // 건 하나의 실패가 회차를 끝내면 뒤 종목이 통째로 밀린다. 다음 회차가 다시 집는다.
                log.warn("[B6] 종목 브리핑 실패 {} — {}", stock.getCode(), e.getMessage());
            }
        }
        log.info("[B6] 브리핑 — 기준일 {} · 종목 {}개 · {}건 생성 (prompt {})",
                targetDate, stocks.size(), written, promptVersion);
        return written;
    }

    /**
     * 브리핑 대상 = 기준일에 시세가 있는 상장 종목.
     *
     * <p>{@code stocks} 표는 시가총액 상위 300 이 날마다 들고나며 코드가 쌓이고 {@code listed} 가
     * false 로 내려가는 일이 없어, 표 전체를 대상으로 잡으면 옛 멤버까지 헛돌고 상한에 밀려 정작
     * 오늘 멤버(코드 큰 종목)가 빠진다. 기준일 일봉이 있는 종목이 그날의 300 이다.
     */
    private List<Stock> stocksQuotedOn(LocalDate targetDate) {
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

    // ── 시장 ─────────────────────────────────────────────────

    private boolean generateMarket(List<Stock> stocks, LocalDate targetDate, String promptVersion) {
        Optional<AiBriefing> existing =
                aiBriefingRepository.findTarget(AiBriefing.Scope.MARKET, null, targetDate).stream().findFirst();
        if (isCurrent(existing, promptVersion)) {
            return false;
        }
        String input = marketInput(stocks, targetDate);
        if (input == null) {
            log.info("[B6] 기준일 {} 지수 시세가 없어 시장 브리핑을 건너뛴다", targetDate);
            return false;
        }
        return write(existing, AiBriefing.Scope.MARKET, null, targetDate, input, promptVersion);
    }

    /** 지수 셋의 1·5영업일 등락률 + 수집 종목 중 상승·하락 상위. 지수가 하나도 없으면 null. */
    String marketInput(List<Stock> stocks, LocalDate targetDate) {
        StringBuilder sb = new StringBuilder("기준일: ").append(targetDate).append('\n');
        boolean anyIndex = false;
        for (IndexQuote.IndexCode code : IndexQuote.IndexCode.values()) {
            List<IndexQuote> recent =
                    indexQuoteRepository.findByIndexCodeOrderByTradeDateDesc(code, Limit.of(6));
            if (recent.isEmpty() || !recent.get(0).getTradeDate().equals(targetDate)) {
                continue;
            }
            anyIndex = true;
            List<BigDecimal> closes = recent.stream().map(IndexQuote::getClose).toList();
            sb.append(label(code)).append(' ').append(plain(closes.get(0)))
                    .append(" · 1영업일 ").append(rate(closes, 1))
                    .append(" · 5영업일 ").append(rate(closes, 5))
                    .append('\n');
        }
        if (!anyIndex) {
            return null;
        }
        appendMovers(sb, stocks, targetDate);
        return sb.toString();
    }

    /** 환율은 지수가 아니다 — 부호 방향까지 라벨에 박아야 "USDKRW 지수 상승" 같은 문장이 안 나온다. */
    private static String label(IndexQuote.IndexCode code) {
        return switch (code) {
            case KOSPI -> "코스피 종가";
            case KOSDAQ -> "코스닥 종가";
            case USDKRW -> "원/달러 환율(상승=원화 약세)";
        };
    }

    private void appendMovers(StringBuilder sb, List<Stock> stocks, LocalDate targetDate) {
        if (stocks.isEmpty()) {
            return;
        }
        Map<String, String> names = new LinkedHashMap<>();
        stocks.forEach(s -> names.put(s.getCode(), s.getName()));
        // 종목·날짜 오름차순 한 방 조회. 종목마다 두 점을 따로 읽으면 600번 왕복이다.
        List<DailyQuote> quotes = dailyQuoteRepository
                .findByStock_CodeInAndTradeDateGreaterThanEqualOrderByStock_CodeAscTradeDateAsc(
                        names.keySet(), targetDate.minusDays(10));

        record Move(String name, BigDecimal rate) {}
        List<Move> moves = new ArrayList<>();
        DailyQuote prev = null;
        for (DailyQuote q : quotes) {
            if (prev != null
                    && prev.getStock().getCode().equals(q.getStock().getCode())
                    && q.getTradeDate().equals(targetDate)) {
                BigDecimal r = changeRate(q.getClose(), prev.getClose());
                if (r != null) {
                    moves.add(new Move(names.get(q.getStock().getCode()), r));
                }
            }
            prev = q;
        }
        if (moves.isEmpty()) {
            return;
        }
        moves.sort(Comparator.comparing(Move::rate).reversed());
        long up = moves.stream().filter(m -> m.rate().signum() > 0).count();
        sb.append("수집 종목 ").append(moves.size()).append("개 중 상승 ").append(up)
                .append("개 · 하락 ").append(moves.size() - up).append("개\n");
        sb.append("상승 상위: ");
        moves.stream().limit(MOVERS).forEach(m -> sb.append(m.name()).append(' ').append(pct(m.rate())).append(", "));
        sb.setLength(sb.length() - 2);
        sb.append("\n하락 상위: ");
        List<Move> down = new ArrayList<>(moves);
        down.sort(Comparator.comparing(Move::rate));
        down.stream().limit(MOVERS).forEach(m -> sb.append(m.name()).append(' ').append(pct(m.rate())).append(", "));
        sb.setLength(sb.length() - 2);
        sb.append('\n');
    }

    // ── 종목 ─────────────────────────────────────────────────

    private boolean generateStock(Stock stock, LocalDate targetDate, String promptVersion) {
        Optional<AiBriefing> existing =
                aiBriefingRepository.findTarget(AiBriefing.Scope.STOCK, stock.getCode(), targetDate).stream().findFirst();
        if (isCurrent(existing, promptVersion)) {
            return false;
        }
        String input = stockInput(stock, targetDate);
        if (input == null) {
            // 그날 거래정지였거나 시세가 아직 안 들어온 종목. 재료 없이 만들면 지어낸 글이 된다.
            return false;
        }
        return write(existing, AiBriefing.Scope.STOCK, stock, targetDate, input, promptVersion);
    }

    /** 시세 등락·거래량·52주 위치 + 3개년 재무 + 최근 뉴스 요약. 기준일 시세가 없으면 null. */
    String stockInput(Stock stock, LocalDate targetDate) {
        List<DailyQuote> quotes = dailyQuoteRepository.findByStock_CodeAndTradeDateBetweenOrderByTradeDate(
                stock.getCode(), targetDate.minusYears(LOOKBACK_YEARS), targetDate);
        if (quotes.size() < 2 || !quotes.get(quotes.size() - 1).getTradeDate().equals(targetDate)) {
            return null;
        }
        // 최신이 앞에 오도록 뒤집는다 — 지수와 같은 계산기를 쓴다.
        List<BigDecimal> closes = new ArrayList<>(quotes.stream().map(DailyQuote::getClose).toList());
        java.util.Collections.reverse(closes);

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
        appendNews(sb, stock.getCode(), targetDate);
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

    /** 기준일 자정(KST) 이전 기사만. 그날 저녁 들어온 D 기사가 D-1 브리핑에 섞이지 않게 한다. */
    private void appendNews(StringBuilder sb, String stockCode, LocalDate targetDate) {
        Instant endOfTarget = targetDate.plusDays(1).atStartOfDay(KST).toInstant();
        List<ResearchDocument> news = researchDocumentRepository
                .findByStock_CodeAndSourceAndPublishedAtBeforeOrderByPublishedAtDesc(
                        stockCode, ResearchDocument.Source.NEWS, endOfTarget, Limit.of(NEWS_PER_STOCK * 2))
                .stream()
                .filter(d -> d.getSummary() != null && !d.getSummary().isBlank())
                .limit(NEWS_PER_STOCK)
                .toList();
        if (news.isEmpty()) {
            sb.append("최근 뉴스 요약: (없음)\n");
            return;
        }
        sb.append("최근 뉴스 요약(날짜 · 내용):\n");
        news.forEach(d -> sb.append("- ")
                .append(d.getPublishedAt().atZone(KST).toLocalDate()).append(" · ")
                .append(d.getSummary()).append('\n'));
    }

    // ── 공통 ─────────────────────────────────────────────────

    private static boolean isCurrent(Optional<AiBriefing> existing, String promptVersion) {
        return existing.isPresent() && promptVersion.equals(existing.get().getPromptVersion());
    }

    private boolean write(
            Optional<AiBriefing> existing,
            AiBriefing.Scope scope,
            Stock stock,
            LocalDate targetDate,
            String input,
            String promptVersion) {
        String text = aiClient.complete(INSTRUCTION, input);
        if (FORBIDDEN.matcher(text).find()) {
            log.warn("[B6] D16 위반 문구가 있어 브리핑을 버린다 scope={} stock={}",
                    scope, stock == null ? "-" : stock.getCode());
            return false;
        }
        String[] parts = text.split("\\R+", 2);
        if (parts.length < 2 || parts[1].isBlank()) {
            // 헤드라인만 오면 본문이 없다. 헤드라인을 본문에 복사해 저장하면 "현 세대"로 굳어 다시
            // 생성되지 않고 상세 화면에 같은 문장이 두 번 뜬다. D16 위반과 같이 버리고 다음 회차에 맡긴다.
            log.warn("[B6] 본문 없는 응답이라 브리핑을 버린다 scope={} stock={}",
                    scope, stock == null ? "-" : stock.getCode());
            return false;
        }
        String headline = parts[0].trim();
        String body = parts[1].trim();

        AiBriefing briefing = existing
                .map(b -> {
                    b.rewrite(headline, body, promptVersion);
                    return b;
                })
                .orElseGet(() -> AiBriefing.of(scope, stock, targetDate, headline, body, promptVersion));
        aiBriefingRepository.save(briefing);
        return true;
    }

    /** {@code closes} 는 최신이 앞. {@code back} 영업일 전 대비 등락률, 그만큼 없으면 "-". */
    private static String rate(List<BigDecimal> closes, int back) {
        if (closes.size() <= back) {
            return "-";
        }
        BigDecimal r = changeRate(closes.get(0), closes.get(back));
        return r == null ? "-" : pct(r);
    }

    private static BigDecimal changeRate(BigDecimal now, BigDecimal before) {
        if (now == null || before == null || before.signum() == 0) {
            return null;
        }
        return now.subtract(before)
                .multiply(BigDecimal.valueOf(100))
                .divide(before, 2, RoundingMode.HALF_UP);
    }

    private static String pct(BigDecimal rate) {
        return (rate.signum() > 0 ? "+" : "") + rate.setScale(2, RoundingMode.HALF_UP).toPlainString() + "%";
    }

    private static String plain(BigDecimal value) {
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
