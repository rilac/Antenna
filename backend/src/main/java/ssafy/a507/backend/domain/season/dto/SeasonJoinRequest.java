package ssafy.a507.backend.domain.season.dto;

/**
 * POST /api/v1/seasons/{id}/join 요청 본문(선택). {@code restart} 가 true 면 진행 중인 회차를
 * 버리고(ABANDONED) 새 회차로 시작한다 — 화면의 "초기화하고 다시 시작" 확인 뒤에만 보낸다.
 */
public record SeasonJoinRequest(Boolean restart) {
    public boolean isRestart() {
        return Boolean.TRUE.equals(restart);
    }
}
