package ssafy.a507.backend.domain.research.dto;

import java.util.List;

/** GET /api/v1/stocks/{code}/documents 200 응답. 최신순 고정이라 커서는 id 하나다. */
public record ResearchDocumentListResponse(
        List<ResearchDocumentItemResponse> items, Long nextCursor, boolean hasNext) {}
