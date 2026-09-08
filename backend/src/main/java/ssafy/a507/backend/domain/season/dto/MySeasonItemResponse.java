package ssafy.a507.backend.domain.season.dto;

import java.math.BigDecimal;
import java.time.Instant;
import ssafy.a507.backend.domain.season.entity.Season;

/**
 * GET /api/v1/seasons/me 의 한 행. 진행 중 시즌이 곧 "이어하기" 진입점이다.
 *
 * <p>v0.38 — 시즌 제목·종료 시각·최종 수익률을 싣는다. 연습 페이지의 "이어서 할 연습"
 * 카드와 "최근 완료한 연습" 목록이 이 한 응답으로 그려진다. 종료 시각은 실제 시각이다 —
 * 시즌의 시기가 아니라 내가 끝낸 날이라 시대 단서가 아니다.
 *
 * @param progress 진행률(%) · 0~100
 * @param endedAt 종료 시각. 진행 중이면 null
 * @param returnRate 최종 수익률(%). 끝난 회차만, 결과가 있으면
 */
public record MySeasonItemResponse(
        Long seasonId,
        Season.Mode mode,
        String title,
        int currentDay,
        int lengthDays,
        int progress,
        Instant endedAt,
        BigDecimal returnRate) {}
