/* 닉네임 실시간 중복 검사. A-02 온보딩과 H-03 환경 설정이 함께 쓴다.
   원래 A-02 [ANT-AUTH-03] 안에 있던 것을 H-03 [ANT-FE-SETTINGS] 이
   같은 검사를 필요로 해 여기로 뺐다 — 두 벌이 되면 한쪽만 고쳐지고 어긋난다.

   서버 판정이 정본이다. 이 훅은 "보내도 될 것 같다"까지만 말하고,
   확정은 PATCH /users/me 의 DUPLICATE_NICKNAME 이 최종 판단한다
   — 검사와 확정 사이에 누가 먼저 가져갈 수 있다. */
import { useEffect, useRef, useState } from 'react'
import { api } from './client'

export const NICKNAME_MIN = 2
export const NICKNAME_MAX = 30

/** 입력이 멈춘 뒤에야 보낸다. 한 글자마다 때리면 서버가 낭비된다. */
const DEBOUNCE_MS = 350

export type NicknameCheck =
  | { state: 'idle' }
  | { state: 'checking' }
  | { state: 'available' }
  | { state: 'taken' }
  | { state: 'invalid'; reason: string }
  /** 현재 내 닉네임과 같다 — 바꿀 것이 없으므로 중복 검사를 보내지 않는다 */
  | { state: 'unchanged' }

/** 서버 NicknameRequest 의 제약(@Size · @Pattern)을 그대로 옮긴다. */
export function validateNickname(value: string): string | null {
  if (value.length < NICKNAME_MIN || value.length > NICKNAME_MAX) {
    return `${NICKNAME_MIN}~${NICKNAME_MAX}자로 입력해 주세요.`
  }
  if (value !== value.trim()) return '닉네임 앞뒤에 공백을 둘 수 없습니다.'
  return null
}

/**
 * @param value   입력 중인 닉네임
 * @param current 현재 내 닉네임. 주면 같은 값일 때 검사를 건너뛰고 'unchanged' 가 된다.
 *                온보딩은 현재 닉네임이 없으므로 넘기지 않는다.
 */
export function useNicknameCheck(value: string, current?: string) {
  /** 서버가 판정을 끝낸 닉네임. 입력이 이 값과 다르면 아직 확인 중이다. */
  const [checked, setChecked] = useState<{ nickname: string; available: boolean } | null>(null)

  /* 늦게 도착한 응답이 최신 입력의 판정을 덮어쓰지 않도록 요청마다 번호를 매긴다. */
  const seq = useRef(0)

  const unchanged = current !== undefined && value === current
  const reason = value === '' ? null : validateNickname(value)

  useEffect(() => {
    if (value === '' || unchanged || validateNickname(value)) return

    const mine = ++seq.current
    const timer = setTimeout(() => {
      api
        .get<{ available: boolean }>('/users/nickname/availability', { query: { nickname: value } })
        .then((res) => {
          if (mine === seq.current) setChecked({ nickname: value, available: res.available })
        })
        .catch(() => { /* 검사 실패는 확정 단계에서 서버가 다시 판정한다 */ })
    }, DEBOUNCE_MS)

    return () => clearTimeout(timer)
  }, [value, unchanged])

  const check: NicknameCheck =
    value === '' ? { state: 'idle' }
      : unchanged ? { state: 'unchanged' }
      : reason ? { state: 'invalid', reason }
      : checked?.nickname !== value ? { state: 'checking' }
      : { state: checked.available ? 'available' : 'taken' }

  /** 확정과 확정 사이에 서버가 중복이라고 답했을 때 판정을 되돌린다. */
  function markTaken(nickname: string) {
    setChecked({ nickname, available: false })
  }

  return { check, markTaken }
}
