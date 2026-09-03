package ssafy.a507.backend.domain.market.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import ssafy.a507.backend.domain.market.entity.IndexQuote;

/**
 * 지수·환율 종가 한 점. 수집 클라이언트가 만들고 적재가 그대로 받는다 — 일봉과 달리 종목명 같은
 * 곁가지가 없어 행 타입과 적재 타입을 나눌 이유가 없다.
 *
 * <p>값은 {@link BigDecimal} 이다. double 을 한 번이라도 거치면 환율 소수 넷째 자리가 흔들린다.
 */
public record IndexQuoteUpsert(
        IndexQuote.IndexCode indexCode, LocalDate tradeDate, BigDecimal close) {}
