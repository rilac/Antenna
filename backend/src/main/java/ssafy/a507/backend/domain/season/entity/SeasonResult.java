package ssafy.a507.backend.domain.season.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.MapsId;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** 시즌 성적. 참가자와 PK를 공유하며 종료 시 1회 생성한 뒤 불변이다. */
@Entity
@Table(name = "season_results")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class SeasonResult {

    @Id
    @Column(name = "participant_id")
    private Long participantId;

    @MapsId
    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "participant_id")
    private SeasonParticipant participant;

    /** 종료 시 총자산(예수금 + 평가금액). */
    @Column(name = "final_asset", nullable = false, precision = 14, scale = 2)
    private BigDecimal finalAsset;

    @Column(name = "return_rate", nullable = false, precision = 8, scale = 3)
    private BigDecimal returnRate;

    /** 같은 구간 벤치마크 수익률. 비교 차트 재료다. */
    @Column(name = "benchmark_return", precision = 8, scale = 3)
    private BigDecimal benchmarkReturn;

    @Column(name = "max_drawdown", precision = 8, scale = 3)
    private BigDecimal maxDrawdown;

    @Column(name = "win_rate", precision = 5, scale = 2)
    private BigDecimal winRate;

    /** 손익비. 손실이 0이면 정의할 수 없어 NULL로 둔다. */
    @Column(name = "profit_factor", precision = 8, scale = 3)
    private BigDecimal profitFactor;

    /** 평균 보유일(게임일 기준). */
    @Column(name = "avg_holding_days", precision = 6, scale = 2)
    private BigDecimal avgHoldingDays;

    /** 랭킹 점수. rankings가 읽는 입력값이다. */
    @Column(precision = 8, scale = 3)
    private BigDecimal score;

    /** S~D 등급. 산정 기준은 코드에 둔다. */
    @Column(length = 2)
    private String grade;

    /** AI 복기 리포트 본문. NULL이면 생성 전이다. */
    @Column(name = "review_body", columnDefinition = "text")
    private String reviewBody;

    @Column(name = "reviewed_at")
    private Instant reviewedAt;

    @Column(name = "prompt_version", length = 16)
    private String promptVersion;

    @Column(name = "closed_at")
    private Instant closedAt;

    /**
     * 종료 시 1회 생성. 점수·등급·AI 복기는 비워 둔다 — 등급 산식은 미확정이고 복기는
     * ANT-SEASON-09 가 채운다.
     */
    public static SeasonResult of(
            SeasonParticipant participant,
            BigDecimal finalAsset,
            BigDecimal returnRate,
            BigDecimal benchmarkReturn,
            BigDecimal maxDrawdown,
            BigDecimal winRate,
            BigDecimal profitFactor,
            BigDecimal avgHoldingDays) {
        SeasonResult r = new SeasonResult();
        r.participant = participant;
        r.finalAsset = finalAsset;
        r.returnRate = returnRate;
        r.benchmarkReturn = benchmarkReturn;
        r.maxDrawdown = maxDrawdown;
        r.winRate = winRate;
        r.profitFactor = profitFactor;
        r.avgHoldingDays = avgHoldingDays;
        r.closedAt = Instant.now();
        return r;
    }

    /**
     * AI 복기를 채운다(ANT-SEASON-09). 종료 시 성적표와 함께 저장되는 것이 보통이고, 키가 없어
     * 비운 채 끝난 회차는 다음 finish 호출이 채운다. promptVersion 은 문장을 만든 세대 태그다.
     */
    public void review(String body, String promptVersion) {
        this.reviewBody = body;
        this.reviewedAt = Instant.now();
        this.promptVersion = promptVersion;
    }
}
