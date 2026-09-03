package ssafy.a507.backend.domain.research.dto;

import java.time.LocalDate;
import ssafy.a507.backend.domain.research.entity.AiBriefing;

/** GET /api/v1/briefings 목록 항목. 본문은 상세에서만 — 카드에는 헤드라인 한 줄이 실린다. */
public record BriefingItemResponse(
        Long id, AiBriefing.Scope scope, String stockCode, String headline, LocalDate targetDate) {

    public static BriefingItemResponse of(AiBriefing briefing) {
        return new BriefingItemResponse(
                briefing.getId(),
                briefing.getScope(),
                briefing.getStock() == null ? null : briefing.getStock().getCode(),
                briefing.getHeadline(),
                briefing.getTargetDate());
    }
}
