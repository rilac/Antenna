package ssafy.a507.backend.domain.monetize.dto;

import java.util.List;

/** GET /api/v1/channels/{userId}/reports 200 응답. 최신순 고정이라 커서는 id 하나다. */
public record ChannelReportListResponse(
        List<ChannelReportItemResponse> items, Long nextCursor, boolean hasNext) {
}
