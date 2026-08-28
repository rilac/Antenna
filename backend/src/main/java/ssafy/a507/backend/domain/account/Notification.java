package ssafy.a507.backend.domain.account;

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
import org.hibernate.annotations.CreationTimestamp;

/** 알림. 대상별 FK 대신 앱 내 경로 문자열로 이동시킨다. */
@Entity
@Table(name = "notifications")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Notification {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    /** 알림 종류. 아이콘·문구 템플릿 분기 키이며 값 목록이 아직 열려 있어 문자열로 둔다. */
    @Column(nullable = false, length = 24)
    private String type;

    /** 목록 한 줄. 발송 시점 문장을 굳혀 저장한다. */
    @Column(nullable = false, length = 100)
    private String title;

    @Column(length = 300)
    private String body;

    @Column(name = "link_path", length = 200)
    private String linkPath;

    /** 확인 시각. NULL 개수가 곧 미확인 뱃지 숫자다. */
    @Column(name = "read_at")
    private Instant readAt;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;
}
