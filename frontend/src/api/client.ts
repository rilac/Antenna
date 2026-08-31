/* API 클라이언트. 설계서 §1 — Base /api/v1, 전 API Authorization: Bearer.

   accessToken 은 메모리에만 둔다. refresh 는 httpOnly 쿠키라 클라이언트가
   저장하거나 읽지 않는다(설계서 §4 A-01). */
import { ApiError, CLIENT_ERROR_CODE, isUnauthenticated, toApiError } from './errors'
import { idempotencyKey } from './idempotency'

const BASE = '/api/v1'

let accessToken: string | null = null

export function setAccessToken(token: string | null) {
  accessToken = token
}

/** 401 을 받았을 때 셸이 할 일(M-08 세션 만료)을 꽂아 두는 자리. */
type UnauthorizedHandler = () => void
let onUnauthorized: UnauthorizedHandler = () => {}
export function setUnauthorizedHandler(fn: UnauthorizedHandler) {
  onUnauthorized = fn
}

export type RequestOptions = {
  method?: 'GET' | 'POST' | 'PUT' | 'PATCH' | 'DELETE'
  /** 쿼리 파라미터. undefined 인 값은 빠진다 */
  query?: Record<string, string | number | boolean | undefined>
  body?: unknown
  /**
   * Idempotency-Key 를 붙일 scope.
   * 같은 scope 로 재시도하면 같은 키가 나간다 — 중복 실행을 막는 핵심이다.
   */
  idempotencyScope?: string
  signal?: AbortSignal
}

function buildUrl(path: string, query?: RequestOptions['query']) {
  const url = new URL(BASE + path, window.location.origin)
  if (query) {
    for (const [k, v] of Object.entries(query)) {
      if (v !== undefined) url.searchParams.set(k, String(v))
    }
  }
  return url.pathname + url.search
}

export async function request<T>(path: string, options: RequestOptions = {}): Promise<T> {
  const { method = 'GET', query, body, idempotencyScope, signal } = options

  const headers: Record<string, string> = {}
  if (accessToken) headers.Authorization = `Bearer ${accessToken}`
  if (body !== undefined) headers['Content-Type'] = 'application/json'
  if (idempotencyScope) headers['Idempotency-Key'] = idempotencyKey(idempotencyScope)

  const res = await fetch(buildUrl(path, query), {
    method,
    headers,
    body: body === undefined ? undefined : JSON.stringify(body),
    // refresh 쿠키를 함께 보낸다
    credentials: 'include',
    signal,
  })

  if (res.status === 204) return undefined as T

  /* 백엔드가 아직 없으면 SPA fallback 이 index.html 을 200 으로 돌려준다.
     그대로 두면 화면이 undefined 를 그리다 터지므로 여기서 걸러낸다. */
  const contentType = res.headers.get('Content-Type') ?? ''
  if (!contentType.includes('application/json')) {
    throw new ApiError(
      { code: CLIENT_ERROR_CODE.CLIENT_NOT_JSON, message: `expected JSON, got ${contentType}` },
      res.status,
    )
  }

  const payload = await res.json().catch(() => null)

  if (!res.ok) {
    const error = toApiError(res.status, payload)
    // 인증 만료는 전역에서 M-08 로 넘긴다.
    // 명세상 UNAUTHENTICATED 는 서명 주소 불일치도 덮으므로, 지갑 서명이 오가는
    // 요청은 화면이 직접 재서명으로 분기한다 — errors.ts isUnauthenticated 주석 참고.
    if (isUnauthenticated(error)) onUnauthorized()
    throw error
  }

  return payload as T
}

export const api = {
  get: <T>(path: string, options?: Omit<RequestOptions, 'method' | 'body'>) =>
    request<T>(path, { ...options, method: 'GET' }),
  post: <T>(path: string, body?: unknown, options?: Omit<RequestOptions, 'method' | 'body'>) =>
    request<T>(path, { ...options, method: 'POST', body }),
  put: <T>(path: string, body?: unknown, options?: Omit<RequestOptions, 'method' | 'body'>) =>
    request<T>(path, { ...options, method: 'PUT', body }),
  patch: <T>(path: string, body?: unknown, options?: Omit<RequestOptions, 'method' | 'body'>) =>
    request<T>(path, { ...options, method: 'PATCH', body }),
  delete: <T>(path: string, options?: Omit<RequestOptions, 'method' | 'body'>) =>
    request<T>(path, { ...options, method: 'DELETE' }),
}

export { ApiError }
