package ssafy.a507.backend.domain.ranking.dto;

import java.time.Instant;
import java.util.List;

/**
 * GET /api/v1/rankings 200 응답 (ANT-RANK-02).
 *
 * <p>{@code computedAt} 은 이 목록을 만든 배치의 산출 시각이다. 화면 E-01 이 "산출 시각" 을 그대로 띄운다 —
 * 요청 시점 재계산이 아니라 스냅샷이라는 것을 사용자가 알아야 하기 때문이다.
 *
 * <p><b>nextCursor 가 없다.</b> 순위는 필터 조합마다 1부터 빈틈 없이 이어지는 정수라, 다음 장의 커서는
 * 받은 마지막 {@code rank + 1} 로 프론트가 그냥 만든다. 스냅샷이라 중간 삽입이 없어 안전하다(명세 §1 페이징 예외).
 *
 * <p>판정(HIT/MISS)이 하나도 없으면 배치가 쓸 행이 없어 {@code computedAt: null · items: []} 가
 * 정상 응답이다. 빈 목록은 404 가 아니다.
 */
public record RankingListResponse(Instant computedAt, List<RankingItemResponse> items) {

    public static RankingListResponse empty() {
        return new RankingListResponse(null, List.of());
    }
}
