package ssafy.a507.backend.domain.community;

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
import ssafy.a507.backend.domain.account.User;
import ssafy.a507.backend.domain.monetize.Report;
import ssafy.a507.backend.domain.prediction.Prediction;

/** 피드 글. 신고로 가릴 때 status만 바꾸고 행은 지우지 않는다. */
@Entity
@Table(name = "feed_posts")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class FeedPost {

    public enum Status {
        VISIBLE,
        BLOCKED
    }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    /** 5000자 상한은 애플리케이션에서 검증한다. */
    @Column(columnDefinition = "text")
    private String body;

    /** 리포스팅 원본. 구독 게이팅은 원본에서 판정한다. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "report_id")
    private Report report;

    /** 함께 붙인 예측 카드. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "prediction_id")
    private Prediction prediction;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private Status status;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;
}
