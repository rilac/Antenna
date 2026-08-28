/* ANTENA 공용 셸 (상단바 + 사이드바) — 프로토타입 assets/app.js 의 React 포팅 */
import { useEffect, useRef, useState } from 'react'
import { Link, useNavigate } from 'react-router-dom'

export type Mode = 'insight' | 'sim'

const ICON: Record<string, React.ReactNode> = {
  home: <><path d="M3 10.5 12 3l9 7.5" /><path d="M5 9.5V20a1 1 0 0 0 1 1h4v-6h4v6h4a1 1 0 0 0 1-1V9.5" /></>,
  stocks: <><path d="M3 3v18h18" /><path d="M7 15l4-5 3 3 5-7" /></>,
  market: <><path d="M12 3v18" /><path d="M5 8h9a3 3 0 0 1 0 6H5" /><path d="M5 8H3" /><path d="M19 14h2" /></>,
  report: <><path d="M6 2h8l4 4v16H6z" /><path d="M14 2v4h4" /><path d="M9 12h6M9 16h6" /></>,
  portfolio: <><path d="M3 7h18v13H3z" /><path d="M9 7V5a2 2 0 0 1 2-2h2a2 2 0 0 1 2 2v2" /><path d="M3 12h18" /></>,
  community: <><path d="M17 20v-2a4 4 0 0 0-4-4H6a4 4 0 0 0-4 4v2" /><circle cx="9.5" cy="7" r="3.5" /><path d="M22 20v-2a4 4 0 0 0-3-3.87" /></>,
  star: <path d="m12 3 2.8 5.7 6.2.9-4.5 4.4 1.1 6.2-5.6-3-5.6 3 1.1-6.2L3 9.6l6.2-.9z" />,
  play: <><circle cx="12" cy="12" r="9" /><path d="m10 8.5 6 3.5-6 3.5z" /></>,
  research: <><circle cx="10.5" cy="10.5" r="6.5" /><path d="m20 20-4.6-4.6" /><path d="M8 10.5h5M10.5 8v5" /></>,
  trophy: <><path d="M7 4h10v5a5 5 0 0 1-10 0z" /><path d="M7 6H4v2a3 3 0 0 0 3 3M17 6h3v2a3 3 0 0 1-3 3" /><path d="M10 19h4M12 14v5M8 21h8" /></>,
}

const MODES = {
  insight: {
    label: 'ANTENA',
    home: '/',
    nav: [
      { key: 'home', icon: 'home', text: '홈', href: '/' },
      { key: 'stocks', icon: 'stocks', text: '종목 보기', href: '/stocks' },
      { key: 'market', icon: 'market', text: '예측가', href: '/market' },
      { key: 'report', icon: 'report', text: '리포트', href: '/reports' },
      { key: 'portfolio', icon: 'portfolio', text: '포트폴리오', href: '/portfolio' },
      { key: 'community', icon: 'community', text: '커뮤니티', href: '/community' },
      { key: 'watchlist', icon: 'star', text: '찜', href: '/watchlist' },
    ],
  },
  sim: {
    label: '모의 투자',
    home: '/sim',
    nav: [
      { key: 'home', icon: 'home', text: '모의 투자 홈', href: '/sim' },
      { key: 'play', icon: 'play', text: '모의 투자', href: '/sim/setup' },
      { key: 'portfolio', icon: 'portfolio', text: '내 포트폴리오', href: '/sim/portfolio' },
      { key: 'research', icon: 'research', text: '리서치', href: '/sim/research' },
      { key: 'ranking', icon: 'trophy', text: '랭킹', href: '/sim/ranking' },
    ],
  },
} as const

