package ssafy.a507.backend.domain.research.scheduler;

import java.time.LocalDate;
import java.time.ZoneId;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import ssafy.a507.backend.domain.research.client.DartException;
import ssafy.a507.backend.domain.research.service.DartIngestService;

/**
 * DART 수집 배치의 시각을 정한다 (ANT-RESEARCH-01).
 *
 * <p><b>주기를 셋으로 나눈 것이 이 클래스의 요점이다.</b> 넷을 한 주기로 묶으면 매일 종목당
 * 네 번씩 부르게 되는데, 고유번호 파일은 29MB 이고 기업개황·재무는 몇 달에 한 번 바뀐다.
 * 실제로 매일 달라지는 것은 공시 목록뿐이다.
 *
 * <p>cron 을 상수가 아니라 설정으로 받는 이유 — 로컬에서 수집을 확인할 때 간격을 낮춰 돌려 볼
 * 수 있어야 하고, 테스트에서는 {@code "-"}(Spring 의 비활성 표시)로 꺼야 한다. 스케줄 등록
 * 자체는 그대로 검증되고 실행만 빠진다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DartIngestScheduler {

    private static final ZoneId KST = ZoneId.of("Asia/Seoul");

    private final DartIngestService ingestService;

    /**
     * 공시 목록 — 매일. 장 마감·정정공시까지 담기도록 저녁에 돈다.
     *
     * <p>되돌아보는 구간이 있어 하루를 걸러도 다음 회차가 메운다.
     */
    @Scheduled(cron = "${app.dart.disclosure-cron:0 30 19 * * *}", zone = "Asia/Seoul")
    public void ingestDisclosures() {
        LocalDate today = LocalDate.now(KST);
        ingestService.ingestDisclosures(ingestService.disclosureFrom(today), today);
    }

    /**
     * 고유번호 시드 + 기업개황 — 매월 1일. 신규 상장·상호 변경을 따라잡는 주기다.
     *
     * <p>시드가 먼저다. 고유번호가 없으면 기업개황을 부를 수 없다.
     */
    @Scheduled(cron = "${app.dart.profile-cron:0 0 4 1 * *}", zone = "Asia/Seoul")
    public void ingestProfiles() {
        try {
            ingestService.seedCorpCodes();
        } catch (DartException e) {
            // 시드는 호출이 한 번뿐이라 종목 단위로 격리할 곳이 없다. 여기서 끊지 않으면
            // 기업개황 갱신이 함께 취소되고, 이 cron 은 한 달에 한 번뿐이라 30일을 잃는다.
            // 고유번호 매핑은 이미 DB 에 있으므로 개황은 그대로 돌 수 있다.
            log.warn("[DART] 고유번호 시드 실패 — 기존 매핑으로 기업개황만 진행한다: {}", e.getMessage());
        }
        ingestService.ingestProfiles();
    }

    /**
     * 연간 재무 — 분기 첫날. 사업보고서는 3월에 몰리지만 정정이 늦게 오는 회사가 있어 분기마다
     * 다시 훑는다. 한 번 호출에 3개년이 오므로 회차당 종목 수만큼만 부른다.
     */
    @Scheduled(cron = "${app.dart.financial-cron:0 0 5 1 1,4,7,10 *}", zone = "Asia/Seoul")
    public void ingestFinancials() {
        LocalDate today = LocalDate.now(KST);
        ingestService.ingestAnnualFinancials(ingestService.financialYear(today));
    }
}
