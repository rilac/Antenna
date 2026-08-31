package ssafy.a507.backend.common.error;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * API 명세 §1 의 오류 본문. field 는 입력값 오류일 때만 채운다.
 * { "code": "SELF_REPORT", "message": "...", "field": "targetId" }
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ErrorResponse(String code, String message, String field) {

    public static ErrorResponse of(ErrorCode code) {
        return new ErrorResponse(code.name(), code.getMessage(), null);
    }

    public static ErrorResponse of(ErrorCode code, String field) {
        return new ErrorResponse(code.name(), code.getMessage(), field);
    }

    public static ErrorResponse of(ErrorCode code, String message, String field) {
        return new ErrorResponse(code.name(), message, field);
    }
}
