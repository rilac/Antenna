package ssafy.a507.backend.domain.market.dto;

import ssafy.a507.backend.domain.market.entity.Stock;

/**
 * 종목 마스터 upsert 한 줄. 수집 배치가 「주식시세정보」 응답에서 뽑아 넘긴다.
 *
 * <p>sector 가 없는 이유 — 시세 API 는 업종을 주지 않는다. 섹터는 KRX 업종분류 CSV 가
 * 단일 원천이라 upsert 가 건드리지 않는다.
 *
 * @param code 6자리 종목코드({@code srtnCd} 뒤 6자리)
 * @param name 종목명({@code itmsNm})
 * @param market 상장 시장({@code mrktCtg}) · 알 수 없으면 null
 */
public record StockUpsert(String code, String name, Stock.Market market) {}
