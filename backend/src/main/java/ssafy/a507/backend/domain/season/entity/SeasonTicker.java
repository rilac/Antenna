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
 * 시즌 종목.
 *
 * <p><b>연습은 실명, 대회는 가명이다</b>(2026-09-07 결정). {@code displayName} 에 연습은
 * "삼성전자" 가, 대회는 "A사" 가 들어간다. 목적이 달라서다 — 연습은 배우는 자리라 실명이
 * 곧 학습이고, 대회는 순위가 걸려 있어 구간을 기억하는 사람이 유리하면 순위가 뜻을 잃는다.
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

    /**
     * 참가자에게 보이는 이름. 연습은 실제 종목명("삼성전자"), 대회는 가명("A사")이다.
     *
     * <p>길이가 20 이라 긴 종목명은 들어가지 않는다 — 수집 범위(KOSPI 300) 안에서는
     * 문제가 없지만 전 종목으로 넓히면 확인이 필요하다.
     */
    @Column(name = "display_name", nullable = false, length = 20)
    private String displayName;

    /**
     * 원본 종목. <b>대회에서는</b> 정답이라 CLOSED 전까지 어떤 응답에도 실으면 안 된다.
     *
     * <p>연습에서는 {@code displayName} 이 이미 실명이라 가릴 것이 없다. 그래도 응답에
     * 담지 않는다 — 응답 계약이 모드마다 갈리면 화면이 두 모양을 다뤄야 한다.
     */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "real_stock_code", nullable = false)
    private Stock realStock;

    /**
     * 섹터 힌트. 값을 복제해 둔다 — 종목 조인 없이 내릴 수 있어야 한다.
     *
     * <p>대회에서는 좁게 담으면 실제 주가와 맞물려 종목이 추정되므로 상위 분류로만 담는다.
     * 연습은 실명이라 그 제약이 없다.
     */
    @Column(length = 30)
    private String sector;

    /**
     * 시즌 종목 하나.
     *
     * @param displayName 연습은 실제 종목명, 대회는 "A사" 같은 가명
     * @param realStock 원본 종목 · 대회에서는 CLOSED 전까지 응답 금지
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
