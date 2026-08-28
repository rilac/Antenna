package ssafy.a507.backend.domain.market.entity;

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
import java.time.Instant;
import java.time.LocalDate;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 일봉 OHLCV. 공공데이터포털에서 날짜별로 수집해 (종목, 영업일)로 upsert한다.
 * 주봉·월봉은 이 표를 GROUP BY로 집계하며 별도 테이블을 두지 않는다.
 */
@Entity
@Table(
        name = "daily_quotes",
        uniqueConstraints =
                @UniqueConstraint(
                        name = "uq_daily_quotes_stock_date",
                        columnNames = {"stock_code", "trade_date"}))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class DailyQuote {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "stock_code", nullable = false)
    private Stock stock;

    /** 만기일이 휴장이면 이 값이 settle_date 이상인 첫 행을 판정 대상으로 삼는다. */
    @Column(name = "trade_date", nullable = false)
    private LocalDate tradeDate;

    /** 캔들 표시용. 판정에는 쓰지 않는다. */
    @Column(name = "open", precision = 14, scale = 2)
    private BigDecimal open;

    @Column(name = "high", precision = 14, scale = 2)
    private BigDecimal high;

    @Column(name = "low", precision = 14, scale = 2)
    private BigDecimal low;

    /** 판정·표시·부트스트랩이 쓰는 유일한 판정 가격. */
    @Column(name = "close", nullable = false, precision = 14, scale = 2)
    private BigDecimal close;

    /** 거래량(주). 거래대금은 저장하지 않는다. */
    @Column(name = "volume")
    private Long volume;

    /** 수집(upsert) 시각. 장애 추적용. */
    @Column(name = "collected_at", nullable = false)
    private Instant collectedAt;
}
