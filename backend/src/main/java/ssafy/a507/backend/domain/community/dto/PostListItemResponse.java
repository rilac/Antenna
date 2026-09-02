package ssafy.a507.backend.domain.community.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.time.Instant;

/**
 * 피드 목록의 글 한 건.
 *
 * <p>명세에는 이 스키마가 "200 성공"으로만 적혀 있어서 Jira AC(글 본문 + 리포스팅 원본
 * 리포트 카드 + 첨부 예측 카드 + 공감·댓글 수)로 역산해 정했다. 같은 섹션의
 * GET /reports 응답 형태를 따랐다.
 *
 * <p>{@code liked} 는 ANT-COMMUNITY-03 에서 더했다 — 좋아요 API 를 만들어도 "내가 이미
 * 눌렀는지"를 못 내려주면 화면이 하트를 채울 수 없다. 명세에 없던 필드라 그쪽도 함께 고쳤다.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record PostListItemResponse(
        Long id,
        String body,
        AuthorResponse author,
        ReportCardResponse reportCard,
        PredictionCardResponse predictionCard,
        long likeCount,
        boolean liked,
        long commentCount,
        Instant createdAt) {
}
