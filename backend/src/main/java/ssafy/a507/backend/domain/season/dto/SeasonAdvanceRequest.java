package ssafy.a507.backend.domain.season.dto;

import jakarta.validation.constraints.NotNull;

/** POST /api/v1/seasons/{id}/advance 요청 본문. expectedDay 는 낙관적 잠금이다(명세 §1). */
public record SeasonAdvanceRequest(
        @NotNull(message = "보고 있는 게임일을 보내야 합니다.")
        Integer expectedDay
) {
}
