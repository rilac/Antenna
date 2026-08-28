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
import java.time.Instant;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import ssafy.a507.backend.domain.account.entity.User;

/** 배너 광고. 메인 배너 자리가 하나라 기간 중복은 애플리케이션에서 막는다. */
@Entity
@Table(name = "ad_banners")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AdBanner {

    /** 관리자 승인 후에만 노출한다. */
    public enum Status {
        PENDING,
        ACTIVE,
        REJECTED,
        ENDED
    }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "advertiser_id", nullable = false)
    private User advertiser;

    /** https만 허용한다. */
    @Column(name = "image_url", nullable = false, length = 500)
    private String imageUrl;

    /** 클릭 시 이동 주소. 피싱 차단을 위해 https만 허용한다. */
    @Column(name = "link_url", nullable = false, length = 500)
    private String linkUrl;

    @Column(name = "starts_at", nullable = false)
    private Instant startsAt;

    /** 지나면 노출 쿼리에서 자동 제외된다. */
    @Column(name = "ends_at", nullable = false)
    private Instant endsAt;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private Status status;

    /** 게재료 소각 트랜잭션. */
    @Column(name = "tx_hash", length = 66)
    private String txHash;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;
}
