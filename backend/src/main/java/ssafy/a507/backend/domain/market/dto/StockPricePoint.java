package ssafy.a507.backend.domain.market.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

/** 일봉 시계열의 한 점. 명세 §홈·시세대로 종가만 내린다 — OHLCV 는 모의투자 쪽 이야기다. */
public record StockPricePoint(LocalDate tradeDate, BigDecimal close) {}
