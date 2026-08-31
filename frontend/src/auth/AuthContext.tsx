/* 셸이 쓰는 최소 인증 상태.
   실제 토큰 발급·갱신은 [ANT-FE-LOGIN] · [ANT-FE-SESSION] 에서 붙인다.
   여기서는 셸이 로그인/비로그인 두 모습을 그릴 수 있을 만큼만 들고 있는다. */
import { useEffect, useMemo, useState } from 'react'
import { AuthCtx, type AuthState, type AuthUser } from './context'

/* 프로토타입이 쓰던 임시 플래그. [ANT-FE-LOGIN] 이 실제 토큰으로 갈아끼운다.
   accessToken 은 메모리, refresh 는 httpOnly 쿠키라 여기에 저장하지 않는다. */
const STUB_KEY = 'antena.auth'

function readStub() {
  try { return localStorage.getItem(STUB_KEY) === '1' } catch { return false }
}

function writeStub(on: boolean) {
  try {
    if (on) localStorage.setItem(STUB_KEY, '1')
    else localStorage.removeItem(STUB_KEY)
  } catch { /* 무시 */ }
}

const DEMO_USER: AuthUser = {
  nickname: '안테나',
  avatarUrl: '/assets/antena-profile.png',
  walletLinked: false,
}

export function AuthProvider({ children }: { children: React.ReactNode }) {
  const [user, setUser] = useState<AuthUser | null>(() => (readStub() ? DEMO_USER : null))

  useEffect(() => { writeStub(user !== null) }, [user])

  const value = useMemo<AuthState>(() => ({
    user,
    authed: user !== null,
    signIn: (next: AuthUser) => setUser(next),
    signOut: () => setUser(null),
  }), [user])

  return <AuthCtx.Provider value={value}>{children}</AuthCtx.Provider>
}
