package ssafy.a507.backend.domain.season.dto;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * GET /api/v1/seasons/{id}/result/me 200 응답 — {@code season_results} 전체.
 * 점수·등급·복기는 아직 채워지지 않는다(ANT-SEASON-09) — null 이면 생성 전이다.
 */
public record SeasonResultResponse(
        Long participantId,
        BigDecimal finalAsset,
        BigDecimal returnRate,
        BigDecimal benchmarkReturn,
        BigDecimal maxDrawdown,
        BigDecimal winRate,
        BigDecimal profitFactor,
        BigDecimal avgHoldingDays,
        BigDecimal score,
        String grade,
        String review,
        Instant closedAt) {}
