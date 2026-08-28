package ssafy.a507.backend.domain.season;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** 모의투자 시즌. 가격은 시작 시 seed로 일괄 생성하며 시즌 전체가 이 시드로 재현된다. */
@Entity
@Table(name = "seasons")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Season {

    /** 부트스트랩·블라인드·보상 정책을 가르는 축. */
    public enum Mode {
        PRACTICE,
        COMPETITION,
        DEMO
    }

    /** CLOSED 전까지 정답(원본 종목)을 공개하지 않는다. */
    public enum Status {
        SCHEDULED,
        RUNNING,
        CLOSED
    }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private Mode mode;

    /** 총 게임일. 종료 조건이다. */
    @Column(name = "length_days", nullable = false)
    private int lengthDays;

    /** 초기 예수금. 전원 공통 출발선이다. */
    @Column(name = "initial_cash", nullable = false, precision = 14, scale = 2)
    private BigDecimal initialCash;

    /** 블록 부트스트랩 시드. 시즌 가격 전체의 재현 근거다. */
    @Column(nullable = false)
    private Long seed;

    /** 대회 공용 진행일. 공개 가능한 가격·뉴스의 상한이다. */
    @Column(name = "current_day", nullable = false)
    private int currentDay;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private Status status;

    /** 대회 시작 시각(09:00). 대회 외 모드는 NULL이다. */
    @Column(name = "opens_at")
    private Instant opensAt;

    /** 대회 종료 시각(18:00). */
    @Column(name = "closes_at")
    private Instant closesAt;

    /** 1게임일이 흐르는 실시간 간격. 대회는 60분 = 1게임일. */
    @Column(name = "day_interval_minutes")
    private Integer dayIntervalMinutes;
}
