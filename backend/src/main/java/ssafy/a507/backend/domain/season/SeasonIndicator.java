package ssafy.a507.backend.domain.season;

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
import jakarta.persistence.UniqueConstraint;
import java.math.BigDecimal;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** 시즌 거시 지표. 차트 재료다. */
@Entity
@Table(
        name = "season_indicators",
        uniqueConstraints =
                @UniqueConstraint(
                        name = "uq_season_indicators_season_day_code",
                        columnNames = {"season_id", "game_day", "code"}))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class SeasonIndicator {

    public enum Code {
        KOSPI,
        USDKRW,
        RATE,
        OIL,
        VIX
    }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "season_id", nullable = false)
    private Season season;

    @Column(name = "game_day", nullable = false)
    private int gameDay;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private Code code;

    /** value는 H2 예약어라 인용부호로 감싼다. */
    @Column(name = "`value`", nullable = false, precision = 14, scale = 4)
    private BigDecimal value;
}
