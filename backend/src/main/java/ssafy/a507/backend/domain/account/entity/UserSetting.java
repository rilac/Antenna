package ssafy.a507.backend.domain.account.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.MapsId;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import java.time.Instant;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.UpdateTimestamp;

/** 알림·표시 설정. users와 PK를 공유하며, 행이 없으면 전부 기본값이다. */
@Entity
@Table(name = "user_settings")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class UserSetting {

    public enum ChartView {
        BEGINNER,
        ADVANCED
    }

    @Id
    @Column(name = "user_id")
    private Long userId;

    @MapsId
    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id")
    private User user;

    /** 판정 결과(HIT/MISS) 알림. */
    @Column(name = "notify_verdict", nullable = false)
    private boolean notifyVerdict;

    /** 시즌 진행·종료·보상 알림. */
    @Column(name = "notify_season", nullable = false)
    private boolean notifySeason;

    /** 구독 발생·구독료 변경 알림. */
    @Column(name = "notify_social", nullable = false)
    private boolean notifySocial;

    @Enumerated(EnumType.STRING)
    @Column(name = "chart_view", nullable = false, length = 10)
    private ChartView chartView;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private Instant updatedAt;
}
