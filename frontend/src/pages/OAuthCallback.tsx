/* 프로바이더가 되돌려준 code 를 서버에 넘기는 동안만 보이는 화면.
   화면설계서에 없는 A-01 의 복귀 구간이라 셸 없이 단독으로 그린다.
   구글·SSAFY 가 같은 경로 모양(/oauth/callback/:provider)을 쓴다.

   A-01 로그인에서 넘어와 다시 A-01 로 돌아갈 수 있는 자리라 auth.css 의 .auth
   스코프를 그대로 쓴다(A-02 온보딩도 같은 방식이다). 배경·카드·버튼이 로그인
   화면과 이어져야 "다른 사이트로 튕겼나" 하는 인상을 주지 않는다. */
import { useEffect, useRef, useState } from 'react'
import { Link, useNavigate, useParams, useSearchParams } from 'react-router-dom'
import { completeLogin, isProvider, providerLabel } from '../auth/oauth'
import { useAuth } from '../auth/context'
import { takeReturnTo } from '../auth/returnTo'
import '../styles/auth.css'

/* A-01 로그인 화면과 같은 마크. 두 화면이 잇달아 보이므로 로고가 바뀌면 안 된다.
   세 번째 화면이 필요해지면 공용 컴포넌트로 뽑는다. */
const BrandMark = () => (
  <svg className="mark" viewBox="0 0 40 40" fill="none" aria-hidden="true">
    <rect width="40" height="40" rx="11" fill="currentColor" />
    <path d="M11.5 29.5 20 11.5l8.5 18" stroke="#fff" strokeWidth="4.2"
          strokeLinecap="round" strokeLinejoin="round" />
    <path d="M15.8 24.2h8.4" stroke="#fff" strokeWidth="4.2" strokeLinecap="round" />
  </svg>
)

export default function OAuthCallback() {
  const [params] = useSearchParams()
  const navigate = useNavigate()
  const { signIn } = useAuth()

  const { provider } = useParams<{ provider: string }>()
  const known = isProvider(provider) ? provider : null
  const label = known ? providerLabel(known) : ''

  const denied = params.get('error')
  const code = params.get('code')

  // URL 만 보고 알 수 있는 실패는 렌더 중에 정한다. 교환 실패만 나중에 채워진다.
  const [error, setError] = useState<string | null>(() => {
    if (!known) return '알 수 없는 로그인 경로입니다.'
    if (denied) return denied === 'access_denied' ? '로그인을 취소했습니다.' : `${providerLabel(known)} 오류: ${denied}`
    if (!code) return '인가 코드가 없습니다.'
    return null
  })

  // 인가 코드는 일회용이라 교환은 딱 한 번만 시도한다.
  const exchanged = useRef(false)

  useEffect(() => {
    if (!known || !code || denied || exchanged.current) return
    exchanged.current = true

    completeLogin(known, code, params.get('state'))
      .then(({ user, isNew }) => {
        signIn(user)
        // 닉네임이 없는 회원은 온보딩부터. returnTo 는 온보딩이 끝난 뒤에 꺼낸다.
        if (isNew) {
          navigate('/onboarding/nickname', { replace: true })
          return
        }
        // 비로그인 딥링크로 막혔던 경로가 있으면 그리로, 없으면 홈으로.
        navigate(takeReturnTo(), { replace: true })
      })
      .catch((e: Error) => setError(e.message))
  }, [known, code, denied, params, navigate, signIn])

  return (
    <div className="auth">
      <main className="cb-shell">
        <Link className="auth-brand cb-brand" to="/">
          <BrandMark />
          <span className="name">ANTENA</span>
        </Link>

        {/* 상태가 바뀌는 곳만 읽어 준다 — 성공하면 곧바로 다른 화면으로 넘어간다 */}
        <section className="sso cb-card" aria-live="polite">
          {error ? (
            <>
              <span className="cb-icon is-error" aria-hidden="true">
                <svg width="26" height="26" viewBox="0 0 24 24" fill="none" stroke="currentColor"
                     strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
                  <path d="M12 8v5" />
                  <path d="M12 16.5v.01" />
                  <path d="M10.3 3.9 2.4 17.4A1.9 1.9 0 0 0 4 20.3h16a1.9 1.9 0 0 0 1.6-2.9L13.7 3.9a1.9 1.9 0 0 0-3.4 0z" />
                </svg>
              </span>
              <h2>로그인하지 못했습니다</h2>
              <p className="sub cb-reason">{error}</p>
              <button type="button" className="sso-btn ssafy"
                      onClick={() => navigate('/login', { replace: true })}>
                로그인 화면으로
              </button>
            </>
          ) : (
            <>
              {/* 진행 중이라는 신호. 움직임을 줄이도록 설정했으면 회전을 멈춘다 */}
              <span className="cb-spinner" aria-hidden="true" />
              <h2>로그인 중</h2>
              <p className="sub">
                {label ? `${label} 계정을 확인하고 있습니다.` : '계정을 확인하고 있습니다.'}
              </p>
              <p className="cb-hint">잠시만 기다려 주세요. 곧 자동으로 이동합니다.</p>
            </>
          )}
        </section>
      </main>
    </div>
  )
}
