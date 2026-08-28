package ssafy.a507.backend.domain.chain.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.math.BigInteger;
import java.time.Instant;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import ssafy.a507.backend.domain.account.entity.User;

/** ANT 토큰 원장. append-only이며 잔액은 SUM(delta)로 구한다. */
@Entity
@Table(name = "token_ledger")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class TokenLedger {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    /** 부호 있는 wei 증감. */
    @Column(nullable = false, precision = 30, scale = 0)
    private BigInteger delta;

    /**
     * 토큰 사용처. 환금·충전 값은 목록에 두지 않는다(전자금융거래법 대응).
     * 금액표(D4)가 아직 열려 있어 값 어휘를 enum으로 굳히지 않았다.
     */
    @Column(nullable = false, length = 32)
    private String reason;

    /** 근거 온체인 이벤트. tx로 역추적한다. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "chain_event_id")
    private ChainEvent chainEvent;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;
}
