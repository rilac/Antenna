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
  // 멱등성
  IDEMPOTENCY_KEY_REQUIRED: 'IDEMPOTENCY_KEY_REQUIRED',
  IDEMPOTENCY_KEY_REUSED: 'IDEMPOTENCY_KEY_REUSED',
  // 신고 (ANT-COMMUNITY-04)
  TARGET_NOT_FOUND: 'TARGET_NOT_FOUND',
  SELF_REPORT: 'SELF_REPORT',
  DUPLICATE_REPORT: 'DUPLICATE_REPORT',
  // 예측 · 비동기 작업 · 모의투자 (백엔드 미구현)
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
} as const

/**
 * 인증 만료(M-08)인지 판별한다.
 *
 * 주의 — 명세상 UNAUTHENTICATED 하나가 "로그인 필요" 와 "서명 주소 불일치" 를
 * 모두 덮는다. 설계서 §6 은 둘을 다르게 처리하라고 하지만(M-08 vs 재서명 안내)
 * code 만으로는 가를 수 없다. 백엔드 협의 전까지는 지갑 서명이 오가는 요청에서만
 * 화면이 직접 재서명으로 분기한다.
 */
export function isUnauthenticated(e: ApiError) {
  return e.status === 401 && e.code === ERROR_CODE.UNAUTHENTICATED
}
