package ssafy.a507.backend.domain.prediction.entity;

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
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.Check;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;
import ssafy.a507.backend.domain.account.entity.User;
import ssafy.a507.backend.domain.common.Track;
import ssafy.a507.backend.domain.market.entity.Stock;
import ssafy.a507.backend.domain.season.entity.SeasonTicker;

/**
 * 예측. 방향 + 목표가 + 기간 + 근거로 이뤄지며 보합과 확신도는 두지 않는다.
 *
 * <p>REAL 트랙은 날짜(base_date/settle_date)로, REPLAY 트랙은 게임일
 * (base_game_day/settle_game_day)로 돈다. 시즌 시세에는 커닝 차단 때문에 실제 날짜가 없다.
 *
 * <p>공개 범위는 사용자가 고르는 값이 아니라 status가 결정한다.
 * HIT/MISS는 내용 전체 공개, BASE/OPEN은 구독자만 볼 수 있고 비구독자에게는 존재와 커밋만 보인다.
 */
@Entity
@Table(name = "predictions")
// 대상·기간 규칙은 앱 검증이 놓쳐도 DB 가 막는다. 값 집합은 ERD v0.4(REAL 캘린더 일수 7/14/30/90) 기준.
@Check(
        name = "ck_predictions_target_matches_track",
        constraints = "(track = 'REAL' and stock_code is not null and season_ticker_id is null)"
                + " or (track = 'REPLAY' and season_ticker_id is not null and stock_code is null)")
@Check(
        name = "ck_predictions_horizon",
        constraints = "(track = 'REAL' and horizon in (7, 14, 30, 90))"
                + " or (track = 'REPLAY' and horizon > 0)")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Prediction {

    /** 방향을 걸지 않는 예측은 예측이 아니라 보합을 두지 않는다. */
    public enum Direction {
        UP,
        DOWN
    }

    /** BASE → OPEN → HIT/MISS. REAL은 영업일 배치가, REPLAY는 게임일 진행이 전이시킨다. */
    public enum Status {
        BASE,
        OPEN,
        HIT,
        MISS
    }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 8)
    private Track track;

    /** REAL 트랙 대상. seasonTicker와 둘 중 하나만 채운다. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "stock_code")
    private Stock stock;

    /** REPLAY 트랙 대상(블라인드 종목). */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "season_ticker_id")
    private SeasonTicker seasonTicker;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 4)
    private Direction direction;

    /** 커밋 payload 구성 요소라 등록 후 바뀌지 않는다. */
    @Column(name = "target_price", nullable = false, precision = 14, scale = 2)
    private BigDecimal targetPrice;

    /** 등록 때 본 직전 종가 박제. 표시용이며 기준가가 아니다. */
    @Column(name = "ref_close", precision = 14, scale = 2)
    private BigDecimal refClose;

    /** REAL은 캘린더 일수 7/14/30/90, REPLAY는 게임일 수. 단위가 트랙마다 다르다. */
    @Column(nullable = false)
    private short horizon;

    /** 기준가 확정 영업일. REAL 전용이며 등록 시 NULL, 배치가 채운다. */
    @Column(name = "base_date")
    private LocalDate baseDate;

    /** 기준가 확정 게임일. REPLAY 전용이며 등록 다음 게임일이다. */
    @Column(name = "base_game_day")
    private Integer baseGameDay;

    /** 기준일 종가 = 판정 출발점. 두 트랙 공용이다. */
    @Column(name = "base_price", precision = 14, scale = 2)
    private BigDecimal basePrice;

    /** 만기일 = base_date + horizon 일. 등록 즉시 확정되므로 D-day 표시가 정확하다. */
    @Column(name = "settle_date")
    private LocalDate settleDate;

    /** 만기 게임일 = base_game_day + horizon. 게임일 진행이 이 값을 넘기면 판정한다. */
    @Column(name = "settle_game_day")
    private Integer settleGameDay;

    /** REAL은 settle_date 종가(휴장이면 그 이후 첫 거래일), REPLAY는 settle_game_day 종가. */
    @Column(name = "settle_price", precision = 14, scale = 2)
    private BigDecimal settlePrice;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 8)
    private Status status;

    /** 목표가 오차 %. 랭킹 점수의 입력 3종 중 하나다. */
    @Column(name = "error_rate", precision = 6, scale = 3)
    private BigDecimal errorRate;

    /** 피드 정렬 키이자 커밋의 시간 증거. */
    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    /** 마지막 상태 전이 시각. 내용 변경 시각이 아니다. */
    @UpdateTimestamp
    @Column(name = "updated_at")
    private Instant updatedAt;

    /**
     * 실전(REAL) 예측 등록 (ANT-PRED-01). 상태는 BASE 로 시작하고 기준가(base_price)는 배치(PRED-03)가 채운다.
     *
     * <p>{@code baseDate}·{@code settleDate} 를 등록 때 확정하는 이유: 판정 배치가 "base_date 도래분" 을 찾고, 화면이 D-day 를
     * 세야 한다. 둘 다 비워 두면 배치는 뭘 잡을지 모르고 D-day 는 못 그린다. {@code refClose} 는 등록 때 본 직전 종가의 박제 —
     * 방향·목표가 모순 검사의 기준이며 기준가가 아니다.
     */
    public static Prediction register(
            User user,
            Stock stock,
            Direction direction,
            BigDecimal targetPrice,
            BigDecimal refClose,
            short horizon,
            LocalDate baseDate,
            LocalDate settleDate) {
        Prediction p = new Prediction();
        p.user = user;
        p.track = Track.REAL;
        p.stock = stock;
        p.direction = direction;
        p.targetPrice = targetPrice;
        p.refClose = refClose;
        p.horizon = horizon;
        p.baseDate = baseDate;
        p.settleDate = settleDate;
        p.status = Status.BASE;
        return p;
    }
}
