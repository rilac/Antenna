package ssafy.a507.backend.domain.market.dto;

/** POST /api/v1/watchlist 201 응답. 담긴 종목코드만 돌려준다 — 시세는 목록에서 본다. */
public record WatchlistAddResponse(String stockCode) {}
