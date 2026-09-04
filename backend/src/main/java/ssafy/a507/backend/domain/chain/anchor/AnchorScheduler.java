package ssafy.a507.backend.domain.chain.anchor;

import java.time.LocalDate;
import java.time.ZoneId;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 앵커 배치 시각 (ANT-CHAIN-02) — 매일 00:05 KST, <b>주말 포함</b>.
 *
 * <p>영업일 13:30 B2(기준가·판정)에 묶지 않는다(결정 A4). 앵커는 시세가 필요 없고, 금요일 오후 등록분을
 * 월요일까지 무보호로 두면 안 된다. 앵커 전 구간은 그냥 DB 행이다.
 * 00:05 인 이유: KST 날짜 경계 직후라 "어제 등록분"이 정확히 한 배치가 되고 B4(00:10)·B1(13:00)과 겹치지 않는다.
 *
 * <p>cron 을 설정으로 받는 이유는 다른 배치와 같다 — 로컬 확인 때 짧게, 테스트에서 "-" 로 끈다.
 * 단일 인스턴스 전제(결정 C4). 인스턴스가 둘이면 같은 커밋이 두 배치로 나간다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AnchorScheduler {

    private static final ZoneId KST = ZoneId.of("Asia/Seoul");

    private final AnchorRunner runner;

    @Scheduled(cron = "${app.chain.anchor.cron:0 5 0 * * *}", zone = "Asia/Seoul")
    public void runDaily() {
        // 00:05 실행분의 business_date 는 어제다 — 그날 등록된 커밋을 묶는 회차라서.
        LocalDate businessDate = LocalDate.now(KST).minusDays(1);
        log.info("앵커 배치 실행 시작 (business_date={})", businessDate);
        try {
            runner.run(businessDate);
        } catch (RuntimeException e) {
            // 스케줄 스레드에서 예외가 새면 다음 회차까지 조용히 죽는다. 여기서 잡아 남긴다.
            log.error("앵커 배치 실행 실패", e);
        }
    }
}
