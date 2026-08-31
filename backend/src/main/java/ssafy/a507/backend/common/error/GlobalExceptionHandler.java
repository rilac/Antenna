package ssafy.a507.backend.common.error;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/** 모든 오류 응답을 API 명세 §1 형식 하나로 모은다. */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ErrorResponse> handleBusiness(BusinessException e) {
        ErrorCode code = e.getErrorCode();
        return ResponseEntity.status(code.getStatus())
                .body(ErrorResponse.of(code, e.getField()));
    }

    /** @Valid 실패. 첫 번째 위반 필드만 내려준다 — 프론트가 한 번에 한 곳을 가리키면 된다. */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidation(MethodArgumentNotValidException e) {
        FieldError first = e.getBindingResult().getFieldErrors().stream().findFirst().orElse(null);
        String field = first == null ? null : first.getField();
        String message = first == null
                ? ErrorCode.INVALID_REQUEST.getMessage()
                : first.getDefaultMessage();
        return ResponseEntity.status(ErrorCode.INVALID_REQUEST.getStatus())
                .body(ErrorResponse.of(ErrorCode.INVALID_REQUEST, message, field));
    }

    /**
     * 쿼리 파라미터·경로 변수 검증 실패(@Validated). 없으면 500 으로 나가므로 본문 검증과 같은 형식으로 맞춘다.
     */
    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ErrorResponse> handleParamValidation(ConstraintViolationException e) {
        ConstraintViolation<?> first = e.getConstraintViolations().stream().findFirst().orElse(null);
        String field = null;
        String message = ErrorCode.INVALID_REQUEST.getMessage();
        if (first != null) {
            // propertyPath 는 "메서드명.파라미터명" 이라 마지막 마디만 필드로 쓴다.
            String path = first.getPropertyPath().toString();
            field = path.substring(path.lastIndexOf('.') + 1);
            message = first.getMessage();
        }
        return ResponseEntity.status(ErrorCode.INVALID_REQUEST.getStatus())
                .body(ErrorResponse.of(ErrorCode.INVALID_REQUEST, message, field));
    }

    /**
     * 본문을 못 읽는 경우 — 깨진 JSON, enum 에 없는 값 등.
     * 파서 메시지는 내부 타입 이름을 흘리므로 내려보내지 않는다.
     */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ErrorResponse> handleUnreadable(HttpMessageNotReadableException e) {
        log.debug("본문 파싱 실패", e);
        return ResponseEntity.status(ErrorCode.INVALID_REQUEST.getStatus())
                .body(ErrorResponse.of(ErrorCode.INVALID_REQUEST));
    }

    /** 마지막 그물. 여기까지 온 것은 예상 못 한 오류이므로 스택을 남긴다. */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleUnexpected(Exception e) {
        log.error("처리되지 않은 예외", e);
        return ResponseEntity.status(ErrorCode.INTERNAL_ERROR.getStatus())
                .body(ErrorResponse.of(ErrorCode.INTERNAL_ERROR));
    }
}
