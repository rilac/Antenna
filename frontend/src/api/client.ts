/* API 클라이언트. 설계서 §1 — Base /api/v1, 전 API Authorization: Bearer.

   accessToken 은 메모리에만 둔다. refresh 는 httpOnly 쿠키라 클라이언트가
   저장하거나 읽지 않는다(설계서 §4 A-01). */
import { ApiError, CLIENT_ERROR_CODE, toApiError } from './errors'
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
    if (await refreshAccessToken()) {
      /* 쿠키가 살아 있으니 세션은 유효하다. 재시도하고, 그래도 401 이면
         만료가 아니라 권한·서명 문제이므로 로그아웃시키지 않고 화면에 넘긴다
         — 명세상 UNAUTHENTICATED 하나가 두 경우를 덮는다(errors.ts 주석). */
      res = await send()
    } else {
      /* 토큰을 쥔 적이 없으면 이 401 은 세션 만료가 아니다 — Authorization 헤더를
         보낸 적이 없기 때문이다. 임시 로그인으로 화면만 보는 중이거나, 백엔드에
         그 API 가 아직 없는 경우다(Spring Security 가 라우팅보다 먼저 걸러서
         없는 경로도 404 가 아니라 401 로 돌려준다). 그때 로그아웃시키면
         화면을 열자마자 로그인으로 튕겨 아무것도 못 본다. */
      const hadToken = accessToken !== null
      setAccessToken(null)
      if (hadToken) onUnauthorized()
      throw toApiError(401, await res.json().catch(() => null))
    }
  }

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
    /* 401 은 위에서 이미 갈랐다 — 재발급이 실패한 경우만 onUnauthorized 로 넘어갔고,
       여기 남는 401 은 세션이 살아 있는데도 거부된 것이라 화면이 처리한다. */
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
