package ssafy.a507.backend.domain.chain.indexer;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 토큰 인덱서 ② 폴링 주기 (ANT-CHAIN-11) — 앵커 인덱서와 같은 {@code app.chain.indexer.cron}(기본 5초).
 *
 * <p>설정을 따로 두지 않는다. 스프링 스케줄러는 단일 스레드라 앵커·토큰 회차가 순서대로 돌고, 둘 다 getLogs 한 번이라
 * 겹칠 이유가 없다. 테스트·비활성은 같은 "-" 다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class TokenIndexerScheduler {

    private final TokenIndexer indexer;

    @Scheduled(cron = "${app.chain.indexer.cron:*/5 * * * * *}")
    public void poll() {
        try {
            indexer.poll();
        } catch (RuntimeException e) {
            log.error("토큰 인덱서 회차 실패", e);
        }
    }
}
