/* A-02 온보딩 · /onboarding/nickname
   담당 스토리 [ANT-AUTH-03]

   최초 로그인 회원은 닉네임이 비어 있다(서버에서 NULL). 확정하기 전에는
   다른 화면으로 못 가게 RequireAccess 가 여기로 돌려보낸다.

   지갑 연동도 필수 온보딩 단계지만(유저플로우 §3) 서버 API 가 아직 없어
   이 화면은 닉네임까지만 책임진다. */
import { useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { api, ApiError } from '../api/client'
import { NICKNAME_MAX, useNicknameCheck, validateNickname } from '../api/useNicknameCheck'
import { useAuth } from '../auth/context'
import { takeReturnTo } from '../auth/returnTo'
import '../styles/auth.css'

export default function OnboardingNickname() {
  const navigate = useNavigate()
  const { setNickname } = useAuth()

  const [value, setValue] = useState('')
  const [submitting, setSubmitting] = useState(false)
  const [error, setError] = useState<string | null>(null)

  /* 디바운스·순번·검증은 훅이 맡는다. H-03 환경 설정도 같은 훅을 쓴다.
     여기서는 현재 닉네임을 넘기지 않는다 — 최초 로그인이라 아직 없다. */
  const { check, markTaken } = useNicknameCheck(value)

  async function submit(event: React.FormEvent) {
    event.preventDefault()
    const reason = validateNickname(value)
    if (reason) {
      setError(reason)
      return
    }

    setSubmitting(true)
    setError(null)
    try {
      await api.patch<{ nickname: string }>('/users/me', { nickname: value })
      setNickname(value)
      // 로그인 전에 막혔던 경로가 있으면 그리로, 없으면 홈으로.
      navigate(takeReturnTo(), { replace: true })
    } catch (e) {
      // 중복 검사와 확정 사이에 누가 먼저 가져갔을 수 있다. 최종 판정은 서버다.
      if (e instanceof ApiError && e.code === 'DUPLICATE_NICKNAME') {
        markTaken(value)
        setError('이미 사용 중인 닉네임입니다.')
      } else {
        setError('닉네임을 저장하지 못했습니다. 잠시 후 다시 시도해 주세요.')
      }
    } finally {
      setSubmitting(false)
    }
  }

  const hint = {
    idle: '2~30자로 정해 주세요. 나중에 마이페이지에서 바꿀 수 있습니다.',
    checking: '확인 중…',
    available: '사용할 수 있는 닉네임입니다.',
    taken: '이미 사용 중인 닉네임입니다.',
    invalid: check.state === 'invalid' ? check.reason : '',
    // 현재 닉네임을 넘기지 않으므로 이 화면에서는 오지 않는다(H-03 전용 상태)
    unchanged: '',
  }[check.state]

  return (
    <div className="auth">
      <main className="auth-shell">
        <section className="sso" aria-label="닉네임 설정">
          <h2>닉네임을 정해 주세요</h2>
          <p className="sub">예측 기록과 랭킹에 표시되는 이름입니다.</p>

          <form onSubmit={submit}>
            <input
              className="nickname-input"
              type="text"
              value={value}
              onChange={(e) => { setValue(e.target.value); setError(null) }}
              maxLength={NICKNAME_MAX}
              autoFocus
              aria-label="닉네임"
              aria-describedby="nickname-hint"
              aria-invalid={check.state === 'taken' || check.state === 'invalid'}
              placeholder="예: 반도체훈련소"
            />
            <p className={`nickname-hint is-${check.state}`} id="nickname-hint" aria-live="polite">
              {error ?? hint}
            </p>

            <button
              type="submit"
              className="sso-btn ssafy"
              disabled={submitting || check.state !== 'available'}
            >
              {submitting ? '저장 중…' : '시작하기'}
            </button>
          </form>
        </section>
      </main>
    </div>
  )
}
