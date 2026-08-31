/* 세션 복원. 새로고침하면 메모리의 accessToken 이 사라지지만 refresh 쿠키는 남아 있다.
   쿠키로 access 를 되찾고 프로필을 읽어 로그인 상태를 되살린다. */
import { api, refreshAccessToken, setAccessToken } from '../api/client'
import type { AuthUser, Role } from './context'

/** 백엔드 UserDtos.MeResponse 와 짝을 이룬다. */
export type MeResponse = {
  id: number
  nickname: string | null
  introduce: string | null
  walletAddress: string | null
  walletLinked: boolean
  role: Role
}

export function toAuthUser(me: MeResponse): AuthUser {
  return {
    // 닉네임 미설정은 빈 문자열로 넘긴다 — 화면은 이 값이 비었는지로 온보딩을 판단한다.
    nickname: me.nickname ?? '',
    // 프로필 이미지는 아직 서버가 주지 않는다.
    avatarUrl: '/assets/antena-profile.png',
    role: me.role,
    walletLinked: me.walletLinked,
  }
}

export function fetchMe() {
  return api.get<MeResponse>('/users/me')
}

/**
 * 앱이 뜰 때 한 번 부른다. 쿠키가 없거나 만료면 null — 비로그인으로 시작하면 된다.
 * 실패를 예외로 올리지 않는다. 첫 화면이 오류로 막히면 로그인조차 못 한다.
 */
export async function restoreSession(): Promise<AuthUser | null> {
  if (!(await refreshAccessToken())) return null
  try {
    return toAuthUser(await fetchMe())
  } catch {
    setAccessToken(null)
    return null
  }
}
