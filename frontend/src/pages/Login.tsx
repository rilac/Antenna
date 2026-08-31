/* A-01 로그인 · /login
   담당 스토리 [ANT-FE-LOGIN]
   설계서 docs/화면설계서.md §3 A · §4 A-01 의 제약을 보고 이 자리를 채운다.

   셸이 미리 붙여 둔 것: 로그인 성공 후 원래 가려던 경로로 돌아가는 처리.
   구글 OAuth 는 [ANT-AUTH-01] 에서 붙였다. SSAFY OAuth 는 아직이다. */
import { useNavigate } from 'react-router-dom'
import { useAuth } from '../auth/context'
import { takeReturnTo } from '../auth/returnTo'
import { startGoogleLogin } from '../auth/google'

export default function Login() {
  const navigate = useNavigate()
  const { signIn } = useAuth()

  /* 백엔드 없이 화면을 만드는 팀원을 위해 남겨 둔 임시 진입로.
     실제 로그인이 안정되면 지운다. */
  function signInAsDemo(role: 'USER' | 'ADMIN') {
    signIn({ nickname: '안테나', avatarUrl: '/assets/antena-profile.png', role, walletLinked: false })
    navigate(takeReturnTo(), { replace: true })
  }

  return (
    <main className="main">
      <div className="main-inner">
        <div className="page-head">
          <h1>로그인</h1>
          <p>{'A-01 · /login'}</p>
        </div>

        {/* 구글로 나갔다가 /oauth/callback/google 로 전체 페이지 로드로 돌아온다.
            돌아올 경로는 returnTo 가 sessionStorage 에 들고 있는다. */}
        <div className="grid" style={{ marginTop: 14 }}>
          <button type="button" className="card" onClick={startGoogleLogin}>
            Google로 로그인
          </button>
        </div>

        <div className="placeholder" style={{ marginTop: 14 }}>
          {'[ANT-FE-LOGIN] 에서 SSAFY OAuth 와 화면 디자인을 붙입니다'}
        </div>

        <div className="grid c2" style={{ marginTop: 14 }}>
          <button type="button" className="card" onClick={() => signInAsDemo('USER')}>
            임시 로그인 (일반)
          </button>
          <button type="button" className="card" onClick={() => signInAsDemo('ADMIN')}>
            임시 로그인 (관리자)
          </button>
        </div>
      </div>
    </main>
  )
}
