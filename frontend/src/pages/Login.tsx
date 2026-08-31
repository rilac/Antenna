/* A-01 로그인 · /login
   담당 스토리 [ANT-FE-LOGIN]

   이 스토리가 채운 것은 화면 디자인이다.
   구글 OAuth 는 [ANT-AUTH-01] 이 auth/google.ts 에 만들어 둔 것을 그대로 부른다 —
   인가 요청·콜백 교환·토큰 저장 모두 그쪽 몫이라 여기서 다시 만들지 않는다.

   SSAFY OAuth 는 아직 붙이지 않았다. 백엔드에 GoogleOAuthClient 만 있고
   SSAFY 쪽은 [ANT-AUTH-02] 가 남아 있어, 지금 프론트만 만들면 부를 곳이 없다.

   회원가입 화면은 없다 — 설계서 §2 상 최초 로그인 시 회원이 자동 생성되므로
   SSO 버튼이 로그인과 가입을 겸한다. */
import { useNavigate } from 'react-router-dom'
import { Link } from 'react-router-dom'
import { useAuth } from '../auth/context'
import { takeReturnTo } from '../auth/returnTo'
import { startGoogleLogin } from '../auth/google'
import '../styles/auth.css'

const BrandMark = () => (
  <svg className="mark" viewBox="0 0 40 40" fill="none" aria-hidden="true">
    <rect width="40" height="40" rx="11" fill="currentColor" />
    <path d="M11.5 29.5 20 11.5l8.5 18" stroke="#fff" strokeWidth="4.2" strokeLinecap="round" strokeLinejoin="round" />
    <path d="M15.8 24.2h8.4" stroke="#fff" strokeWidth="4.2" strokeLinecap="round" />
  </svg>
)

const FEATURES = [
  {
    title: 'AI 리서치',
    icon: (
      <>
        <path d="M12 5a2.5 2.5 0 0 0-5 0 2.5 2.5 0 0 0-2 4 2.5 2.5 0 0 0 1 4.6A2.5 2.5 0 0 0 9 19a2.5 2.5 0 0 0 3-1.5z" />
        <path d="M12 5a2.5 2.5 0 0 1 5 0 2.5 2.5 0 0 1 2 4 2.5 2.5 0 0 1-1 4.6A2.5 2.5 0 0 1 15 19a2.5 2.5 0 0 1-3-1.5z" />
        <path d="M12 5v14" />
      </>
    ),
  },
  {
    title: '블록체인 검증',
    icon: (
      <>
        <path d="M12 3 4.5 6v6c0 4.5 3.2 7.9 7.5 9 4.3-1.1 7.5-4.5 7.5-9V6z" />
        <path d="m9 12 2.2 2.2L15.5 10" />
      </>
    ),
  },
  {
    title: '예측 포트폴리오',
    icon: (
      <>
        <path d="M3 20h18" /><path d="M6 20v-6M10.5 20v-9M15 20v-4" />
        <path d="m13 8 4-4M17 4h-3.4M17 4v3.4" /><path d="M19.5 20V8" />
      </>
    ),
  },
]

