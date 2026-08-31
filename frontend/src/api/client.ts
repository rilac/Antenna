/* API 클라이언트. 설계서 §1 — Base /api/v1, 전 API Authorization: Bearer.

   accessToken 은 메모리에만 둔다. refresh 는 httpOnly 쿠키라 클라이언트가
   저장하거나 읽지 않는다(설계서 §4 A-01). */
import { ApiError, toApiError } from './errors'
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

const REFRESH_PATH = '/auth/refresh'

/** 백엔드 ErrorCode.UNAUTHENTICATED 와 짝을 이룬다. */
const SESSION_EXPIRED_CODE = 'UNAUTHENTICATED'

/** 본문 없는 401(리소스 서버가 토큰을 거부한 경우)도 세션 만료로 본다. */
function isSessionExpiry(code: string | undefined) {
  return !code || code === SESSION_EXPIRED_CODE || code === 'UNAUTHORIZED'
}

/* 재발급이 동시에 여러 번 나가면 회전된 토큰끼리 서로를 무효화해
   재사용 탐지에 걸린다. 진행 중인 요청 하나를 모두가 함께 기다린다. */
let refreshing: Promise<boolean> | null = null

/**
 * refresh 쿠키로 access 를 되찾는다. 쿠키가 없거나 만료면 false.
 * 실패를 예외로 올리지 않는다 — 호출부는 "다시 시도할 수 있는가"만 알면 된다.
 */
export function refreshAccessToken(): Promise<boolean> {
  if (refreshing) return refreshing

  refreshing = fetch(BASE + REFRESH_PATH, { method: 'POST', credentials: 'include' })
    .then(async (res) => {
      if (!res.ok) return false
      const body = (await res.json().catch(() => null)) as { accessToken?: string } | null
      if (!body?.accessToken) return false
      setAccessToken(body.accessToken)
      return true
    })
    .catch(() => false)
    .finally(() => { refreshing = null })

  return refreshing
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

  const send = () => {
    const headers: Record<string, string> = {}
    // 재발급 뒤 재시도할 때 새 토큰을 쓰려면 헤더를 매번 다시 만들어야 한다.
    if (accessToken) headers.Authorization = `Bearer ${accessToken}`
    if (body !== undefined) headers['Content-Type'] = 'application/json'
    // 같은 scope 면 재시도해도 같은 키가 나가므로 중복 실행이 생기지 않는다.
    if (idempotencyScope) headers['Idempotency-Key'] = idempotencyKey(idempotencyScope)

    return fetch(buildUrl(path, query), {
      method,
      headers,
      body: body === undefined ? undefined : JSON.stringify(body),
      // refresh 쿠키를 함께 보낸다
      credentials: 'include',
      signal,
    })
  }

  let res = await send()

  /* access 가 만료됐을 뿐이면 사용자 눈에 띄지 않게 되살린다.
     재발급 자체(/auth/refresh)가 401 이면 쿠키가 죽은 것이라 재시도하지 않는다. */
  if (res.status === 401 && path !== REFRESH_PATH) {
    // 401 이라고 다 세션 만료가 아니다 — 지갑 서명 주소 불일치는 화면이 처리한다.
    const peeked = await res.json().catch(() => null)
    const code = (peeked as { code?: string } | null)?.code
    if (!isSessionExpiry(code)) throw toApiError(401, peeked)

    const revived = await refreshAccessToken()
    if (revived) res = await send()

    if (!revived || res.status === 401) {
      setAccessToken(null)
      onUnauthorized()
      throw new ApiError({ code: code ?? SESSION_EXPIRED_CODE, message: '', status: 401 })
    }
  }

  if (res.status === 204) return undefined as T

  /* 백엔드가 아직 없으면 SPA fallback 이 index.html 을 200 으로 돌려준다.
     그대로 두면 화면이 undefined 를 그리다 터지므로 여기서 걸러낸다. */
  const contentType = res.headers.get('Content-Type') ?? ''
  if (!contentType.includes('application/json')) {
    throw new ApiError({ code: 'NOT_JSON', message: `expected JSON, got ${contentType}`, status: res.status })
  }

  const payload = await res.json().catch(() => null)

  if (!res.ok) {
    // 401 은 위에서 재발급까지 시도하고 onUnauthorized 도 이미 불렀다.
    throw toApiError(res.status, payload)
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