// 상단바 검색용 종목 데이터 — 종목 보기 목록과 동일하게 유지한다.
const STOCKS = [
  { code: '005930', name: '삼성전자', market: 'KOSPI', sector: '반도체', price: 71800, change: 1.42, per: 14.3, pbr: 1.31, logo: 'logo-samsung', mark: '삼' },
  { code: '000660', name: 'SK하이닉스', market: 'KOSPI', sector: '반도체', price: 198500, change: 2.85, per: 9.8, pbr: 1.72, logo: 'logo-skhy', mark: 'S' },
  { code: '247540', name: '에코프로비엠', market: 'KOSDAQ', sector: '2차전지', price: 167200, change: -1.36, per: 48.5, pbr: 5.24, logo: 'logo-ecopro', mark: '에' },
  { code: '373220', name: 'LG에너지솔루션', market: 'KOSPI', sector: '2차전지', price: 342000, change: -1.87, per: 72.4, pbr: 3.61, logo: 'logo-lgenergy', mark: 'L' },
  { code: '035420', name: 'NAVER', market: 'KOSPI', sector: '인터넷', price: 176300, change: -0.62, per: 18.7, pbr: 1.12, logo: 'logo-naver2', mark: 'N' },
  { code: '005380', name: '현대차', market: 'KOSPI', sector: '자동차', price: 242000, change: 0.83, per: 5.4, pbr: 0.68, logo: 'logo-hyundai', mark: '현' },
  { code: '035720', name: '카카오', market: 'KOSPI', sector: '인터넷', price: 42150, change: -1.04, per: 25.2, pbr: 1.08, logo: 'logo-kakao2', mark: '카' },
  { code: '068270', name: '셀트리온', market: 'KOSPI', sector: '바이오', price: 194600, change: -0.28, per: 41.9, pbr: 2.54, logo: 'logo-celltrion', mark: '셀' },
  { code: '207940', name: '삼성바이오로직스', market: 'KOSPI', sector: '바이오', price: 968000, change: 0.37, per: 62.1, pbr: 6.48, logo: 'logo-sambio', mark: '삼' },
  { code: '105560', name: 'KB금융', market: 'KOSPI', sector: '금융', price: 87900, change: 0.11, per: 6.2, pbr: 0.59, logo: 'logo-kb', mark: 'K' },
]
type Stock = (typeof STOCKS)[number]

// 로그인 상태 — 프로토타입이라 localStorage 로만 흉내낸다.
const AUTH_KEY = 'antena.auth'
const RAIL_KEY = 'antena.rail'
const WIDE = '(min-width: 901px)'

const read = (key: string) => { try { return localStorage.getItem(key) } catch { return null } }
const write = (key: string, value: string) => { try { localStorage.setItem(key, value) } catch { /* 무시 */ } }

function detailUrl(s: Stock) {
  const params = new URLSearchParams({
    code: s.code, name: s.name, market: s.market, sector: s.sector,
    price: String(s.price), change: String(s.change),
    per: String(s.per), pbr: String(s.pbr), logo: s.logo, mark: s.mark,
  })
  return `/stock-detail?${params}`
}

function match(q: string) {
  const key = q.trim().toLowerCase().replace(/\s+/g, '')
  if (!key) return []
  return STOCKS.filter((s) =>
    s.name.toLowerCase().replace(/\s+/g, '').includes(key) ||
    s.code.startsWith(key) ||
    s.sector.replace(/\s+/g, '').includes(key)
  ).slice(0, 7)
}

