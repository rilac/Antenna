package ssafy.a507.backend.domain.market.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.time.LocalDate;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 날짜 하나에 대한 수집 회차 기록. 이 표가 "무엇을 아직 못 받았는가"의 단일 원천이다.
 *
 * <p><b>batch_runs 를 쓰지 않는 이유.</b> 그 표는 예측 판정 배치의 것이고 business_date 가
 * 유니크다. 수집이 같은 표를 쓰면 하루 한 행을 두고 판정 배치와 부딪힌다. 칸의 뜻도 다르다 —
 * opened·verified·hit 은 예측 이야기지 시세 이야기가 아니다.
 *
 * <p><b>종결 판정이 두 가지다.</b> 일일 수집은 SUCCESS 만 종결로 본다({@link #isCollected}) —
 * 포털 응답이 비었다는 것만으로는 공휴일인지 아직 공개 전인지 구분할 수 없어, 창 안에 있는
 * 동안은 EMPTY 도 다시 시도한다. 공휴일을 몇 번 더 두드리는 비용은 회차당 1콜이라, 공개가
 * 늦은 날을 영영 놓치는 쪽보다 싸다. 반면 백필은 EMPTY 도 종결로 본다({@link #isSettled}) —
 * 과거의 빈 날은 공휴일로 굳었으므로, 이걸 계속 두드리면 백필이 다 끝난 뒤에도 매 회차
 * 3년치 공휴일 수십 일을 다시 불러 일 콜 한도를 갉아먹는다.
 */
@Entity
@Table(name = "ingest_runs")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class IngestRun {

    public enum Status {
        /** 시작만 기록된 상태. 이 채로 남아 있으면 회차가 도중에 죽은 것이다. */
        RUNNING,
        SUCCESS,
        /** 호출은 됐는데 행이 없다. 공휴일·휴장, 또는 아직 공개 전. */
        EMPTY,
        FAILED
    }

    /** message 컬럼 폭. 원인 요약만 남기고 자세한 것은 로그에 있다. */
    private static final int MAX_MESSAGE_LENGTH = 300;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 수집 대상 영업일(basDt). 유니크라 한 날짜의 회차 기록은 하나로 덮어쓴다. */
    @Column(name = "base_date", nullable = false, unique = true)
    private LocalDate baseDate;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 12)
    private Status status;

    /** 적재한 일봉 건수. */
    @Column(name = "quote_count", nullable = false)
    private int quoteCount;

    /** 몇 번째 시도인지. 같은 날짜가 계속 실패하면 이 숫자가 는다. */
    @Column(name = "attempt_count", nullable = false)
    private int attemptCount;

    @Column(name = "started_at", nullable = false)
    private Instant startedAt;

    @Column(name = "finished_at")
    private Instant finishedAt;

    /** 실패 원인 요약. 성공하면 비운다. */
    @Column(length = MAX_MESSAGE_LENGTH)
    private String message;

    private IngestRun(LocalDate baseDate) {
        this.baseDate = baseDate;
        this.status = Status.RUNNING;
        this.attemptCount = 0;
        this.startedAt = Instant.EPOCH;
    }

    public static IngestRun of(LocalDate baseDate) {
        return new IngestRun(baseDate);
    }

    /** 재시도면 앞 회차의 결과를 지우고 다시 센다 — 마지막 시도의 결과만 남긴다. */
    public void start(Instant at) {
        this.status = Status.RUNNING;
        this.attemptCount++;
        this.startedAt = at;
        this.finishedAt = null;
        this.message = null;
        this.quoteCount = 0;
    }

    public void succeed(int quoteCount, Instant at) {
        this.status = Status.SUCCESS;
        this.quoteCount = quoteCount;
        this.finishedAt = at;
    }

    public void markEmpty(Instant at) {
        this.status = Status.EMPTY;
        this.quoteCount = 0;
        this.finishedAt = at;
    }

    public void fail(String message, Instant at) {
        this.status = Status.FAILED;
        this.finishedAt = at;
        this.message = truncate(message);
    }

    /** 일일 수집이 다시 집지 않아도 되는 날짜인가. */
    public boolean isCollected() {
        return status == Status.SUCCESS;
    }

    /** 백필이 다시 집지 않아도 되는 날짜인가 — 과거의 EMPTY 는 공휴일로 굳었다고 본다. */
    public boolean isSettled() {
        return status == Status.SUCCESS || status == Status.EMPTY;
    }

    private static String truncate(String message) {
        if (message == null) {
            return null;
        }
        return message.length() <= MAX_MESSAGE_LENGTH
                ? message
                : message.substring(0, MAX_MESSAGE_LENGTH);
    }
}
