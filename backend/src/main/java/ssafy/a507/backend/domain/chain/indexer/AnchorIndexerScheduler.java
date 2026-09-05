package ssafy.a507.backend.domain.chain.indexer;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 인덱서 폴링 주기 (ANT-CHAIN-04) — 기본 5초.
 *
 * <p>fixedDelay 가 아니라 cron 인 이유: 팀 컨벤션이 "테스트·비활성은 cron '-'" 라서다(SchedulingConfig).
 * 5초면 폴링 UI(3~5초)와 같은 급이고, 블록이 10초마다 나오는 체인에서 그보다 짧게 물을 이유가 없다.
 * 앵커가 하루 한 번이라 회차 대부분은 빈손이다 — getLogs 한 번(주소·topic 필터)이라 노드 부담은 없다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AnchorIndexerScheduler {

    private final AnchorIndexer indexer;

    @Scheduled(cron = "${app.chain.indexer.cron:*/5 * * * * *}")
    public void poll() {
        try {
            indexer.poll();
        } catch (RuntimeException e) {
            // 스케줄 스레드에서 예외가 새면 다음 회차까지 조용히 죽는다. 여기서 잡아 남긴다.
            log.error("앵커 인덱서 회차 실패", e);
        }
    }
}
