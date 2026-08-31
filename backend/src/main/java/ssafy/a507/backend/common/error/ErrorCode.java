package ssafy.a507.backend.common.error;

import lombok.Getter;
import org.springframework.http.HttpStatus;

/**
 * API 명세 §1 의 오류 code 어휘. 대문자 스네이크로 고정한다.
 * 프론트가 code 로 분기하므로 한 번 정한 값은 바꾸지 않는다.
 *
 * <p>명세 §1 "오류 code 어휘" 표가 원본이다. 여기에 값을 추가하면 그 표도 함께 고친다 —
 * 표에 없는 code 를 즉석에서 만들지 않기로 팀에서 정했다.
 */
@Getter
public enum ErrorCode {

    // 공통
    INVALID_REQUEST(HttpStatus.BAD_REQUEST, "요청 값이 올바르지 않습니다."),
    UNAUTHENTICATED(HttpStatus.UNAUTHORIZED, "로그인이 필요합니다."),
    INTERNAL_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "서버 오류가 발생했습니다."),

    // 멱등성 (명세 §1)
    IDEMPOTENCY_KEY_REQUIRED(HttpStatus.BAD_REQUEST, "Idempotency-Key 헤더가 필요합니다."),
    IDEMPOTENCY_KEY_REUSED(HttpStatus.CONFLICT, "같은 Idempotency-Key 로 다른 요청을 보냈습니다."),

    // 신고 (ANT-COMMUNITY-04)
    TARGET_NOT_FOUND(HttpStatus.NOT_FOUND, "신고 대상을 찾을 수 없습니다."),
    SELF_REPORT(HttpStatus.BAD_REQUEST, "자신을 신고할 수 없습니다."),
    DUPLICATE_REPORT(HttpStatus.CONFLICT, "이미 접수된 신고입니다."),

    // 피드 글 (ANT-COMMUNITY-02)
    POST_NOT_FOUND(HttpStatus.NOT_FOUND, "글을 찾을 수 없습니다."),
    REPORT_NOT_FOUND(HttpStatus.NOT_FOUND, "리포트를 찾을 수 없습니다."),
    PREDICTION_NOT_FOUND(HttpStatus.NOT_FOUND, "예측을 찾을 수 없습니다.");

    private final HttpStatus status;
    private final String message;

    ErrorCode(HttpStatus status, String message) {
        this.status = status;
        this.message = message;
    }
}
