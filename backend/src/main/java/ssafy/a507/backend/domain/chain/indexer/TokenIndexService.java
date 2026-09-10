package ssafy.a507.backend.domain.chain.indexer;

import java.math.BigInteger;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ssafy.a507.backend.domain.account.entity.User;
import ssafy.a507.backend.domain.account.repository.UserRepository;
import ssafy.a507.backend.domain.chain.entity.ChainEvent;
import ssafy.a507.backend.domain.chain.entity.Operation;
import ssafy.a507.backend.domain.chain.entity.TokenLedger;
import ssafy.a507.backend.domain.chain.relay.TokenReason;
import ssafy.a507.backend.domain.chain.repository.ChainEventRepository;
import ssafy.a507.backend.domain.chain.repository.OperationRepository;
import ssafy.a507.backend.domain.chain.repository.TokenLedgerRepository;
import ssafy.a507.backend.domain.monetize.repository.AdBannerRepository;
import ssafy.a507.backend.domain.monetize.repository.SubscriptionRepository;
import tools.jackson.databind.json.JsonMapper;

/**
 * 토큰 인덱서 ② 의 DB 쪽 (ANT-CHAIN-11). 메서드 하나가 트랜잭션 하나다 — {@link AnchorIndexService} 와 같은 구조.
 *
 * <p>이벤트 한 건이 남기는 것: {@code token_ledger} 증감(회원 지갑만) → 같은 tx 의 {@code operations} SUCCEEDED →
 * 리소스 전이(구독 ACTIVE · 광고 ACTIVE). 전부 한 트랜잭션이라 중간에 터지면 이벤트도 안 남고 다음 회차가 다시 받는다.
 *
 * <p>이벤트가 <b>안 오는</b> 실패도 여기서 닫는다({@link #findStalePending} → {@link #failOperation}) — 릴레이어(CHAIN-10)가
 * 남긴 잔여 경로 둘: "전송~markSent 사이 크래시"(tx_hash 없음)와 "시뮬 통과 후 채굴에서 revert"(receipt status 0).
 * 체인 조회(receipt)는 트랜잭션 밖에서 {@link TokenIndexer} 가 하고, 여기는 판정 결과만 기록한다.
 *
 * <p><b>자동 환불은 없다</b>(plan §3-⑥, 유저 결정 09-10). SEND_LOST 로 닫힌 작업의 tx 가 실제로는 나갔다면 원장은
 * 이벤트대로 차감되고 리소스만 없다 — 짝 없는 Burned 를 ERROR 로 남기고 사람이 본다. 의도치 않은 mint 가 더 위험하다.
 */
@Slf4j
@Service
public class TokenIndexService {

    /** tx_hash 없이 PENDING 인 채 이만큼 지나면 SEND_LOST. 전송은 요청 안에서 수백 ms 라 10분이면 충분히 넉넉하다. */
    static final Duration SEND_LOST_AFTER = Duration.ofMinutes(10);
    /** tx 를 보낸 뒤 이만큼(블록 3개) 지나야 receipt 를 묻는다 — 갓 보낸 tx 마다 5초에 한 번 묻지 않으려는 것. */
    static final Duration REVERT_CHECK_AFTER = Duration.ofSeconds(30);
    /** receipt 가 이만큼 안 나오면 풀에서 사라진 것으로 본다. gas 0 Besu 에서는 거의 없는 일이다. */
    static final Duration DROPPED_AFTER = Duration.ofMinutes(10);

    private final ChainEventRepository events;
    private final OperationRepository operations;
    private final TokenLedgerRepository ledger;
    private final UserRepository users;
    private final SubscriptionRepository subscriptions;
    private final AdBannerRepository banners;
    /** Boot 4 = Jackson 3. 웹 계층 매퍼 빈에 기대지 않고 여기서 만든다({@code AnchorIndexService} 와 같은 이유). */
    private final JsonMapper json = JsonMapper.builder().build();

    public TokenIndexService(
            ChainEventRepository events,
            OperationRepository operations,
            TokenLedgerRepository ledger,
            UserRepository users,
            SubscriptionRepository subscriptions,
            AdBannerRepository banners) {
        this.events = events;
        this.operations = operations;
        this.ledger = ledger;
        this.users = users;
        this.subscriptions = subscriptions;
        this.banners = banners;
    }

    /**
     * {@code chain_events.payload}. 이벤트 인자 전부 + reorg 검사용 블록 해시. 금액은 문자열 — numeric(30,0) 을 JSON 숫자로
     * 왕복시키지 않으려는 것. {@code reason} 은 bytes32 원문(hex)만 둔다. 되돌리기는 {@link TokenLog#reason()} 규칙 그대로.
     */
    public record Payload(
            String kind,
            String from,
            String to,
            String amount,
            String creatorShare,
            String platformShare,
            String reasonHex,
            String blockHash) {}

