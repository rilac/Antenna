/* OAuth 인가 코드 방식. 프론트는 code 만 받아 서버로 넘기고,
   교환·검증과 토큰 발급은 서버가 한다. client_secret 은 프론트에 두지 않는다.

   accessToken 은 api/client 의 메모리에만 둔다. refresh 는 httpOnly 쿠키라
   클라이언트가 저장하거나 읽지 않는다(설계서 §4 A-01).

   프로바이더가 둘(구글 · SSAFY)이라 갈리는 값만 표로 두고 흐름은 하나로 쓴다
   — 인가 요청·state 대조·코드 교환이 프로바이더마다 다를 이유가 없다. */
import { request, setAccessToken } from '../api/client'
import type { AuthUser } from './context'

export const PROVIDERS = ['google', 'ssafy'] as const
export type Provider = (typeof PROVIDERS)[number]

type ProviderConfig = {
  /** 화면 문구용 이름. "구글 계정을 확인하고 있습니다" 처럼 쓰인다 */
  label: string
  clientId: string
  authorizationUri: string
  scope: string
  /** 이 프로바이더에만 붙는 인가 요청 파라미터 */
  extraParams?: Record<string, string>
}

const CONFIG: Record<Provider, ProviderConfig> = {
  google: {
    label: '구글',
    clientId: import.meta.env.VITE_GOOGLE_CLIENT_ID ?? '',
    authorizationUri: 'https://accounts.google.com/o/oauth2/v2/auth',
    scope: 'openid email profile',
    // 계정을 여러 개 쓰는 사람이 매번 고를 수 있게 한다
    extraParams: { prompt: 'select_account' },
  },
  /* 인가 요청은 /oauth 아래고 토큰·userInfo 는 /ssafy 아래다(개발자센터 문서).
     접두사가 달라 한쪽 주소로 다른 쪽을 유추할 수 없으니 둘 다 그대로 적는다.
     client_id 가 비면 isConfigured 가 false 라 로그인 버튼이 잠긴 채로 남는다. */
  ssafy: {
    label: 'SSAFY',
    clientId: import.meta.env.VITE_SSAFY_CLIENT_ID ?? '',
    authorizationUri: 'https://project.ssafy.com/oauth/sso-check',
    // SSAFY 는 인가 요청에 scope 를 받지 않는다 — 문서상 파라미터가 셋뿐이다.
    scope: '',
  },
}

const STATE_KEY = 'antena.oauthState'

/** 백엔드 AuthDtos.LoginResponse 와 짝을 이룬다. */
type LoginResponse = {
  accessToken: string
  user: { id: number; nickname: string | null; walletLinked: boolean; isNew: boolean }
}

export type LoginResult = { user: AuthUser; isNew: boolean }

/** 콜백 경로의 :provider 는 아무 문자열이나 올 수 있다. 표에 있는 값만 통과시킨다. */
export function isProvider(value: string | undefined): value is Provider {
  return PROVIDERS.includes(value as Provider)
}

export function providerLabel(provider: Provider) {
  return CONFIG[provider].label
}

/** 자격증명이 아직 안 들어온 프로바이더는 버튼을 잠근다. */
export function isConfigured(provider: Provider) {
  const { clientId, authorizationUri } = CONFIG[provider]
  return clientId !== '' && authorizationUri !== ''
}

/** 프로바이더 콘솔에 등록한 리디렉션 URI 와 문자 하나까지 같아야 한다. */
export function redirectUri(provider: Provider) {
  return `${window.location.origin}/oauth/callback/${provider}`
}

/** 프로바이더 동의 화면으로 보낸다. state 는 콜백에서 대조할 CSRF 값. */
export function startLogin(provider: Provider) {
  const config = CONFIG[provider]
  const state = crypto.randomUUID()
  // 어느 프로바이더로 나갔는지도 같이 남긴다 — 콜백이 교환 대상을 이 값으로 검증한다
  sessionStorage.setItem(STATE_KEY, JSON.stringify({ provider, state }))

  const params = new URLSearchParams({
    client_id: config.clientId,
    redirect_uri: redirectUri(provider),
    response_type: 'code',
    state,
    ...config.extraParams,
  })
  if (config.scope) params.set('scope', config.scope)

  window.location.assign(`${config.authorizationUri}?${params}`)
}

/** 나갈 때 남긴 값을 꺼내면서 지운다. 한 번 쓰면 끝이라 재사용되지 않는다. */
function takeState(): { provider: string; state: string } | null {
  try {
    const raw = sessionStorage.getItem(STATE_KEY)
    sessionStorage.removeItem(STATE_KEY)
    if (!raw) return null
    const parsed = JSON.parse(raw) as { provider?: string; state?: string }
    if (!parsed.provider || !parsed.state) return null
    return { provider: parsed.provider, state: parsed.state }
  } catch {
    return null
  }
}

/** 콜백에서 받은 code 를 서버에 넘겨 세션을 연다. */
export async function completeLogin(
  provider: Provider,
  code: string,
  state: string | null,
): Promise<LoginResult> {
  const saved = takeState()
  // 프로바이더까지 대조한다 — 구글로 나갔는데 SSAFY 콜백으로 돌아오는 경로는 없다
  if (!saved || saved.provider !== provider || saved.state !== state) {
    throw new Error('요청이 위조되었을 수 있습니다. 다시 로그인해 주세요.')
  }

  const body = await request<LoginResponse>(`/auth/login/${provider}`, {
    method: 'POST',
    body: { code, redirectUri: redirectUri(provider) },
  })

  setAccessToken(body.accessToken)

  return {
    user: {
      // 신규 가입은 닉네임이 비어 있다. 온보딩 화면이 isNew 로 분기한다.
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
