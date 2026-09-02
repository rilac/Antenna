package ssafy.a507.backend.domain.market.dto;

import java.math.BigDecimal;
import ssafy.a507.backend.domain.market.entity.Stock;

/**
 * GET /api/v1/stocks 목록 한 줄.
 *
 * @param prevClose 직전 영업일 종가. 그날 거래가 정지됐던 종목은 비어 있다.
 *     실전 시세에 현재가는 없다 — 실시간 시세는 법적 제약이라 종가만 내린다(명세 §1).
 */
public record StockListItemResponse(
        String code, String name, String sector, Stock.Market market, BigDecimal prevClose) {}
