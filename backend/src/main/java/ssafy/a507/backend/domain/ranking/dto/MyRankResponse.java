package ssafy.a507.backend.domain.ranking.dto;

import java.math.BigDecimal;

/**
 * GET /api/v1/rankings/me 200 응답 (ANT-RANK-03, 화면 E-01 상단 카드).
 *
 * <p>랭킹에 들지 않았으면 이 본문 대신 <b>204</b> 다 — 프론트 클라이언트가 204 를 {@code undefined} 로
 * 받고 화면이 {@code {me && ...}} 로 카드를 숨긴다. 404 를 주면 목록과 함께 묶인
 * {@code Promise.all} 이 깨져 랭킹 페이지 전체가 오류 화면이 된다.
 *
 * @param rank 필터 조합 안에서의 순위
 * @param percentile 상위 몇 % — {@code rank / 전체 × 100}, 소수 첫째 자리. 화면이 "상위 3.7%" 로 그린다.
 *     작을수록 상위다(백분위의 통상 정의와 반대 방향이니 주의).
 * @param delta 직전 스냅샷 대비 순위 변동. <b>양수가 상승</b>. 0 은 변동 없음이고 화면이 아무것도 그리지 않는다.
 * @param tier 리플레이 전용 티어({@code DIAMOND·PLATINUM·GOLD}). 실전에서는 null 이고 화면도 REAL 에서 감춘다.
 */
public record MyRankResponse(int rank, BigDecimal percentile, int delta, String tier) {}
