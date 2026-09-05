package ssafy.a507.backend.domain.chain.indexer;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ssafy.a507.backend.domain.chain.entity.AnchorBatch;
import ssafy.a507.backend.domain.chain.entity.ChainEvent;
import ssafy.a507.backend.domain.chain.repository.AnchorBatchRepository;
import ssafy.a507.backend.domain.chain.repository.ChainEventRepository;
import tools.jackson.databind.json.JsonMapper;

/**
 * 인덱서의 DB 쪽 (ANT-CHAIN-04). 메서드 하나가 트랜잭션 하나다 — {@code AnchorBatchService} 와 같은 구조.
 *
 * <p>이벤트 저장과 배치 전이를 <b>한 트랜잭션</b>에 둔다(결정 C3). 전이가 예외로 실패하면 이벤트도 남지 않고,
 * 다음 폴링이 같은 구간을 다시 받아 처음부터 한다. "저장은 됐는데 반영은 안 된" 행(processed_at NULL)은
 * 그래서 원칙적으로 생기지 않으며, {@link #reprocess} 는 운영자가 손으로 되돌린 행을 다시 태우는 경로다.
 */
@Slf4j
@Service
public class AnchorIndexService {

    static final String EVENT_NAME = "Anchored";

    private final ChainEventRepository events;
    private final AnchorBatchRepository batches;
    /** Boot 4 = Jackson 3. 웹 계층 매퍼 빈에 기대지 않고 여기서 만든다({@code CommitAnchorAbi} 와 같은 이유). */
    private final JsonMapper json = JsonMapper.builder().build();

    public AnchorIndexService(ChainEventRepository events, AnchorBatchRepository batches) {
        this.events = events;
        this.batches = batches;
    }

    /**
     * {@code chain_events.payload} 의 형식. 이벤트 인자 전부 + reorg 검사용 블록 해시.
     *
     * <p>{@code commitHashes} 를 통째로 넣는 이유: 이 한 행이 배치 하나의 리프 전량이라, {@code prediction_commits}
     * 없이도 proof 를 재구축할 수 있다(CHAIN-08 "체인만으로 복구"를 DB 쪽에서도 지킨다).
     */
    public record Payload(long batchId, String merkleRoot, List<String> commitHashes, String blockHash) {}

    /** reorg 검사 재료 — 마지막 이벤트의 블록 번호와 그때 기록한 블록 해시. */
    public record LastEvent(long blockNumber, String blockHash) {}

    /**
     * 로그 하나를 적재하고 배치에 반영한다. 이미 있는 (tx_hash, log_index) 면 아무것도 하지 않는다 —
     * 재훑기·재시작에서 늘 걸리는 정상 경로라 로그도 남기지 않는다.
     *
     * @return 새로 적재했으면 true
     */
    @Transactional
    public boolean record(AnchoredLog log, Instant now) {
        if (events.existsByTxHashAndLogIndex(log.txHash(), log.logIndex())) {
            return false;
        }
        Payload payload = new Payload(log.batchId(), log.merkleRoot(), log.commitHashes(), log.blockHash());
        ChainEvent event =
                ChainEvent.record(
                        log.txHash(),
                        log.logIndex(),
                        log.contractAddress(),
                        EVENT_NAME,
                        log.blockNumber(),
                        json.writeValueAsString(payload));
        events.save(event);
        apply(event, payload, now);
        event.markProcessed(now);
        return true;
    }

    /** {@code processed_at IS NULL} 행을 payload 로 다시 반영한다. 행 자체는 손대지 않는다(트리거). */
    @Transactional
    public void reprocess(long eventId, Instant now) {
        ChainEvent event = events.findById(eventId).orElseThrow();
        if (event.isProcessed()) {
            return;
        }
        if (!EVENT_NAME.equals(event.getEventName())) {
            // 토큰 이벤트(인덱서 ②, CHAIN-03 뒤)는 이 서비스 소관이 아니다. 건드리지 않고 남긴다.
            return;
        }
        Payload payload = json.readValue(event.getPayload(), Payload.class);
        apply(event, payload, now);
        event.markProcessed(now);
    }

