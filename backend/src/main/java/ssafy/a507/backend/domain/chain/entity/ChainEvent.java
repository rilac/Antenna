package ssafy.a507.backend.domain.chain.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.Instant;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.DynamicUpdate;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * 체인 인덱서가 적재하는 온체인 이벤트 원본. 재시작 커서는 block_number 최대값이다.
 *
 * <p>append-only 트리거(db/append-only.sql, ANT-DB-02)가 {@code processed_at} 외의 UPDATE 를 막는다.
 * {@code @DynamicUpdate} 는 그 약속을 SQL 수준에서도 지키려는 것이다 — Hibernate 기본은 바뀐 컬럼만이 아니라
 * 전 컬럼을 UPDATE 문에 싣는데, payload(jsonb)를 같은 값으로 다시 쓰는 문장이 트리거 비교를 지나가는지에
 * 기대는 것보다 아예 {@code processed_at} 한 컬럼만 보내는 편이 확실하다.
 */
@Entity
@Table(
        name = "chain_events",
        uniqueConstraints =
                @UniqueConstraint(
                        name = "uq_chain_events_tx_log",
                        columnNames = {"tx_hash", "log_index"}))
@DynamicUpdate
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ChainEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "tx_hash", nullable = false, length = 66)
    private String txHash;

    /** tx 안의 로그 순번. tx_hash와 묶어 중복 인덱싱을 막는다. */
    @Column(name = "log_index", nullable = false)
    private int logIndex;

    @Column(name = "contract_address", nullable = false, length = 42)
    private String contractAddress;

    /** Subscribed / Burned / Anchored 등. 후속 처리 분기 키다. */
    @Column(name = "event_name", nullable = false, length = 32)
    private String eventName;

    @Column(name = "block_number", nullable = false)
    private Long blockNumber;

    /** ABI 디코딩 인자 원본. 재처리용이라 스키마를 고정하지 않는다. */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "payload")
    private String payload;

    /** 후속 반영 완료 시각. NULL이면 저장만 되고 아직 반영 전이다. */
    @Column(name = "processed_at")
    private Instant processedAt;

    /**
     * 인덱서(ANT-CHAIN-04)가 로그 하나를 행으로 옮긴다. {@code processed_at} 은 NULL — 같은 트랜잭션에서
     * 도메인 반영이 끝난 뒤 {@link #markProcessed} 로 채운다.
     *
     * @param payload ABI 디코딩 결과 JSON. 인덱서가 재처리·복구에 쓰는 원본이라 형식은 인덱서가 정한다
     */
    public static ChainEvent record(
            String txHash,
            int logIndex,
            String contractAddress,
            String eventName,
            long blockNumber,
            String payload) {
        ChainEvent e = new ChainEvent();
        e.txHash = txHash;
        e.logIndex = logIndex;
        e.contractAddress = contractAddress;
        e.eventName = eventName;
        e.blockNumber = blockNumber;
        e.payload = payload;
        return e;
    }

    /** 도메인 반영 완료. 트리거가 허용하는 유일한 UPDATE 다. 이미 찍혀 있으면 그대로 둔다. */
    public void markProcessed(Instant now) {
        if (this.processedAt == null) {
            this.processedAt = now;
        }
    }

    public boolean isProcessed() {
        return processedAt != null;
    }
}
