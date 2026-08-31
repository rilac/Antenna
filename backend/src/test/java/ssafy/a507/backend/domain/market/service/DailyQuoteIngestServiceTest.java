package ssafy.a507.backend.domain.market.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 수집 대상 날짜를 고르는 규칙. 역주행 보정·재시도·정기 수집이 전부 이 한 함수에서 나온다.
 *
 * <p>기준 주간 — 8/20(목) 8/21(금) [8/22 토 8/23 일] 8/24(월) ~ 8/28(금) [8/29 토 8/30 일].
 */
class DailyQuoteIngestServiceTest {

    private static final LocalDate FROM = LocalDate.of(2026, 8, 20);
    private static final LocalDate TO = LocalDate.of(2026, 8, 30);

    @Test
    @DisplayName("주말은 부르지 않는다 — 장이 서지 않는 날이라 호출을 낭비할 이유가 없다")
    void 주말을_건너뛴다() {
        List<LocalDate> targets = DailyQuoteIngestService.pendingDates(FROM, TO, Set.of());

        assertThat(targets)
                .doesNotContain(
                        LocalDate.of(2026, 8, 22),
                        LocalDate.of(2026, 8, 23),
                        LocalDate.of(2026, 8, 29),
                        LocalDate.of(2026, 8, 30))
                .hasSize(7);
    }

    @Test
    @DisplayName("이미 받은 날짜는 다시 부르지 않는다")
    void 수집한_날짜를_건너뛴다() {
        Set<LocalDate> collected =
                Set.of(
                        LocalDate.of(2026, 8, 24),
                        LocalDate.of(2026, 8, 25),
                        LocalDate.of(2026, 8, 26),
                        LocalDate.of(2026, 8, 27));

        List<LocalDate> targets = DailyQuoteIngestService.pendingDates(FROM, TO, collected);

        assertThat(targets)
                .containsExactly(
                        LocalDate.of(2026, 8, 20),
                        LocalDate.of(2026, 8, 21),
                        LocalDate.of(2026, 8, 28));
    }

    /**
     * 월요일 13시 회차가 금요일치를 집어 오는 장면. 주말 동안 배치가 돌지 않아 8/28(금)이
     * 비어 있는데, 창이 그 날짜를 덮고 있어 별도 보정 로직 없이 대상으로 올라온다.
     */
    @Test
    @DisplayName("주말 낀 월요일 회차가 금요일치를 소급해 집는다")
    void 주말_뒤_누락_구간을_메운다() {
        LocalDate monday = LocalDate.of(2026, 8, 31);
        LocalDate lastTarget = monday.minusDays(1);
        LocalDate from = lastTarget.minusDays(10);
        Set<LocalDate> collectedUntilFriday =
                Set.of(
                        LocalDate.of(2026, 8, 20),
                        LocalDate.of(2026, 8, 21),
                        LocalDate.of(2026, 8, 24),
                        LocalDate.of(2026, 8, 25),
                        LocalDate.of(2026, 8, 26),
                        LocalDate.of(2026, 8, 27));

        List<LocalDate> targets =
                DailyQuoteIngestService.pendingDates(from, lastTarget, collectedUntilFriday);

        assertThat(targets).containsExactly(LocalDate.of(2026, 8, 28));
    }

    @Test
    @DisplayName("오래된 날짜부터 돌려준다 — 과거 구멍을 먼저 메운다")
    void 오래된_날짜부터_준다() {
        List<LocalDate> targets = DailyQuoteIngestService.pendingDates(FROM, TO, Set.of());

        assertThat(targets).isSorted();
        assertThat(targets.get(0)).isEqualTo(FROM);
    }
}
