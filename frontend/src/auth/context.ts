/* 인증 컨텍스트의 타입과 훅.
   AuthProvider(컴포넌트)와 파일을 나눠 둔다 — 한 파일이 컴포넌트와 훅을 함께
   내보내면 Vite Fast Refresh 가 동작하지 않는다. */
import { createContext, useContext } from 'react'

export type Role = 'USER' | 'ADMIN'

export type AuthUser = {
  nickname: string
  /** 프로필 이미지 경로. 실제로는 GET /users/me 가 내려준다 */
  avatarUrl: string
  role: Role
  /** 지갑 연동 여부 — C-01 진입 시 M-01 을 띄울지 판단한다 */
  walletLinked: boolean
}

export type AuthState = {
  user: AuthUser | null
  authed: boolean
  /** 새로고침 직후 쿠키로 세션을 되살리는 중. 이때 가드가 판단하면 로그인 화면으로 잘못 튕긴다. */
  booting: boolean
  signIn: (user: AuthUser) => void
  signOut: () => void
  /** 온보딩에서 닉네임을 확정한 뒤 셸의 표시 이름을 갱신한다. */
  setNickname: (nickname: string) => void
}

export const AuthCtx = createContext<AuthState | null>(null)

export function useAuth() {
  const ctx = useContext(AuthCtx)
  if (!ctx) throw new Error('useAuth 는 AuthProvider 안에서만 쓸 수 있다')
  return ctx
}
