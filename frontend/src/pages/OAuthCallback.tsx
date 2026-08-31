/* 프로바이더가 되돌려준 code 를 서버에 넘기는 동안만 보이는 화면.
   화면설계서에 없는 A-01 의 복귀 구간이라 셸 없이 단독으로 그린다.
   구글·SSAFY 가 같은 경로 모양(/oauth/callback/:provider)을 쓴다. */
import { useEffect, useRef, useState } from 'react'
import { useNavigate, useParams, useSearchParams } from 'react-router-dom'
import { completeLogin, isProvider, providerLabel } from '../auth/oauth'
import { useAuth } from '../auth/context'
import { takeReturnTo } from '../auth/returnTo'

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
    <main className="main">
      <div className="main-inner">
        <div className="page-head" aria-live="polite">
          {error ? (
            <>
              <h1>로그인 실패</h1>
              <p>{error}</p>
            </>
          ) : (
            <>
              <h1>로그인 중</h1>
              <p>{label} 계정을 확인하고 있습니다.</p>
            </>
          )}
        </div>
        {error && (
          <div className="grid" style={{ marginTop: 14 }}>
            <button type="button" className="card" onClick={() => navigate('/login', { replace: true })}>
              다시 로그인
            </button>
          </div>
        )}
      </div>
    </main>
  )
}
