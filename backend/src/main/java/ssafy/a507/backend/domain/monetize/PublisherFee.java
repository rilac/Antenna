package ssafy.a507.backend.domain.monetize;

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
import ssafy.a507.backend.domain.account.User;

/** 구독료 이력. 현재가는 최신 행이고 기존 구독은 만료까지 옛 가격을 쓴다. */
@Entity
@Table(name = "publisher_fees")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PublisherFee {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "publisher_id", nullable = false)
    private User publisher;

    @Column(nullable = false, precision = 30, scale = 0)
    private BigInteger fee;

    @Column(name = "effective_from", nullable = false)
    private Instant effectiveFrom;

    /** 변경 시각. 구독자 알림 발송 근거다. */
    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;
}
