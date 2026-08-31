package ssafy.a507.backend.common.error;

import lombok.Getter;
import org.springframework.http.HttpStatus;

/**
 * API 오류 어휘. 프론트가 이 이름으로 분기하므로 한 번 정한 값은 바꾸지 않는다.
 * 도메인별로 구획을 나누고 새 코드는 자기 구획 끝에 붙인다.
 */
@Getter
public enum ErrorCode {

    // 공통
    INVALID_REQUEST(HttpStatus.BAD_REQUEST, "요청 값이 올바르지 않습니다."),
    UNAUTHORIZED(HttpStatus.UNAUTHORIZED, "인증이 필요합니다."),

    // 지갑 · 서명 (ANT-AUTH-04 · ANT-AUTH-06)
    INVALID_SIGNATURE(HttpStatus.BAD_REQUEST, "서명 형식이 올바르지 않습니다."),
    /** 발급받은 적이 없거나 이미 썼거나 5분이 지났다. 셋을 구분해 주지 않는다 — 대응은 "재발급"으로 같다. */
    NONCE_NOT_FOUND(HttpStatus.BAD_REQUEST, "인증 요청이 만료되었습니다. 다시 시도해주세요."),
    /** 서명에서 복원한 주소가 기대한 주소와 다르다. 변조이거나 다른 지갑으로 서명한 것이다. */
    SIGNER_MISMATCH(HttpStatus.UNAUTHORIZED, "서명한 지갑이 일치하지 않습니다."),
    /** 프론트가 이 코드를 보고 지갑 연동 화면으로 유도한다. */
    WALLET_NOT_LINKED(HttpStatus.BAD_REQUEST, "지갑을 먼저 연동해주세요."),
    WALLET_ALREADY_LINKED(HttpStatus.CONFLICT, "이미 연동된 지갑입니다.");

    private final HttpStatus status;
    private final String message;

    ErrorCode(HttpStatus status, String message) {
        this.status = status;
        this.message = message;
    }
}
