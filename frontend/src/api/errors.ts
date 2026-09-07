/* 오류 계약. API 명세서 §오류 계약 · 설계서 §6.

   본문은 { code, message, field? } 세 필드로 고정이다. HTTP 상태는 본문에 없으므로
   응답에서 따로 받는다. 화면은 code 로 분기하고 message 를 그대로 노출하지 않는다. */
import type { ApiErrorBody } from './types'

export class ApiError extends Error {
  readonly code: string
  readonly status: number
  /** 입력값 오류일 때만 채워진다. 화면이 그 필드 옆에 오류를 붙인다 */
  readonly field?: string

  constructor(body: ApiErrorBody, status: number) {
    // message 는 로깅·디버깅용이다. 화면에는 code 로 고른 문구를 쓴다.
    super(body.message)
    this.name = 'ApiError'
    this.code = body.code
    this.status = status
    this.field = body.field
  }
}

/** 응답이 오류 계약을 따르는지 확인한다. 아니면 상태 코드만으로 만들어 준다. */
export function toApiError(status: number, body: unknown): ApiError {
  if (
    body && typeof body === 'object' &&
    'code' in body && typeof (body as ApiErrorBody).code === 'string'
  ) {
    const b = body as ApiErrorBody
    return new ApiError({ code: b.code, message: b.message ?? '', field: b.field }, status)
  }
  return new ApiError({ code: 'UNKNOWN', message: '' }, status)
}

/* API 명세서의 code 어휘표. 이 표가 프론트 분기의 유일한 원본이며,
   표에 없는 code 를 즉석에서 만들지 않는다. 도메인 code 는 백엔드가
   스토리를 구현하며 표에 추가하면 여기에도 함께 반영한다. */
export const ERROR_CODE = {
  // 공통 3개 — 전 API 가 공유한다
  INVALID_REQUEST: 'INVALID_REQUEST',
  UNAUTHENTICATED: 'UNAUTHENTICATED',
  INTERNAL_ERROR: 'INTERNAL_ERROR',
  // 인증 (ANT-AUTH-02)
  PROVIDER_NOT_SUPPORTED: 'PROVIDER_NOT_SUPPORTED',
  ACCOUNT_BANNED: 'ACCOUNT_BANNED',
  // 멱등성
  IDEMPOTENCY_KEY_REQUIRED: 'IDEMPOTENCY_KEY_REQUIRED',
  IDEMPOTENCY_KEY_REUSED: 'IDEMPOTENCY_KEY_REUSED',
  // 회원 (ANT-AUTH-03)
  USER_NOT_FOUND: 'USER_NOT_FOUND',
  DUPLICATE_NICKNAME: 'DUPLICATE_NICKNAME',
  // 신고 (ANT-COMMUNITY-04)
  TARGET_NOT_FOUND: 'TARGET_NOT_FOUND',
  SELF_REPORT: 'SELF_REPORT',
  DUPLICATE_REPORT: 'DUPLICATE_REPORT',
  // 피드 글 첨부 (ANT-COMMUNITY-02)
  POST_NOT_FOUND: 'POST_NOT_FOUND',
  REPORT_NOT_FOUND: 'REPORT_NOT_FOUND',
  PREDICTION_NOT_FOUND: 'PREDICTION_NOT_FOUND',
  // 지갑 · 서명 (ANT-AUTH-04 · ANT-AUTH-06)
  INVALID_SIGNATURE: 'INVALID_SIGNATURE',
  NONCE_NOT_FOUND: 'NONCE_NOT_FOUND',
  SIGNER_MISMATCH: 'SIGNER_MISMATCH',
  WALLET_NOT_LINKED: 'WALLET_NOT_LINKED',
  WALLET_ALREADY_LINKED: 'WALLET_ALREADY_LINKED',
  // 댓글 · 좋아요 (ANT-COMMUNITY-03)
  COMMENT_NOT_FOUND: 'COMMENT_NOT_FOUND',
  DUPLICATE_LIKE: 'DUPLICATE_LIKE',
  // 업로드 (ANT-COMMUNITY-06)
  UPLOAD_FILE_NOT_FOUND: 'UPLOAD_FILE_NOT_FOUND',
  UNSUPPORTED_IMAGE_TYPE: 'UNSUPPORTED_IMAGE_TYPE',
  INVALID_IMAGE_RATIO: 'INVALID_IMAGE_RATIO',
  FILE_TOO_LARGE: 'FILE_TOO_LARGE',
  IMAGE_TOO_LARGE: 'IMAGE_TOO_LARGE',
  // 광고 · 비동기 작업 (ANT-COMMUNITY-05)
  AD_SLOT_SOLD_OUT: 'AD_SLOT_SOLD_OUT',
  OPERATION_NOT_FOUND: 'OPERATION_NOT_FOUND',
  OPERATION_FORBIDDEN: 'OPERATION_FORBIDDEN',
  // 시세 · 리서치 (ANT-DATA · ANT-RESEARCH)
  STOCK_NOT_FOUND: 'STOCK_NOT_FOUND',
  BRIEFING_NOT_FOUND: 'BRIEFING_NOT_FOUND',
  DUPLICATE_WATCHLIST_ITEM: 'DUPLICATE_WATCHLIST_ITEM',
  // 온체인 (ANT-CHAIN)
  CHAIN_UNAVAILABLE: 'CHAIN_UNAVAILABLE',
  ANCHOR_NOT_FOUND: 'ANCHOR_NOT_FOUND',
  /** 미판정 예측의 검산은 작성자·유효 구독자만 볼 수 있다 */
  PREDICTION_FORBIDDEN: 'PREDICTION_FORBIDDEN',
  // 예측 · 모의투자 (백엔드 미구현)
  PREDICTION_SLOT_EXCEEDED: 'PREDICTION_SLOT_EXCEEDED',
  INSUFFICIENT_BALANCE: 'INSUFFICIENT_BALANCE',
  DAY_MISMATCH: 'DAY_MISMATCH',
} as const

