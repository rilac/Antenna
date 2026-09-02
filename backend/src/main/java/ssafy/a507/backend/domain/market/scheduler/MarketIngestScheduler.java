package ssafy.a507.backend.domain.market.scheduler;

import java.time.LocalDate;
import java.time.ZoneId;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import ssafy.a507.backend.domain.market.service.DailyQuoteIngestService;

/**
 * 일봉 수집 배치(B1)의 시각을 정한다.
 *
 * <p>월~금 13:00 KST. 공휴일 달력은 갖고 있지 않아 그냥 돌리고, 장이 서지 않은 날은 포털이
 * 빈 응답을 주므로 그 날짜가 EMPTY 로 남을 뿐이다. 요일만 걸러도 헛호출의 대부분은 준다.
 *
 * <p>cron 을 상수가 아니라 설정으로 받는 이유 — 로컬에서 수집을 확인할 때 몇 분 간격으로
 * 낮춰 돌려 볼 수 있어야 하고, 테스트에서는 {@code "-"} 로 꺼야 한다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class MarketIngestScheduler {

    private static final ZoneId KST = ZoneId.of("Asia/Seoul");

    private final DailyQuoteIngestService ingestService;

    @Scheduled(cron = "${app.market-data.cron:0 0 13 * * MON-FRI}", zone = "Asia/Seoul")
    public void ingestDailyQuotes() {
        ingestService.ingestPending(LocalDate.now(KST));
    }
}
