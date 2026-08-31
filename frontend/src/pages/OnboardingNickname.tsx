/* A-02 온보딩 · /onboarding/nickname
   담당 스토리 [ANT-AUTH-03]

   최초 로그인 회원은 닉네임이 비어 있다(서버에서 NULL). 확정하기 전에는
   다른 화면으로 못 가게 RequireAccess 가 여기로 돌려보낸다.

   지갑 연동도 필수 온보딩 단계지만(유저플로우 §3) 서버 API 가 아직 없어
   이 화면은 닉네임까지만 책임진다. */
import { useEffect, useRef, useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { api, ApiError } from '../api/client'
import { useAuth } from '../auth/context'
import { takeReturnTo } from '../auth/returnTo'
import '../styles/auth.css'

const MIN = 2
const MAX = 30
/** 입력이 멈춘 뒤에야 중복 검사를 보낸다. 한 글자마다 때리면 서버가 낭비된다. */
const DEBOUNCE_MS = 350

type Check =
  | { state: 'idle' }
  | { state: 'checking' }
  | { state: 'available' }
  | { state: 'taken' }
  | { state: 'invalid'; reason: string }

function validate(value: string): string | null {
  if (value.length < MIN || value.length > MAX) return `${MIN}~${MAX}자로 입력해 주세요.`
  if (value !== value.trim()) return '닉네임 앞뒤에 공백을 둘 수 없습니다.'
  return null
}

export default function OnboardingNickname() {
  const navigate = useNavigate()
  const { setNickname } = useAuth()

  const [value, setValue] = useState('')
  /** 서버가 판정을 끝낸 닉네임. 입력이 이 값과 다르면 아직 확인 중이다. */
  const [checked, setChecked] = useState<{ nickname: string; available: boolean } | null>(null)
  const [submitting, setSubmitting] = useState(false)
  const [error, setError] = useState<string | null>(null)

  /* 늦게 도착한 응답이 최신 입력의 판정을 덮어쓰지 않도록 요청마다 번호를 매긴다. */
  const seq = useRef(0)

  const reason = value === '' ? null : validate(value)
  const check: Check =
    value === '' ? { state: 'idle' }
      : reason ? { state: 'invalid', reason }
      : checked?.nickname !== value ? { state: 'checking' }
      : { state: checked.available ? 'available' : 'taken' }

  useEffect(() => {
    if (value === '' || validate(value)) return

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
  }, [value])

  async function submit(event: React.FormEvent) {
    event.preventDefault()
    const reason = validate(value)
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
        setChecked({ nickname: value, available: false })
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
              maxLength={MAX}
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
