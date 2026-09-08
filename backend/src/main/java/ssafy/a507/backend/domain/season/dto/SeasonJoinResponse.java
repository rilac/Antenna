package ssafy.a507.backend.domain.season.dto;

/**
 * {@code POST /api/v1/seasons/{id}/join} 201 응답. 명세는 본문을 정하지 않았지만 화면이
 * 바로 진행 화면으로 가므로 진행일과 회차를 준다 — 기록·거래·포지션은 회차에 귀속된다.
 */
public record SeasonJoinResponse(Long participantId, int attemptNo, int currentDay) {}
