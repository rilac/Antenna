package ssafy.a507.backend.domain.market.client;

import java.math.BigDecimal;
import java.time.LocalDate;
import ssafy.a507.backend.domain.market.entity.Stock;

/**
 * 포털 응답 한 줄을 우리 어휘로 옮긴 것. 한 줄이 종목 마스터와 일봉 양쪽의 재료가 된다.
 *
 * <p>포털의 값은 전부 문자열로 오지만 여기서부터는 {@link BigDecimal} 이다 — double 을
 * 한 번이라도 거치면 종가 비교가 흔들려 판정이 뒤집힌다.
 *
 * @param listedShares 상장주식수({@code lstgStCnt}) · PER·PBR 의 분모(EPS·BPS) 재료 · 없으면 null
 * @param marketCap 시가총액({@code mrktTotAmt}) · 시가총액 상위 N 종목을 고르는 데만 쓰고
 *     저장하지는 않는다 · 없으면 null
 */
public record StockPriceRow(
        String stockCode,
        String stockName,
        Stock.Market market,
        LocalDate tradeDate,
        BigDecimal open,
        BigDecimal high,
        BigDecimal low,
        BigDecimal close,
        Long volume,
        Long listedShares,
        BigDecimal marketCap) {}
