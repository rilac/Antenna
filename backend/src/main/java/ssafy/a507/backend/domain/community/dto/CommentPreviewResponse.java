package ssafy.a507.backend.domain.community.dto;

import java.time.Instant;
import ssafy.a507.backend.domain.community.entity.PostComment;

/** 글 상세의 댓글 미리보기 한 건. 전체 목록은 GET /posts/{postId}/comments(ANT-COMMUNITY-03). */
public record CommentPreviewResponse(
        Long id, String body, AuthorResponse author, Instant createdAt) {

    public static CommentPreviewResponse from(PostComment comment) {
        return new CommentPreviewResponse(
                comment.getId(),
                comment.getBody(),
                AuthorResponse.from(comment.getUser()),
                comment.getCreatedAt());
    }
}
