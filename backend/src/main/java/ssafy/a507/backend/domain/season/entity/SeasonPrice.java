package ssafy.a507.backend.domain.season.entity;

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

/** 시즌 가격. 커닝 차단 때문에 실제 날짜가 없고 게임일 인덱스만 둔다. */
@Entity
@Table(
        name = "season_prices",
        uniqueConstraints =
                @UniqueConstraint(
                        name = "uq_season_prices_ticker_day",
                        columnNames = {"ticker_id", "game_day"}))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class SeasonPrice {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "ticker_id", nullable = false)
    private SeasonTicker ticker;

    @Column(name = "game_day", nullable = false)
    private int gameDay;

    /** 시작 시 seed로 일괄 생성한 뒤 읽기 전용이다. */
    @Column(name = "close", nullable = false, precision = 14, scale = 2)
    private BigDecimal close;
}
