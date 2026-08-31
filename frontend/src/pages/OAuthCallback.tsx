import { useEffect, useRef, useState } from 'react'
import { useNavigate, useSearchParams } from 'react-router-dom'
import { completeGoogleLogin } from '../lib/auth'

/** 구글이 되돌려준 code 를 서버에 넘기는 동안만 보이는 화면. */
export default function OAuthCallback() {
  const [params] = useSearchParams()
  const navigate = useNavigate()
  const denied = params.get('error')
  const code = params.get('code')

  // URL 만 보고 알 수 있는 실패는 렌더 중에 정한다. 교환 실패만 나중에 채워진다.
  const [error, setError] = useState<string | null>(() => {
    if (denied) return denied === 'access_denied' ? '로그인을 취소했습니다.' : `구글 오류: ${denied}`
    if (!code) return '인가 코드가 없습니다.'
    return null
  })
  // 인가 코드는 일회용이라 교환은 딱 한 번만 시도한다.
  const exchanged = useRef(false)

  useEffect(() => {
    if (!code || denied || exchanged.current) return
    exchanged.current = true

    completeGoogleLogin(code, params.get('state'))
      // 닉네임 설정 화면이 생기면 user.isNew 로 분기한다.
      .then(() => navigate('/', { replace: true }))
      .catch((e: Error) => setError(e.message))
  }, [code, denied, params, navigate])

  return (
    <div className="auth">
      <main className="auth-shell" style={{ display: 'grid', placeItems: 'center', minHeight: '60vh' }}>
        <section className="sso" aria-live="polite">
          {error ? (
            <>
              <h2>로그인 실패</h2>
              <p className="sub">{error}</p>
              <a className="sso-btn ssafy" href="/login">다시 로그인</a>
            </>
          ) : (
            <>
              <h2>로그인 중</h2>
              <p className="sub">구글 계정을 확인하고 있습니다.</p>
            </>
          )}
        </section>
      </main>
    </div>
  )
}
