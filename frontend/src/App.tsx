import { useEffect } from 'react'
import { Route, Routes, useLocation, useNavigate } from 'react-router-dom'
import Layout from './Layout'
import { setNavigate } from './nav'
import { ROUTES, type PageMeta } from './routes'

/* 프로토타입 화면 한 장. 마크업은 JSX 로 렌더하고,
   프로토타입의 페이지 스크립트는 DOM 이 올라온 뒤 그대로 한 번 실행한다. */
function Page({ meta }: { meta: PageMeta }) {
  const Element = meta.element

  useEffect(() => {
    document.title = meta.title
    if (meta.mode) document.body.dataset.mode = meta.mode
    else {
      delete document.body.dataset.mode
      document.body.className = meta.bodyClass ?? ''  // 셸이 없는 화면(로그인)은 여기서 직접
    }

    meta.script?.()
    window.scrollTo(0, 0)
    // meta 는 라우트마다 고정이라 마운트 시 한 번만 돈다
  }, [meta])

  const body = <Element />
  return meta.mode
    ? <Layout mode={meta.mode} nav={meta.nav ?? 'home'} bodyClass={meta.bodyClass ?? ''}>{body}</Layout>
    : body
}

export default function App() {
  const navigate = useNavigate()
  const { pathname, search } = useLocation()

  // 포팅한 프로토타입 스크립트도 SPA 이동을 쓰게 한다
  useEffect(() => { setNavigate((to) => navigate(to)) }, [navigate])

  // 화면 마크업의 <a href="/…"> 를 통째로 SPA 이동으로 가로챈다
  // (20개 화면의 링크를 <Link> 로 하나하나 바꾸지 않기 위한 선택)
  useEffect(() => {
    function onClick(event: MouseEvent) {
      if (event.defaultPrevented || event.button !== 0 || event.metaKey || event.ctrlKey || event.shiftKey || event.altKey) return
      const link = (event.target as Element | null)?.closest?.('a')
      if (!link || link.target || link.hasAttribute('download')) return
      const href = link.getAttribute('href')
      if (!href || !href.startsWith('/')) return
      event.preventDefault()
      navigate(href)
    }
    document.addEventListener('click', onClick)
    return () => document.removeEventListener('click', onClick)
  }, [navigate])

  return (
    <Routes>
      {ROUTES.map((meta) => (
        // key 에 쿼리를 포함해, ?code=… 가 바뀌면 페이지 스크립트가 다시 돌게 한다
        <Route key={meta.path} path={meta.path} element={<Page key={pathname + search} meta={meta} />} />
      ))}
      <Route path="*" element={<Page key={pathname} meta={ROUTES[0]} />} />
    </Routes>
  )
}
