package ssafy.a507.backend.common.error;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * 전 API 공통 오류 본문. 성공 응답에는 래퍼가 없다 — 컨트롤러가 DTO를 그대로 반환한다.
 * field는 입력값 오류일 때만 채우고, 그 외에는 직렬화에서 빠진다.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ErrorResponse(String code, String message, String field) {

    public static ErrorResponse of(ErrorCode errorCode) {
        return new ErrorResponse(errorCode.name(), errorCode.getMessage(), null);
    }

    public static ErrorResponse of(ErrorCode errorCode, String field) {
        return new ErrorResponse(errorCode.name(), errorCode.getMessage(), field);
    }

    /** @Valid 실패처럼 메시지를 DTO 제약에서 가져와야 할 때 쓴다. */
    public static ErrorResponse of(ErrorCode errorCode, String message, String field) {
        return new ErrorResponse(errorCode.name(), message, field);
    }
}
