package ssafy.a507.backend.domain.prediction.dto;

import java.time.LocalDate;

/**
 * {@code GET /predictions/slots} (명세 §예측, ANT-PRED-01 에서 구현 — PRED-06 AC 에 있으나 등록 검사와 같은 코드다).
 *
 * <p>{@code overCost} 는 슬롯 초과 시 소각할 ANT 금액(정수 문자열). 서버 상수이고 ANT-TOKEN-08 이 확정하기 전까지 잠정값이다.
 * ANT 는 decimals 0 이라(ANT-CHAIN-03) {@code "2000"} 이 곧 2,000 ANT 다. 숫자가 아니라 문자열인 이유는 다른 금액 필드
 * (구독료 등 {@code numeric(30,0)})와 형식을 통일하기 위해서다 — 이제 JS number 정밀도 문제는 없다.
 */
public record PredictionSlotResponse(LocalDate date, int freeLimit, int used, int remaining, String overCost) {}
