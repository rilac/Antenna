package ssafy.a507.backend.domain.research.dto;

import java.util.List;

/** GET /api/v1/briefings 200 응답. 페이징 없음(명세). */
public record BriefingListResponse(List<BriefingItemResponse> items) {}
