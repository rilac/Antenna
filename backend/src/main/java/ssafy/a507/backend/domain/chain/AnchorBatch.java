package ssafy.a507.backend.domain.chain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;

/** 머클 앵커 배치. 커밋 수천 건을 트랜잭션 1건으로 묶는다. */
@Entity
@Table(name = "anchor_batches")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AnchorBatch {

    /** 실패분은 다음 회차에서 재시도한다. */
    public enum Status {
        PENDING,
        CONFIRMED,
        FAILED
    }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 회차 커밋들의 머클 루트. 온체인에 올리는 유일한 값이다. */
    @Column(name = "merkle_root", nullable = false, unique = true, length = 66)
    private String merkleRoot;

    @Column(name = "commit_count", nullable = false)
    private int commitCount;

    /** 앵커 트랜잭션. 전송 전에는 NULL이다. */
    @Column(name = "tx_hash", length = 66)
    private String txHash;

    /** 확정 블록. 확정 전에는 NULL이다. */
    @Column(name = "block_number")
    private Long blockNumber;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 12)
    private Status status;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "confirmed_at")
    private Instant confirmedAt;
}
