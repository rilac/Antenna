package ssafy.a507.backend.domain.season.dto;

/**
 * GET /api/v1/seasons/me 의 {@code status} 파라미터.
 *
 * <p>시즌의 상태(SCHEDULED·RUNNING·CLOSED)가 아니라 <b>내 회차</b>의 상태다 — 연습은
 * 개인 진행일로 흐르므로 같은 시즌을 두고도 사람마다 끝났는지가 다르다.
 */
public enum MySeasonStatus {
    /** 개인 진행일이 총 게임일에 못 미친 회차 · 이어하기 대상 */
    ONGOING,
    /** 마지막 게임일까지 간 회차 · G-09 기록에서 본다 */
    DONE
}
