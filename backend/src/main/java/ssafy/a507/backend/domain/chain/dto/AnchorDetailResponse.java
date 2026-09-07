package ssafy.a507.backend.domain.chain.dto;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import ssafy.a507.backend.domain.chain.entity.AnchorBatch;

/**
 * GET /api/v1/anchors/{id} 200 응답 (ANT-CHAIN-06, 화면 D-02).
 *
 * <p>{@code commits} 는 <b>리프 순서(prediction id 오름차순)</b> 그대로다 — 이 순서로 트리를 다시 접으면
 * {@code merkleRoot} 가 나와야 한다. 페이징이 없는 것도 같은 이유다(리프 전량이 있어야 검산이 된다).
 * {@code sentAt · attempts · lastError} 는 운영 가시성용(결정 A5 선택 컬럼).
 *
 * <p><b>predictionId 를 싣는다 — 결정 F2 를 뒤집었다 (ANT-CHAIN-09, 2026-09-07).</b>
 * F2 는 "predictionId 가 붙으면 미판정 예측의 존재가 비구독자에게 샌다" 를 근거로 해시만 내렸는데,
 * 명세 §예측 공개 규칙과 {@link ssafy.a507.backend.domain.prediction.entity.Prediction} javadoc 이 이미
 * "BASE/OPEN 은 비구독자에게 <b>존재와 커밋만</b> 보인다" 로 정하고 있다 — 존재는 원래 공개 대상이고
 * 가리는 것은 내용이다. 여기 실리는 것도 번호와 해시뿐이라 작성자·내용은 드러나지 않고,
 * 내용 게이팅은 {@code GET /predictions/{id}/proof} 가 그대로 막는다(미판정이면 작성자·유효 구독자만).
 * 반대로 F2 를 지키면 D-02 에서 D-03({@code /ledger/verify/:predictionId})으로 넘어갈 길이 없어
 * 온체인 검증 기능의 진입 동선이 통째로 끊긴다. 잃는 것이 얻는 것보다 컸다.
 */
public record AnchorDetailResponse(
        long id,
        LocalDate businessDate,
        String merkleRoot,
        int commitCount,
        AnchorBatch.Status status,
        String txHash,
        Long blockNumber,
        Instant sentAt,
        Instant confirmedAt,
        int attempts,
        String lastError,
        String contractAddress,
        long chainId,
        List<Leaf> commits) {

    /**
     * 리프 하나. {@code commitHash} 는 머클 트리에 들어간 값 그대로이고, {@code predictionId} 는 그 커밋의 주인
     * 예측이다 — 프론트가 이 번호로 3단계 검산 화면(D-03)에 들어간다.
     */
    public record Leaf(long predictionId, String commitHash) {}

    public static AnchorDetailResponse of(AnchorBatch b, List<Leaf> commits) {
        return new AnchorDetailResponse(
                b.getId(),
                b.getBusinessDate(),
                b.getMerkleRoot(),
                b.getCommitCount(),
                b.getStatus(),
                b.getTxHash(),
                b.getBlockNumber(),
                b.getSentAt(),
                b.getConfirmedAt(),
                b.getAttempts(),
                b.getLastError(),
                b.getContractAddress(),
                b.getChainId(),
                commits);
    }
}
