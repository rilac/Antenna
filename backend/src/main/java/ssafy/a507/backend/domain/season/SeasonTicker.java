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
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import ssafy.a507.backend.domain.market.Stock;

/** 시즌 종목. 블라인드일 때 참가자에게는 "A사"로만 보인다. */
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

    /** 정답 원본 종목. 시즌이 CLOSED 되기 전까지 어떤 응답에도 실으면 안 된다. */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "real_stock_code", nullable = false)
    private Stock realStock;

    /** 블라인드에도 공개하는 힌트. 정답 누수를 막으려 값을 복제해 둔다. */
    @Column(length = 30)
    private String sector;
}
