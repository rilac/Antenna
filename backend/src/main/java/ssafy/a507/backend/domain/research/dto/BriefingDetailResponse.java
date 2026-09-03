package ssafy.a507.backend.domain.research.dto;

import java.time.LocalDate;
import ssafy.a507.backend.domain.research.entity.AiBriefing;

/** GET /api/v1/briefings/{id} 200 응답. */
public record BriefingDetailResponse(Long id, String headline, String body, LocalDate targetDate) {

    public static BriefingDetailResponse of(AiBriefing briefing) {
        return new BriefingDetailResponse(
                briefing.getId(), briefing.getHeadline(), briefing.getBody(), briefing.getTargetDate());
    }
}
