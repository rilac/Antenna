package ssafy.a507.backend.domain.market.scheduler;

import java.time.LocalDate;
import java.time.ZoneId;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import ssafy.a507.backend.domain.market.service.DailyQuoteIngestService;
import ssafy.a507.backend.domain.market.service.IndexQuoteIngestService;

/**
 * 시세 수집 배치(B1)의 시각을 정한다 — 일봉 13:00, 지수·환율 13~18시 매시.
 *
 * <p>월~금 KST. 공휴일 달력은 갖고 있지 않아 그냥 돌리고, 장이 서지 않은 날은 포털이
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
    private final IndexQuoteIngestService indexIngestService;

    /** 부팅 직후 한 번. 서버가 이미 요청을 받는 상태라 헬스체크를 막지 않는다. */
    @EventListener(ApplicationReadyEvent.class)
    public void backfillListedShares() {
        ingestService.backfillListedSharesIfMissing();
    }

    @Scheduled(cron = "${app.market-data.cron:0 0 13 * * MON-FRI}", zone = "Asia/Seoul")
    public void ingestDailyQuotes() {
        ingestService.ingestPending(LocalDate.now(KST));
    }

    /**
     * 지수·환율(ANT-DATA-04). 포털이 지수를 "영업일 하루 뒤 13시 이후"에 올리는데 시각이
     * 일정하지 않아 13~18시 매시 두드린다. 멱등 upsert 라 몇 번 받아도 같고, 공개 전 회차는
     * 빈손으로 돌아올 뿐이다. 한 번만 돌리면 공개가 조금만 늦어도 하루를 통째로 놓친다.
     */
    @Scheduled(cron = "${app.market-data.index.cron:0 0 13-18 * * MON-FRI}", zone = "Asia/Seoul")
    public void ingestIndexQuotes() {
        indexIngestService.ingestPending(LocalDate.now(KST));
    }
}
