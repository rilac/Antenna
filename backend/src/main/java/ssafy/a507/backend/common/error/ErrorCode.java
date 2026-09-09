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
    /** 용량과 별개다 — 헤더만 큰 크기를 선언한 작은 파일이 렌더 시점에 클라이언트를 마비시킨다. */
    IMAGE_TOO_LARGE(HttpStatus.BAD_REQUEST, "이미지 크기가 너무 큽니다."),

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

    // 모의투자 시즌 (ANT-SEASON-01)
    /**
     * 시즌이 없다. 관리자 전용 모드(DEMO)를 일반 사용자가 부른 것도 이 code 다 —
     * 403 으로 답하면 "그런 시즌이 있다" 는 사실이 새고, 시연 시즌은 존재 자체가 비공개다.
     */
    SEASON_NOT_FOUND(HttpStatus.NOT_FOUND, "시즌을 찾을 수 없습니다."),
    /** 그 시즌에 없는 종목. 남의 시즌 종목 id 를 넣어 가격을 떠보는 것도 여기로 막힌다. */
    SEASON_TICKER_NOT_FOUND(HttpStatus.NOT_FOUND, "시즌 종목을 찾을 수 없습니다."),
    /** 진행 중인 회차가 있다. 끝내야 다음 회차를 시작할 수 있다(ANT-SEASON-03). */
    SEASON_ALREADY_JOINED(HttpStatus.CONFLICT, "이미 진행 중인 회차가 있습니다."),
    /** RUNNING 이 아닌 시즌에는 참가할 수 없다. */
    SEASON_NOT_RUNNING(HttpStatus.CONFLICT, "참가할 수 있는 상태의 시즌이 아닙니다."),
    /** 대회 참가는 참가비 소각 서명이 붙는다 — 아직 없다(ANT-SEASON-06 · TOKEN). */
    SEASON_JOIN_NOT_SUPPORTED(HttpStatus.NOT_IMPLEMENTED, "대회 참가는 아직 지원하지 않습니다."),
    /** 참가한 적이 없는 시즌의 현황·주문·체결 내역을 불렀다. 참가가 먼저다(ANT-SEASON-03). */
    SEASON_NOT_JOINED(HttpStatus.CONFLICT, "참가하지 않은 시즌입니다."),
    /** 매도 수량이 보유 수량을 넘는다. 매수의 예수금 부족은 INSUFFICIENT_BALANCE 다. */
    SEASON_INSUFFICIENT_QTY(HttpStatus.CONFLICT, "보유 수량이 부족합니다."),
    /** 그 종목에 내 진행일 봉이 없다. 게임일 구간은 전 종목이 빠짐없이 있어야 하므로 데이터 결함이다. */
    SEASON_PRICE_NOT_FOUND(HttpStatus.NOT_FOUND, "그 게임일의 가격이 없습니다."),
    /** expectedDay 가 서버의 현재 게임일과 다르다 — 낙관적 잠금(ANT-SEASON-04). 화면은 다시 읽고 다시 누른다. */
    DAY_MISMATCH(HttpStatus.CONFLICT, "보고 있는 게임일이 서버와 다릅니다."),
    /** 대회는 공용 진행일이라 개인이 넘길 수 없다 — 배치 B5 가 넘긴다(ANT-SEASON-08). */
    SEASON_ADVANCE_NOT_ALLOWED(HttpStatus.CONFLICT, "대회 시즌은 수동으로 진행할 수 없습니다."),
    /** 끝난(DONE) 회차에 주문·진행을 보냈다. 다시 하려면 새 회차로 참가한다. */
    SEASON_ATTEMPT_ENDED(HttpStatus.CONFLICT, "끝난 회차입니다."),
    /** 마지막 게임일에서 더 넘기려 했다. 종료(finish)가 다음 단계다. */
    SEASON_LAST_DAY(HttpStatus.CONFLICT, "마지막 게임일입니다. 종료해 주세요."),
    /** 마지막 게임일 전에 종료하려 했다. 연습은 끝까지 가야 결과가 있다. */
    SEASON_NOT_LAST_DAY(HttpStatus.CONFLICT, "아직 마지막 게임일이 아닙니다."),
    /** 끝나지 않은 회차의 결과를 불렀다. 결과는 finish 가 만든다. */
    SEASON_RESULT_NOT_FOUND(HttpStatus.NOT_FOUND, "아직 결과가 없습니다."),
    SEASON_REVIEW_FAILED(HttpStatus.SERVICE_UNAVAILABLE, "AI 복기를 만들지 못했습니다. 잠시 후 다시 시도해 주세요."),

    // AI 브리핑 (ANT-RESEARCH-03)
    BRIEFING_NOT_FOUND(HttpStatus.NOT_FOUND, "브리핑을 찾을 수 없습니다."),

    // 투자 포인트 (ANT-RESEARCH-04) — 요청 시점 생성이 실패했다. 다시 부르면 다시 만든다.
    POINT_GENERATION_FAILED(HttpStatus.SERVICE_UNAVAILABLE, "투자 포인트를 만들지 못했습니다. 잠시 후 다시 시도해 주세요."),

    // 관심 종목 (ANT-DATA-06)
    /** 이미 담은 종목을 다시 담았다. 빼기(DELETE)는 멱등(204)이라 이 code 를 쓰지 않는다. */
    DUPLICATE_WATCHLIST_ITEM(HttpStatus.CONFLICT, "이미 관심 종목에 있습니다."),

    // 지갑 · 서명 (ANT-AUTH-04 · ANT-AUTH-06)
    INVALID_SIGNATURE(HttpStatus.BAD_REQUEST, "서명 형식이 올바르지 않습니다."),
    /** 발급받은 적이 없거나 이미 썼거나 5분이 지났다. 셋을 구분해 주지 않는다 — 대응은 "재발급"으로 같다. */
    NONCE_NOT_FOUND(HttpStatus.BAD_REQUEST, "인증 요청이 만료되었습니다. 다시 시도해주세요."),
    /** 서명에서 복원한 주소가 기대한 주소와 다르다. 변조이거나 다른 지갑으로 서명한 것이다. */
    SIGNER_MISMATCH(HttpStatus.UNAUTHORIZED, "서명한 지갑이 일치하지 않습니다."),
    /** 프론트가 이 코드를 보고 지갑 연동 화면으로 유도한다. */
    WALLET_NOT_LINKED(HttpStatus.BAD_REQUEST, "지갑을 먼저 연동해주세요."),
    WALLET_ALREADY_LINKED(HttpStatus.CONFLICT, "이미 연동된 지갑입니다."),

    // 온체인 (ANT-CHAIN-05)
    /**
     * RPC 노드에 닿지 못했거나 릴레이어가 설정되지 않았다. 온체인 동반 요청만 이 코드로 실패하고
     * 조회 API 는 정상이다(명세 §1). 503 인 이유: 서버 잘못이 아니라 의존 서비스가 없는 상태라서다.
     */
    CHAIN_UNAVAILABLE(HttpStatus.SERVICE_UNAVAILABLE, "블록체인 네트워크에 연결할 수 없습니다. 잠시 후 다시 시도해주세요."),

    // 예측 등록 (ANT-PRED-01)
    /**
     * 오늘 무료 슬롯(3건)을 다 썼다. 명세는 "서명 동반 소각(202) 경로로 전환" 이라는 뜻으로 이 code 를 두었다 —
     * 소각할 토큰(ANT-CHAIN-03)이 생기면 그 재요청을 받는 202 경로가 여기에 붙는다. 지금은 여기서 끝난다.
     */
    PREDICTION_SLOT_EXCEEDED(HttpStatus.CONFLICT, "오늘 무료 예측 슬롯을 모두 사용했습니다."),

    // 온체인 검증 (ANT-CHAIN-06)
    /** 없는 batchId 다. 체인에는 있는데 DB 에 없는 번호(데모 소모분)도 여기로 온다 — 원장은 DB 기준이다. */
    ANCHOR_NOT_FOUND(HttpStatus.NOT_FOUND, "앵커 배치를 찾을 수 없습니다."),
    /**
     * 미판정(BASE/OPEN) 예측을 작성자도 구독자도 아닌 사람이 열었다. 404 로 숨기지 않는다 — 명세 §예측 공개 규칙은
     * "존재와 커밋 무결성은 공개" 이고, 프론트가 이 code 로 구독 CTA(SubscriptionGate)를 띄운다.
     */
    PREDICTION_FORBIDDEN(HttpStatus.FORBIDDEN, "판정 전 예측은 작성자와 구독자만 볼 수 있습니다.");

    private final HttpStatus status;
    private final String message;

    ErrorCode(HttpStatus status, String message) {
        this.status = status;
        this.message = message;
    }
}
