/* ANTENA 공용 셸 — 사이드바(호버 확장) + 상단바.
   프로토타입 assets/app.js 의 rail()/topbar() 를 그대로 옮긴다.

   확장/축소는 CSS(.rail:hover, :focus-within)가 전담한다. 여기 상태는
   좁은 화면(<901px) 서랍을 여닫는 railOpen 하나뿐이다. */
import { useEffect, useRef, useState } from 'react'
import { Link, useNavigate } from 'react-router-dom'
import { useAuth } from './auth/context'
import { takeOnboardingPending } from './auth/onboarding'
import BriefingModal from './components/BriefingModal'
import OnboardingTour from './components/OnboardingTour'
import WalletLinkModal from './components/wallet/WalletLinkModal'
import { useBriefingParam } from './components/briefingParam'

export type Mode = 'insight' | 'sim'

const ICON: Record<string, React.ReactNode> = {
  home: <><path d="M3 10.5 12 3l9 7.5" /><path d="M5 9.5V20a1 1 0 0 0 1 1h4v-6h4v6h4a1 1 0 0 0 1-1V9.5" /></>,
  stocks: <><path d="M3 3v18h18" /><path d="M7 15l4-5 3 3 5-7" /></>,
  market: <><path d="M12 3v18" /><path d="M5 8h9a3 3 0 0 1 0 6H5" /><path d="M5 8H3" /><path d="M19 14h2" /></>,
  report: <><path d="M6 2h8l4 4v16H6z" /><path d="M14 2v4h4" /><path d="M9 12h6M9 16h6" /></>,
  portfolio: <><path d="M3 7h18v13H3z" /><path d="M9 7V5a2 2 0 0 1 2-2h2a2 2 0 0 1 2 2v2" /><path d="M3 12h18" /></>,
  community: <><path d="M17 20v-2a4 4 0 0 0-4-4H6a4 4 0 0 0-4 4v2" /><circle cx="9.5" cy="7" r="3.5" /><path d="M22 20v-2a4 4 0 0 0-3-3.87" /></>,
  play: <><circle cx="12" cy="12" r="9" /><path d="m10 8.5 6 3.5-6 3.5z" /></>,
  trophy: <><path d="M7 4h10v5a5 5 0 0 1-10 0z" /><path d="M7 6H4v2a3 3 0 0 0 3 3M17 6h3v2a3 3 0 0 1-3 3" /><path d="M10 19h4M12 14v5M8 21h8" /></>,
}

const MODES = {
  insight: {
    label: '인사이트',
    home: '/',
    nav: [
      { key: 'home', icon: 'home', text: '홈', href: '/' },
      { key: 'stocks', icon: 'stocks', text: '종목 탐색', href: '/stocks' },
      { key: 'market', icon: 'market', text: '주가 예측', href: '/rankings' },
      { key: 'report', icon: 'report', text: '리포트', href: '/reports' },
      { key: 'community', icon: 'community', text: '커뮤니티', href: '/posts' },
      { key: 'portfolio', icon: 'portfolio', text: '예측 포트폴리오', href: '/me/portfolio' },
    ],
  },
  sim: {
    label: '모의 투자',
    home: '/sim',
    nav: [
      { key: 'home', icon: 'home', text: '홈', href: '/sim' },
      { key: 'play', icon: 'play', text: '투자하기', href: '/sim/modes' },
      { key: 'portfolio', icon: 'portfolio', text: '투자 포트폴리오', href: '/sim/history' },
      /* 시즌 리더보드는 /sim/:id/leaderboard 라 시즌 없이 갈 수 없다.
         G-01 홈에서 진행 중 시즌을 골라 들어가는 게 정본 경로다. */
      { key: 'ranking', icon: 'trophy', text: '모의투자 랭킹', href: '/rankings' },
    ],
  },
} as const

const WIDE = '(min-width: 901px)'

/* 상단바 검색 — 입력과 이동까지만 맡는다.
   자동완성 드롭다운은 GET /search 를 쓰는 A-03 [ANT-FE-SEARCH] 의 몫이라
   셸에서 종목 데이터를 들고 있지 않는다. */
function Search() {
  const navigate = useNavigate()
  const [query, setQuery] = useState('')

  return (
    <form
      className="search" role="search"
      onSubmit={(e) => {
        e.preventDefault()
        const q = query.trim()
        if (q) navigate(`/search?q=${encodeURIComponent(q)}`)
      }}
    >
      <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round">
        <circle cx="11" cy="11" r="7" /><path d="m20 20-3.5-3.5" />
      </svg>
      <input
        type="search" id="topbar-search" autoComplete="off"
        aria-label="종목 검색"
        placeholder="종목명 또는 종목코드를 검색하세요 (예: 삼성전자, 005930)"
        value={query}
        onChange={(e) => setQuery(e.target.value)}
      />
    </form>
  )
}

