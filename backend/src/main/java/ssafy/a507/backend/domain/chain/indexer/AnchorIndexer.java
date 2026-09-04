package ssafy.a507.backend.domain.chain.indexer;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import ssafy.a507.backend.common.error.BusinessException;
import ssafy.a507.backend.domain.chain.config.ChainProperties;
import ssafy.a507.backend.domain.chain.config.CommitAnchorProperties;

/**
 * 앵커 인덱서 (ANT-CHAIN-04). 스케줄러가 5초마다 {@link #poll} 을 부른다.
 *
 * <pre>
 * ① 재처리   processed_at IS NULL 행 → 배치 반영               (운영자가 되돌린 행. 평소엔 0건)
 * ② 커서     메모리 커서 + 1, 없으면 max(DB 커서, from-block)   (DB 가 진실, 메모리는 재훑기 방지)
 * ③ reorg    마지막 이벤트 블록의 해시 ≠ 체인 → 알람 + 정지     (이 체인은 BFT 라 실제로는 안 난다 — 안전장치)
 * ④ 구간     [from, head] 를 max-block-range 로 잘라 getLogs → 로그마다 record (멱등)
 * </pre>
 *
 * <p><b>confirmations = 0.</b> SSAFY 체인은 Besu BFT(검증자 4 순환 · difficulty 1 · 블록 10초)라 즉시 확정이다(plan §실측).
 * 로그가 보이면 그 블록은 확정이고, 되감기는 만들지 않는다 — {@code chain_events} 는 트리거가 DELETE 를 막는다.
 *
 * <p><b>정지(halted)</b>는 컴포넌트 플래그다. 프로세스도, 앵커 전송기도 안 죽는다. 재시작하면 ③ 부터 다시 검사한다.
 * 자동으로 풀지 않는 이유: 해시가 다르다는 건 우리가 기록한 블록이 체인에 없다는 뜻이고, 그 위에 쌓인
 * {@code anchor_batches} 확정이 전부 의심스러워진다. 사람이 봐야 한다.
 *
 * <p><b>단일 인스턴스 전제</b>(결정 C4). 둘이 돌면 record 의 멱등 검사 뒤 UQ 충돌로 한쪽 트랜잭션이 깨진다 — 데이터는 안 망가지지만 로그가 시끄럽다.
 */
@Slf4j
@Component
public class AnchorIndexer {

    private final ChainLogSource source;
    private final AnchorIndexService service;
    private final ChainProperties props;
    private final CommitAnchorProperties contract;

    /** reorg 검사 실패 상태. 첫 정지 때 ERROR 한 번, 이후 회차는 조용히 건너뛴다. */
    private volatile boolean halted;
    /** 이번 프로세스가 성공적으로 훑은 마지막 블록. null 이면 아직 한 번도 안 훑었다(재시작 직후). */
    private Long scannedUpTo;

    public AnchorIndexer(
            ChainLogSource source,
            AnchorIndexService service,
            ChainProperties props,
            CommitAnchorProperties contract) {
        this.source = source;
        this.service = service;
        this.props = props;
        this.contract = contract;
        if (isEnabled()) {
            log.info("앵커 인덱서 준비: 컨트랙트 {}, 시작 블록 {}", contract.address(), props.indexer().fromBlock());
        } else {
            log.info("앵커 인덱서 꺼짐 — CHAIN_RPC_URL · CONTRACT_COMMIT_ANCHOR 를 확인하라 (릴레이어 키는 필요 없다)");
        }
    }

    /** RPC 와 컨트랙트 주소만 있으면 된다. 읽기 전용이라 릴레이어 키는 안 본다. */
    public boolean isEnabled() {
        return props.hasRpcUrl() && contract.isDeployed();
    }

    public boolean isHalted() {
        return halted;
    }

    /** 이번 프로세스가 훑은 마지막 블록. 테스트·운영 확인용. */
    public Optional<Long> scannedUpTo() {
        return Optional.ofNullable(scannedUpTo);
    }

    /** 테스트 전용 — 재시작 직후 상태(메모리 커서 없음·정지 아님)로. 싱글턴이라 테스트 사이에 상태가 남는다. */
    synchronized void resetForTest() {
        halted = false;
        scannedUpTo = null;
    }

    /** 한 회차. 스프링 스케줄러는 단일 스레드라 겹치지 않지만, 수동 호출과 섞일 수 있어 synchronized 로 둔다. */
    public synchronized void poll() {
        if (!isEnabled() || halted) {
            return;
        }
        Instant now = Instant.now();
        String address = contract.normalizedAddress();

        // ① 재처리 — 행마다 트랜잭션. 하나가 실패해도 나머지는 간다.
        for (Long id : service.findUnprocessedIds()) {
            try {
                service.reprocess(id, now);
            } catch (RuntimeException e) {
                log.error("chain_events #{} 재처리 실패", id, e);
            }
        }

        try {
            // ② 커서
            long from;
            if (scannedUpTo != null) {
                from = scannedUpTo + 1;
            } else {
                long dbCursor = service.cursor(address).map(b -> b + 1).orElse(0L);
                from = Math.max(dbCursor, props.indexer().fromBlock());
            }

            // ③ reorg 검사 — 이벤트가 하나라도 있을 때만. 없으면 대조할 게 없다.
            Optional<AnchorIndexService.LastEvent> last = service.lastEvent(address);
            if (last.isPresent()) {
                Optional<String> onchain = source.blockHash(last.get().blockNumber());
                if (onchain.isEmpty() || !onchain.get().equalsIgnoreCase(last.get().blockHash())) {
                    halted = true;
                    log.error(
                            "앵커 인덱서 정지 — 블록 {} 의 해시가 다르다. 기록 {} / 체인 {}. 이 체인(BFT)에서는 나면 안 되는 일이다. "
                                    + "chain_events 이후 행과 anchor_batches 확정을 사람이 확인하고 서버를 재시작해라",
                            last.get().blockNumber(),
                            last.get().blockHash(),
                            onchain.orElse("(블록 없음)"));
                    return;
                }
            }

            // ④ 구간 루프
            long head = source.latestBlock();
            int range = Math.max(1, props.indexer().maxBlockRange());
            while (from <= head) {
                long to = Math.min(head, from + range - 1);
                List<AnchoredLog> logs = source.anchoredLogs(from, to);
                int inserted = 0;
                for (AnchoredLog l : logs) {
                    if (service.record(l, now)) {
                        inserted++;
                    }
                }
                if (!logs.isEmpty()) {
                    log.info("앵커 인덱서 [{}, {}] — 로그 {}건, 신규 {}건", from, to, logs.size(), inserted);
                }
                scannedUpTo = to;
                from = to + 1;
            }
        } catch (BusinessException e) {
            // RPC 장애. 다음 회차가 이어받는다 — 메모리 커서는 마지막 성공 구간까지만 전진해 있다.
            log.warn("앵커 인덱서 회차 건너뜀 — {}", e.getErrorCode());
        }
    }
}
