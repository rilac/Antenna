package ssafy.a507.backend.domain.season.dto;

import java.util.List;

/**
 * GET /api/v1/seasons/{id}/tickers/{tickerId}/prices 200 응답. 게임일 오름차순이다.
 *
 * <p>진행일을 넘는 봉은 들어 있지 않다 — 커닝 차단이라 서버가 아예 내리지 않는다.
 * 아직 참가하지 않았으면 진행일이 0 이라 목록이 빈다.
 */
public record SeasonPriceListResponse(List<SeasonPricePoint> items) {}
