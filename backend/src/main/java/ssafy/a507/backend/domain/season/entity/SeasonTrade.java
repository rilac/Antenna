package ssafy.a507.backend.domain.season.entity;

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
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;

/** 체결 내역. append-only이며 수정·삭제하지 않는다. */
@Entity
@Table(name = "season_trades")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class SeasonTrade {

    public enum Side {
        BUY,
        SELL
    }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "participant_id", nullable = false)
    private SeasonParticipant participant;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "ticker_id", nullable = false)
    private SeasonTicker ticker;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 4)
    private Side side;

    @Column(nullable = false)
    private int qty;

    /** 체결가는 그 게임일 종가다. 슬리피지를 두지 않는다. */
    @Column(nullable = false, precision = 14, scale = 2)
    private BigDecimal price;

    @Column(name = "game_day", nullable = false)
    private int gameDay;

    /**
     * 실현손익 = (체결가 − 평단) × 수량. 매도에만 있고 매수는 NULL 이다(ERD v0.9).
     * 체결 시점의 평단으로 굳혀 둔다 — 포지션이 지워진 뒤에는 다시 구할 수 없다.
     */
    @Column(name = "realized_pnl", precision = 14, scale = 2)
    private BigDecimal realizedPnl;

    /** 요청 시각. 같은 게임일 안의 순서를 가른다. */
    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    public static SeasonTrade of(
            SeasonParticipant participant,
            SeasonTicker ticker,
            Side side,
            int qty,
            BigDecimal price,
            int gameDay,
            BigDecimal realizedPnl) {
        SeasonTrade t = new SeasonTrade();
        t.participant = participant;
        t.ticker = ticker;
        t.side = side;
        t.qty = qty;
        t.price = price;
        t.gameDay = gameDay;
        t.realizedPnl = realizedPnl;
        return t;
    }
}
