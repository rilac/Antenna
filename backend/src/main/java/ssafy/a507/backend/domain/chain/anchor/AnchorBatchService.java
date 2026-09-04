package ssafy.a507.backend.domain.chain.anchor;

import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.web3j.utils.Numeric;
import ssafy.a507.backend.domain.chain.config.ChainProperties;
import ssafy.a507.backend.domain.chain.config.CommitAnchorProperties;
import ssafy.a507.backend.domain.chain.entity.AnchorBatch;
import ssafy.a507.backend.domain.chain.merkle.MerkleTree;
import ssafy.a507.backend.domain.chain.relay.AnchorResult;
import ssafy.a507.backend.domain.chain.repository.AnchorBatchRepository;
import ssafy.a507.backend.domain.prediction.entity.PredictionCommit;
import ssafy.a507.backend.domain.prediction.repository.PredictionCommitRepository;

/**
 * 앵커 배치의 DB 쪽 (ANT-CHAIN-02). 메서드 하나가 트랜잭션 하나다.
 *
 * <p>체인 호출은 여기 없다 — {@link AnchorRunner} 가 트랜잭션 <b>밖</b>에서 릴레이어를 부르고, 결과를
 * 이 클래스의 메서드로 다시 기록한다. 체인 전송을 DB 트랜잭션 안에 넣으면 "체인엔 박혔는데 DB 는 롤백"
 * 이 생긴다. 반대 순서("DB 는 PENDING 인데 체인엔 안 감")는 다음 실행이 rootOf 로 복구할 수 있다.
 */
@Service
@RequiredArgsConstructor
public class AnchorBatchService {

    private final AnchorBatchRepository batches;
    private final PredictionCommitRepository commits;
    private final CommitAnchorProperties contract;
    private final ChainProperties chain;

    /** 배치 하나를 보내는 데 필요한 것 전부. 엔티티를 트랜잭션 밖으로 들고 나가지 않으려고 값만 뽑는다. */
    public record Payload(long batchId, byte[] merkleRoot, List<byte[]> commitHashes) {}

    /**
     * 대기 커밋 전부를 새 배치로 묶는다. 0건이면 empty — 배치 행도 만들지 않는다(결정 A3).
     * 커밋 순서 = prediction id 오름차순 = 리프 순서.
     */
    @Transactional
    public Optional<Payload> openBatch(LocalDate businessDate) {
        List<PredictionCommit> pending = commits.findByAnchorBatchIsNullOrderByPredictionIdAsc();
        if (pending.isEmpty()) {
            return Optional.empty();
        }
        List<byte[]> leaves = toLeaves(pending);
        MerkleTree tree = MerkleTree.build(leaves);
        String root = Numeric.toHexString(tree.root());

        AnchorBatch batch =
                AnchorBatch.open(
                        businessDate, root, pending.size(), contract.normalizedAddress(), chain.chainId());
        batches.save(batch);
        for (PredictionCommit c : pending) {
            c.assignBatch(batch);
        }
        return Optional.of(new Payload(batch.getId(), tree.root(), leaves));
    }

    /** 재시도할 배치들 — FAILED, 또는 보냈는데 확인 못 한 PENDING. 안 보낸 PENDING 은 이전 실행이 죽은 것이라 이것도 포함한다. */
    @Transactional(readOnly = true)
    public List<Long> findRetryTargets() {
        List<Long> ids = new ArrayList<>();
        for (AnchorBatch b :
                batches.findByStatusInOrderByIdAsc(List.of(AnchorBatch.Status.FAILED, AnchorBatch.Status.PENDING))) {
            ids.add(b.getId());
        }
        return ids;
    }

    /** 기존 배치의 전송 재료. 커밋 목록은 배치에 소속된 것을 같은 순서로 다시 읽는다. */
    @Transactional(readOnly = true)
    public Payload payloadOf(long batchId) {
        AnchorBatch batch = batches.findById(batchId).orElseThrow();
        List<byte[]> leaves = toLeaves(commits.findByAnchorBatchOrderByPredictionIdAsc(batch));
        return new Payload(batchId, Numeric.hexStringToByteArray(batch.getMerkleRoot()), leaves);
    }

    @Transactional(readOnly = true)
    public boolean wasSent(long batchId) {
        return batches.findById(batchId).orElseThrow().wasSent();
    }

    @Transactional
    public void markSending(long batchId) {
        batches.findById(batchId).orElseThrow().markSending();
    }

    @Transactional
    public void recordResult(long batchId, AnchorResult result, Instant now) {
        AnchorBatch batch = batches.findById(batchId).orElseThrow();
        switch (result.status()) {
            case CONFIRMED -> batch.markConfirmed(result.txHash(), result.blockNumber(), now);
            case ALREADY_ANCHORED -> batch.markConfirmed(null, null, now);
            case SENT_UNCONFIRMED -> batch.markSent(result.txHash(), now);
        }
    }

    /** 다음 실행이 rootOf 로 "이미 박혀 있다" 를 확인했을 때. tx·블록은 모른다 — 인덱서가 채운다. */
    @Transactional
    public void confirmByRootCheck(long batchId, Instant now) {
        batches.findById(batchId).orElseThrow().markConfirmed(null, null, now);
    }

    @Transactional
    public void markFailed(long batchId, String reason) {
        batches.findById(batchId).orElseThrow().markFailed(reason);
    }

    private static List<byte[]> toLeaves(List<PredictionCommit> list) {
        List<byte[]> leaves = new ArrayList<>(list.size());
        for (PredictionCommit c : list) {
            leaves.add(Numeric.hexStringToByteArray(c.getCommitHash()));
        }
        return leaves;
    }
}
