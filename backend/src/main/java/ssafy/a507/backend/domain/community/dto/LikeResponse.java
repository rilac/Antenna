package ssafy.a507.backend.domain.community.dto;

/**
 * 좋아요 등록 201 응답.
 *
 * <p>명세에는 "201 등록"만 있고 본문이 없었다. 본문 없는 201 은 {@code Content-Type} 도 없어서
 * 프론트 공용 클라이언트({@code api/client.ts})가 204 만 통과시키고 나머지는 JSON 을 기대하다
 * {@code CLIENT_NOT_JSON} 으로 던진다 — 성공한 좋아요가 화면에는 "서버에 연결하지 못했습니다"로
 * 보인다. 갱신된 수를 담아 내리면 그 함정을 피하면서 화면이 재조회 없이 숫자를 바꿀 수 있다.
 */
public record LikeResponse(long likeCount) {
}
