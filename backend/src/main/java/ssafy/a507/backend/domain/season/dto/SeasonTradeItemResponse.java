package ssafy.a507.backend.domain.season.dto;

import java.math.BigDecimal;
import ssafy.a507.backend.domain.season.entity.SeasonTrade;

/** 체결 한 건. {@code realizedPnl} 은 매도에만 있고 매수는 null 이다. */
public record SeasonTradeItemResponse(
        Long tradeId,
        Long tickerId,
        String tickerName,
        SeasonTrade.Side side,
        int qty,
        BigDecimal price,
        BigDecimal amount,
        int gameDay,
        BigDecimal realizedPnl) {}
