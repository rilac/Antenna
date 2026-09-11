package ssafy.a507.backend.domain.chain.dto;

import java.time.Instant;
import ssafy.a507.backend.common.error.ErrorCode;
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

    /** {@code message} 는 코드에 붙은 서버 문구다. DB 의 원문({@code error_message})은 운영자용이라 내리지 않는다(-226). */
    public record Error(String code, String message) {}

    public static OperationResponse from(Operation operation) {
        Resource resource =
                operation.getResourceType() == null
                        ? null
                        : new Resource(operation.getResourceType(), operation.getResourceId());
        Error error =
                operation.getErrorCode() == null
                        ? null
                        : new Error(operation.getErrorCode(), messageOf(operation.getErrorCode()));

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

    /**
     * 코드 → 사용자 문구. {@code ErrorCode} 이름이면 그 메시지(팀이 이미 API 문구로 쓰는 것), 인덱서 코드면
     * {@link Operation.ChainFailure} 문구, 그 밖(다른 배포·옛 행)은 서버 오류 문구. 원문을 내리지 않는 이유는
     * RPC 주소·내부 경로·예외 클래스명이 섞이기 때문이다.
     */
    private static String messageOf(String code) {
        for (ErrorCode c : ErrorCode.values()) {
            if (c.name().equals(code)) {
                return c.getMessage();
            }
        }
        for (Operation.ChainFailure f : Operation.ChainFailure.values()) {
            if (f.name().equals(code)) {
                return f.message();
            }
        }
        return ErrorCode.INTERNAL_ERROR.getMessage();
    }
}
