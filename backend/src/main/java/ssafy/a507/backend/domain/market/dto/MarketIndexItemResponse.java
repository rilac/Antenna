package ssafy.a507.backend.domain.market.dto;

import java.math.BigDecimal;
import java.util.List;

/**
 * GET /api/v1/market/indices 의 한 줄. 명세 §홈·시세 {@code { code, close, changeRate, series[] }}.
 *
 * @param code KOSPI · KOSDAQ · USDKRW — 3종 고정이다. 늘리거나 사용자가 고르게 하지 않는다(화면설계 B-01)
 * @param close 마지막 영업일 종가(환율은 그날 매매기준율). 현재가가 아니다
 * @param changeRate 전일 대비 등락률(%) · 소수 둘째 자리 · 점이 하나뿐이면 0
 * @param series 미니차트용 최근 N개 종가 · 오래된 것이 먼저다 · 마지막 원소가 close 와 같다
 */
public record MarketIndexItemResponse(
        String code, BigDecimal close, BigDecimal changeRate, List<BigDecimal> series) {}
