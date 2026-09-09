package ssafy.a507.backend.domain.season.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import ssafy.a507.backend.common.ai.AiClient;
import ssafy.a507.backend.common.ai.AiProperties;
import ssafy.a507.backend.domain.season.entity.Season;
import ssafy.a507.backend.domain.season.entity.SeasonTrade;

/**
 * AI 복기(ANT-SEASON-09). 회차를 끝낼 때 부른다 — 호출은 회차당 1회다.
 *
 * <p>비용을 줄이려고 재료를 압축한다. 체결을 전부 싣지 않고 종목별 집계 한 줄씩과 최근
 * {@value #MAX_TRADES}건만 넣는다. 출력 길이는 지시문으로 묶는다(세 단락 · 500자 안팎).
 *
 * <p>키가 비어 있으면 부르지 않고 null 을 준다 — 로컬·CI 에서 종료 흐름이 막히면 안 된다.
 * 그 회차는 복기 없이 성적표만 남고, 키가 생긴 뒤 finish 를 다시 부르면 그때 채운다.
 *
 * <p>시대 단서 규칙: 재료에 날짜·연도를 넣지 않고 게임일 D+n 만 쓴다. 지시문이 실제 사건명·연도
 * 언급을 금지한다.
 */
@Service
@RequiredArgsConstructor
public class SeasonReviewService {

    static final int MAX_TRADES = 60;

    public static final String INSTRUCTION =
            """
            너는 모의투자 복기 코치다. 아래 재료만 근거로 한국어 복기 리포트를 쓴다.
            형식: 정확히 세 단락. 각 단락은 "잘한 판단:", "아쉬운 판단:", "개선 제안:" 으로 시작한다. \
            전체 500자 안팎. 제목·머리말·불릿 없이 문장만 쓴다.
            규칙: 재료에 없는 사실·수치를 지어내지 않는다. 실제 연도·날짜·사건명·지수명을 말하지 않고 \
            게임일 D+n 으로만 가리킨다. 특정 종목의 매수·매도 권유나 수익 보장 표현을 쓰지 않는다. 존댓말을 쓴다.
            """;

    private final AiClient aiClient;
    private final AiProperties aiProperties;

    /** 복기 본문. 키가 없으면 null. GMS 실패는 AiException 그대로 올린다 — 부른 쪽이 정한다. */
    public String review(String input) {
        if (!hasKey()) {
            return null;
        }
        return aiClient.complete(INSTRUCTION, input);
    }

    public boolean hasKey() {
        return aiProperties.apiKey() != null && !aiProperties.apiKey().isBlank();
    }

    public String promptVersion() {
        return aiProperties.promptVersion();
    }

    /**
     * 프롬프트 재료. 엔티티의 지연 로딩이 여기서 일어나므로 트랜잭션 안에서 만든다.
     *
     * @param trades 이 회차의 체결, 오래된 순
     */
    public static String input(Season season, List<SeasonTrade> trades, SeasonResultCalculator.Result r) {
        StringBuilder sb = new StringBuilder(2048);
        sb.append("시즌: ").append(season.getTitle());
        if (season.getTheme() != null && !season.getTheme().isBlank()) {
            sb.append(" · 테마 ").append(season.getTheme());
        }
        sb.append(" · ").append(season.getLengthDays()).append("게임일 · 시작 예수금 ")
                .append(won(season.getInitialCash())).append('\n');

        sb.append("성과: 최종 자산 ").append(won(r.finalAsset()))
                .append(" · 수익률 ").append(pct(r.returnRate()))
                .append(" · 시장(시즌 종목 등가중) ").append(pct(r.benchmarkReturn()))
                .append(" · 최대 낙폭 ").append(pct(r.maxDrawdown()))
                .append(" · 승률 ").append(pct(r.winRate()))
                .append(" · 손익비 ").append(num(r.profitFactor()))
                .append(" · 평균 보유일 ").append(num(r.avgHoldingDays())).append('\n');

        if (trades.isEmpty()) {
            sb.append("체결: 없음 — 한 번도 매매하지 않고 끝냈다.\n");
            return sb.toString();
        }

        // 종목별 집계 — [매수 횟수, 매도 횟수] 와 실현손익 합
        Map<String, int[]> counts = new LinkedHashMap<>();
        Map<String, BigDecimal> realized = new LinkedHashMap<>();
        for (SeasonTrade t : trades) {
            String name = t.getTicker().getDisplayName();
            int[] c = counts.computeIfAbsent(name, k -> new int[2]);
            c[t.getSide() == SeasonTrade.Side.BUY ? 0 : 1]++;
            if (t.getRealizedPnl() != null) {
                realized.merge(name, t.getRealizedPnl(), BigDecimal::add);
            }
        }
        sb.append("종목별: ");
        counts.forEach((name, c) -> {
            sb.append(name).append(" 매수 ").append(c[0]).append("회/매도 ").append(c[1]).append("회");
            if (realized.containsKey(name)) {
                sb.append(" 실현손익 ").append(signedWon(realized.get(name)));
            }
            sb.append(" ; ");
        });
        sb.append('\n');

        int from = Math.max(0, trades.size() - MAX_TRADES);
        sb.append("체결(오래된 순");
        if (from > 0) {
            sb.append(", 앞 ").append(from).append("건 생략");
        }
        sb.append("): ");
        for (SeasonTrade t : trades.subList(from, trades.size())) {
            sb.append("D+").append(t.getGameDay()).append(' ')
                    .append(t.getSide() == SeasonTrade.Side.BUY ? "매수 " : "매도 ")
                    .append(t.getTicker().getDisplayName()).append(' ')
                    .append(t.getQty()).append("주 @").append(won(t.getPrice()));
            if (t.getRealizedPnl() != null) {
                sb.append(" (").append(signedWon(t.getRealizedPnl())).append(')');
            }
            sb.append(" · ");
        }
        sb.append('\n');
        return sb.toString();
    }

    private static String won(BigDecimal v) {
        return v == null ? "-" : v.setScale(0, RoundingMode.HALF_UP).toPlainString() + "원";
    }

    private static String signedWon(BigDecimal v) {
        return (v.signum() > 0 ? "+" : "") + won(v);
    }

    private static String pct(BigDecimal v) {
        return v == null ? "-" : v.stripTrailingZeros().toPlainString() + "%";
    }

    private static String num(BigDecimal v) {
        return v == null ? "-" : v.stripTrailingZeros().toPlainString();
    }
}