    public record LastEvent(long blockNumber, String blockHash) {}

    /** 정리 대상 PENDING 작업. 체인 조회는 {@link TokenIndexer} 가 트랜잭션 밖에서 한다. */
    public record StalePending(String operationId, String txHash, Instant createdAt) {}

    /** 로그 하나를 적재하고 반영한다. 이미 있는 (tx_hash, log_index) 면 아무것도 하지 않는다. @return 새로 적재했으면 true */
    @Transactional
    public boolean record(TokenLog log, Instant now) {
        if (events.existsByTxHashAndLogIndex(log.txHash(), log.logIndex())) {
            return false;
        }
        Payload payload =
                new Payload(
                        log.kind().name(),
                        log.from(),
                        log.to(),
                        log.amount().toString(),
                        log.creatorShare() == null ? null : log.creatorShare().toString(),
                        log.platformShare() == null ? null : log.platformShare().toString(),
                        log.reasonHex(),
                        log.blockHash());
        ChainEvent event =
                ChainEvent.record(
                        log.txHash(),
                        log.logIndex(),
                        log.contractAddress(),
                        log.kind().eventName,
                        log.blockNumber(),
                        json.writeValueAsString(payload));
        events.save(event);
        apply(event, payload, now);
        event.markProcessed(now);
        return true;
    }

    /** {@code processed_at IS NULL} 인 토큰 이벤트를 payload 로 다시 반영한다. 앵커 이벤트는 {@link AnchorIndexService} 소관. */
    @Transactional
    public void reprocess(long eventId, Instant now) {
        ChainEvent event = events.findById(eventId).orElseThrow();
        if (event.isProcessed() || TokenLog.Kind.ofEventName(event.getEventName()).isEmpty()) {
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
            if (TokenLog.Kind.ofEventName(e.getEventName()).isPresent()) {
                ids.add(e.getId());
            }
        }
        return ids;
    }

    /** 재시작 커서 = 토큰 컨트랙트 이벤트의 max(block_number). 앵커 커서와 독립이다(컨트랙트별 쿼리). */
    @Transactional(readOnly = true)
    public Optional<Long> cursor(String contractAddress) {
        return events.findMaxBlockNumber(contractAddress);
    }

    @Transactional(readOnly = true)
    public Optional<LastEvent> lastEvent(String contractAddress) {
        return events.findTopByContractAddressOrderByBlockNumberDescLogIndexDesc(contractAddress)
                .map(e -> new LastEvent(e.getBlockNumber(), json.readValue(e.getPayload(), Payload.class).blockHash()));
    }

    /**
     * 이벤트가 안 온 PENDING 후보. 둘로 나뉜다 — tx_hash 없이 {@link #SEND_LOST_AFTER} 지난 것(바로 닫는다)과
     * tx_hash 있고 {@link #REVERT_CHECK_AFTER} 지난 것(receipt 를 봐야 안다).
     */
    @Transactional(readOnly = true)
    public List<StalePending> findStalePending(Instant now) {
        List<StalePending> out = new ArrayList<>();
        for (Operation op :
                operations.findByStatusAndTxHashIsNullAndCreatedAtBefore(
                        Operation.Status.PENDING, now.minus(SEND_LOST_AFTER))) {
            out.add(new StalePending(op.getId(), null, op.getCreatedAt()));
        }
        for (Operation op :
                operations.findByStatusAndTxHashIsNotNullAndCreatedAtBefore(
                        Operation.Status.PENDING, now.minus(REVERT_CHECK_AFTER))) {
            out.add(new StalePending(op.getId(), op.getTxHash(), op.getCreatedAt()));
        }
        return out;
    }

    /**
     * 작업을 FAILED 로 닫고 리소스도 그에 맞게. 광고는 {@code AdBanner.reject()} — 소유자가 "결제 실패" 용으로 열어 둔 메서드다.
     * 구독은 손대지 않는다(enum 에 FAILED 가 없다, TOKEN-01 몫). 이미 PENDING 이 아니면 아무것도 하지 않는다 — 같은 회차에
     * 이벤트가 먼저 왔을 수 있다.
     */
    @Transactional
    public void failOperation(String operationId, String code, String message) {
        Optional<Operation> found = operations.findById(operationId);
        if (found.isEmpty() || found.get().getStatus() != Operation.Status.PENDING) {
            return;
        }
        Operation op = found.get();
        op.markFailed(code, message);
        if (op.getResourceType() == Operation.ResourceType.AD && op.getResourceId() != null) {
            banners.findById(op.getResourceId()).ifPresent(b -> b.reject());
        }
        log.warn("작업 {} — {} ({}), kind {}", op.getId(), code, message, op.getKind());
    }

    // ── 반영 ────────────────────────────────────────────────────────────

