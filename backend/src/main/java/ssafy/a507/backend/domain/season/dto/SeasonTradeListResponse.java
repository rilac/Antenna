package ssafy.a507.backend.domain.season.dto;

import java.util.List;

/** GET /api/v1/seasons/{id}/trades 200 응답. 커서 페이징 규약은 명세 §1 을 따른다. */
public record SeasonTradeListResponse(
        List<SeasonTradeItemResponse> items, Long nextCursor, boolean hasNext) {}
