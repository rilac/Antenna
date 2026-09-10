package ssafy.a507.backend.domain.prediction.service;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ssafy.a507.backend.domain.market.entity.DailyQuote;
import ssafy.a507.backend.domain.market.repository.DailyQuoteRepository;
import ssafy.a507.backend.domain.prediction.entity.Prediction;
import ssafy.a507.backend.domain.prediction.repository.PredictionRepository;
import ssafy.a507.backend.domain.ranking.entity.BatchRun;
import ssafy.a507.backend.domain.ranking.repository.BatchRunRepository;

/**
 * 판정 배치 B2 의 <b>한 건</b>을 처리한다 (ANT-PRED-03·04). 메서드 하나가 트랜잭션 하나다.
 *
 * <p><b>루프가 여기 없는 이유.</b> AC 가 "예측 단위 트랜잭션(전체 롤백 금지)" 다. 그런데 같은 클래스 안에서 루프를 돌며
 * {@code this.settle()} 을 부르면 스프링 프록시를 거치지 않아 {@code @Transactional} 이 통째로 무시된다(self-invocation).
 * 한 건의 실패가 회차 전체를 조용히 롤백한다 — 정확히 AC 가 금지한 것이고 테스트로도 잘 안 잡힌다.
 * 그래서 루프는 {@link PredictionSettlementRunner} 라는 별도 빈에 둔다.
 * {@code AnchorRunner}/{@code AnchorBatchService} 가 같은 이유로 이미 나뉘어 있다 — 새 패턴이 아니다.
 *
 * <p><b>대상 선별은 날짜가 아니라 시세 유무다</b>(plan §설계 ②). 13:00 수집이 "어제까지" 만 받으므로(장 마감 15:30)
 * 13:30 에 볼 수 있는 최신 종가는 어제 것이다. "base_date == 오늘" 로 잡으면 그날은 전부 보류되고 다음 날엔 조건에서 빠져
 * 모든 예측이 영원히 BASE 에 갇힌다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PredictionSettlementService {

    /** 판정 한 건의 결과. 보류를 null 이 아니라 값으로 두어 호출 쪽 분기를 하나로 만든다. */
    public enum Outcome {
        /** 시세가 아직 없어 이번 회차에서 건너뛴다. 오판보다 지연(AC). */
        PENDING,
        HIT,
        MISS
    }

    private final PredictionRepository predictions;
    private final DailyQuoteRepository quotes;
    private final BatchRunRepository batchRuns;

    /**
     * 기준가 확정 BASE → OPEN (ANT-PRED-03).
     *
     * @return 확정했으면 true, 시세가 없어 보류했으면 false
     */
    @Transactional
    public boolean fixBasePrice(long predictionId) {
        Prediction p = predictions.findById(predictionId).orElse(null);
        // 대상 목록을 뽑은 뒤 이 트랜잭션이 열리기까지 사이에 상태가 바뀌었을 수 있다. 다시 확인한다.
        if (p == null || p.getStatus() != Prediction.Status.BASE || p.getBaseDate() == null || p.getStock() == null) {
            return false;
        }
        Optional<DailyQuote> quote = closeOnOrAfter(p.getStock().getCode(), p.getBaseDate());
        if (quote.isEmpty()) {
            return false;
        }
        p.fixBasePrice(quote.get().getClose());
        return true;
    }

    /** 만기 판정 OPEN → HIT/MISS (ANT-PRED-04). */
    @Transactional
    public Outcome settle(long predictionId) {
        Prediction p = predictions.findById(predictionId).orElse(null);
        if (p == null || p.getStatus() != Prediction.Status.OPEN || p.getSettleDate() == null || p.getStock() == null) {
            return Outcome.PENDING;
        }
        // 기준가가 비어 있으면 판정할 수 없다. 같은 회차의 ① 단계가 방금 채웠거나, 아직 못 채운 것이다.
        if (p.getBasePrice() == null) {
            log.warn("예측 {} 이 OPEN 인데 기준가가 없다 — 판정을 보류한다", predictionId);
            return Outcome.PENDING;
        }
        Optional<DailyQuote> quote = closeOnOrAfter(p.getStock().getCode(), p.getSettleDate());
        if (quote.isEmpty()) {
            return Outcome.PENDING;
        }
        DailyQuote settled = quote.get();
        SettlementRules.Verdict verdict = SettlementRules.judge(
                p.getDirection(), p.getTargetPrice(), p.getBasePrice(), settled.getClose());
        p.settle(settled.getClose(), settled.getTradeDate(), verdict.hit(), verdict.errorRate());
        return verdict.hit() ? Outcome.HIT : Outcome.MISS;
    }

    /**
     * 회차 기록. 오늘 행이 있으면 카운터를 더하고, 없으면 만든다 — {@code business_date} 가 UNIQUE 라 새 행을 못 만든다.
     *
     * <p>같은 날 다시 돌리는 것이 안전한 이유: 대상 선별이 날짜가 아니라 상태와 시세라(§설계 ②) 이미 확정된 건은
     * status 가 바뀌어 다음 회차 대상에서 자연히 빠진다.
     */
    @Transactional
    public void recordRun(
            LocalDate businessDate, int opened, int verified, int hit, Instant startedAt, boolean partial) {
        BatchRun run = batchRuns
                .findByBusinessDate(businessDate)
                .orElseGet(() -> batchRuns.save(BatchRun.start(businessDate, startedAt)));
        run.add(opened, verified, hit);
        run.finish(partial ? BatchRun.PARTIAL : BatchRun.SUCCESS, Instant.now());
    }

    /**
     * 그 날짜 <b>이후 첫 거래일</b>의 일봉. 휴장·연휴·거래정지·수집 지연이 한 규칙으로 처리된다 —
     * 없으면 비어 돌아오고 배치는 보류한다.
     */
    private Optional<DailyQuote> closeOnOrAfter(String stockCode, LocalDate from) {
        return quotes.findFirstByStock_CodeAndTradeDateGreaterThanEqualOrderByTradeDateAsc(stockCode, from);
    }
}
