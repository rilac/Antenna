package ssafy.a507.backend.domain.market.dto;

import java.util.List;

/** GET /api/v1/market/indices 200 응답. 아직 한 점도 없는 지수는 목록에서 빠진다. */
public record MarketIndexListResponse(List<MarketIndexItemResponse> items) {}
