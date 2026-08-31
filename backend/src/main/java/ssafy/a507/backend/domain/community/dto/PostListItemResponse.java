package ssafy.a507.backend.domain.community.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.time.Instant;

/**
 * 피드 목록의 글 한 건.
 *
 * <p>명세에는 이 스키마가 "200 성공"으로만 적혀 있어서 Jira AC(글 본문 + 리포스팅 원본
 * 리포트 카드 + 첨부 예측 카드 + 공감·댓글 수)로 역산해 정했다. 같은 섹션의
 * GET /reports 응답 형태를 따랐다.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record PostListItemResponse(
        Long id,
        String body,
        AuthorResponse author,
        ReportCardResponse reportCard,
        PredictionCardResponse predictionCard,
        long likeCount,
        long commentCount,
        Instant createdAt) {
}
