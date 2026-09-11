package ssafy.a507.backend.domain.chain.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import java.time.Instant;
import java.time.LocalDate;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;

/**
 * 머클 앵커 배치. 커밋 수천 건을 트랜잭션 1건으로 묶는다.
 *
 * <p>id 가 곧 온체인 batchId 다(CommitAnchor 의 멱등키). 그래서 DB 를 초기화하면 컨트랙트도
 * 새로 배포해야 한다 — contracts/README.md 함정 2.
 *
 * <p>상태 전이(ANT-CHAIN-02): {@code PENDING(sent_at NULL)} → 전송 → {@code PENDING(sent_at, tx_hash)}
 * → receipt → {@code CONFIRMED}. 실패는 {@code FAILED} 로 두고 다음 실행이 <b>같은 id</b> 로 재전송한다.
 * 새 커밋은 항상 새 배치로 간다 — 실패 배치에 합치지 않는다.
 */
@Entity
@Table(
        name = "anchor_batches",
        indexes = @Index(name = "ix_anchor_batches_business_date", columnList = "business_date"))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AnchorBatch {

    /** 실패분은 다음 회차에서 같은 batchId 로 재시도한다. */
    public enum Status {
        PENDING,
        CONFIRMED,
        FAILED
    }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 회차 커밋들의 머클 루트. 온체인에 올리는 값이고, 컨트랙트가 리프로 재계산해 대조한다. */
    @Column(name = "merkle_root", nullable = false, unique = true, length = 66)
    private String merkleRoot;

    @Column(name = "commit_count", nullable = false)
    private int commitCount;

    /** 앵커 트랜잭션. 전송 전에는 NULL이다. */
    @Column(name = "tx_hash", length = 66)
    private String txHash;

    /** 확정 블록. 확정 전에는 NULL이다. anchoredAt 으로 뒤늦게 확인한 배치는 CONFIRMED 여도 NULL 일 수 있다. */
    @Column(name = "block_number")
    private Long blockNumber;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 12)
    private Status status;

    /**
     * 어느 날 실행분인지. UQ 가 아니다 — 같은 날 재실행에 새 커밋이 있으면 두 번째 배치가 생기는 게
     * 그 커밋을 하루 무보호로 두는 것보다 낫다. "하루 한 번" 은 cron 이 보장한다.
     */
    @Column(name = "business_date", nullable = false)
    private LocalDate businessDate;

    /** 앵커한 컨트랙트. 재배포 뒤에도 옛 배치는 옛 주소의 장부(anchoredAt)로 검증해야 한다. */
    @Column(name = "contract_address", nullable = false, length = 42)
    private String contractAddress;

    /** 위와 같은 이유. ProofBundle 필드이기도 하다. */
    @Column(name = "chain_id", nullable = false)
    private long chainId;

    /**
     * tx 를 보낸 시각. NULL 이면 아직 안 보냈다. "보냈는데 receipt 를 못 받은" 배치를 "안 보낸" 배치와
     * 가르는 유일한 표지 — 전자는 재전송 전에 anchoredAt(루트)을 먼저 봐야 한다.
     */
    @Column(name = "sent_at")
    private Instant sentAt;

    /** 전송 시도 횟수. 운영 가시성용이고 로직은 안 본다. */
    @Column(nullable = false)
    private int attempts;

    /** 마지막 실패 사유. custom error 이름 또는 예외 메시지 앞부분. */
    @Column(name = "last_error", length = 300)
    private String lastError;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "confirmed_at")
    private Instant confirmedAt;

    /** 배치를 연다. 아직 아무것도 안 보낸 PENDING. */
    public static AnchorBatch open(
            LocalDate businessDate, String merkleRoot, int commitCount, String contractAddress, long chainId) {
        AnchorBatch b = new AnchorBatch();
        b.businessDate = businessDate;
        b.merkleRoot = merkleRoot;
        b.commitCount = commitCount;
        b.contractAddress = contractAddress;
        b.chainId = chainId;
        b.status = Status.PENDING;
        b.attempts = 0;
        return b;
    }

    /** 전송 직전. attempts 를 올리고 FAILED 였다면 PENDING 으로 되돌린다. */
    public void markSending() {
        this.attempts++;
        this.status = Status.PENDING;
    }

    /** tx 는 나갔는데 receipt 는 못 받았다. 다음 실행이 anchoredAt 으로 확인한다. */
    public void markSent(String txHash, Instant now) {
        this.txHash = txHash;
        this.sentAt = now;
        this.status = Status.PENDING;
        this.lastError = null;
    }

    /**
     * 확정. receipt 로 확인했으면 txHash·blockNumber 가 있고, 다음 실행이 anchoredAt 으로만 확인했으면 둘 다 없을 수 있다
     * (그 경우 인덱서 CHAIN-04 가 이벤트에서 채운다).
     */
    public void markConfirmed(String txHash, Long blockNumber, Instant now) {
        if (txHash != null) {
            this.txHash = txHash;
        }
        if (blockNumber != null) {
            this.blockNumber = blockNumber;
        }
        if (this.sentAt == null) {
            this.sentAt = now;
        }
        this.status = Status.CONFIRMED;
        this.confirmedAt = now;
        this.lastError = null;
    }

    /**
     * 인덱서(ANT-CHAIN-04) 전용 — 체인에서 이 배치의 {@code Anchored} 이벤트를 <b>우리 루트로</b> 읽었다.
     *
     * <p>{@link #markConfirmed} 를 쓰지 않는 이유: 그건 릴레이어 경로라 부를 때마다 {@code confirmed_at} 을 지금으로
     * 덮는다. 인덱서는 폴링 지연이 섞인 시각이라 이미 CONFIRMED 인 배치의 확정 시각을 밀어서는 안 된다.
     * 여기서는 tx·블록은 <b>체인 값으로 항상</b> 맞추고(체인이 진실이다 — anchoredAt 으로만 확인한 배치는 NULL 이었고,
     * receipt 를 못 받은 배치는 옛 tx 해시일 수 있다), 상태는 CONFIRMED 가 아닐 때만 올린다.
     */
    public void confirmFromChain(String txHash, long blockNumber, Instant now) {
        this.txHash = txHash;
        this.blockNumber = blockNumber;
        if (this.status != Status.CONFIRMED) {
            this.status = Status.CONFIRMED;
            this.confirmedAt = now;
            this.lastError = null;
            if (this.sentAt == null) {
                this.sentAt = now;
            }
        }
    }

    public void markFailed(String reason) {
        this.status = Status.FAILED;
        this.lastError = reason == null ? null : reason.substring(0, Math.min(reason.length(), 300));
    }

    public boolean isConfirmed() {
        return status == Status.CONFIRMED;
    }

    /** 보낸 적이 있는 배치. 재전송 전에 anchoredAt(루트)을 먼저 봐야 한다. */
    public boolean wasSent() {
        return sentAt != null;
    }
}
