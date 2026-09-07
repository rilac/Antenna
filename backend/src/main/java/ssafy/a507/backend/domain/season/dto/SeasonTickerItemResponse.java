package ssafy.a507.backend.domain.season.dto;

/**
 * GET /api/v1/seasons/{id}/tickers 의 한 행.
 *
 * <p><b>정답 원본 종목이 없다.</b> {@code season_tickers.real_stock_code} 는 시즌이 CLOSED 되기
 * 전까지 어떤 응답에도 실으면 안 된다 — 이 record 에 자리를 두지 않는 것이 그 규칙을 지키는
 * 방법이다. 필드가 없으면 실수로 담을 수 없다.
 *
 * @param displayName 블라인드 이름 · "A사" 처럼 정체를 지운 값
 * @param sector 참가자에게 보이는 유일한 힌트 · 상위 분류로만
 */
public record SeasonTickerItemResponse(Long tickerId, String displayName, String sector) {}
