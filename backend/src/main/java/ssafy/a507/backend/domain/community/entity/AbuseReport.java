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
import jakarta.persistence.UniqueConstraint;
import java.time.Instant;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.Check;
import org.hibernate.annotations.CreationTimestamp;
import ssafy.a507.backend.domain.account.entity.User;

/**
 * 신고. 대상 셋(글·댓글·유저) 중 정확히 하나만 채워야 하고 자기 신고는 막는다.
 *
 * 두 규칙 다 CHECK 로 DB 에 건다. 애플리케이션 쪽 정적 팩터리·서비스 검사가
 * 이미 막고 있지만, 배치나 직접 SQL 처럼 그 경로를 타지 않는 쓰기가 언제든 생긴다.
 *
 * 자기 신고 CHECK 는 유저 신고만 덮는다 — 글·댓글의 작성자는 다른 테이블에 있어
 * CHECK 로는 볼 수 없다. 그쪽은 AbuseReportService 가 유일한 방어선이다.
 *
 * 중복 신고는 유니크 제약으로 막는다. 대상 컬럼이 NULL 인 행끼리는 Postgres 가
 * 서로 다른 값으로 보므로 종류가 다른 신고끼리는 충돌하지 않는다.
 * status 를 키에 넣어 관리자가 처리한 뒤의 재신고는 새 사안으로 받는다.
 */
@Entity
@Table(
        name = "abuse_reports",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uk_abuse_reports_reporter_post_status",
                        columnNames = {"reporter_id", "target_post_id", "status"}),
                @UniqueConstraint(
                        name = "uk_abuse_reports_reporter_comment_status",
                        columnNames = {"reporter_id", "target_comment_id", "status"}),
                @UniqueConstraint(
                        name = "uk_abuse_reports_reporter_user_status",
                        columnNames = {"reporter_id", "target_user_id", "status"})
        })
// Postgres 의 num_nonnulls 가 짧지만 H2 에 없어 테스트가 통째로 깨진다. CASE 합산은 양쪽에서 돈다.
@Check(
        name = "ck_abuse_reports_exactly_one_target",
        constraints = "(case when target_post_id is null then 0 else 1 end"
                + " + case when target_comment_id is null then 0 else 1 end"
                + " + case when target_user_id is null then 0 else 1 end) = 1")
@Check(
        name = "ck_abuse_reports_no_self_report",
        constraints = "target_user_id is null or target_user_id <> reporter_id")
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