    @Transactional(readOnly = true)
    public List<Long> findUnprocessedIds() {
        List<Long> ids = new ArrayList<>();
        for (ChainEvent e : events.findByProcessedAtIsNullOrderByBlockNumberAscLogIndexAsc()) {
            ids.add(e.getId());
        }
        return ids;
    }

    /** 재시작 커서 = 이 컨트랙트 이벤트의 max(block_number). 없으면 empty. */
    @Transactional(readOnly = true)
    public Optional<Long> cursor(String contractAddress) {
        return events.findMaxBlockNumber(contractAddress);
    }

    @Transactional(readOnly = true)
    public Optional<LastEvent> lastEvent(String contractAddress) {
        return events.findTopByContractAddressOrderByBlockNumberDescLogIndexDesc(contractAddress)
                .map(
                        e -> {
                            Payload p = json.readValue(e.getPayload(), Payload.class);
                            return new LastEvent(e.getBlockNumber(), p.blockHash());
                        });
    }

    /**
     * 배치 반영. 이벤트는 이미 저장돼 있고, 여기서는 {@code anchor_batches} 만 만진다.
     *
     * <pre>
     * DB 에 없는 batchId              → WARN. 다른 배포·데모·테스트가 태운 번호(README 함정 2). 이벤트는 남는다
     * 배치의 컨트랙트 ≠ 이벤트 컨트랙트 → WARN. 재배포 전 배치와 새 컨트랙트의 같은 번호 — 서로 무관하다
     * 루트 불일치                     → BATCH_ID_COLLISION FAILED. 남이 우리 번호에 다른 루트를 박았다(CHAIN-02 와 같은 판정)
     * 루트 일치                       → confirmFromChain(tx, block) — CONFIRMED 였으면 참조만 맞추고 시각은 유지
     * </pre>
     */
    private void apply(ChainEvent event, Payload payload, Instant now) {
        Optional<AnchorBatch> found = batches.findById(payload.batchId());
        if (found.isEmpty()) {
            log.warn(
                    "Anchored batchId {} — DB 에 없는 배치. 다른 배포·데모의 번호다(contracts/README.md 함정 2). tx {}",
                    payload.batchId(),
                    event.getTxHash());
            return;
        }
        AnchorBatch batch = found.get();
        if (!batch.getContractAddress().equalsIgnoreCase(event.getContractAddress())) {
            log.warn(
                    "Anchored batchId {} — 이벤트 컨트랙트 {} 가 배치의 컨트랙트 {} 와 다르다. 건드리지 않음",
                    payload.batchId(),
                    event.getContractAddress(),
                    batch.getContractAddress());
            return;
        }
        if (!batch.getMerkleRoot().equalsIgnoreCase(payload.merkleRoot())) {
            String reason = "BATCH_ID_COLLISION: onchain root " + payload.merkleRoot() + " != ours";
            batch.markFailed(reason);
            log.error(
                    "앵커 배치 #{} — 체인의 루트({})가 우리 루트({})와 다르다. batchId 충돌. DB id 를 건너뛰거나 컨트랙트를 재배포해라",
                    batch.getId(),
                    payload.merkleRoot(),
                    batch.getMerkleRoot());
            return;
        }
        boolean wasConfirmed = batch.isConfirmed();
        batch.confirmFromChain(event.getTxHash(), event.getBlockNumber(), now);
        if (wasConfirmed) {
            log.info("앵커 배치 #{} — 체인 이벤트로 확인(이미 CONFIRMED). tx {} block {}", batch.getId(), event.getTxHash(), event.getBlockNumber());
        } else {
            log.info("앵커 배치 #{} — 체인 이벤트로 CONFIRMED. tx {} block {}", batch.getId(), event.getTxHash(), event.getBlockNumber());
        }
    }
}
