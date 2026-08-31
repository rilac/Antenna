package ssafy.a507.backend.common.error;

import lombok.Getter;

/** 도메인 규칙 위반. 상태 코드와 code 어휘는 ErrorCode 가 들고 있다. */
@Getter
public class BusinessException extends RuntimeException {

    private final ErrorCode errorCode;
    private final String field;

    public BusinessException(ErrorCode errorCode) {
        this(errorCode, null);
    }

    public BusinessException(ErrorCode errorCode, String field) {
        super(errorCode.getMessage());
        this.errorCode = errorCode;
        this.field = field;
    }
}
