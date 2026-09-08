package ssafy.a507.backend.domain.season.dto;

import java.math.BigDecimal;

/**
 * 한 게임일의 봉.
 *
 * <p><b>실제 날짜가 없다.</b> {@code game_day} 인덱스만 있다 — 날짜가 곧 시대 단서라
 * 시즌 가격에는 애초에 날짜를 담지 않았다(ERD v0.7).
 *
 * <p>{@code close} 만 항상 있다. 나머지는 원천 일봉에 없으면 null 이고, 그때 화면은
 * 심지와 거래량 막대만 비운다 — 체결·판정은 close 만 쓰므로 그대로 돈다.
 */
public record SeasonPricePoint(
        int gameDay,
        BigDecimal open,
        BigDecimal high,
        BigDecimal low,
        BigDecimal close,
        Long volume) {}
