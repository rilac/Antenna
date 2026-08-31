/* 오류 계약. 설계서 §6 — code 로 분기하고 message 를 그대로 노출하지 않는다. */
import type { ApiErrorBody } from './types'

export class ApiError extends Error {
  readonly code: string
  readonly status: number

  constructor(body: ApiErrorBody) {
    // message 는 로깅·디버깅용이다. 화면에는 code 로 고른 문구를 쓴다.
    super(body.message)
    this.name = 'ApiError'
    this.code = body.code
    this.status = body.status
  }
}

/** 응답이 오류 계약을 따르는지 확인한다. 아니면 상태 코드만으로 만들어 준다. */
export function toApiError(status: number, body: unknown): ApiError {
  if (
    body && typeof body === 'object' &&
    'code' in body && typeof (body as ApiErrorBody).code === 'string'
  ) {
    const b = body as ApiErrorBody
    return new ApiError({ code: b.code, message: b.message ?? '', status: b.status ?? status })
  }
  return new ApiError({ code: 'UNKNOWN', message: '', status })
}

/* 설계서 §6 에 적힌, 화면이 분기해야 하는 code 들.
   문구는 화면마다 다르므로 여기서는 판별만 돕는다. */
export const ERROR_CODE = {
  /** 클라이언트 결함이라 사용자에게 노출하지 않고 키 발급 후 재요청한다 */
  IDEMPOTENCY_KEY_REQUIRED: 'IDEMPOTENCY_KEY_REQUIRED',
  IDEMPOTENCY_KEY_REUSED: 'IDEMPOTENCY_KEY_REUSED',
  /** G-04 진행 낙관적 잠금 — 서버 currentDay 로 동기화 후 재시도 */
  DAY_MISMATCH: 'DAY_MISMATCH',
} as const

/** 401 중 인증 만료(M-08 행)와 지갑 서명 주소 불일치(재서명 안내)를 가른다. */
export function isSignatureMismatch(e: ApiError) {
  return e.status === 401 && e.code !== 'UNAUTHORIZED'
}
