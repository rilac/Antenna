package ssafy.a507.backend.domain.season.dto;

import java.util.List;

/**
 * GET /api/v1/seasons 200 응답.
 *
 * <p>커서 페이징이 없다(명세 §1 규칙 6 의 예외). 동시에 열려 있는 시즌은 화면 한 장 안이다.
 */
public record SeasonListResponse(List<SeasonListItemResponse> items) {}
