package ssafy.a507.backend.domain.ranking.entity;

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
import java.math.BigDecimal;
import java.time.Instant;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import ssafy.a507.backend.domain.account.entity.User;
import ssafy.a507.backend.domain.common.Track;

/** 배치가 미리 계산해 두는 신뢰도 랭킹. 사전 계산해 저장하는 것이 이 표의 존재 이유다. */
@Entity
@Table(
        name = "rankings",
        uniqueConstraints =
                @UniqueConstraint(
                        name = "uq_rankings_track_filter_user",
                        columnNames = {"track", "filter_key", "user_id"}))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Ranking {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 8)
    private Track track;

    /** 기간·섹터·시즌 필터 조합 키. */
    @Column(name = "filter_key", nullable = false, length = 30)
    private String filterKey;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    /**
     * 종합 점수. 입력은 done_count·hit_rate·avg_error 3종뿐이다.
     * 구독자 수는 인기투표로 회귀하므로 넣지 않는다. 가중치 비율은 아직 미정이다.
     */
    @Column(precision = 8, scale = 3)
    private BigDecimal score;

    /** HIT / (HIT + MISS). */
    @Column(name = "hit_rate", precision = 5, scale = 2)
    private BigDecimal hitRate;

    @Column(name = "avg_error", precision = 6, scale = 3)
    private BigDecimal avgError;

    /** 판정 완료 수. 표본 부족 가중 판단에 쓴다. */
    @Column(name = "done_count", nullable = false)
    private int doneCount;

    /** rank는 예약어라 인용부호로 감싼다. */
    @Column(name = "`rank`", nullable = false)
    private int rank;

    /**
     * 직전 스냅샷에서의 순위. 배치가 표를 갈아 끼우기 전에 옮겨 담는다.
     *
     * <p>NULL 은 "직전 회차에 이 필터에 없었다" 는 뜻이다 — 새로 진입했거나 첫 스냅샷이다.
     * 0 을 쓰지 않는 이유는 0 등이라는 순위가 없어서고, 조회는 NULL 을 변동 없음(0)으로 읽는다.
     */
    @Column(name = "prev_rank")
    private Integer prevRank;

    @Column(name = "computed_at", nullable = false)
    private Instant computedAt;
}
