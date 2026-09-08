package ssafy.a507.backend.domain.season.dto;

import java.math.BigDecimal;

/**
 * 보유 종목 한 줄. {@code value} 는 내 진행일 종가 × 수량, {@code pnl} 은 value − 평단 × 수량,
 * {@code weight} 는 총자산 대비 비중(%)이다.
 */
public record MyPositionResponse(
        Long tickerId,
        String displayName,
        int qty,
        BigDecimal avgPrice,
        BigDecimal value,
        BigDecimal pnl,
        BigDecimal weight) {}