export default function Layout({ mode, nav, bodyClass, children }: { mode: Mode; nav: string; bodyClass: string; children: React.ReactNode }) {
  const { authed, user, signOut } = useAuth()
  const navigate = useNavigate()
  // 좁은 화면 서랍 전용. 넓은 화면은 접힌 레일이 늘 떠 있어 여닫을 상태가 없다.
  const [railOpen, setRailOpen] = useState(false)

  /* M-09 온보딩 튜토리얼. **홈이 아니라 셸에 둔다** — 비로그인 딥링크로 막혔던
     회원은 가입 뒤 홈이 아니라 그 경로로 착지한다(returnTo). 홈에만 걸면 그
     사람은 튜토리얼을 아예 못 본다.

     로그인(A-01)과 닉네임(A-02)에는 셸이 없어서(routes.ts 의 mode 가 없다)
     저절로 제외된다. 그래도 authed · nickname 을 확인한다 — 셸은 가드가
     리다이렉트하는 동안에도 한 번 그려지고, 그때 띄우면 빈 화면 위에 뜬다.

     ref 로 잠그는 이유: 이 이펙트는 authed · nickname 에 반응하는데, 한 번
     꺼낸 뒤 그 값이 바뀌어 다시 돌면 빈 플래그를 읽어 **열려 있는 튜토리얼을
     닫아 버린다**(H-03 에서 닉네임을 고치면 실제로 그렇게 된다). */
  const [tour, setTour] = useState(false)
  const tourChecked = useRef(false)
  useEffect(() => {
    if (tourChecked.current || !authed || !user?.nickname) return
    tourChecked.current = true
    setTour(takeOnboardingPending())
  }, [authed, user?.nickname])

  /* M-01 지갑 연동. **튜토리얼 안에서 열지 않는다** — 모달 3겹이 되기 때문이고,
     WalletLinkModal 머리말이 "호출부가 온보딩을 닫고 띄운다" 로 계약을 적어
     두었다. 그래서 튜토리얼을 먼저 닫고 이걸 세운다. */
  const [linking, setLinking] = useState(false)

  /* M-10 브리핑 상세. 여는 쪽(B-01 홈 띠 · B-03 브리핑 카드)은 쿼리만 세우고,
     그리는 것은 셸이 맡는다 — M-09 오버레이를 셸로 올린 것과 같은 이유다.
     띠가 비어 있으면 그 컴포넌트는 null 을 돌려주는데, 모달을 그 안에 두면
     ?briefing=12 로 들어온 딥링크가 아무것도 못 연다. */
  const briefing = useBriefingParam()

  useEffect(() => {
    document.body.className = railOpen ? `${bodyClass} rail-open`.trim() : bodyClass
  }, [railOpen, bodyClass])

  useEffect(() => {
    const mq = window.matchMedia(WIDE)
    const onChange = () => { if (mq.matches) setRailOpen(false) }
    const onKey = (e: KeyboardEvent) => { if (e.key === 'Escape') setRailOpen(false) }
    mq.addEventListener('change', onChange)
    document.addEventListener('keydown', onKey)
    return () => { mq.removeEventListener('change', onChange); document.removeEventListener('keydown', onKey) }
  }, [])

  const modeConfig = MODES[mode] ?? MODES.insight

  return (
    <>
      <header className="topbar">
        {/* 넓은 화면에서는 CSS 가 숨긴다 — 서랍이 없기 때문 */}
        <button
          className="rail-toggle" id="rail-toggle" type="button"
          aria-label="메뉴 열기/닫기" aria-controls="rail" aria-expanded={railOpen}
          onClick={() => setRailOpen((v) => !v)}
        >
          <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.9" strokeLinecap="round">
            <path d="M4 7h16M4 12h16M4 17h16" />
          </svg>
        </button>

        <Search />

        <div className="topbar-right">
          {authed ? (
            <>
              {/* 잔액은 H-01 [ANT-FE-WALLET] 이 GET /wallet/balance 로 채운다 */}
              <div className="wallet"><i /><span className="num">0</span> ANT</div>
              {/* 미확인 뱃지는 H-02 [ANT-FE-NOTIFICATION] 이 채운다 */}
              <Link className="iconbtn" to="/notifications" aria-label="알림">
                <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.7" strokeLinecap="round" strokeLinejoin="round">
                  <path d="M18 8a6 6 0 1 0-12 0c0 6-3 7-3 7h18s-3-1-3-7" /><path d="M13.7 21a2 2 0 0 1-3.4 0" />
                </svg>
              </Link>
              <Link className="iconbtn" to="/settings" aria-label="설정">
                <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.7" strokeLinecap="round" strokeLinejoin="round">
                  <circle cx="12" cy="12" r="3" />
                  <path d="M19.4 15a1.6 1.6 0 0 0 .3 1.8l.1.1a2 2 0 1 1-2.8 2.8l-.1-.1a1.6 1.6 0 0 0-2.7 1.1v.2a2 2 0 1 1-4 0v-.1a1.6 1.6 0 0 0-2.7-1.2l-.1.1a2 2 0 1 1-2.8-2.8l.1-.1a1.6 1.6 0 0 0-1.1-2.7H3.4a2 2 0 1 1 0-4h.1A1.6 1.6 0 0 0 4.7 6.3l-.1-.1a2 2 0 1 1 2.8-2.8l.1.1a1.6 1.6 0 0 0 2.7-1.1V2a2 2 0 1 1 4 0v.1a1.6 1.6 0 0 0 2.7 1.2l.1-.1a2 2 0 1 1 2.8 2.8l-.1.1a1.6 1.6 0 0 0 1.1 2.7h.2a2 2 0 1 1 0 4h-.1a1.6 1.6 0 0 0-1.5 1" />
                </svg>
              </Link>
              {/* refresh 는 httpOnly 쿠키라 브라우저가 직접 못 지운다.
                  서버에 폐기를 요청하는 이 버튼이 유일한 정리 수단이다. */}
              <button
                className="iconbtn" type="button" aria-label="로그아웃"
                onClick={() => { signOut(); navigate('/login', { replace: true }) }}
              >
                <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.7" strokeLinecap="round" strokeLinejoin="round">
                  <path d="M15 17v2a2 2 0 0 1-2 2H6a2 2 0 0 1-2-2V5a2 2 0 0 1 2-2h7a2 2 0 0 1 2 2v2" />
                  <path d="M18 15l3-3-3-3" /><path d="M21 12H9" />
                </svg>
              </button>
            </>
          ) : (
            <nav className="topbar-auth" aria-label="계정">
              <Link className="topbar-login" to="/login">로그인</Link>
              <Link className="topbar-signup" to="/login">회원가입</Link>
            </nav>
          )}
        </div>
      </header>

      <aside className="rail" id="rail">
        <Link className="brand" to="/">
          <svg className="brand-mark" viewBox="0 0 40 40" fill="none" aria-hidden="true">
            <rect width="40" height="40" rx="11" fill="currentColor" />
            <path d="M11.5 29.5 20 11.5l8.5 18" stroke="#fff" strokeWidth="4.2" strokeLinecap="round" strokeLinejoin="round" />
            <path d="M15.8 24.2h8.4" stroke="#fff" strokeWidth="4.2" strokeLinecap="round" />
          </svg>
          <span className="brand-name">ANTENA</span>
        </Link>

        <nav className="modeswitch" aria-label="서비스 선택">
          {(['insight', 'sim'] as const).map((k) => (
            <Link key={k} to={MODES[k].home} className={k === mode ? 'on' : ''}>{MODES[k].label}</Link>
          ))}
        </nav>

        <nav className="nav">
          {modeConfig.nav.map((n) => (
            <Link
              key={n.key} to={n.href} className={n.key === nav ? 'on' : ''}
              onClick={() => { if (!window.matchMedia(WIDE).matches) setRailOpen(false) }}
            >
              <svg width="19" height="19" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.7" strokeLinecap="round" strokeLinejoin="round">
                {ICON[n.icon]}
              </svg>
              <span>{n.text}</span>
            </Link>
          ))}
        </nav>

        {/* 비로그인은 하단을 비워 둔다 — 로그인 진입은 상단바에만 있다 */}
        {authed && (
          <div className="rail-foot">
            {/* 설계서 §3 E 기준 마이페이지는 /me 다. 프로토타입의 /mypage 가 아니다 */}
            <Link className="rail-me" to="/me">
              <img className="rail-me-avatar" src={user?.avatarUrl} alt="" />
              <span><b>{`${user?.nickname ?? '안테나'}님`}</b><small>마이페이지</small></span>
              <em aria-hidden="true">›</em>
            </Link>
          </div>
        )}
      </aside>

      <div className="rail-backdrop" id="rail-backdrop" onClick={() => setRailOpen(false)} />

      {children}

      {/* 화면 내용 뒤에 둔다. 오버레이라 위치는 CSS(z-index)가 정하지만,
          읽는 순서에서도 본문 다음에 오는 게 맞다 */}
      {tour && (
        <OnboardingTour
          onClose={() => setTour(false)}
          onLinkWallet={() => { setTour(false); setLinking(true) }}
        />
      )}

      {linking && <WalletLinkModal onClose={() => setLinking(false)} />}

      {briefing.id !== null && (
        <BriefingModal id={briefing.id} onClose={briefing.close} />
      )}
    </>
  )
}
