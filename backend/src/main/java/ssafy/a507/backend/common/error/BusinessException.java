package ssafy.a507.backend.common.error;

import lombok.Getter;

/**
 * 도메인 규칙 위반. 컨트롤러에서 try/catch 하지 않는다 — GlobalExceptionHandler가 받는다.
 * 스택 트레이스를 쓸 일이 없어 fillInStackTrace를 끄고 던진다.
 */
@Getter
public class BusinessException extends RuntimeException {

    private final ErrorCode errorCode;

    /** 입력값 오류일 때만 채운다. 응답의 field가 된다. */
    private final String field;

    public BusinessException(ErrorCode errorCode) {
        this(errorCode, null);
    }

    public BusinessException(ErrorCode errorCode, String field) {
        super(errorCode.getMessage(), null, false, false);
        this.errorCode = errorCode;
        this.field = field;
    }
}
