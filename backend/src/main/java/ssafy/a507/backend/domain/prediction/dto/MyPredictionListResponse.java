package ssafy.a507.backend.domain.prediction.dto;

import java.math.BigDecimal;
import java.util.List;

/**
 * GET /api/v1/predictions/me 200 응답 (ANT-PRED-06, 화면 C-02).
 *
 * <p>커서 규칙은 커밋 원장(GET /anchors)과 같다 — 최신(id 큰) 순 고정이라 커서는 마지막으로 받은 id 하나다.
 *
 * <p>집계 네 값({@code total·pendingCount·judgedCount·hitRate})은 <b>status 필터와 무관하게 내 예측 전량</b>
 * 기준이다. 목록 위 요약 칩이 필터를 바꿔도 흔들리면 안 되기 때문이다(프론트 요청, 2026-09-07).
 * {@code hitRate} 는 판정 건이 없으면 null 이다 — 0 으로 내리면 "전부 틀렸다" 로 읽힌다.
 * 누적 성과 전체(평균 오차·앵커 상태)는 같은 스토리의 GET /predictions/me/portfolio 쪽이다.
 */
public record MyPredictionListResponse(
        List<MyPredictionItemResponse> items,
        Long nextCursor,
        boolean hasNext,
        long total,
        long pendingCount,
        long judgedCount,
        BigDecimal hitRate) {}