/* 프론트가 자체로 만드는 code. 서버 어휘와 겹치지 않게 접두어를 붙인다. */
export const CLIENT_ERROR_CODE = {
  /** 응답이 JSON 이 아니다 — 백엔드 연동 전 SPA fallback 등 */
  CLIENT_NOT_JSON: 'CLIENT_NOT_JSON',
  /** 오류 계약을 따르지 않는 응답 */
  UNKNOWN: 'UNKNOWN',

  /* 지갑은 브라우저 확장에서 실패할 수 있다. 서버가 볼 일이 없는 사유라
     서버 어휘에 없고, 그래서 여기서 만든다. 셋을 한 문구로 합치지 않는다 —
     설치·재시도·확장 열기로 사용자가 할 일이 서로 다르다(M-01 설계 제약). */
  /** window.ethereum 이 없다 — 지갑 확장 미설치 */
  CLIENT_WALLET_MISSING: 'CLIENT_WALLET_MISSING',
  /** 사용자가 연결·서명 창에서 거부했다 (EIP-1193 4001) */
  CLIENT_SIGN_REJECTED: 'CLIENT_SIGN_REJECTED',
  /** 이미 뜬 지갑 창이 응답을 기다리는 중이다 (EIP-1193 -32002) */
  CLIENT_WALLET_BUSY: 'CLIENT_WALLET_BUSY',
  /** 잠긴 지갑 등으로 계정을 하나도 못 받았다 */
  CLIENT_NO_ACCOUNT: 'CLIENT_NO_ACCOUNT',
} as const

/**
 * 인증 만료(M-08)인지 판별한다.
 *
 * 401 이라고 다 세션 만료가 아니다 — 서명 주소 불일치(SIGNER_MISMATCH)도 401 이다.
 * 그쪽은 재서명으로 풀리므로 로그아웃시키면 안 된다. 그래서 status 가 아니라
 * code 로 가른다. (백엔드가 두 사유를 별도 code 로 나눠 주어 갈 수 있게 됐다.)
 */
export function isUnauthenticated(e: ApiError) {
  return e.status === 401 && e.code === ERROR_CODE.UNAUTHENTICATED
}

/**
 * 구독으로 풀리는 잠금인지 판별한다(설계서 §5).
 *
 * isUnauthenticated 와 나란히 여기 둔다 — 어떤 오류가 무슨 뜻인지는
 * 오류 계약의 일이고, 화면은 그 판단을 받아 그리기만 한다.
 *
 * 403 은 "없다" 가 아니라 "아직 못 본다" 다. 404 로 그리면 존재가 감춰져
 * 구독 유인이 사라지므로, 호출부는 이 판별이 참일 때 잠금 카드를 그린다.
 */
export function isSubscriptionGated(e: ApiError | null | undefined): boolean {
  return e?.status === 403
}
