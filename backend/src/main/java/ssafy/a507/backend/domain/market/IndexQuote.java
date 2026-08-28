package ssafy.a507.backend.domain.market;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.math.BigDecimal;
import java.time.LocalDate;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** 지수·환율 종가. 종류가 셋뿐이라 마스터 테이블 없이 enum으로 제한한다. */
@Entity
@Table(
        name = "index_quotes",
        uniqueConstraints =
                @UniqueConstraint(
                        name = "uq_index_quotes_code_date",
                        columnNames = {"index_code", "trade_date"}))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class IndexQuote {

    public enum IndexCode {
        KOSPI,
        KOSDAQ,
        USDKRW
    }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(name = "index_code", nullable = false, length = 16)
    private IndexCode indexCode;

    @Column(name = "trade_date", nullable = false)
    private LocalDate tradeDate;

    /** 종가 또는 종가 환율. scale 4는 환율 때문이다. */
    @Column(name = "close", nullable = false, precision = 14, scale = 4)
    private BigDecimal close;
}
