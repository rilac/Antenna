package ssafy.a507.backend.domain.community.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.time.Instant;
import java.util.List;

/**
 * GET /api/v1/posts/{postId} 200 응답 — 글 + 댓글 미리보기.
 *
 * <p>댓글 수는 미리보기 건수가 아니라 전체 개수다. 화면이 "댓글 12개 모두 보기"를 그리려면
 * 미리보기로 잘린 3건이 아니라 12를 알아야 한다.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record PostDetailResponse(
        Long id,
        String body,
        AuthorResponse author,
        ReportCardResponse reportCard,
        PredictionCardResponse predictionCard,
        long likeCount,
        long commentCount,
        Instant createdAt,
        List<CommentPreviewResponse> comments) {
}
