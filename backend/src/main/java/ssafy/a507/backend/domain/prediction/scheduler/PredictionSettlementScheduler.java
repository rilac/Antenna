package ssafy.a507.backend.domain.prediction.scheduler;

import java.time.LocalDate;
import java.time.ZoneId;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import ssafy.a507.backend.domain.prediction.service.PredictionSettlementRunner;

/**
 * 판정 배치 B2 의 시각을 정한다 (ANT-PRED-03·04) — 영업일 13:30 KST.
 *
 * <p>13:00 시세 수집(B1) 30분 뒤다. 명세 §6 배치표 그대로이며, 수집이 끝난 직후여야 그날 새로 들어온 종가로 판정할 수 있다.
 *
 * <p><b>주말에 돌지 않는 이유</b>: 주말에는 새 종가가 없어 전 건이 보류된다. 앵커 배치(00:05)가 주말에도 도는 것과 반대인데,
 * 앵커는 시세가 필요 없고 금요일 등록분을 월요일까지 무보호로 둘 수 없기 때문이다(ANT-CHAIN-02, 결정 A4).
 * 판정은 시세가 전제라 장이 서지 않은 날 돌 이유가 없다.
 *
 * <p>{@code business_date} 는 <b>실행일</b>이다. 앵커 배치가 "어제" 를 쓰는 것과 다른데, 앵커는 "어제 등록된 커밋" 을 묶는 회차라
 * 그 날짜가 대상 구간을 뜻하는 반면 여기서는 회차 자체의 날짜이기 때문이다(ERD: "영업일당 1회 실행 · 중복 실행 방지 키").
 *
 * <p>cron 을 상수가 아니라 설정으로 받는 이유는 다른 배치와 같다 — 로컬 확인 때 몇 분 간격으로 낮추고, 테스트에서는
 * {@code "-"} 로 끈다(등록은 검증되고 실행만 빠진다). 단일 인스턴스 전제다. 둘이면 같은 예측을 두 회차가 동시에 집는다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PredictionSettlementScheduler {

    private static final ZoneId KST = ZoneId.of("Asia/Seoul");

    private final PredictionSettlementRunner runner;

    @Scheduled(cron = "${app.prediction.settle-cron:0 30 13 * * MON-FRI}", zone = "Asia/Seoul")
    public void runDaily() {
        LocalDate businessDate = LocalDate.now(KST);
        log.info("판정 배치 실행 시작 (business_date={})", businessDate);
        try {
            runner.run(businessDate);
        } catch (RuntimeException e) {
            // 스케줄 스레드에서 예외가 새면 다음 회차까지 조용히 죽는다. 여기서 잡아 남긴다(AnchorScheduler 와 같은 이유).
            log.error("판정 배치 실행 실패", e);
        }
    }
}
