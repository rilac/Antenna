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

    // 인증 (ANT-AUTH-01 · ANT-AUTH-02)
    /** 어휘에는 있지만 아직 붙이지 않은 프로바이더. 프론트 오타(INVALID_REQUEST)와 구분한다. */
    PROVIDER_NOT_SUPPORTED(HttpStatus.NOT_IMPLEMENTED, "아직 지원하지 않는 로그인 방식입니다."),
    /** 제재로 막힌 계정. 다시 로그인해도 풀리지 않으므로 401 이 아니라 403 이다. */
    ACCOUNT_BANNED(HttpStatus.FORBIDDEN, "이용이 제한된 계정입니다."),

    // 멱등성 (명세 §1)
    IDEMPOTENCY_KEY_REQUIRED(HttpStatus.BAD_REQUEST, "Idempotency-Key 헤더가 필요합니다."),
    IDEMPOTENCY_KEY_REUSED(HttpStatus.CONFLICT, "같은 Idempotency-Key 로 다른 요청을 보냈습니다."),

    // 회원 (ANT-AUTH-03)
    USER_NOT_FOUND(HttpStatus.NOT_FOUND, "회원을 찾을 수 없습니다."),
    DUPLICATE_NICKNAME(HttpStatus.CONFLICT, "이미 사용 중인 닉네임입니다."),

    // 신고 (ANT-COMMUNITY-04)
    TARGET_NOT_FOUND(HttpStatus.NOT_FOUND, "신고 대상을 찾을 수 없습니다."),
    SELF_REPORT(HttpStatus.BAD_REQUEST, "자신을 신고할 수 없습니다."),
    DUPLICATE_REPORT(HttpStatus.CONFLICT, "이미 접수된 신고입니다."),

    // 피드 글 (ANT-COMMUNITY-02)
    POST_NOT_FOUND(HttpStatus.NOT_FOUND, "글을 찾을 수 없습니다."),
    REPORT_NOT_FOUND(HttpStatus.NOT_FOUND, "리포트를 찾을 수 없습니다."),
    PREDICTION_NOT_FOUND(HttpStatus.NOT_FOUND, "예측을 찾을 수 없습니다."),

    // 댓글 · 좋아요 (ANT-COMMUNITY-03)
    COMMENT_NOT_FOUND(HttpStatus.NOT_FOUND, "댓글을 찾을 수 없습니다."),
    /** 이미 누른 좋아요를 다시 눌렀다. 취소는 멱등(204)이지만 등록은 중복을 알려준다. */
    DUPLICATE_LIKE(HttpStatus.CONFLICT, "이미 좋아요를 누른 대상입니다."),

    // 업로드 (ANT-COMMUNITY-06)
    /** 다른 API 가 넘겨받은 fileId 가 upload_files 에 없다. 남의 fileId 를 지목한 경우도 여기로 온다. */
    UPLOAD_FILE_NOT_FOUND(HttpStatus.NOT_FOUND, "업로드한 파일을 찾을 수 없습니다."),
    UNSUPPORTED_IMAGE_TYPE(HttpStatus.BAD_REQUEST, "png · jpeg · webp 이미지만 올릴 수 있습니다."),
    /** 배너는 노출 자리가 고정이라 비율이 어긋나면 잘리거나 늘어난다. 올리는 시점에 막는다. */
    INVALID_IMAGE_RATIO(HttpStatus.BAD_REQUEST, "배너 이미지 비율이 규격과 다릅니다."),
    FILE_TOO_LARGE(HttpStatus.PAYLOAD_TOO_LARGE, "파일 용량이 5MB 를 넘습니다."),

    // 광고 (ANT-COMMUNITY-05)
    /** 고정 단가 × 기간 만큼의 토큰이 없다. 명세 §광고의 409 두 사유 중 하나다. */
    INSUFFICIENT_BALANCE(HttpStatus.CONFLICT, "토큰 잔액이 부족합니다."),
    /** 같은 기간에 이미 슬롯이 다 찼다. 선착순이므로 기다렸다 다시 신청하는 수밖에 없다. */
    AD_SLOT_SOLD_OUT(HttpStatus.CONFLICT, "해당 기간의 광고 슬롯이 마감되었습니다."),

    // 작업 리소스 (ANT-COMMUNITY-07)
    /** 없는 operationId 이거나 종료 후 24h 가 지나 정리된 작업이다. 둘을 구분해 주지 않는다. */
    OPERATION_NOT_FOUND(HttpStatus.NOT_FOUND, "작업을 찾을 수 없습니다."),
    /** 남의 작업이다. 404 로 숨기지 않는 이유는 명세가 403 으로 못박았기 때문이다. */
    OPERATION_FORBIDDEN(HttpStatus.FORBIDDEN, "다른 사용자의 작업입니다."),

    // 시세 (ANT-DATA-03)
    /** 종목코드가 종목 마스터에 없다. 구간에 시세가 없는 것(200 + 빈 목록)과 구분한다. */
    STOCK_NOT_FOUND(HttpStatus.NOT_FOUND, "종목을 찾을 수 없습니다."),

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
