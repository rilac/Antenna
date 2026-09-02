package ssafy.a507.backend.domain.monetize.dto;

import ssafy.a507.backend.domain.chain.entity.Operation;

/**
 * POST /api/v1/ads 202 응답.
 *
 * <p>{@code adId} 를 함께 내리는 이유는, 폴링이 끝나기 전에도 화면이 "신청한 배너"를 가리킬
 * 수 있어야 하기 때문이다. 상태 확인은 {@code GET /operations/{operationId}} 하나로 한다.
 */
public record AdCreateResponse(String operationId, Long adId, Operation.Status status) {}
