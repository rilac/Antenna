package ssafy.a507.backend.domain.market.scheduler;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import ssafy.a507.backend.domain.market.entity.IngestRun;
import ssafy.a507.backend.domain.market.service.DailyQuoteIngestService;

/**
 * 과거 시세 백필. 평소에는 꺼져 있고(cron {@code "-"}), 백필이 필요할 때만 켠다.
 *
 * <p><b>왜 일회성 러너가 아니라 스케줄인가.</b> 1,600여 영업일을 한 번에 돌면 앱 기동이 몇
 * 시간씩 묶이고, 중간에 죽으면 어디까지 했는지 알 방법이 사람의 기억뿐이다. 회차를 잘게 나누면
 * 진행 상황이 ingest_runs 에 남아 다음 회차가 이어받고, 포털에 몰아치지도 않는다.
 *
 * <p>끝나면 "남은 영업일이 없다" 로그가 계속 찍힌다 — 그때 cron 을 다시 {@code "-"} 로 돌린다.
 */
@Slf4j
@Component
public class MarketBackfillScheduler {

    private static final ZoneId KST = ZoneId.of("Asia/Seoul");

    private final DailyQuoteIngestService ingestService;
    private final LocalDate from;
    private final int chunkSize;

    public MarketBackfillScheduler(
            DailyQuoteIngestService ingestService,
            @Value("${app.market-data.backfill.from}") LocalDate from,
            @Value("${app.market-data.backfill.chunk-size}") int chunkSize) {
        this.ingestService = ingestService;
        this.from = from;
        this.chunkSize = chunkSize;
    }

    @Scheduled(cron = "${app.market-data.backfill.cron:-}", zone = "Asia/Seoul")
    public void backfill() {
        List<IngestRun> runs = ingestService.backfill(LocalDate.now(KST), from, chunkSize);
        if (runs.isEmpty()) {
            log.info("백필할 영업일이 남아 있지 않다 — backfill.cron 을 다시 '-' 로 돌려도 된다.");
        }
    }
}
