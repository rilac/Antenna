package ssafy.a507.backend.domain.prediction.service;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import ssafy.a507.backend.domain.chain.anchor.SaltRevealService;
import ssafy.a507.backend.domain.common.Track;
import ssafy.a507.backend.domain.prediction.entity.Prediction;
import ssafy.a507.backend.domain.prediction.repository.PredictionRepository;
import ssafy.a507.backend.domain.prediction.service.PredictionSettlementService.Outcome;

/**
 * 판정 배치 B2 한 회차 (ANT-PRED-03·04). 스케줄러가 영업일 13:30 에 부른다.
 *
 * <pre>
 * ① 기준가  BASE 전량 → base_date 이후 첫 거래일 종가가 있으면 base_price 채우고 OPEN
 * ② 판정    OPEN 전량 → settle_date 이후 첫 거래일 종가가 있으면 HIT/MISS · 오차 산출
 * ③ 리빌    판정(HIT/MISS)이 끝난 커밋의 salt 공개
 * ④ 기록    batch_runs 에 회차 결과
 * </pre>
 *
 * <p><b>①과 ②의 순서가 이 클래스의 전부다.</b> ① 이 OPEN 으로 올려 주지 않으면 ② 가 잡을 대상이 없다.
 * 같은 회차 안에서 ① 다음 ② 를 돌리므로, 기준가 확정과 만기가 같은 날 겹치는 건도 그날 판정된다.
 *
 * <p><b>트랜잭션이 여기 없다.</b> 한 건이 트랜잭션 하나이고 그 경계는 {@link PredictionSettlementService} 가 갖는다.
 * 여기서 트랜잭션을 열면 한 건의 실패가 회차 전체를 롤백한다 — AC 가 금지하는 것이다.
 *
 * <p>예외는 건 단위로 삼킨다. 삼킨 건은 상태가 그대로라 다음 회차가 자연히 다시 집는다 — 재시도 큐를 두지 않는 이유는
 * 시세 수집 배치와 같다. 한 건이라도 삼켰으면 회차 status 가 PARTIAL 이 되어 로그를 뒤질 실마리가 남는다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PredictionSettlementRunner {

    private final PredictionRepository predictions;
    private final PredictionSettlementService settlements;
    private final SaltRevealService reveal;

    public void run(LocalDate businessDate) {
        Instant startedAt = Instant.now();
        int opened = 0;
        int verified = 0;
        int hit = 0;
        boolean partial = false;

        // ① 기준가 확정. REPLAY 는 게임일 진행이 전이시키므로 REAL 만 본다.
        List<Long> pendingBase = predictions.findIdsByTrackAndStatus(Track.REAL, Prediction.Status.BASE);
        for (Long id : pendingBase) {
            try {
                if (settlements.fixBasePrice(id)) {
                    opened++;
                }
            } catch (RuntimeException e) {
                partial = true;
                log.error("예측 {} 기준가 확정 실패 — 이 건만 건너뛴다", id, e);
            }
        }

        // ② 만기 판정. ① 이 방금 OPEN 으로 올린 건도 포함하려고 목록을 여기서 다시 읽는다.
        List<Long> pendingSettle = predictions.findIdsByTrackAndStatus(Track.REAL, Prediction.Status.OPEN);
        for (Long id : pendingSettle) {
            try {
                Outcome outcome = settlements.settle(id);
                if (outcome != Outcome.PENDING) {
                    verified++;
                    if (outcome == Outcome.HIT) {
                        hit++;
                    }
                }
            } catch (RuntimeException e) {
                partial = true;
                log.error("예측 {} 만기 판정 실패 — 이 건만 건너뛴다", id, e);
            }
        }

        // ③ 리빌. 00:05 앵커 배치도 같은 메서드를 부르지만 여기서 부르면 리빌 지연이 0 이 된다.
        //    이미 공개된 커밋은 건드리지 않아 두 곳에서 불려도 결과가 같다.
        try {
            reveal.revealSettled(Instant.now());
        } catch (RuntimeException e) {
            partial = true;
            log.error("salt 리빌 실패 — 다음 앵커 배치가 이어받는다", e);
        }

        // ④ 기록. 판정이 전부 끝난 뒤에 남긴다 — 여기서 터져도 예측의 전이는 이미 커밋돼 있다.
        try {
            settlements.recordRun(businessDate, opened, verified, hit, startedAt, partial);
        } catch (RuntimeException e) {
            log.error("batch_runs 기록 실패 (business_date={}) — 판정 결과 자체는 저장됐다", businessDate, e);
        }

        log.info(
                "판정 배치 완료 (business_date={}) — 기준가 {}/{}건, 판정 {}/{}건(적중 {}), status={}",
                businessDate,
                opened,
                pendingBase.size(),
                verified,
                pendingSettle.size(),
                hit,
                partial ? "PARTIAL" : "SUCCESS");
    }
}
