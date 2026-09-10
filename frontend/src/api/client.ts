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

/**
 * 지금 들고 있는 토큰. **이 파일 밖에서 fetch 를 직접 쓸 때만** 쓴다.
 *
 * 유일한 사용처는 이미지 업로드(M-07)다 — 아래 request 가 본문을 항상 JSON 으로 굳히므로
 * multipart 를 보낼 수 없고, 진행률도 fetch 로는 못 읽어(업로드 이벤트가 없다) XHR 을 쓴다.
 * 화면 코드가 이 값을 직접 헤더에 넣는 일은 없어야 한다.
 */
export function getAccessToken() {
  return accessToken
}

/** 업로드가 401 을 재발급 후 재시도할 때 필요하다. request 의 재시도 규칙을 그대로 흉내 낸다. */
export const API_BASE = BASE

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
    /* 본문을 여기서 한 번 읽어 둔다. 아래에서 재시도하면 res 가 갈리므로
       원래 401 이 무엇이었는지는 지금이 아니면 알 수 없다. */
    const unauthorized = toApiError(401, await res.json().catch(() => null))

    /* ── 401 이라고 다 세션 만료가 아니다 (설계 제약) ──────────────
       서명한 지갑이 다르면 서버는 SIGNER_MISMATCH 로 401 을 준다(ErrorCode). 세션은
       멀쩡하니 재발급은 성공하고, 그러면 **같은 요청이 한 번 더 나간다.** 그런데
       SignatureGuard 는 검증보다 먼저 nonce 를 태우므로(recover 주석: "실패해도 nonce는
       이미 소비된 상태다") 두 번째 요청은 NONCE_NOT_FOUND 로 끝난다.

       결과적으로 화면은 "서명한 지갑이 다릅니다" 대신 "인증 요청이 만료되었습니다" 를
       말하게 된다 — 원인을 가리키지 못하는 데다, 사용자는 지갑을 바꿀 생각을 못 하고
       처음부터 다시 시도하다 같은 자리에서 또 막힌다.

       그래서 재발급으로 풀릴 수 있는 401(UNAUTHENTICATED)만 아래로 보낸다. */
    if (!isUnauthenticated(unauthorized)) throw unauthorized

    if (await refreshAccessToken()) {
      // 쿠키가 살아 있으니 세션은 유효하다. 원래 하려던 요청을 그대로 잇는다.
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
      throw unauthorized
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
    /* 여기 남는 401 은 **재발급에 성공한 뒤 다시 받은** 것이다. 세션은 살아 있는데도
       거부됐다는 뜻이라 로그아웃 대상이 아니고 화면이 처리한다. */
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
