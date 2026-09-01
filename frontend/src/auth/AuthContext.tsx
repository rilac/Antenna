/* 셸이 쓰는 최소 인증 상태.
   실제 토큰 발급·갱신은 [ANT-FE-LOGIN] · [ANT-FE-SESSION] 에서 붙인다.
   여기서는 셸이 로그인/비로그인 두 모습을 그릴 수 있을 만큼만 들고 있는다. */
import { useEffect, useMemo, useState } from 'react'
import { setAccessToken, setUnauthorizedHandler } from '../api/client'
import { endSession, restoreSession } from './session'
import { AuthCtx, type AuthState, type AuthUser, type Role } from './context'

/* 프로토타입이 쓰던 임시 플래그. [ANT-FE-LOGIN] 이 실제 토큰으로 갈아끼운다.
   accessToken 은 메모리, refresh 는 httpOnly 쿠키라 여기에 저장하지 않는다.
   값: '1' 일반 사용자 · 'admin' 관리자 (관리자 가드 확인용) */
const STUB_KEY = 'antena.auth'

function readStub(): Role | null {
  try {
    const v = localStorage.getItem(STUB_KEY)
    if (v === 'admin') return 'ADMIN'
    if (v === '1') return 'USER'
  } catch { /* 무시 */ }
  return null
}

function writeStub(role: Role | null) {
  try {
    if (role === 'ADMIN') localStorage.setItem(STUB_KEY, 'admin')
    else if (role === 'USER') localStorage.setItem(STUB_KEY, '1')
    else localStorage.removeItem(STUB_KEY)
  } catch { /* 무시 */ }
}

function demoUser(role: Role): AuthUser {
  return {
    nickname: '안테나',
    avatarUrl: '/assets/antena-profile.png',
    role,
    walletLinked: false,
  }
}

export function AuthProvider({ children }: { children: React.ReactNode }) {
  const [user, setUser] = useState<AuthUser | null>(() => {
    const role = readStub()
    return role ? demoUser(role) : null
  })
  const [booting, setBooting] = useState(true)

  useEffect(() => { writeStub(user?.role ?? null) }, [user])

  /* 새로고침하면 accessToken(메모리)은 사라지고 refresh 쿠키만 남는다.
     쿠키로 세션을 되살린다. 실패하면 임시 로그인 스텁이 있던 상태를 그대로 둔다. */
  useEffect(() => {
    let cancelled = false
    restoreSession()
      .then((restored) => {
        if (cancelled || !restored) return
        setUser(restored)
      })
      .finally(() => { if (!cancelled) setBooting(false) })
    return () => { cancelled = true }
  }, [])

  /* 401 UNAUTHENTICATED 는 API 클라이언트가 전역으로 넘겨준다.
     재발급까지 실패한 경우만 오므로 여기서는 로그아웃 처리만 한다.
     M-08 모달은 [ANT-FE-SESSION] 이 이 자리에 붙인다. */
  useEffect(() => {
    setUnauthorizedHandler(() => {
      setAccessToken(null)
      setUser(null)
    })
  }, [])

  const value = useMemo<AuthState>(() => ({
    user,
    authed: user !== null,
    booting,
    signIn: (next: AuthUser) => setUser(next),
    signOut: () => {
      /* 서버 응답을 기다리지 않는다 — 화면은 즉시 로그아웃 상태가 되어야 한다.
         access token 정리는 endSession 이 요청을 마친 뒤에 한다(토큰이 있어야 부를 수 있다). */
      void endSession()
      setUser(null)
    },
    setNickname: (nickname: string) =>
      setUser((prev) => (prev ? { ...prev, nickname } : prev)),
    setWalletLinked: (linked: boolean) =>
      setUser((prev) => (prev ? { ...prev, walletLinked: linked } : prev)),
  }), [user, booting])

  return <AuthCtx.Provider value={value}>{children}</AuthCtx.Provider>
}
