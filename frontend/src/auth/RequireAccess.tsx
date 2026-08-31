/* 라우트 권한 가드.
   설계서 §1 — 공개 화면은 A-01 하나뿐이고 나머지는 전부 JWT 를 요구한다.
   §5 — 관리자 화면은 비관리자에게 진입 자체를 막는다. */
import { Navigate, useLocation } from 'react-router-dom'
import type { Access } from '../routes'
import { useAuth } from './context'
import { rememberReturnTo } from './returnTo'

/** 닉네임을 정하기 전까지 머무는 화면. */
const ONBOARDING_PATH = '/onboarding/nickname'

export default function RequireAccess({ access, children }: { access: Access; children: React.ReactNode }) {
  const { authed, user, booting } = useAuth()
  const location = useLocation()

  if (access === 'public') return <>{children}</>

  // 새로고침 직후에는 쿠키로 세션을 되살리는 중이다. 여기서 판단하면 로그인 화면으로 잘못 튕긴다.
  if (booting) return null

  if (!authed) {
    // 로그인 후 이 자리로 돌려보내기 위해 경로를 남긴다
    rememberReturnTo(location.pathname + location.search)
    return <Navigate to="/login" replace />
  }

  /* 최초 로그인 회원은 닉네임이 비어 있다. 확정하기 전에는 다른 화면을 열지 못하게 막는다
     — 닉네임 없이 예측·글을 남기면 작성자를 표시할 이름이 없다. */
  if (!user?.nickname && location.pathname !== ONBOARDING_PATH) {
    return <Navigate to={ONBOARDING_PATH} replace />
  }

  if (access === 'admin' && user?.role !== 'ADMIN') {
    // 홈으로 튕기지 않고 왜 막혔는지 알린다.
    // 제대로 된 403 화면은 A-04 [ANT-FE-ERROR] 가 채운다.
    return (
      <main className="main">
        <div className="main-inner">
          <div className="page-head">
            <h1>접근 권한이 없습니다</h1>
            <p>{'403 · 관리자 전용 화면입니다'}</p>
          </div>
          <div className="placeholder tall">{'[ANT-FE-ERROR] 에서 오류 화면을 구현합니다'}</div>
        </div>
      </main>
    )
  }

  return <>{children}</>
}
