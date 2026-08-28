package ssafy.a507.backend.domain.monetize.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
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
import org.hibernate.annotations.UpdateTimestamp;
import ssafy.a507.backend.domain.account.entity.User;

/**
 * 구독. 근거 열람 게이팅의 판정값이다.
 *
 * <p>DDL에 두 제약이 더 필요하다. JPA 애너테이션으로는 표현할 수 없어 마이그레이션에서 건다.
 * <ul>
 *   <li>CHECK (subscriber_id &lt;&gt; publisher_id) — 자기 구독 금지
 *   <li>부분 유니크: (subscriber_id, publisher_id) WHERE status IN ('PENDING','ACTIVE')
 *       — 만료 후 재구독과 자동 갱신은 허용한다
 * </ul>
 */
@Entity
@Table(name = "subscriptions")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Subscription {

    public enum Status {
        PENDING,
        ACTIVE,
        EXPIRED
    }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "subscriber_id", nullable = false)
    private User subscriber;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "publisher_id", nullable = false)
    private User publisher;

    /** 결제 시점 가격 박제(wei). 이후 가격이 바뀌어도 이 행은 그대로다. */
    @Column(nullable = false, precision = 30, scale = 0)
    private BigInteger fee;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private Status status;

    /** 결제 트랜잭션. 70:30 배분 확인 증빙이다. */
    @Column(name = "tx_hash", length = 66)
    private String txHash;

    @Column(name = "started_at")
    private Instant startedAt;

    /** 개시 +30일. 갱신은 기간 연장이 아니라 새 행이다. */
    @Column(name = "expires_at")
    private Instant expiresAt;

    /** true면 만료 배치가 다음 주기 결제를 자동 개시한다. */
    @Column(name = "auto_renew", nullable = false)
    private boolean autoRenew;

    /** 자동 갱신 해지 시각. 남은 기간은 유지하고 다음 주기부터 결제하지 않는다. */
    @Column(name = "canceled_at")
    private Instant canceledAt;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private Instant updatedAt;
}
