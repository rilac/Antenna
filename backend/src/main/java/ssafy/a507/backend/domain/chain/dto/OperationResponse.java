package ssafy.a507.backend.domain.chain.dto;

import java.time.Instant;
import ssafy.a507.backend.domain.chain.entity.Operation;

/**
 * GET /api/v1/operations/{operationId} 200 응답 — 명세 §2 비동기 작업.
 *
 * <p>{@code resource} 와 {@code error} 는 없을 때 null 로 내린다. 빈 객체를 내리면 프론트가
 * {@code resource.id} 를 읽고 undefined 를 리소스 id 로 쓰게 된다.
 */
public record OperationResponse(
        String operationId,
        Operation.Kind kind,
        Operation.Status status,
        Resource resource,
        String txHash,
        Error error,
        Instant createdAt,
        Instant settledAt) {

    public record Resource(Operation.ResourceType type, Long id) {}

    public record Error(String code, String message) {}

    public static OperationResponse from(Operation operation) {
        Resource resource =
                operation.getResourceType() == null
                        ? null
                        : new Resource(operation.getResourceType(), operation.getResourceId());
        Error error =
                operation.getErrorCode() == null
                        ? null
                        : new Error(operation.getErrorCode(), operation.getErrorMessage());

        return new OperationResponse(
                operation.getId(),
                operation.getKind(),
                operation.getStatus(),
                resource,
                operation.getTxHash(),
                error,
                operation.getCreatedAt(),
                operation.getSettledAt());
    }
}