export default function Login() {
  const navigate = useNavigate()
  const { signIn } = useAuth()

  /* 백엔드 없이 화면을 만드는 팀원을 위해 남겨 둔 임시 진입로.
     실제 로그인이 안정되면 지운다. — [ANT-AUTH-01] 이 남긴 것을 그대로 둔다.
     라우트 40개 중 39개가 가드 뒤에 있어 이게 없으면 로컬에서 화면을 못 본다. */
  function signInAsDemo(role: 'USER' | 'ADMIN') {
    signIn({ nickname: '안테나', avatarUrl: '/assets/antena-profile.png', role, walletLinked: false })
    navigate(takeReturnTo(), { replace: true })
  }

  return (
    <div className="auth">
      <main className="auth-shell">
        <section className="auth-intro" aria-label="서비스 소개">
          <div className="intro-copy">
            <Link className="auth-brand" to="/">
              <BrandMark />
              <span className="name">ANTENA</span>
            </Link>
            <h1>근거를 보고 예측하는<br />AI 투자 플랫폼</h1>
            <p className="lead">AI 리서치와 블록체인 검증, 예측 포트폴리오로<br />더 똑똑한 투자 결정을 경험하세요.</p>

            <ul className="feats">
              {FEATURES.map((f) => (
                <li className="feat" key={f.title}>
                  <span className="feat-ic">
                    <svg width="22" height="22" viewBox="0 0 24 24" fill="none" stroke="currentColor"
                         strokeWidth="1.6" strokeLinecap="round" strokeLinejoin="round">
                      {f.icon}
                    </svg>
                  </span>
                  <h3>{f.title}</h3>
                </li>
              ))}
            </ul>
          </div>

          <figure className="intro-art">
            <img src="/assets/antena-character-transparent.png" alt="" aria-hidden="true" />
          </figure>
        </section>

        <section className="sso" aria-label="로그인">
          <h2>로그인</h2>
          <p className="sub">계정을 선택하여 간편하게 로그인하세요.</p>

          {/* 구글로 나갔다가 /oauth/callback/google 로 전체 페이지 로드로 돌아온다.
              돌아올 경로는 returnTo 가 sessionStorage 에 들고 있는다. */}
          <button type="button" className="sso-btn google" onClick={startGoogleLogin}>
            <svg width="24" height="24" viewBox="0 0 48 48" aria-hidden="true">
              <path fill="#EA4335" d="M24 9.5c3.54 0 6.71 1.22 9.21 3.6l6.85-6.85C35.9 2.38 30.47 0 24 0 14.62 0 6.51 5.38 2.56 13.22l7.98 6.19C12.43 13.72 17.74 9.5 24 9.5z" />
              <path fill="#4285F4" d="M46.98 24.55c0-1.57-.15-3.09-.38-4.55H24v9.02h12.94c-.58 2.96-2.26 5.48-4.78 7.18l7.73 6c4.51-4.18 7.09-10.36 7.09-17.65z" />
              <path fill="#FBBC05" d="M10.53 28.59c-.48-1.45-.76-2.99-.76-4.59s.27-3.14.76-4.59l-7.98-6.19C.92 16.46 0 20.12 0 24c0 3.88.92 7.54 2.56 10.78l7.97-6.19z" />
              <path fill="#34A853" d="M24 48c6.48 0 11.93-2.13 15.89-5.81l-7.73-6c-2.15 1.45-4.92 2.3-8.16 2.3-6.26 0-11.57-4.22-13.47-9.91l-7.98 6.19C6.51 42.62 14.62 48 24 48z" />
            </svg>
            Google로 로그인
          </button>

          {/* SSAFY 는 백엔드가 아직 없다([ANT-AUTH-02]). 버튼 자리를 미리 잡아 두되
              누를 수 없게 막는다 — 눌리면 부를 곳이 없어 그냥 실패한다. */}
          <button type="button" className="sso-btn ssafy" disabled aria-describedby="ssafy-note">
            <svg width="26" height="26" viewBox="0 0 40 40" fill="none" aria-hidden="true">
              <path d="M11.5 29.5 20 11.5l8.5 18" stroke="currentColor" strokeWidth="4.2" strokeLinecap="round" strokeLinejoin="round" />
              <path d="M15.8 24.2h8.4" stroke="currentColor" strokeWidth="4.2" strokeLinecap="round" />
            </svg>
            SSAFY로 로그인
          </button>
          <p className="sso-note" id="ssafy-note">SSAFY 로그인은 준비 중입니다.</p>

          <div className="dev-signin">
            <span className="dev-signin-label">백엔드 없이 화면을 볼 때 쓰는 임시 진입로</span>
            <div className="dev-signin-row">
              <button type="button" onClick={() => signInAsDemo('USER')}>임시 로그인 (일반)</button>
              <button type="button" onClick={() => signInAsDemo('ADMIN')}>임시 로그인 (관리자)</button>
            </div>
          </div>
        </section>
      </main>
    </div>
  )
}