    /** 원장 → 작업 → 리소스. 이벤트는 이미 저장돼 있다. */
    private void apply(ChainEvent event, Payload p, Instant now) {
        TokenLog.Kind kind = TokenLog.Kind.valueOf(p.kind());
        BigInteger amount = new BigInteger(p.amount());
        switch (kind) {
            case MINTED -> credit(p.to(), amount, reasonOf(p, event), event);
            case BURNED -> credit(p.from(), amount.negate(), reasonOf(p, event), event);
            case SUBSCRIBED -> {
                credit(p.from(), amount.negate(), TokenReason.SUBSCRIBE.name(), event);
                credit(p.to(), new BigInteger(p.creatorShare()), TokenReason.SUBSCRIBE_INCOME.name(), event);
                // 플랫폼 몫(p.platformShare)은 원장에 안 쓴다 — 수납 주소는 회원이 아니다.
            }
        }
        settleOperation(event, kind, now);
    }

    /** 회원 지갑이면 원장 한 행. 아니면 WARN 한 줄 — 테스트 mint·데모·수납 주소가 여기 걸린다. 이벤트는 이미 남아 있다. */
    private void credit(String wallet, BigInteger delta, String reason, ChainEvent event) {
        Optional<User> user = users.findByWalletAddress(wallet);
        if (user.isEmpty()) {
            log.warn("{} {} ANT ({}) — 회원이 아닌 지갑 {}. 원장에 쓰지 않음. tx {}", event.getEventName(), delta, reason, wallet, event.getTxHash());
            return;
        }
        ledger.save(TokenLedger.record(user.get(), delta, reason, event));
    }

    /** Minted/Burned 의 reason. 서버 어휘면 enum 이름, 아니면 ASCII 원문 + WARN(이관·수동 tx). */
    private String reasonOf(Payload p, ChainEvent event) {
        TokenLog probe = new TokenLog(null, 0, 0, null, null, TokenLog.Kind.MINTED, null, null, null, null, null, p.reasonHex());
        Optional<TokenReason> known = probe.reason();
        if (known.isPresent()) {
            return known.get().name();
        }
        String text = probe.reasonText();
        log.warn("{} reason '{}' 은 서버 어휘(TokenReason)에 없다 — 원문 그대로 원장에 쓴다. tx {}", event.getEventName(), text, event.getTxHash());
        return text == null || text.isEmpty() ? "UNKNOWN" : text;
    }

    /**
     * 같은 tx 의 작업을 SUCCEEDED 로. PENDING 일 때만 — 이미 끝난 작업은 재훑기·수동 재처리의 정상 경로라 INFO 만.
     * 작업이 없으면 이 이벤트는 우리 서버가 접수한 게 아니거나(데모·수동 tx), SEND_LOST 창에서 실제로 나간 tx 다 — 후자는
     * 사용자 토큰만 움직인 것이라 ERROR 로 남긴다(자동 환불 없음, plan §3-⑥).
     */
    private void settleOperation(ChainEvent event, TokenLog.Kind kind, Instant now) {
        Optional<Operation> found = operations.findByTxHash(event.getTxHash());
        if (found.isEmpty()) {
            if (kind == TokenLog.Kind.MINTED) {
                log.info("{} tx {} — 짝 되는 작업 없음(보너스·데모·수동 mint). 원장만 반영", event.getEventName(), event.getTxHash());
            } else {
                log.error(
                        "{} tx {} — 짝 되는 작업 없음. 서버가 보낸 tx 라면 SEND_LOST 창(전송~markSent 사이 크래시)이다: 토큰은 움직였고 리소스는 없다. "
                                + "자동 환불하지 않는다 — 사람이 확인해라",
                        event.getEventName(),
                        event.getTxHash());
            }
            return;
        }
        Operation op = found.get();
        if (op.getStatus() != Operation.Status.PENDING) {
            log.info("작업 {} — 이미 {} 다. 이벤트 {} 는 저장만", op.getId(), op.getStatus(), event.getTxHash());
            return;
        }
        op.markSucceeded(event.getTxHash());
        if (op.getResourceType() != null && op.getResourceId() != null) {
            switch (op.getResourceType()) {
                case SUBSCRIPTION -> subscriptions.findById(op.getResourceId()).ifPresent(s -> s.activate(event.getTxHash(), now));
                case AD -> banners.findById(op.getResourceId()).ifPresent(b -> b.activate(event.getTxHash()));
                // PREDICTION: PRED-01 202 경로가 붙을 때 여기 분기 하나. SEASON_PARTICIPANT: join 이 곧 ONGOING 이라 전이할 게 없다.
                case PREDICTION, SEASON_PARTICIPANT -> {}
            }
        }
        log.info("작업 {} — SUCCEEDED ({}, tx {}, {} {})", op.getId(), op.getKind(), event.getTxHash(), op.getResourceType(), op.getResourceId());
    }
}
