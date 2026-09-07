package ssafy.a507.backend.domain.season.dto;

import java.util.List;

/** GET /api/v1/seasons/me 200 응답. 최근 참가 순. */
public record MySeasonListResponse(List<MySeasonItemResponse> items) {}
