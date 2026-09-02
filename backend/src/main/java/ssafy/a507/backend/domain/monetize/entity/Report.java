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
import java.time.Instant;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.ColumnDefault;
import org.hibernate.annotations.CreationTimestamp;
import ssafy.a507.backend.domain.account.entity.User;

/** 예측자 리포트. 목록(제목)은 미구독자에게도 보여 구독 유인으로 쓴다. */
@Entity
@Table(name = "reports")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Report {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(nullable = false, length = 100)
    private String title;

    /** 20000자 상한은 애플리케이션에서 검증한다. */
    @Column(columnDefinition = "text")
    private String body;

    /** true면 본문 전체 공개, false면 미구독자에게 앞 3줄만 보여 준다. */
    @Column(name = "is_public", nullable = false)
    private boolean isPublic;

    /**
     * 열람 수. {@code GET /reports/{id}} 에서 증가하며 {@code sort=POPULAR} 정렬 키다(ERD v5).
     *
     * <p>증가는 이 필드에 직접 쓰지 않고 리포지토리의 UPDATE 로 처리한다 — 읽어서 +1 해
     * 저장하면 같은 리포트를 동시에 연 두 요청 중 하나가 사라진다.
     *
     * <p>DB 기본값 0 을 함께 박는 이유 — 이 컬럼은 나중에 추가된 NOT NULL 컬럼이다.
     * {@code ddl-auto: update} 는 기존 행이 있는 테이블에 기본값 없는 NOT NULL 컬럼을 붙일 수
     * 없고, 컬럼을 명시하지 않는 SQL(테스트의 native insert 등)도 실패한다.
     */
    @ColumnDefault("0")
    @Column(name = "view_count", nullable = false)
    private int viewCount;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    /** 리포트 발행. 본문 길이 상한과 공개 범위는 호출부(요청 DTO)가 이미 검증했다. */
    public static Report create(User user, String title, String body, boolean isPublic) {
        Report report = new Report();
        report.user = user;
        report.title = title;
        report.body = body;
        report.isPublic = isPublic;
        report.viewCount = 0;
        return report;
    }
}
