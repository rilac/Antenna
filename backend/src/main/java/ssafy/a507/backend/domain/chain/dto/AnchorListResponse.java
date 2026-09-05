package ssafy.a507.backend.domain.chain.dto;

import java.util.List;

/** GET /api/v1/anchors 200 응답. 최신(id 큰) 순 고정이라 커서는 id 하나다 — 프론트 {@code useCursorList} 계약. */
public record AnchorListResponse(List<AnchorItemResponse> items, Long nextCursor, boolean hasNext) {}
