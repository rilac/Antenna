package ssafy.a507.backend.domain.community.entity;

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

/**
 * 신고. 대상 셋(글·댓글·유저) 중 정확히 하나만 채워야 하고 자기 신고는 막는다.
 * 두 규칙 다 CHECK 제약이라 마이그레이션에서 건다.
 */
@Entity
@Table(name = "abuse_reports")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AbuseReport {

    public enum Reason {
        SPAM,
        ABUSE,
        FRAUD,
        ETC
    }

    /** ACCEPTED면 대상을 BLOCKED로 내린다. */
    public enum Status {
        PENDING,
        ACCEPTED,
        REJECTED
    }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "reporter_id", nullable = false)
    private User reporter;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "target_post_id")
    private FeedPost targetPost;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "target_comment_id")
    private PostComment targetComment;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "target_user_id")
    private User targetUser;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 12)
    private Reason reason;

    /** 상세 사유. 선택 입력이다. */
    @Column(length = 300)
    private String detail;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private Status status;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "handled_by")
    private User handledBy;

    @Column(name = "handled_at")
    private Instant handledAt;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    /*
     * 대상별 정적 팩터리 셋. 생성 경로를 셋으로 나눠두면
     * "대상 셋 중 정확히 하나"를 런타임 검사 없이 타입 수준에서 지킬 수 있다.
     */

    public static AbuseReport againstPost(User reporter, FeedPost post, Reason reason, String detail) {
        AbuseReport report = newPending(reporter, reason, detail);
        report.targetPost = post;
        return report;
    }

    public static AbuseReport againstComment(User reporter, PostComment comment, Reason reason, String detail) {
        AbuseReport report = newPending(reporter, reason, detail);
        report.targetComment = comment;
        return report;
    }

    public static AbuseReport againstUser(User reporter, User target, Reason reason, String detail) {
        AbuseReport report = newPending(reporter, reason, detail);
        report.targetUser = target;
        return report;
    }

    private static AbuseReport newPending(User reporter, Reason reason, String detail) {
        AbuseReport report = new AbuseReport();
        report.reporter = reporter;
        report.reason = reason;
        report.detail = detail;
        report.status = Status.PENDING;
        return report;
    }
}
