package ssafy.a507.backend.domain.research.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import ssafy.a507.backend.domain.market.entity.Stock;

/**
 * GET /api/v1/stocks/{code}/peers 200 응답 — 같은 KRX 섹터의 활성 종목, 자기 자신은 뺀다.
 *
 * <p>{@code marketCap}·{@code per}·{@code pbr} 는 지금 항상 null 이다(상장주식수 미수집 —
 * {@link ValuationResponse} 와 같은 사정). 키는 화면 분기 때문에 남긴다.
 *
 * @param priceDate 종가 기준일. 시세가 한 건도 없으면 null 이고 items 도 빈 목록이다
 */
public record PeerListResponse(LocalDate priceDate, List<Item> items) {

    /** @param prevClose 기준일 종가. 그날 거래정지면 null */
    public record Item(
            String code, String name, BigDecimal prevClose, BigDecimal marketCap, BigDecimal per, BigDecimal pbr) {

        public static Item of(Stock stock, BigDecimal prevClose) {
            return new Item(stock.getCode(), stock.getName(), prevClose, null, null, null);
        }
    }
}
