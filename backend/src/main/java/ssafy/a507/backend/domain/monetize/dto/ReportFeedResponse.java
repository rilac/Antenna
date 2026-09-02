package ssafy.a507.backend.domain.monetize.dto;

import java.util.List;

/**
 * GET /api/v1/reports 200 응답.
 *
 * <p>{@code nextCursor} 가 {@code GET /posts} 와 달리 문자열이다. {@code sort=POPULAR} 는
 * 정렬 키가 열람 수라 id 하나로는 다음 페이지 시작점을 특정할 수 없어 커서가 복합값이 된다.
 * 두 정렬이 서로 다른 타입을 내리면 프론트가 분기해야 하므로 양쪽 다 문자열로 두고,
 * 클라이언트는 받은 값을 그대로 되돌려주기만 한다 — 값의 구조는 서버 것이다.
 */
public record ReportFeedResponse(
        List<ReportFeedItemResponse> items, String nextCursor, boolean hasNext) {
}
