package ssafy.a507.backend.domain.research.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.math.BigInteger;
import java.time.Instant;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 한 종목·한 회계기간의 주요 재무 계정 (ANT-RESEARCH-01).
 *
 * <p><b>계정별 행(long)이 아니라 계정별 컬럼(wide)이다.</b> DART 주요계정 API 가 주는 계정이
 * 14 개로 고정이고, 이 표를 읽는 쪽(밸류에이션 PER·PBR·ROE·부채비율)이 언제나 여러 계정을
 * 한꺼번에 필요로 한다. long 이면 지표 하나 계산에 피벗이 붙는다.
 *
 * <p>{@code quarter} 는 지금 항상 4(사업보고서)다. 분기 수집을 붙일 자리를 열어 둔 것이고,
 * 그때 UQ 가 그대로 쓰인다 — 나중에 컬럼을 추가하면 기존 행의 유니크가 흔들린다.
 */
@Entity
@Table(
        name = "corp_financials",
        uniqueConstraints =
                @UniqueConstraint(
                        name = "uq_corp_financials_period",
                        columnNames = {"stock_code", "fiscal_year", "quarter"}))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class CorpFinancial {

    /** 사업보고서(연간). 분기를 붙이기 전까지 모든 행이 이 값이다. */
    public static final int ANNUAL_QUARTER = 4;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "stock_code", nullable = false, length = 6)
    private String stockCode;

    @Column(name = "fiscal_year", nullable = false)
    private int fiscalYear;

    /** 1~4. 4 = 사업보고서(연간). */
    @Column(nullable = false)
    private int quarter;

    /**
     * {@code CFS}(연결) 또는 {@code OFS}(별도).
     *
     * <p>화면에 반드시 밝혀야 하는 값이다 — 연결과 별도는 같은 회사의 매출이 배로 차이 나는
     * 일이 흔해서, 어느 쪽인지 모르고 비교하면 엉뚱한 결론이 나온다.
     */
    @Column(name = "fs_div", nullable = false, length = 3)
    private String fsDiv;

    /** 통화. 해외 상장 지주사 등에서 KRW 가 아닌 경우가 있어 숫자와 함께 보관한다. */
    @Column(length = 5)
    private String currency;

    /** 이 숫자가 실린 보고서의 접수번호. "어느 공시에서 왔는가"를 되짚는 유일한 끈이다. */
    @Column(name = "receipt_no", length = 20)
    private String receiptNo;

    /** 계정이 보고서에 없을 수 있어 전부 nullable 이다 — 0 으로 채우면 "실적 0"으로 읽힌다. */
    @Column(precision = 30, scale = 0)
    private BigInteger revenue;

    @Column(name = "operating_profit", precision = 30, scale = 0)
    private BigInteger operatingProfit;

    @Column(name = "net_income", precision = 30, scale = 0)
    private BigInteger netIncome;

    @Column(name = "total_assets", precision = 30, scale = 0)
    private BigInteger totalAssets;

    @Column(name = "total_liabilities", precision = 30, scale = 0)
    private BigInteger totalLiabilities;

    @Column(name = "total_equity", precision = 30, scale = 0)
    private BigInteger totalEquity;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    public static CorpFinancial of(String stockCode, int fiscalYear, int quarter) {
        CorpFinancial financial = new CorpFinancial();
        financial.stockCode = stockCode;
        financial.fiscalYear = fiscalYear;
        financial.quarter = quarter;
        financial.updatedAt = Instant.now();
        return financial;
    }

    /**
     * 값을 갱신한다. 같은 연도를 여러 해에 걸쳐 다시 받게 되는 구조라(한 호출에 3개년이 온다)
     * 재적재가 기본 동작이고, 정정 공시가 나오면 최신 보고서 숫자로 바뀐다.
     *
     * <p><b>들어온 값이 null 이면 기존 값을 지우지 않는다.</b> 같은 연도가 어떤 회차에는 당기로,
     * 다음 회차에는 전전기 자리로 온다. 신규 상장사처럼 그 자리가 비어 오는 회사가 있는데,
     * 그대로 덮으면 이미 받아 둔 재무제표가 재적재 때마다 지워진다.
     */
    public void update(
            String fsDiv,
            String currency,
            String receiptNo,
            BigInteger revenue,
            BigInteger operatingProfit,
            BigInteger netIncome,
            BigInteger totalAssets,
            BigInteger totalLiabilities,
            BigInteger totalEquity) {
        this.fsDiv = fsDiv;
        this.currency = currency;
        this.receiptNo = receiptNo;
        this.revenue = keep(revenue, this.revenue);
        this.operatingProfit = keep(operatingProfit, this.operatingProfit);
        this.netIncome = keep(netIncome, this.netIncome);
        this.totalAssets = keep(totalAssets, this.totalAssets);
        this.totalLiabilities = keep(totalLiabilities, this.totalLiabilities);
        this.totalEquity = keep(totalEquity, this.totalEquity);
        this.updatedAt = Instant.now();
    }

    private static BigInteger keep(BigInteger incoming, BigInteger current) {
        return incoming != null ? incoming : current;
    }
}
