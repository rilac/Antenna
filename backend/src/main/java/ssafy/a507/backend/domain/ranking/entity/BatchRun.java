package ssafy.a507.backend.domain.ranking.entity;

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
import java.time.LocalDate;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import ssafy.a507.backend.domain.chain.entity.AnchorBatch;

/** 영업일 배치 실행 기록. business_date 유니크가 중복 실행을 막는다. */
@Entity
@Table(name = "batch_runs")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class BatchRun {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "business_date", nullable = false, unique = true)
    private LocalDate businessDate;

    /** 기준가 확정(OPEN 전환) 건수. */
    @Column(name = "opened_count", nullable = false)
    private int openedCount;

    @Column(name = "verified_count", nullable = false)
    private int verifiedCount;

    @Column(name = "hit_count", nullable = false)
    private int hitCount;

    /** 그날의 머클 앵커. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "anchor_batch_id")
    private AnchorBatch anchorBatch;

    /** 실행 결과. 실패분은 다음 회차에서 재시도한다. */
    @Column(nullable = false, length = 12)
    private String status;

    @Column(name = "started_at", nullable = false)
    private Instant startedAt;

    @Column(name = "finished_at")
    private Instant finishedAt;

    /** 실행 중. 회차가 끝나기 전에 앱이 죽으면 이 값이 남는다 — 다음 회차가 같은 행을 이어받아 덮는다. */
    public static final String RUNNING = "RUNNING";

    /** 전 건이 예외 없이 끝났다. 보류(시세 없음)는 실패가 아니다. */
    public static final String SUCCESS = "SUCCESS";

    /** 한 건 이상이 예외로 넘어갔다. 나머지는 저장됐고, 넘어간 건은 다음 회차가 다시 집는다. */
    public static final String PARTIAL = "PARTIAL";

    /**
     * 그날 회차를 연다 (ANT-PRED-03·04). 카운터는 0 에서 시작하고 실행 중에 더해진다.
     *
     * <p>{@code anchorBatch} 는 비워 둔다. 앵커는 13:30 이 아니라 00:05 에 따로 돌고(ANT-CHAIN-02, 결정 A4)
     * 그쪽이 이 표를 쓰지 않는다. ERD 의 "그날의 머클 앵커" 는 두 배치가 한 몸이던 시절의 표기다.
     */
    public static BatchRun start(LocalDate businessDate, Instant startedAt) {
        BatchRun run = new BatchRun();
        run.businessDate = businessDate;
        run.status = RUNNING;
        run.startedAt = startedAt;
        return run;
    }

    /**
     * 회차 결과를 <b>더한다</b>. 대입이 아니다 — 같은 날 다시 돌리면 {@code business_date} UNIQUE 때문에 행을 새로 만들 수 없고,
     * 덮어쓰면 오전 회차의 실적이 사라진다. 읽고 싶은 값은 "그날 총합" 이라 누적이 맞다.
     */
    public void add(int opened, int verified, int hit) {
        this.openedCount += opened;
        this.verifiedCount += verified;
        this.hitCount += hit;
    }

    public void finish(String status, Instant finishedAt) {
        this.status = status;
        this.finishedAt = finishedAt;
    }
}
