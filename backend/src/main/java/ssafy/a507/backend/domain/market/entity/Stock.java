package ssafy.a507.backend.domain.market.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** 종목 마스터. 6자리 종목코드를 불변 자연키로 쓴다. */
@Entity
@Table(name = "stocks")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Stock {

    /**
     * 상장 시장. 공공데이터 「주식시세정보」 응답의 {@code mrktCtg} 를 그대로 받는다.
     *
     * <p>KONEX 는 명세 §홈·시세의 GET /stocks 필터 대상(KOSPI·KOSDAQ)이 아니지만 응답에는
     * 섞여 온다. 값으로 인정하지 않으면 수집 배치가 통째로 깨지므로 여기에 둔다 —
     * 걸러내는 것은 조회 쪽 책임이다.
     */
    public enum Market {
        KOSPI,
        KOSDAQ,
        KONEX
    }

    @Id
    @Column(length = 6)
    private String code;

    @Column(nullable = false, length = 60)
    private String name;

    /** KRX 업종분류 CSV로 시드한다. */
    @Column(length = 30)
    private String sector;

    /**
     * 상장 시장. ERD 에는 없던 칸이다 — GET /stocks 가 market 으로 필터하고 응답에도 싣는데
     * 파생시킬 원천이 없어 수집 시점에 적재한다. CSV 시드로 들어온 기존 행은 값이 빌 수 있어
     * NOT NULL 을 걸지 않는다.
     */
    @Enumerated(EnumType.STRING)
    @Column(length = 8)
    private Market market;

    /** 상장 여부. 폐지되면 false로 두고 행은 지우지 않는다. */
    @Column(nullable = false)
    private boolean listed;

    /**
     * 상장주식수. 「주식시세정보」의 {@code lstgStCnt} 를 회차마다 덮는다 — 증자·감자 때만 바뀐다.
     * PER·PBR 의 분모(EPS·BPS)에 쓰는 유일한 재료라, 없으면 두 지표가 null 이다.
     */
    @Column(name = "listed_shares")
    private Long listedShares;

    /**
     * PER·PBR 파생값. 명세 §홈·시세 — "PER 은 corp_financials × 전일 종가 파생값이므로 필터·정렬을
     * 걸려면 파생 컬럼으로 적재해야 한다". 종가·재무가 바뀐 직후 배치가 다시 쓴다
     * ({@code MarketUpsertRepository.refreshValuations}). 재료가 하나라도 없거나 적자·자본잠식이면 null.
     */
    @Column(precision = 12, scale = 2)
    private BigDecimal per;

    @Column(precision = 12, scale = 2)
    private BigDecimal pbr;
}
