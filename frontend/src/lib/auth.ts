// 구글 OAuth 인가 코드 방식. 프론트는 code 만 받아 서버로 넘기고,
// 교환·검증과 토큰 발급은 서버가 한다.

const API_BASE = import.meta.env.VITE_API_BASE_URL ?? 'http://localhost:8080/api/v1'
const GOOGLE_CLIENT_ID = import.meta.env.VITE_GOOGLE_CLIENT_ID ?? ''
const GOOGLE_AUTH_URL = 'https://accounts.google.com/o/oauth2/v2/auth'

/** 구글 콘솔에 등록한 리디렉션 URI 와 문자 하나까지 같아야 한다. */
export const REDIRECT_PATH = '/oauth/callback/google'

const AUTH_KEY = 'antena.auth'
const TOKEN_KEY = 'antena.accessToken'
const STATE_KEY = 'antena.oauthState'

export type LoginUser = {
  id: number
  nickname: string
  walletLinked: boolean
  isNew: boolean
}

export function redirectUri() {
  return window.location.origin + REDIRECT_PATH
}

export function accessToken() {
  try { return localStorage.getItem(TOKEN_KEY) } catch { return null }
}

/** 구글 동의 화면으로 보낸다. state 는 콜백에서 대조할 CSRF 값. */
export function startGoogleLogin() {
  const state = crypto.randomUUID()
  sessionStorage.setItem(STATE_KEY, state)
  const params = new URLSearchParams({
    client_id: GOOGLE_CLIENT_ID,
    redirect_uri: redirectUri(),
    response_type: 'code',
    scope: 'openid email profile',
    state,
    prompt: 'select_account',
  })
  window.location.assign(`${GOOGLE_AUTH_URL}?${params}`)
}

/** 콜백에서 받은 code 를 서버에 넘겨 accessToken 을 받는다. Refresh 는 httpOnly 쿠키로 온다. */
export async function completeGoogleLogin(code: string, state: string | null): Promise<LoginUser> {
  const expected = sessionStorage.getItem(STATE_KEY)
  sessionStorage.removeItem(STATE_KEY)
  if (!expected || expected !== state) {
    throw new Error('요청이 위조되었을 수 있습니다. 다시 로그인해 주세요.')
  }

  const response = await fetch(`${API_BASE}/auth/login/google`, {
    method: 'POST',
    // refresh 쿠키를 받아야 하므로 자격증명을 포함한다.
    credentials: 'include',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ code, redirectUri: redirectUri() }),
  })
  if (!response.ok) {
    throw new Error(`로그인에 실패했습니다. (${response.status})`)
  }

  const body: { accessToken: string; user: LoginUser } = await response.json()
  // 프로토타입 단계라 localStorage 에 둔다. XSS 에 노출되므로 운영 전에 메모리 보관 + 새로고침 시
  // /auth/refresh 재발급 방식으로 바꿀 것.
  localStorage.setItem(TOKEN_KEY, body.accessToken)
  localStorage.setItem(AUTH_KEY, '1')
  return body.user
}
