package ssafy.a507.backend.domain.chain.dto;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import ssafy.a507.backend.domain.chain.entity.AnchorBatch;

/**
 * GET /api/v1/anchors/{id} 200 응답 (ANT-CHAIN-06, 화면 D-02).
 *
 * <p>{@code commitHashes} 는 <b>리프 순서(prediction id 오름차순)</b> 그대로다 — 이 순서로 트리를 다시 접으면
 * {@code merkleRoot} 가 나와야 한다. predictionId 는 싣지 않는다(결정 F2): 해시만으로는 내용을 알 수 없지만
 * predictionId 가 붙으면 미판정 예측의 존재가 비구독자에게 새고, 페이징도 없다 — 리프 전량이 있어야 검산이 된다.
 * {@code sentAt · attempts · lastError} 는 운영 가시성용(결정 A5 선택 컬럼).
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
        List<String> commitHashes) {

    public static AnchorDetailResponse of(AnchorBatch b, List<String> commitHashes) {
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
                commitHashes);
    }
}
