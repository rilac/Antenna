package ssafy.a507.backend.domain.community.dto;

import java.util.List;

/** GET /api/v1/posts/{postId}/comments 200 응답. 커서 페이징 규약은 명세 §1 을 따른다. */
public record CommentListResponse(
        List<CommentListItemResponse> items, Long nextCursor, boolean hasNext) {
}
