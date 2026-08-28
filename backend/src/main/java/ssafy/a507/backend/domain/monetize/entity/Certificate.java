package ssafy.a507.backend.domain.monetize.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import ssafy.a507.backend.domain.account.entity.User;

/** 검증 증명서(ERC-721 SBT). 발급 시점 지표를 스냅샷으로 굳혀 둔다. */
@Entity
@Table(name = "certificates")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Certificate {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    /** 컨트랙트가 순차 발행하는 tokenId. */
    @Column(name = "token_id", nullable = false, unique = true)
    private Long tokenId;

    @Column(name = "verified_count", nullable = false)
    private int verifiedCount;

    @Column(name = "hit_rate", precision = 5, scale = 2)
    private BigDecimal hitRate;

    @Column(name = "avg_error", precision = 6, scale = 3)
    private BigDecimal avgError;

    @Column(name = "tx_hash", length = 66)
    private String txHash;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;
}
