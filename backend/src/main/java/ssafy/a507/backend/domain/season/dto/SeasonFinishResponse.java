package ssafy.a507.backend.domain.season.dto;

import java.math.BigDecimal;

/**
 * POST /api/v1/seasons/{id}/finish 200 응답 — {@code season_results} 의 성과 지표.
 * 점수·등급·AI 복기는 아직 없다(ANT-SEASON-09). 이미 끝난 회차면 저장된 값을 그대로 준다.
 */
public record SeasonFinishResponse(
        Long participantId,
        BigDecimal finalAsset,
        BigDecimal returnRate,
        BigDecimal benchmarkReturn,
        BigDecimal maxDrawdown,
        BigDecimal winRate,
        BigDecimal profitFactor,
        BigDecimal avgHoldingDays) {}
