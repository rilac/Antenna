package ssafy.a507.backend.domain.ranking.scheduler;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import ssafy.a507.backend.domain.ranking.service.RankingSnapshotService;

/**
 * 랭킹 스냅샷 배치 B3 의 시각을 정한다 (ANT-RANK-01).
 *
 * <p>명세의 배치표는 "B2 직후" 다. 그 B2(판정·앵커 13:30 · ANT-PRED-03·04)가 아직 없어서
 * 지금은 13:35 로 따로 돈다 — 판정 배치가 생기면 이 스케줄러를 지우고 그쪽 끝에서
 * {@link RankingSnapshotService#runOnce()} 를 부르는 것이 맞다. 순서가 뒤집히면 그날 판정분이
 * 하루 늦게 랭킹에 반영된다.
 *
 * <p>cron 을 상수가 아니라 설정으로 받는 이유는 시세 수집 배치와 같다 — 로컬에서 몇 분 간격으로
 * 낮춰 확인해야 하고, 테스트에서는 {@code "-"} 로 꺼야 한다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RankingScheduler {

    private final RankingSnapshotService snapshots;

    @Scheduled(cron = "${app.ranking.snapshot-cron:0 35 13 * * MON-FRI}", zone = "Asia/Seoul")
    public void snapshot() {
        log.info("랭킹 스냅샷 배치 시작");
        snapshots.runOnce();
    }
}
