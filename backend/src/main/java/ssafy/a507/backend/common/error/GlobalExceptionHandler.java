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
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

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
     * 쿼리 파라미터·경로 변수의 <b>타입 변환</b> 실패 — {@code ?size=abc}, {@code /reports/abc}.
     *
     * <p>없으면 아래 마지막 그물로 떨어져 500 이 나간다. 클라이언트가 숫자 자리에 문자를 보낸
     * 것이므로 400 이어야 하고, 명세 §1 의 오류 본문 형식도 맞춰야 한다. 컨트롤러마다 파라미터를
     * 문자열로 받아 직접 파싱하는 대신 여기서 한 번에 처리한다 — 노출된 엔드포인트가
     * ANT-COMMUNITY-01 뿐이 아니다.
     */
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ErrorResponse> handleTypeMismatch(MethodArgumentTypeMismatchException e) {
        log.debug("파라미터 타입 변환 실패", e);
        return ResponseEntity.status(ErrorCode.INVALID_REQUEST.getStatus())
                .body(ErrorResponse.of(ErrorCode.INVALID_REQUEST, e.getName()));
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

    /**
     * multipart 상한 초과. 서블릿이 본문을 다 읽기 전에 끊으므로 컨트롤러까지 오지 못한다 —
     * 이 핸들러가 없으면 마지막 그물로 떨어져 500 이 나간다. 명세 §첨부·업로드는 413 이다.
     */
    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<ErrorResponse> handleTooLarge(MaxUploadSizeExceededException e) {
        log.debug("업로드 용량 초과", e);
        return ResponseEntity.status(ErrorCode.FILE_TOO_LARGE.getStatus())
                .body(ErrorResponse.of(ErrorCode.FILE_TOO_LARGE, "file"));
    }

    /** 마지막 그물. 여기까지 온 것은 예상 못 한 오류이므로 스택을 남긴다. */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleUnexpected(Exception e) {
        log.error("처리되지 않은 예외", e);
        return ResponseEntity.status(ErrorCode.INTERNAL_ERROR.getStatus())
                .body(ErrorResponse.of(ErrorCode.INTERNAL_ERROR));
    }
}
