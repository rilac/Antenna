package ssafy.a507.backend.domain.season.entity;

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
import java.math.BigDecimal;
import java.time.Instant;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import ssafy.a507.backend.domain.account.entity.User;

/** 시즌 참가자. 대회는 attempt_no를 1로 고정하고 연습·시연에서만 회차가 늘어난다. */
@Entity
@Table(
        name = "season_participants",
        uniqueConstraints =
                @UniqueConstraint(
                        name = "uq_season_participants_season_user_attempt",
                        columnNames = {"season_id", "user_id", "attempt_no"}))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class SeasonParticipant {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "season_id", nullable = false)
    private Season season;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    /** 시도 회차. 회차별 기록이 따로 남는다. */
    @Column(name = "attempt_no", nullable = false)
    private short attemptNo;

    /** 현재 예수금. 매수하면 줄고 매도하면 는다. */
    @Column(nullable = false, precision = 14, scale = 2)
    private BigDecimal cash;

    /** 개인 진행 게임일. 연습 모드 이어하기 저장점이다. */
    @Column(name = "current_day", nullable = false)
    private int currentDay;

    /** 참가비 소각 tx. 연습(PRACTICE)은 NULL이다. */
    @Column(name = "entry_tx_hash", length = 66)
    private String entryTxHash;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;
}
