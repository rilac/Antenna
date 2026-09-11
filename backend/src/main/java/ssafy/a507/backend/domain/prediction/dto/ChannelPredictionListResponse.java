package ssafy.a507.backend.domain.prediction.dto;

import java.util.List;

/** GET /api/v1/channels/{userId}/predictions 200 응답. 최신순 고정이라 커서는 id 하나다. */
public record ChannelPredictionListResponse(
        List<ChannelPredictionItemResponse> items, Long nextCursor, boolean hasNext) {}
