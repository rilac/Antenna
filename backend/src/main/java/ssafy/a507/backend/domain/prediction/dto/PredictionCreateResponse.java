package ssafy.a507.backend.domain.prediction.dto;

import java.time.LocalDate;
import ssafy.a507.backend.domain.chain.dto.ProofResponse;
import ssafy.a507.backend.domain.prediction.entity.Prediction;
import ssafy.a507.backend.domain.prediction.entity.PredictionCommit;

/**
 * {@code POST /predictions} 201 (ANT-PRED-01). 명세의 네 필드에 {@code baseDate·settleDate} 를 더했다 — 지라 AC 가
 * 요구하고, 화면이 등록 직후 "기준일·만기일" 을 바로 보여줘야 한다. {@code anchorStatus} 는 등록 직후 항상 WAITING 이지만
 * 어휘를 proof·목록과 맞추려고 값으로 내린다.
 */
public record PredictionCreateResponse(
        long id,
        String commitHash,
        ProofResponse.AnchorStatus anchorStatus,
        Prediction.Status status,
        LocalDate baseDate,
        LocalDate settleDate) {

    public static PredictionCreateResponse of(Prediction prediction, PredictionCommit commit) {
        return new PredictionCreateResponse(
                prediction.getId(),
                commit.getCommitHash(),
                ProofResponse.AnchorStatus.of(commit.getAnchorBatch()),
                prediction.getStatus(),
                prediction.getBaseDate(),
                prediction.getSettleDate());
    }
}
