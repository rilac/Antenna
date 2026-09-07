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

    /* 아래 넷은 캔들 표시 전용이다(G-04·G-05). 원천에 없으면 null 이라
       거래량 막대나 심지가 빠질 수 있다 — 화면이 null 을 견뎌야 한다.
       판정·체결은 close 만 쓰므로 close 만 NOT NULL 이다. */

    /** 시가 */
    @Column(name = "open", precision = 14, scale = 2)
    private BigDecimal open;

    /** 고가 */
    @Column(name = "high", precision = 14, scale = 2)
    private BigDecimal high;

    /** 저가 */
    @Column(name = "low", precision = 14, scale = 2)
    private BigDecimal low;

    /**
     * 그 게임일 종가. 체결·판정·표시가 쓰는 유일한 가격이다.
     *
     * <p>seed 로 만들지 않는다 — daily_quotes 에서 실제 과거 종가를 복사한다(ERD v0.6).
     * 시즌 시작 시 일괄 복사한 뒤 읽기 전용이다.
     */
    @Column(name = "close", nullable = false, precision = 14, scale = 2)
    private BigDecimal close;

    /** 거래량(주) · 캔들 하단 막대 */
    @Column(name = "volume")
    private Long volume;

    /**
     * 게임일 하나의 가격. {@code daily_quotes} 한 행을 그대로 옮기되 실제 날짜는 버리고
     * {@code gameDay} 인덱스만 남긴다 — 날짜가 곧 시대 단서다(ERD v0.6).
     *
     * <p>OHLCV 중 close 만 필수다. 나머지는 원천에 없으면 null 로 들어오고 화면이 견딘다.
     */
    public static SeasonPrice of(
            SeasonTicker ticker,
            int gameDay,
            BigDecimal open,
            BigDecimal high,
            BigDecimal low,
            BigDecimal close,
            Long volume) {
        SeasonPrice price = new SeasonPrice();
        price.ticker = ticker;
        price.gameDay = gameDay;
        price.open = open;
        price.high = high;
        price.low = low;
        price.close = close;
        price.volume = volume;
        return price;
    }
}
