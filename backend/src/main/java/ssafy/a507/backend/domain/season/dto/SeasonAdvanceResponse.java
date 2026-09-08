package ssafy.a507.backend.domain.season.dto;

/** POST /api/v1/seasons/{id}/advance 200 응답. isLastDay 면 다음 단계는 finish 다. */
public record SeasonAdvanceResponse(int currentDay, boolean isLastDay) {}
