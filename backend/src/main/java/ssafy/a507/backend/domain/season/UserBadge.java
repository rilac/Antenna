package ssafy.a507.backend.domain.season;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
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
import org.hibernate.annotations.CreationTimestamp;
import ssafy.a507.backend.domain.account.User;

/** 배지. 계정당 평생 1회라 재획득해도 행이 늘지 않는다. */
@Entity
@Table(
        name = "user_badges",
        uniqueConstraints =
                @UniqueConstraint(
                        name = "uq_user_badges_user_code",
                        columnNames = {"user_id", "badge_code"}))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class UserBadge {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    /** 배지 코드. 이름·설명·달성 조건은 앱 상수로 둔다. */
    @Column(name = "badge_code", nullable = false, length = 24)
    private String badgeCode;

    /** 처음 딴 시즌. 실전 트랙 배지는 NULL이다. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "season_id")
    private Season season;

    /** 프로필 전시 여부. */
    @Column(nullable = false)
    private boolean pinned;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;
}