function Search() {
  const navigate = useNavigate()
  const [query, setQuery] = useState('')
  const [open, setOpen] = useState(false)
  const [cursor, setCursor] = useState(-1)
  const hits = open ? match(query) : []

  function pick(s: Stock) {
    setOpen(false)
    navigate(detailUrl(s))
  }

  return (
    <div className="search">
      <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round">
        <circle cx="11" cy="11" r="7" /><path d="m20 20-3.5-3.5" />
      </svg>
      <input
        type="search" id="topbar-search" autoComplete="off" role="combobox"
        aria-expanded={hits.length > 0 || (open && query.trim().length > 0)}
        aria-controls="search-suggest"
        placeholder="종목명 또는 종목코드를 검색하세요 (예: 삼성전자, 005930)"
        value={query}
        onChange={(e) => { setQuery(e.target.value); setOpen(true); setCursor(-1) }}
        onFocus={() => { if (query.trim()) setOpen(true) }}
        onBlur={() => setTimeout(() => setOpen(false), 120)}
        onKeyDown={(e) => {
          if (e.key === 'ArrowDown') { e.preventDefault(); setCursor((c) => (c + 1) % Math.max(1, hits.length)) }
          else if (e.key === 'ArrowUp') { e.preventDefault(); setCursor((c) => (c - 1 + hits.length) % Math.max(1, hits.length)) }
          else if (e.key === 'Escape') setOpen(false)
          else if (e.key === 'Enter') {
            e.preventDefault()
            const list = hits.length ? hits : match(query)
            const target = list[cursor >= 0 ? cursor : 0]
            if (target) pick(target)
          }
        }}
      />
      <div className="search-suggest" id="search-suggest" role="listbox" hidden={!open || !query.trim()}>
        {!hits.length ? <p className="sg-empty">일치하는 종목이 없습니다</p> : hits.map((s, i) => (
          <button
            key={s.code} className={`sg-item${i === cursor ? ' on' : ''}`} type="button" role="option"
            onMouseDown={(e) => { e.preventDefault(); pick(s) }}
          >
            <i className={`sg-logo ${s.logo}`}>{s.mark}</i>
            <span className="sg-main">
              <b>{s.name}</b>
              <small>{`${s.code} · ${s.market} · ${s.sector}`}</small>
            </span>
            <span className="sg-price">
              <b>{`${s.price.toLocaleString('ko-KR')}원`}</b>
              <small className={s.change >= 0 ? 'rise' : 'fall'}>{`${s.change >= 0 ? '+' : ''}${s.change.toFixed(2)}%`}</small>
            </span>
          </button>
        ))}
      </div>
    </div>
  )
}

