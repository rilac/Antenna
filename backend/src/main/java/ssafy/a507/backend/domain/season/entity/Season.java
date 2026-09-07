package ssafy.a507.backend.domain.season.entity;

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
import java.time.LocalDate;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 모의투자 시즌.
 *
 * <p>가격은 합성하지 않는다 — 시작 시 {@code daily_quotes} 에서 실제 과거 구간을
 * {@code season_prices} 로 복사한 뒤 읽기 전용이다(ERD v0.6). {@code seed} 는 가격이
 * 아니라 <b>종목·구간 선정</b>의 재현 근거다.
 *
 * <p>시기는 참가자에게 공개하지 않는다. {@code base_date} 는 서버 전용이며 어떤 응답에도
 * 싣지 않는다 — 연습은 성격({@code title})과 섹터만 고르고 시기는 서버가 고른다.
 */
@Entity
@Table(name = "seasons")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Season {

    /**
     * 진행 방식·블라인드·보상 정책을 가르는 축.
     *
     * <p>{@code DEMO} 는 관리자에게만 노출한다 — 일반 사용자의 모드 선택에는
     * 연습·대회 두 장만 나온다(설계서 §3 G).
     */
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

    /**
     * 시즌의 성격. "급락 구간"·"실적 발표 구간" 처럼 무슨 장이었는지만 담는다.
     * 연도와 사건 고유명사를 넣지 않는다 — 그것만으로 구간이 특정된다.
     *
     * <p>아래 셋(title·note·theme)과 base_date 는 <b>일부러 nullable</b> 이다. 이 프로젝트에는
     * 마이그레이션 도구가 없고 ddl-auto:update 는 기존 행을 백필하지 못한다 — seasons 에
     * 행이 있는 DB 에 NOT NULL 컬럼을 더하면 기동이 실패한다. 값이 반드시 있어야 한다는
     * 규칙은 관리자 생성 API 가 지킨다(POST /admin/seasons 요청 필수 · API 명세 v0.24).
     *
     * <p>정리 순서 — 기존 행 백필 → NOT NULL 승격. 도구가 들어온 뒤에 한다.
     */
    @Column(length = 40)
    private String title;

    /** 카드 부제 한 줄. G-02a·G-03 이 그대로 표시한다. */
    @Column(length = 120)
    private String note;

    /** 섹터·테마 키. seed 로 종목을 뽑을 때의 후보 조건이다. */
    @Column(length = 20)
    private String theme;

    /** 총 게임일. 종료 조건이다. */
    @Column(name = "length_days", nullable = false)
    private int lengthDays;

    /** 초기 예수금. 전원 공통 출발선이다. */
    @Column(name = "initial_cash", nullable = false, precision = 14, scale = 2)
    private BigDecimal initialCash;

    /**
     * <b>응답에 절대 싣지 않는다.</b> game_day 를 실제 영업일로 바꾸는 서버 전용 열쇠다 —
     * daily_quotes 에서 구간을 떠올 때만 쓴다.
     *
     * <p>참가자에게 연도가 새면 그다음에 무슨 일이 있었는지 아는 상태로 시작해 예측이
     * 아니라 복기가 된다. 연습은 성격(title)과 섹터만 고르고 시기는 서버가 고른다.
     */
    @Column(name = "base_date")
    private LocalDate baseDate;

    /**
     * <b>종목·구간 선정</b>의 재현 근거다. 가격 생성 시드가 아니다 —
     * 가격은 daily_quotes 의 실제 과거 주가를 복사한다(ERD v0.6).
     *
     * <p>같은 seed·theme 면 같은 종목과 같은 구간이 뽑혀 대회 공정성이 보장된다.
     */
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
