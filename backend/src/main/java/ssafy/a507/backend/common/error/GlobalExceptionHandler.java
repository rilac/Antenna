package ssafy.a507.backend.common.error;

import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * 오류 응답을 한 곳에서 만든다.
 * 예상 못 한 예외(catch-all)는 여기서 잡지 않는다 — 삼키면 팀 전체가 개발 중에 스택을 못 본다.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ErrorResponse> handleBusiness(BusinessException e) {
        ErrorCode code = e.getErrorCode();
        return ResponseEntity.status(code.getStatus())
                .body(ErrorResponse.of(code, e.getField()));
    }

    /**
     * @Valid 실패. 위반이 여러 개여도 첫 번째만 내려간다 —
     * 그래서 DTO의 message = 를 사람이 읽을 한국어로 쓰는 게 곧 API 문구가 된다.
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidation(MethodArgumentNotValidException e) {
        FieldError first = e.getBindingResult().getFieldErrors().stream().findFirst().orElse(null);
        ErrorCode code = ErrorCode.INVALID_REQUEST;
        if (first == null) {
            return ResponseEntity.status(code.getStatus()).body(ErrorResponse.of(code));
        }
        return ResponseEntity.status(code.getStatus())
                .body(ErrorResponse.of(code, first.getDefaultMessage(), first.getField()));
    }
}
