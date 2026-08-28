package ssafy.a507.backend.domain.chain;

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
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/** 체인 인덱서가 적재하는 온체인 이벤트 원본. 재시작 커서는 block_number 최대값이다. */
@Entity
@Table(
        name = "chain_events",
        uniqueConstraints =
                @UniqueConstraint(
                        name = "uq_chain_events_tx_log",
                        columnNames = {"tx_hash", "log_index"}))
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
}
