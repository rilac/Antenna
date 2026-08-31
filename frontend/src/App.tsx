import { useEffect } from 'react'
import { Navigate, Route, Routes } from 'react-router-dom'
import Layout from './Layout'
import RequireAccess from './auth/RequireAccess'
import { ROUTES, type PageMeta } from './routes'

/* 화면 한 장. 셸을 씌울지와 body 속성만 여기서 정하고,
   내용은 meta.element(각 화면 컴포넌트)가 그린다. */
function Page({ meta }: { meta: PageMeta }) {
  const Element = meta.element

  useEffect(() => {
    document.title = `${meta.title} · ANTENA`
    if (meta.mode) document.body.dataset.mode = meta.mode
    else {
      delete document.body.dataset.mode
      // 셸이 없는 화면(로그인)은 body 클래스를 직접 관리한다
      document.body.className = meta.bodyClass ?? ''
    }
    window.scrollTo(0, 0)
  }, [meta])

  const body = <Element />

  return meta.mode
    ? <Layout mode={meta.mode} nav={meta.nav ?? 'home'} bodyClass={meta.bodyClass ?? ''}>{body}</Layout>
    : body
}

export default function App() {
  return (
    <Routes>
      {ROUTES.map((meta) => (
        <Route
          key={meta.path} path={meta.path}
          element={<RequireAccess access={meta.access}><Page meta={meta} /></RequireAccess>}
        />
      ))}
      {/* 없는 경로는 홈으로. 404 화면(A-04)은 [ANT-FE-ERROR] 에서 붙인다 */}
      <Route path="*" element={<Navigate to="/" replace />} />
    </Routes>
  )
}
