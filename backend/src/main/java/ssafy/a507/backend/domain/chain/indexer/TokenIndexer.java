package ssafy.a507.backend.domain.chain.indexer;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import ssafy.a507.backend.common.error.BusinessException;
import ssafy.a507.backend.domain.chain.config.ChainProperties;
import ssafy.a507.backend.domain.chain.config.PredictTokenProperties;

/**
 * 토큰 인덱서 ② (ANT-CHAIN-11). 스케줄러가 5초마다 {@link #poll} 을 부른다.
 *
 * <pre>
 * ① 재처리   processed_at IS NULL 인 토큰 이벤트 → 다시 반영           (운영자가 되돌린 행. 평소엔 0건)
 * ② 커서     메모리 커서 + 1, 없으면 max(DB 토큰 커서, from-block)      (앵커 커서와 독립 — 컨트랙트별)
 * ③ reorg    토큰 컨트랙트 마지막 이벤트 블록의 해시 ≠ 체인 → 알람 + 정지
 * ④ 구간     [from, head] 를 max-block-range 로 잘라 tokenLogs → record (멱등)
 * ⑤ 정리     이벤트가 안 온 PENDING 작업 — tx_hash 없음 10분 → SEND_LOST · receipt 0 → REVERTED · receipt 없음 10분 → TX_DROPPED
 * </pre>
 *
 * <p>①~④ 는 {@link AnchorIndexer} 의 뼈대를 그대로 복제했다. 추상 기반 클래스로 뽑지 않은 이유는 인덱서가 둘뿐이고 dev 의
 * 앵커 코드를 안 건드리려는 것이다 — 셋째(위성 컨트랙트)가 생기면 그때 뽑는다. <b>reorg 검사를 한쪽만 고치면 어긋난다.</b>
 *
 * <p>⑤ 가 ④ 뒤인 이유: 같은 회차에 이벤트가 오면 SUCCEEDED 가 먼저 찍혀 정리 대상에서 빠진다. receipt 조회는 트랜잭션 밖에서
 * 한다 — DB 트랜잭션을 잡은 채 RPC 를 기다리지 않는다(CHAIN-02 부터의 원칙).
 *
 * <p>confirmations = 0 · 단일 인스턴스 · 정지는 컴포넌트 플래그(재시작으로 풀림) — 전부 앵커 인덱서와 같다.
 */
@Slf4j
@Component
public class TokenIndexer {

    private final ChainLogSource source;
    private final TokenIndexService service;
    private final ChainProperties props;
    private final PredictTokenProperties token;

    private volatile boolean halted;
    private Long scannedUpTo;

    public TokenIndexer(
            ChainLogSource source, TokenIndexService service, ChainProperties props, PredictTokenProperties token) {
        this.source = source;
        this.service = service;
        this.props = props;
        this.token = token;
        if (isEnabled()) {
            log.info("토큰 인덱서 준비: PredictToken {}, 시작 블록 {}", token.address(), props.indexer().fromBlock());
        } else {
            log.info("토큰 인덱서 꺼짐 — CHAIN_RPC_URL · CONTRACT_PREDICT_TOKEN 를 확인하라 (릴레이어 키는 필요 없다)");
        }
    }

    /** RPC 와 토큰 주소만 있으면 된다. 읽기 전용이라 릴레이어 키는 안 본다. */
    public boolean isEnabled() {
        return props.hasRpcUrl() && token.isDeployed();
    }

    public boolean isHalted() {
        return halted;
    }

    public Optional<Long> scannedUpTo() {
        return Optional.ofNullable(scannedUpTo);
    }

    /** 테스트 전용 — 재시작 직후 상태로. */
    synchronized void resetForTest() {
        halted = false;
        scannedUpTo = null;
    }

    public void poll() {
        poll(Instant.now());
    }

    /** 한 회차. {@code now} 를 받는 이유는 ⑤ 의 시간 문턱을 테스트가 밀어 볼 수 있게 하려는 것. */
    public synchronized void poll(Instant now) {
        if (!isEnabled() || halted) {
            return;
        }
        String address = token.normalizedAddress();

        // ① 재처리
        for (Long id : service.findUnprocessedIds()) {
            try {
                service.reprocess(id, now);
            } catch (RuntimeException e) {
                log.error("chain_events #{} (토큰) 재처리 실패", id, e);
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

            // ③ reorg 검사
            Optional<TokenIndexService.LastEvent> last = service.lastEvent(address);
            if (last.isPresent()) {
                Optional<String> onchain = source.blockHash(last.get().blockNumber());
                if (onchain.isEmpty() || !onchain.get().equalsIgnoreCase(last.get().blockHash())) {
                    halted = true;
                    log.error(
                            "토큰 인덱서 정지 — 블록 {} 의 해시가 다르다. 기록 {} / 체인 {}. 이 체인(BFT)에서는 나면 안 되는 일이다. "
                                    + "chain_events 이후 행과 token_ledger·operations 를 사람이 확인하고 서버를 재시작해라",
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
                List<TokenLog> logs = source.tokenLogs(from, to);
                int inserted = 0;
                for (TokenLog l : logs) {
                    if (service.record(l, now)) {
                        inserted++;
                    }
                }
                if (!logs.isEmpty()) {
                    log.info("토큰 인덱서 [{}, {}] — 로그 {}건, 신규 {}건", from, to, logs.size(), inserted);
                }
                scannedUpTo = to;
                from = to + 1;
            }

            // ⑤ 정리
            settleStale(now);
        } catch (BusinessException e) {
            log.warn("토큰 인덱서 회차 건너뜀 — {}", e.getErrorCode());
        }
    }

    /**
     * 이벤트가 안 온 PENDING 을 닫는다. 규칙은 {@link TokenIndexService} 의 상수 셋. receipt 가 있고 성공인데 이벤트가 아직
     * 없으면 그대로 둔다 — 인덱서 지연이고 다음 회차가 잡는다.
     */
    private void settleStale(Instant now) {
        for (TokenIndexService.StalePending s : service.findStalePending(now)) {
            if (s.txHash() == null) {
                service.failOperation(s.operationId(), "SEND_LOST", "전송 주체가 tx 를 남기지 못했다(전송 실패 또는 크래시)");
                continue;
            }
            Optional<Boolean> receipt = source.receiptStatus(s.txHash());
            if (receipt.isPresent() && !receipt.get()) {
                service.failOperation(s.operationId(), "REVERTED", "채굴에서 revert — 시뮬레이션 뒤 잔액이 바뀌었다. tx " + s.txHash());
            } else if (receipt.isEmpty() && s.createdAt().plus(TokenIndexService.DROPPED_AFTER).isBefore(now)) {
                service.failOperation(s.operationId(), "TX_DROPPED", "receipt 가 10분 안에 나오지 않았다. tx " + s.txHash());
            }
        }
    }
}
