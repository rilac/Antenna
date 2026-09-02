package ssafy.a507.backend.domain.community.dto;

import java.time.Instant;

/**
 * 댓글 목록의 한 건.
 *
 * <p>명세에 이 스키마가 "200 성공"으로만 적혀 있어서 상세의 댓글 미리보기
 * ({@link CommentPreviewResponse})에 댓글 좋아요 두 필드를 더한 형태로 정했다.
 * 좋아요 API 를 같은 스토리에서 만드는데 화면이 개수와 내 상태를 못 받으면 하트를 그릴 수 없다.
 */
public record CommentListItemResponse(
        Long id,
        String body,
        AuthorResponse author,
        long likeCount,
        boolean liked,
        Instant createdAt) {
}
