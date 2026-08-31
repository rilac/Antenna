/* 라우트 권한 가드.
   설계서 §1 — 공개 화면은 A-01 하나뿐이고 나머지는 전부 JWT 를 요구한다.
   §5 — 관리자 화면은 비관리자에게 진입 자체를 막는다. */
import { Navigate, useLocation } from 'react-router-dom'
import type { Access } from '../routes'
import { useAuth } from './context'
import { rememberReturnTo } from './returnTo'

export default function RequireAccess({ access, children }: { access: Access; children: React.ReactNode }) {
  const { authed, user } = useAuth()
  const location = useLocation()

  if (access === 'public') return <>{children}</>

  if (!authed) {
    // 로그인 후 이 자리로 돌려보내기 위해 경로를 남긴다
    rememberReturnTo(location.pathname + location.search)
    return <Navigate to="/login" replace />
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
