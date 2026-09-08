package ssafy.a507.backend.domain.season.dto;

import java.math.BigDecimal;
import java.util.List;

/**
 * GET /api/v1/seasons/{id}/me 200 응답 — 내 회차의 현황·포트폴리오.
 *
 * <p>{@code pnl} 은 총자산 − 시즌 출발 예수금, {@code pnlRate} 는 그 비율(%)이다.
 * 결과창 포트폴리오도 이 응답을 그대로 쓴다.
 */
public record MySeasonStatusResponse(
        BigDecimal cash,
        BigDecimal totalAsset,
        BigDecimal stockValue,
        BigDecimal pnl,
        BigDecimal pnlRate,
        int currentDay,
        List<MyPositionResponse> positions) {}