export default function Layout({ mode, nav, bodyClass, children }: { mode: Mode; nav: string; bodyClass: string; children: React.ReactNode }) {
  const navigate = useNavigate()
  const [railOpen, setRailOpen] = useState(() => window.matchMedia(WIDE).matches && read(RAIL_KEY) !== '0')
  const [authed, setAuthed] = useState(() => read(AUTH_KEY) === '1')
  const wide = useRef(window.matchMedia(WIDE))

  // 사이드바: 넓은 화면은 기본 열림(선택을 기억), 좁은 화면은 기본 닫힘
  // body 클래스는 화면 클래스와 rail-open 을 함께 여기서 관리한다
  useEffect(() => {
    document.body.className = railOpen ? `${bodyClass} rail-open`.trim() : bodyClass
  }, [railOpen, bodyClass])

  useEffect(() => {
    const mq = wide.current
    const onChange = () => setRailOpen(mq.matches && read(RAIL_KEY) !== '0')
    const onKey = (e: KeyboardEvent) => {
      if (e.key === 'Escape' && !mq.matches) setRailOpen(false)
    }
    mq.addEventListener('change', onChange)
    document.addEventListener('keydown', onKey)
    return () => { mq.removeEventListener('change', onChange); document.removeEventListener('keydown', onKey) }
  }, [])

  function toggleRail() {
    const next = !railOpen
    setRailOpen(next)
    if (wide.current.matches) write(RAIL_KEY, next ? '1' : '0')
  }

  const modeConfig = MODES[mode] ?? MODES.insight

  return (
    <>
      <header className="topbar">
        <button className="rail-toggle" id="rail-toggle" aria-label="메뉴 열기/닫기" aria-controls="rail" aria-expanded={railOpen} onClick={toggleRail}>
          <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.9" strokeLinecap="round">
            <path d="M4 7h16M4 12h16M4 17h16" />
          </svg>
        </button>
        <Link className="brand" to="/">
          <svg className="brand-mark" viewBox="0 0 40 40" fill="none" aria-hidden="true">
            <rect width="40" height="40" rx="11" fill="currentColor" />
            <path d="M11.5 29.5 20 11.5l8.5 18" stroke="#fff" strokeWidth="4.2" strokeLinecap="round" strokeLinejoin="round" />
            <path d="M15.8 24.2h8.4" stroke="#fff" strokeWidth="4.2" strokeLinecap="round" />
          </svg>
          <span className="brand-name">ANTENA</span>
        </Link>
        <Search />
        <nav className="modeswitch">
          {(['insight', 'sim'] as const).map((k) => (
            <Link key={k} to={MODES[k].home} className={k === mode ? 'on' : ''}>{MODES[k].label}</Link>
          ))}
        </nav>
        <div className="topbar-right">
          <div className="wallet"><i /><span className="num">1,250</span> ANT</div>
          <button className="iconbtn" aria-label="알림">
            <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.7" strokeLinecap="round" strokeLinejoin="round">
              <path d="M18 8a6 6 0 1 0-12 0c0 6-3 7-3 7h18s-3-1-3-7" /><path d="M13.7 21a2 2 0 0 1-3.4 0" />
            </svg>
            <span className="badge">2</span>
          </button>
          <button className="iconbtn" aria-label="설정">
            <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.7" strokeLinecap="round" strokeLinejoin="round">
              <circle cx="12" cy="12" r="3" />
              <path d="M19.4 15a1.6 1.6 0 0 0 .3 1.8l.1.1a2 2 0 1 1-2.8 2.8l-.1-.1a1.6 1.6 0 0 0-2.7 1.1v.2a2 2 0 1 1-4 0v-.1a1.6 1.6 0 0 0-2.7-1.2l-.1.1a2 2 0 1 1-2.8-2.8l.1-.1a1.6 1.6 0 0 0-1.1-2.7H3.4a2 2 0 1 1 0-4h.1A1.6 1.6 0 0 0 4.7 6.3l-.1-.1a2 2 0 1 1 2.8-2.8l.1.1a1.6 1.6 0 0 0 2.7-1.1V2a2 2 0 1 1 4 0v.1a1.6 1.6 0 0 0 2.7 1.2l.1-.1a2 2 0 1 1 2.8 2.8l-.1.1a1.6 1.6 0 0 0 1.1 2.7h.2a2 2 0 1 1 0 4h-.1a1.6 1.6 0 0 0-1.5 1" />
            </svg>
          </button>
          <Link className="avatar" to="/mypage" aria-label="내 계정" />
        </div>
      </header>

      <aside className="rail" id="rail">
        <nav className="nav">
          {modeConfig.nav.map((n) => (
            <Link
              key={n.key} to={n.href} className={n.key === nav ? 'on' : ''}
              onClick={() => { if (!wide.current.matches) setRailOpen(false) }}
            >
              <svg width="19" height="19" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.7" strokeLinecap="round" strokeLinejoin="round">
                {ICON[n.icon]}
              </svg>
              <span>{n.text}</span>
            </Link>
          ))}
        </nav>
        <div className="rail-foot">
          {authed ? (
            <>
              <Link className="rail-me" to="/mypage">
                <i className="rail-me-avatar">안</i>
                <span><b>안테나님</b><small>마이페이지</small></span>
                <em aria-hidden="true">›</em>
              </Link>
              <button
                className="rail-logout" id="rail-logout" type="button"
                onClick={() => {
                  try { localStorage.removeItem(AUTH_KEY) } catch { /* 무시 */ }
                  setAuthed(false)
                  navigate('/')
                }}
              >로그아웃</button>
            </>
          ) : (
            <Link className="btn-login" to="/login">로그인 / 회원가입</Link>
          )}
        </div>
      </aside>
      <div className="rail-backdrop" id="rail-backdrop" onClick={() => setRailOpen(false)} />

      {children}
    </>
  )
}
