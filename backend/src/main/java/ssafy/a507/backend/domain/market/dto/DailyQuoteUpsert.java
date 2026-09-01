package ssafy.a507.backend.domain.market.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 일봉 upsert 한 줄. 가격은 전부 {@link BigDecimal} 이다 — double 로 받으면 종가 비교가
 * 흔들려 판정이 뒤집힌다(ERD 도메인 B: numeric(14,2)).
 *
 * @param stockCode 종목코드 · {@code stocks} 에 먼저 들어가 있어야 한다(FK)
 * @param tradeDate 시세가 속한 영업일({@code basDt})
 * @param open 시가 · 캔들 표시 전용
 * @param high 고가
 * @param low 저가
 * @param close 종가 · 판정·표시가 쓰는 유일한 가격
 * @param volume 거래량(주) · 없으면 null
 */
public record DailyQuoteUpsert(
        String stockCode,
        LocalDate tradeDate,
        BigDecimal open,
        BigDecimal high,
        BigDecimal low,
        BigDecimal close,
        Long volume) {}
