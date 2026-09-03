package ssafy.a507.backend.domain.market.dto;

import java.math.BigDecimal;
import java.util.List;

/**
 * GET /api/v1/watchlist 의 한 줄. 명세 §홈·시세 {@code { stockCode, name, prevClose, changeRate, series[] }}.
 *
 * @param prevClose 이 종목의 마지막 영업일 종가. 수집 범위(KOSPI 300) 밖으로 밀려 시세가 없으면 null
 * @param changeRate 전일 대비 등락률(%) · 소수 둘째 자리 · 점이 둘 미만이면 0
 * @param series 미니차트용 최근 30 영업일 종가 · 오래된 것이 먼저 · 마지막 원소가 prevClose 와 같다
 */
public record WatchlistItemResponse(
        String stockCode,
        String name,
        BigDecimal prevClose,
        BigDecimal changeRate,
        List<BigDecimal> series) {}
