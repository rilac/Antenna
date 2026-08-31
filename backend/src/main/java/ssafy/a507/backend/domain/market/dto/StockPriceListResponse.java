package ssafy.a507.backend.domain.market.dto;

import java.util.List;

/** GET /api/v1/stocks/{code}/prices 200 응답. 구간이 비면 items 가 빈 목록이다. */
public record StockPriceListResponse(List<StockPricePoint> items) {}
