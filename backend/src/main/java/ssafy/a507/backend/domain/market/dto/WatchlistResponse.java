package ssafy.a507.backend.domain.market.dto;

import java.util.List;

/** GET /api/v1/watchlist 200 응답. 최근 담은 순. 페이징 없음 — 담는 수가 화면 한 장 안이다. */
public record WatchlistResponse(List<WatchlistItemResponse> items) {}
