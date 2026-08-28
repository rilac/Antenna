package ssafy.a507.backend.domain.season;

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
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** 보유 포지션. 참가자·종목 조합당 1행이고 전량 매도로 수량이 0이 되면 행을 지운다. */
@Entity
@Table(
        name = "season_positions",
        uniqueConstraints =
                @UniqueConstraint(
                        name = "uq_season_positions_participant_ticker",
                        columnNames = {"participant_id", "ticker_id"}))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class SeasonPosition {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "participant_id", nullable = false)
    private SeasonParticipant participant;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "ticker_id", nullable = false)
    private SeasonTicker ticker;

    @Column(nullable = false)
    private int qty;

    /** 평균 매입단가. 실현손익 계산 기준이다. */
    @Column(name = "avg_price", nullable = false, precision = 14, scale = 2)
    private BigDecimal avgPrice;
}
