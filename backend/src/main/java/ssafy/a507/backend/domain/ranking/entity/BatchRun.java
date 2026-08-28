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
}
