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
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import ssafy.a507.backend.domain.market.entity.Stock;

/**
 * 시즌 종목. 실명이다(ERD v0.8) — "A사" 가명은 걷어냈다. 종목이 누구인지 모르면 업종 사이의
 * 연관이나 실적 같은 공부가 성립하지 않는다. 숨기는 것은 실제 날짜뿐이다.
 */
@Entity
@Table(
        name = "season_tickers",
        uniqueConstraints =
                @UniqueConstraint(
                        name = "uq_season_tickers_season_name",
                        columnNames = {"season_id", "display_name"}))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class SeasonTicker {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "season_id", nullable = false)
    private Season season;

    @Column(name = "display_name", nullable = false, length = 20)
    private String displayName;

    /** 원본 종목. 종목코드·종목명은 응답에 실어도 된다(검색 재료). 실제 날짜는 여기서 나오지 않는다. */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "real_stock_code", nullable = false)
    private Stock realStock;

    /** 종목의 KRX 업종명. 진행 화면의 업종 필터 재료다. 시즌 생성 시점 값을 복제해 둔다. */
    @Column(length = 30)
    private String sector;

    /**
     * 시즌 종목 하나. {@code displayName} 은 종목의 실제 이름이다(v0.8).
     *
     * <p>{@code sector} 는 참가자에게 보여 주는 유일한 힌트다. 좁게 담으면 실제 주가와
     * 맞물려 종목이 추정되므로 상위 분류로만 담는다(ERD v0.6).
     */
    public static SeasonTicker of(Season season, String displayName, Stock realStock, String sector) {
        SeasonTicker ticker = new SeasonTicker();
        ticker.season = season;
        ticker.displayName = displayName;
        ticker.realStock = realStock;
        ticker.sector = sector;
        return ticker;
    }
}
