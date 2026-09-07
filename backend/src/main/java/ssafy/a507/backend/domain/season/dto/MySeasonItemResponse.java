package ssafy.a507.backend.domain.season.dto;

import ssafy.a507.backend.domain.season.entity.Season;

/**
 * GET /api/v1/seasons/me 의 한 행. 진행 중 시즌이 곧 "이어하기" 진입점이다.
 *
 * <p>다섯 필드가 전부다. 시즌 이름·수익률을 여기 싣지 않는다 — 이름은 목록·상세에서,
 * 수익률은 {@code /seasons/{id}/me} 에서 온다.
 *
 * @param progress 진행률(%) · 0~100
 */
public record MySeasonItemResponse(
        Long seasonId, Season.Mode mode, int currentDay, int lengthDays, int progress) {}
