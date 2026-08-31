/* 구글 OAuth 인가 코드 방식. 프론트는 code 만 받아 서버로 넘기고,
   교환·검증과 토큰 발급은 서버가 한다. client_secret 은 프론트에 두지 않는다.

   accessToken 은 api/client 의 메모리에만 둔다. refresh 는 httpOnly 쿠키라
   클라이언트가 저장하거나 읽지 않는다(설계서 §4 A-01). */
import { request, setAccessToken } from '../api/client'
import type { AuthUser } from './context'

const CLIENT_ID = import.meta.env.VITE_GOOGLE_CLIENT_ID ?? ''
const GOOGLE_AUTH_URL = 'https://accounts.google.com/o/oauth2/v2/auth'

/** 구글 콘솔의 승인된 리디렉션 URI 와 문자 하나까지 같아야 한다. */
export const REDIRECT_PATH = '/oauth/callback/google'

const STATE_KEY = 'antena.oauthState'

/** 백엔드 AuthDtos.LoginResponse 와 짝을 이룬다. */
type LoginResponse = {
  accessToken: string
  user: { id: number; nickname: string | null; walletLinked: boolean; isNew: boolean }
}

export type LoginResult = { user: AuthUser; isNew: boolean }

export function redirectUri() {
  return window.location.origin + REDIRECT_PATH
}

/** 구글 동의 화면으로 보낸다. state 는 콜백에서 대조할 CSRF 값. */
export function startGoogleLogin() {
  const state = crypto.randomUUID()
  sessionStorage.setItem(STATE_KEY, state)

  const params = new URLSearchParams({
    client_id: CLIENT_ID,
    redirect_uri: redirectUri(),
    response_type: 'code',
    scope: 'openid email profile',
    state,
    prompt: 'select_account',
  })
  window.location.assign(`${GOOGLE_AUTH_URL}?${params}`)
}

/** 콜백에서 받은 code 를 서버에 넘겨 세션을 연다. */
export async function completeGoogleLogin(code: string, state: string | null): Promise<LoginResult> {
  const expected = sessionStorage.getItem(STATE_KEY)
  sessionStorage.removeItem(STATE_KEY)
  if (!expected || expected !== state) {
    throw new Error('요청이 위조되었을 수 있습니다. 다시 로그인해 주세요.')
  }

  const body = await request<LoginResponse>('/auth/login/google', {
    method: 'POST',
    body: { code, redirectUri: redirectUri() },
  })

  setAccessToken(body.accessToken)

  return {
    user: {
      // 신규 가입은 닉네임이 비어 있다. 온보딩 화면이 생기면 isNew 로 분기한다.
      nickname: body.user.nickname ?? '',
      // 로그인 응답에 프로필 이미지가 없다. GET /users/me 가 생기면 그 값으로 바꾼다.
      avatarUrl: '/assets/antena-profile.png',
      // 로그인 응답에 role 이 없어 일반 사용자로 둔다.
      // 관리자 판별은 [ANT-FE-SESSION] 이 /users/me 를 붙일 때 채운다.
      role: 'USER',
      walletLinked: body.user.walletLinked,
    },
    isNew: body.user.isNew,
  }
}
