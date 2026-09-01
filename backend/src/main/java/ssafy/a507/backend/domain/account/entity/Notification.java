package ssafy.a507.backend.domain.account.entity;

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

    /**
     * 알림 발송. 문구는 발송 시점에 굳혀 저장하므로 호출부가 완성된 문장을 넘긴다.
     *
     * <p>{@code title} 100자 · {@code body} 300자는 컬럼 길이 제약이고 이 값들은 사용자
     * 입력(리포트 제목 등)에서 조립된다. 넘겨받은 값을 여기서 자르는 이유는, 길이를 넘기면
     * INSERT 가 실패해 <b>알림 때문에 발행 자체가 롤백</b>되기 때문이다.
     */
    public static Notification create(
            User user, String type, String title, String body, String linkPath) {
        Notification notification = new Notification();
        notification.user = user;
        notification.type = type;
        notification.title = truncate(title, 100);
        notification.body = truncate(body, 300);
        notification.linkPath = truncate(linkPath, 200);
        return notification;
    }

    /**
     * 컬럼 길이에 맞춰 자른다. 서로게이트 쌍은 쪼개지 않는다 — 이모지 한 글자의 앞쪽 절반만
     * 남으면 Postgres 가 짝 없는 서로게이트를 거절해서, 자르기로 막으려던 발행 롤백이 그대로
     * 일어난다. 한 칸 물러서면 이모지 하나가 빠지는 것으로 끝난다.
     */
    private static String truncate(String value, int max) {
        if (value == null || value.length() <= max) {
            return value;
        }
        int end = Character.isHighSurrogate(value.charAt(max - 1)) ? max - 1 : max;
        return value.substring(0, end);
    }
}
