package ssafy.a507.backend.domain.research.dto;

import java.math.BigInteger;
import java.util.List;
import ssafy.a507.backend.domain.research.entity.CorpFinancial;

/**
 * GET /api/v1/stocks/{code}/financials 200 응답. 오래된 연도가 먼저다 — 차트가 왼쪽에서 오른쪽으로
 * 그린다.
 *
 * <p>{@code fsDiv}(CFS 연결 · OFS 별도)는 행마다 싣는다. 같은 회사도 연결과 별도가 배로 차이 나서
 * 화면이 배지로 밝혀야 하고, 연도에 따라 어느 쪽이 수집됐는지 다를 수 있다.
 */
public record CorpFinancialListResponse(List<Item> items) {

    /** 금액은 전부 원 단위. 계정이 보고서에 없으면 null — 0 은 "실적 0"으로 읽힌다. */
    public record Item(
            int year,
            int quarter,
            String fsDiv,
            BigInteger revenue,
            BigInteger operatingProfit,
            BigInteger netIncome,
            BigInteger assets,
            BigInteger liabilities,
            BigInteger equity) {

        public static Item of(CorpFinancial f) {
            return new Item(
                    f.getFiscalYear(),
                    f.getQuarter(),
                    f.getFsDiv(),
                    f.getRevenue(),
                    f.getOperatingProfit(),
                    f.getNetIncome(),
                    f.getTotalAssets(),
                    f.getTotalLiabilities(),
                    f.getTotalEquity());
        }
    }
}
