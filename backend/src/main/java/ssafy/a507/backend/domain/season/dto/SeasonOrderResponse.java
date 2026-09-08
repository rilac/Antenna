package ssafy.a507.backend.domain.season.dto;

import java.math.BigDecimal;

/** POST /api/v1/seasons/{id}/orders 201 응답. 체결가는 그 게임일 종가다. */
public record SeasonOrderResponse(Long tradeId, BigDecimal price, int gameDay) {}
