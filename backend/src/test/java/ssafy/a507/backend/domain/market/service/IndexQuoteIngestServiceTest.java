package ssafy.a507.backend.domain.market.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 환율 수집 대상 날짜를 고르는 규칙.
 *
 * <p>환율은 날짜별로만 받을 수 있고 일 1,000콜 한도가 있다. 어느 날을 불러야 하는지는 코스피
 * 거래일 달력이 정한다 — 코스피가 있는 날인데 환율이 없는 날만 대상이다. 그래서 공휴일은
 * 애초에 대상에 오르지 않고, 백필도 따로 종결 판정 없이 스스로 끝난다.
 */
class IndexQuoteIngestServiceTest {

    private static final LocalDate MON = LocalDate.of(2026, 8, 31);
    private static final LocalDate TUE = LocalDate.of(2026, 9, 1);
    private static final LocalDate WED = LocalDate.of(2026, 9, 2);

    @Test
    @DisplayName("코스피 거래일 중 환율이 없는 날만 고른다")
    void 환율이_없는_거래일만_고른다() {
        List<LocalDate> pending =
                IndexQuoteIngestService.pendingFxDates(List.of(MON, TUE, WED), Set.of(TUE));

        assertThat(pending).containsExactlyInAnyOrder(MON, WED);
    }

    @Test
    @DisplayName("최신 날짜부터 돌려준다 — 한도에 걸려도 홈 화면이 먼저 채워진다")
    void 최신_날짜부터_돌려준다() {
        List<LocalDate> pending =
                IndexQuoteIngestService.pendingFxDates(List.of(MON, TUE, WED), Set.of());

        assertThat(pending).containsExactly(WED, TUE, MON);
    }

    @Test
    @DisplayName("코스피 거래일이 없으면 부를 날도 없다 — 지수가 먼저 들어와야 환율이 돈다")
    void 거래일이_없으면_비어_있다() {
        assertThat(IndexQuoteIngestService.pendingFxDates(List.of(), Set.of(MON))).isEmpty();
    }
}
