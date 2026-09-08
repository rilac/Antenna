package ssafy.a507.backend.domain.season.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import ssafy.a507.backend.domain.season.entity.SeasonTrade;

/** POST /api/v1/seasons/{id}/orders 요청 본문. 가격은 받지 않는다 — 체결가는 게임일 종가다. */
public record SeasonOrderRequest(
        @NotNull(message = "종목을 골라야 합니다.")
        @Positive(message = "종목 ID가 올바르지 않습니다.")
        Long tickerId,
        @NotNull(message = "매수·매도를 골라야 합니다.")
        SeasonTrade.Side side,
        @NotNull(message = "수량을 입력해야 합니다.")
        @Positive(message = "수량은 1 이상이어야 합니다.")
        Integer qty
) {
}
