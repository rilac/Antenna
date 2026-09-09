package ssafy.a507.backend.domain.research.service;

import static ssafy.a507.backend.domain.research.service.StockMaterials.changeRate;
import static ssafy.a507.backend.domain.research.service.StockMaterials.pct;
import static ssafy.a507.backend.domain.research.service.StockMaterials.plain;
import static ssafy.a507.backend.domain.research.service.StockMaterials.rate;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Pattern;
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
import ssafy.a507.backend.domain.research.entity.AiBriefing;
import ssafy.a507.backend.domain.research.repository.AiBriefingRepository;

/**
 * AI 브리핑 생성 — 배치 B6 의 브리핑 갈래 (ANT-RESEARCH-03).
 *
 * <p><b>수치는 전부 코드가 계산해 프롬프트에 넣는다</b>({@link StockMaterials}). 모델은 주어진
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

    /** 시장 브리핑에 넣는 상승·하락 상위 종목 수. */
    private static final int MOVERS = 5;

    /**
     * D16 위반 패턴 셋 — ① 방향 + 확률·가능성("상승 확률", "오를 가능성이"), ② 확률·가능성 + 수치
     * ("가능성이 60%"), ③ 방향 + 수치("하락 68%"). "3.2% 하락" 같은 사실 서술(수치가 앞)은 통과한다
     * — 그건 우리가 준 값이다.
     *
     * <p>"확률" 단어 하나만으로는 막지 않는다. 뉴스 발췌("금리 인하 확률이 높아졌다")가 재료로
     * 들어가면 모델이 그대로 옮기는데, 그걸 막으면 그 종목은 매일 호출하고 매일 버린다.
     *
     * <p>포인트 생성(ANT-RESEARCH-04)도 이 패턴을 그대로 쓴다 — 금지 문구는 화면마다 다를 이유가 없다.
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
            - 주어진 수치에 있는 사실만 쓴다. 새 수치를 계산하거나 지어내지 않는다.
            - 매수·매도 권유, 목표주가, 주가 방향 예측을 쓰지 않는다.
            - 상승·하락의 확률이나 가능성을 수치로 쓰지 않는다. "확률"이라는 단어를 쓰지 않는다.
            - 사실 서술과 "확인해 볼 점"까지만 쓴다. 판단은 읽는 사람에게 남긴다.
            """;

    private final AiClient aiClient;
    private final AiProperties aiProperties;
    private final StockMaterials stockMaterials;
    private final AiBriefingRepository aiBriefingRepository;
    private final DailyQuoteRepository dailyQuoteRepository;
    private final IndexQuoteRepository indexQuoteRepository;

    /**
     * 시장 브리핑 1건. 없거나 세대가 뒤처졌을 때만 만든다 — 하루 1콜.
     *
     * <p>종목 브리핑은 2026-09-09 에 없앴다. 종목당 매일 1콜(≈300)을 쓰면서, 같은 탭에 숫자로 이미
     * 있는 등락률·재무를 문장으로 다시 쓴 것이었다. 뉴스·공시 쪽은 투자 포인트가 담고, 그건 요청
     * 시점에 종목별로 만든다({@link ResearchPointGenerationService}).
     *
     * @return 이번 회차에 새로 만들거나 다시 쓴 건수 · 0 또는 1
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
        try {
            boolean written = generateMarket(stockMaterials.quotedOn(targetDate), targetDate, promptVersion);
            log.info("[B6] 시장 브리핑 — 기준일 {} · {} (prompt {})", targetDate, written ? "생성" : "이미 있음", promptVersion);
            return written ? 1 : 0;
        } catch (AiException | DataAccessException e) {
            log.warn("[B6] 시장 브리핑 실패 — {}", e.getMessage());
            return 0;
        }
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
        return write(existing, targetDate, input, promptVersion);
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

    // ── 공통 ─────────────────────────────────────────────────

    private static boolean isCurrent(Optional<AiBriefing> existing, String promptVersion) {
        return existing.isPresent() && promptVersion.equals(existing.get().getPromptVersion());
    }

    private boolean write(Optional<AiBriefing> existing, LocalDate targetDate, String input, String promptVersion) {
        String text = aiClient.complete(INSTRUCTION, input);
        if (FORBIDDEN.matcher(text).find()) {
            log.warn("[B6] D16 위반 문구가 있어 브리핑을 버린다 기준일={}", targetDate);
            return false;
        }
        String[] parts = text.split("\\R+", 2);
        if (parts.length < 2 || parts[1].isBlank()) {
            // 헤드라인만 오면 본문이 없다. 헤드라인을 본문에 복사해 저장하면 "현 세대"로 굳어 다시
            // 생성되지 않고 상세 화면에 같은 문장이 두 번 뜬다. D16 위반과 같이 버리고 다음 회차에 맡긴다.
            log.warn("[B6] 본문 없는 응답이라 브리핑을 버린다 기준일={}", targetDate);
            return false;
        }
        String headline = parts[0].trim();
        String body = parts[1].trim();

        AiBriefing briefing = existing
                .map(b -> {
                    b.rewrite(headline, body, promptVersion);
                    return b;
                })
                .orElseGet(() -> AiBriefing.of(AiBriefing.Scope.MARKET, null, targetDate, headline, body, promptVersion));
        aiBriefingRepository.save(briefing);
        return true;
    }
}
