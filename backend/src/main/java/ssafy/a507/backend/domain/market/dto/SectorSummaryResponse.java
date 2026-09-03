package ssafy.a507.backend.domain.market.dto;

import java.math.BigDecimal;
import java.util.List;

/**
 * GET /api/v1/stocks/sectors 200 응답 — 종목 탐색 상단의 섹터 요약 칩.
 *
 * <p>첫 행은 전체({@code sector} null)다. 섹터가 비어 있는 종목은 전체 수에는 들어가지만 칩으로는
 * 나오지 않는다 — "미분류" 칩과 "전체" 칩을 화면이 구분할 방법이 없다.
 *
 * @param changeRate 그 묶음의 평균 등락률(%) · 등락률을 낼 수 있는 종목만 평균한다 · 하나도 없으면 0
 */
public record SectorSummaryResponse(List<Item> items) {

    public record Item(String sector, long count, BigDecimal changeRate) {}
}
