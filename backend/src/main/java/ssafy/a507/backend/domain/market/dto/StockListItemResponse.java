package ssafy.a507.backend.domain.market.dto;

import java.math.BigDecimal;
import ssafy.a507.backend.domain.market.entity.Stock;

/**
 * GET /api/v1/stocks 목록 한 줄. 명세 §홈·시세의 행 스키마 중 지금 재료가 있는 것만 값이 있다.
 *
 * <p>재료가 없는 값도 키를 뺀 채로 내리지 않는다 — 화면이 {@code s.per === null} 로 분기하므로
 * 키가 없으면 {@code undefined} 가 흘러 렌더가 죽는다. 예측 집계는 predictions 판정이 생기는
 * 스토리에서 채운다.
 *
 * @param prevClose 직전 영업일 종가. 그날 거래가 정지됐던 종목은 null. 실전 시세에 현재가는 없다
 * @param changeRate 전일 대비 등락률(%) · 소수 둘째 자리. 어느 한쪽 종가가 없으면 null
 * @param per 종가 × 상장주식수 / 최신 연간 순이익 · 소수 둘째 자리. 재료가 없거나 적자면 null
 * @param pbr 종가 × 상장주식수 / 최신 연간 자본총계 · 소수 둘째 자리. 재료가 없거나 자본잠식이면 null
 * @param predictionCount 판정 대기 예측 수 — 아직 0
 * @param upRatio 예측 중 UP 비율 — 예측이 없어 null
 * @param watched 내 관심 종목인가
 */
public record StockListItemResponse(
        String code,
        String name,
        String sector,
        Stock.Market market,
        BigDecimal prevClose,
        BigDecimal changeRate,
        BigDecimal per,
        BigDecimal pbr,
        int predictionCount,
        BigDecimal upRatio,
        boolean watched) {}
