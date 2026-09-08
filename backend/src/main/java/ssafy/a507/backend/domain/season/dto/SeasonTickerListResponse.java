package ssafy.a507.backend.domain.season.dto;

import java.util.List;

/** GET /api/v1/seasons/{id}/tickers 200 응답. 시즌 종목은 다섯 개 남짓이라 페이징이 없다. */
public record SeasonTickerListResponse(List<SeasonTickerItemResponse> items) {}
