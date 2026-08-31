/* A-01 로그인 · /login
   담당 스토리 [ANT-FE-LOGIN]
   설계서 docs/화면설계서.md §3 A · §4 A-01 의 제약을 보고 이 자리를 채운다.

   셸이 미리 붙여 둔 것: 로그인 성공 후 원래 가려던 경로로 돌아가는 처리.
   OAuth 버튼과 POST /auth/login/{provider} 연동은 이 스토리에서 만든다. */
import { useNavigate } from 'react-router-dom'
import { useAuth } from '../auth/context'
import { takeReturnTo } from '../auth/returnTo'

export default function Login() {
  const navigate = useNavigate()
  const { signIn } = useAuth()

  // 실제로는 인가 코드를 서버에 넘겨 받은 사용자 정보로 signIn 한다
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
        <div className="placeholder tall">{'[ANT-FE-LOGIN] 에서 SSAFY · Google OAuth 를 붙입니다'}</div>
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
