package ssafy.a507.backend.domain.chain.anchor;

import java.time.Instant;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ssafy.a507.backend.domain.prediction.entity.Prediction;
import ssafy.a507.backend.domain.prediction.entity.PredictionCommit;
import ssafy.a507.backend.domain.prediction.repository.PredictionCommitRepository;

/**
 * salt 리빌 (ANT-CHAIN-02, 결정 A9).
 *
 * <p><b>판정 기준</b>이다. 예측이 HIT/MISS 로 확정된 뒤에만 salt 를 공개한다 — 명세 "만기 전에는 salt·판정
 * 비공개" 그대로. 만기일 컬럼을 누가 채우느냐에 기대지 않는다.
 *
 * <p>일일 앵커 실행의 마지막 단계에서 돌고, 판정 배치(ANT-PRED-03)가 판정 직후 같은 메서드를 부르면
 * 리빌 지연이 0 이 된다. 두 곳에서 불려도 결과가 같다 — 이미 공개된 커밋은 건드리지 않는다.
 *
 * <p>리빌은 <b>오프체인</b>이다. 체인에 tx 를 보내지 않고 {@code revealed_at} 만 채운다.
 * 이후 proof API(ANT-CHAIN-06)가 salt 를 응답에 싣는다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SaltRevealService {

    private static final List<Prediction.Status> SETTLED = List.of(Prediction.Status.HIT, Prediction.Status.MISS);

    private final PredictionCommitRepository commits;

    /** @return 이번에 공개한 건수 */
    @Transactional
    public int revealSettled(Instant now) {
        List<PredictionCommit> targets = commits.findUnrevealedByPredictionStatusIn(SETTLED);
        for (PredictionCommit c : targets) {
            c.reveal(now);
        }
        if (!targets.isEmpty()) {
            log.info("salt 리빌 {}건", targets.size());
        }
        return targets.size();
    }
}
