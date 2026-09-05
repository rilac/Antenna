package ssafy.a507.backend.domain.chain.dto;

import java.time.Instant;
import java.time.LocalDate;
import ssafy.a507.backend.domain.chain.entity.AnchorBatch;

/**
 * GET /api/v1/anchors 의 목록 항목 (ANT-CHAIN-06, 화면 D-01).
 *
 * <p>명세의 여섯 필드에 {@code businessDate · contractAddress · chainId · txHash} 를 더했다. 목록에서 바로
 * "어느 날 봉인분인지" 와 "어느 장부(컨트랙트)에 박혔는지" 를 보여야 하고, 재배포 뒤에는 배치마다 주소가
 * 다를 수 있어서다(결정 A5). {@code id} 는 곧 온체인 batchId 라 숫자 그대로 내린다.
 */
public record AnchorItemResponse(
        long id,
        LocalDate businessDate,
        String merkleRoot,
        int commitCount,
        AnchorBatch.Status status,
        String txHash,
        Long blockNumber,
        Instant confirmedAt,
        String contractAddress,
        long chainId) {

    public static AnchorItemResponse of(AnchorBatch b) {
        return new AnchorItemResponse(
                b.getId(),
                b.getBusinessDate(),
                b.getMerkleRoot(),
                b.getCommitCount(),
                b.getStatus(),
                b.getTxHash(),
                b.getBlockNumber(),
                b.getConfirmedAt(),
                b.getContractAddress(),
                b.getChainId());
    }
}
