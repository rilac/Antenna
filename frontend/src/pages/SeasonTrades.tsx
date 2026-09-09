/* G-06 매매일지 · /sim/:id/trades
   담당 스토리 [ANT-FE-SEASON-TRADES]
   설계서 docs/화면설계서.md §3 · §4 G-06 · API 명세 §모의투자

   ── 왜 따로 있는가 ───────────────────────────────────────
   GET /seasons/{id}/me 는 <b>지금 보유</b>지 이력이 아니다(설계서 §G-06). 결과 화면의
   종목별 성적도 종목마다 합계만 말한다. "언제 얼마에 샀더라" 를 볼 데가 여기다.

   ── 전부 받아 두고 화면에서 거른다 ───────────────────────
   서버가 종목·방향 필터를 받지만 쓰지 않는다. 한 회차 체결이 수십 건이라 커서로 끝까지
   받아 두는 편이 낫다 — 필터를 바꿀 때마다 왕복하지 않고, 위의 요약 숫자도 걸러진 것이
   아니라 전체 기준이 된다. 건수가 커지면 서버 필터로 옮기면 된다(getTrades 가 이미 받는다).

   ── 실현손익은 매도에만 있다 ─────────────────────────────
   매수는 아직 결과가 없다. 그 칸을 0 으로 채우면 "본전" 으로 읽히므로 — 로 비운다. */
import { useEffect, useMemo, useState, type ReactNode } from 'react'
import { Link, useParams } from 'react-router-dom'
import { ApiError } from '../api/errors'
import { getTrades, rate, signOf, type Side, type Trade } from '../api/seasonPlay'
import { useApiQuery } from '../api/useApiQuery'
import ErrorState from '../components/state/ErrorState'
import '../styles/screens/sim-trades.css'

type SeasonHead = {
  id: number
  title: string
  lengthDays: number
  initialCash: number
}

const won = (n: number) => `${Math.round(n).toLocaleString('ko-KR')}원`
const cut = (n: number) => Math.round(n).toLocaleString('ko-KR')

/** 끝까지 받되 한도를 둔다. 서버가 커서를 잘못 주면 무한히 도는 것을 막는다 */
const MAX_PAGES = 20
const PAGE = 100

const SIDES: { key: Side | 'ALL'; label: string }[] = [
  { key: 'ALL', label: '전체' },
  { key: 'BUY', label: '매수' },
  { key: 'SELL', label: '매도' },
]

const Ico = ({ size = 18, children }: { size?: number; children: ReactNode }) => (
  <svg width={size} height={size} viewBox="0 0 24 24" fill="none" stroke="currentColor"
       strokeWidth="1.7" strokeLinecap="round" strokeLinejoin="round" aria-hidden="true">
    {children}
  </svg>
)

function Stat({ tone, label, value, sub, icon }: {
  tone: string
  label: string
  value: string
  sub?: ReactNode
  icon: ReactNode
}) {
  return (
    <div className={`st-stat t-${tone}`}>
      <div className="st-stat-txt">
        <span className="st-stat-label">{label}</span>
        <b className="st-stat-val num">{value}</b>
        {sub && <span className="st-stat-sub num">{sub}</span>}
      </div>
      <span className="st-stat-ico" aria-hidden="true">{icon}</span>
    </div>
  )
}

export default function SeasonTrades() {
  const { id } = useParams<{ id: string }>()
  const seasonId = Number(id)
  const season = useApiQuery<SeasonHead>(`/seasons/${id}`)

  const [trades, setTrades] = useState<Trade[] | null>(null)
  const [loadError, setLoadError] = useState<ApiError | null>(null)

  const [side, setSide] = useState<Side | 'ALL'>('ALL')
  const [ticker, setTicker] = useState<number | 'ALL'>('ALL')

  /* 커서를 따라 끝까지 받는다. 페이지마다 다음 커서가 오고 hasNext 가 false 면 끝이다 */
  useEffect(() => {
    if (!seasonId) return
    let alive = true
    ;(async () => {
      try {
        const all: Trade[] = []
        let cursor: number | undefined
        for (let i = 0; i < MAX_PAGES; i += 1) {
          const page = await getTrades(seasonId, { cursor, size: PAGE })
          all.push(...page.items)
          if (!page.hasNext || page.nextCursor === null) break
          cursor = page.nextCursor
        }
        if (alive) {
          setTrades(all)
          setLoadError(null)
        }
      } catch (e) {
        if (alive) setLoadError(e instanceof ApiError ? e : null)
      }
    })()
    return () => { alive = false }
  }, [seasonId])

  /* 종목 고르개는 실제로 매매한 종목만 낸다. 시즌 종목 200개를 다 늘어놓으면
     한 번도 안 산 종목이 대부분이라 고를 것을 찾을 수 없다. */
  const tickerOptions = useMemo(() => {
    const seen = new Map<number, string>()
    ;(trades ?? []).forEach((t) => seen.set(t.tickerId, t.tickerName))
    return [...seen.entries()].sort((a, b) => a[1].localeCompare(b[1], 'ko'))
  }, [trades])

  const rows = useMemo(
    () =>
      (trades ?? []).filter(
        (t) =>
          (side === 'ALL' || t.side === side) &&
          (ticker === 'ALL' || t.tickerId === ticker),
      ),
    [trades, side, ticker],
  )

  /* 요약은 걸러지기 전 전체 기준이다. 필터를 바꿀 때마다 총계가 흔들리면
     "내가 이 판에서 몇 번 샀나" 를 답할 수 없다. */
  const all = trades ?? []
  const buys = all.filter((t) => t.side === 'BUY').length
  const sells = all.filter((t) => t.side === 'SELL')
  const realized = sells.reduce((a, t) => a + (t.realizedPnl ?? 0), 0)
  const wins = sells.filter((t) => (t.realizedPnl ?? 0) > 0).length

  if (season.loading || trades === null) {
    return (
      <main className="main">
        <div className="main-inner sim-trades">
          {loadError
            ? <ErrorState error={loadError} onRetry={() => setLoadError(null)} />
            : <div className="placeholder tall">{'매매일지를 불러오는 중…'}</div>}
        </div>
      </main>
    )
  }

  if (season.error || !season.data) {
    return (
      <main className="main">
        <div className="main-inner sim-trades">
          {season.error
            ? <ErrorState error={season.error} onRetry={season.reload} />
            : <div className="placeholder tall">{'시즌을 찾을 수 없습니다'}</div>}
        </div>
      </main>
    )
  }

  const s = season.data

  return (
    <main className="main">
      <div className="main-inner sim-trades">
        <nav className="st-crumb" aria-label="위치">
          <Link to="/sim">모의투자 홈</Link>
          <i aria-hidden="true">›</i>
          <Link to="/sim/practice">연습</Link>
          <i aria-hidden="true">›</i>
          <span>매매일지</span>
        </nav>

        <header className="st-head">
          <div>
            <h1>{s.title}</h1>
            <p>매매일지 · 총 {s.lengthDays}게임일</p>
          </div>
          <div className="st-acts">
            <Link className="st-back" to={`/sim/${seasonId}/play`}>진행으로</Link>
            <Link className="st-go" to={`/sim/${seasonId}/result`}>결과 보기</Link>
          </div>
        </header>

        {/* ── 요약 4칸 ─────────────────────────────────── */}
        <section className="st-stats" aria-label="매매 요약">
          <Stat
            tone="all"
            label="전체 매매"
            value={`${all.length.toLocaleString('ko-KR')}회`}
            sub={<span className="st-dim">{tickerOptions.length}종목</span>}
            icon={<Ico><path d="M4 7h13M14 4l3 3-3 3" /><path d="M20 17H7M10 14l-3 3 3 3" /></Ico>}
          />
          <Stat
            tone="buy"
            label="매수"
            value={`${buys.toLocaleString('ko-KR')}회`}
            icon={<Ico><path d="M12 5v14M5 12l7 7 7-7" /></Ico>}
          />
          <Stat
            tone="sell"
            label="매도"
            value={`${sells.length.toLocaleString('ko-KR')}회`}
            sub={
              <span className="st-dim">
                {sells.length === 0 ? '아직 판 적이 없습니다' : `${wins}건 이익`}
              </span>
            }
            icon={<Ico><path d="M12 19V5M5 12l7-7 7 7" /></Ico>}
          />
          <Stat
            tone={realized > 0 ? 'up' : realized < 0 ? 'down' : 'flat'}
            label="실현 손익"
            value={`${realized > 0 ? '+' : ''}${won(realized)}`}
            sub={<span className="st-dim">판 것에서 확정된 몫</span>}
            icon={<Ico><path d="M12 19V5M12 5l-5 5M12 5l5 5" /></Ico>}
          />
        </section>

        {/* ── 목록 ─────────────────────────────────────── */}
        <section className="st-card">
          <div className="st-tools">
            <div className="st-sides" role="group" aria-label="매수·매도">
              {SIDES.map((x) => (
                <button
                  key={x.key}
                  type="button"
                  aria-pressed={side === x.key}
                  className={side === x.key ? 'on' : undefined}
                  onClick={() => setSide(x.key)}
                >
                  {x.label}
                </button>
              ))}
            </div>

            <label className="st-pick">
              <span>종목</span>
              <select
                value={ticker === 'ALL' ? 'ALL' : String(ticker)}
                onChange={(e) => setTicker(e.target.value === 'ALL' ? 'ALL' : Number(e.target.value))}
              >
                <option value="ALL">전체 ({tickerOptions.length})</option>
                {tickerOptions.map(([tid, name]) => (
                  <option key={tid} value={tid}>{name}</option>
                ))}
              </select>
              <i aria-hidden="true">⌄</i>
            </label>

            <p className="st-count num">{rows.length}건</p>
          </div>

          {rows.length === 0 ? (
            <p className="st-empty">
              {all.length === 0
                ? '아직 체결된 주문이 없습니다.'
                : '고른 조건에 맞는 체결이 없습니다.'}
              <small>
                {all.length === 0
                  ? '진행 화면에서 주문을 넣으면 여기에 쌓입니다.'
                  : '필터를 전체로 두면 다시 보입니다.'}
              </small>
            </p>
          ) : (
            <ul className="st-list">
              <li className="st-row st-row-head">
                <span>게임일</span>
                <span>구분</span>
                <span>종목</span>
                <span>수량</span>
                <span>체결가</span>
                <span>금액</span>
                <span>실현 손익</span>
              </li>
              {rows.map((t) => (
                <li key={t.tradeId} className="st-row">
                  <span className="st-day num">DAY {t.gameDay}</span>
                  <span className={`st-side ${t.side === 'BUY' ? 'buy' : 'sell'}`}>
                    {t.side === 'BUY' ? '매수' : '매도'}
                  </span>
                  <b>{t.tickerName}</b>
                  <span className="num">{cut(t.qty)}주</span>
                  <span className="num">{cut(t.price)}</span>
                  <span className="st-amt num">{won(t.amount)}</span>
                  {/* 매수는 아직 결과가 없다. 0 으로 채우면 "본전" 으로 읽힌다 */}
                  <span className={`st-pnl num ${
                    t.realizedPnl === null ? 'flat' : signOf(t.realizedPnl)}`}
                  >
                    {t.realizedPnl === null
                      ? '—'
                      : `${t.realizedPnl > 0 ? '+' : ''}${won(t.realizedPnl)}`}
                  </span>
                </li>
              ))}
            </ul>
          )}

          <p className="st-note">
            체결가는 그 게임일 종가입니다. 수수료와 슬리피지는 없습니다.
            {sells.length > 0 && (
              <> 실현 손익은 평균 매입가 기준입니다. 매도 {sells.length}건 중 {wins}건이
                이익이라 건수로 세면 {rate((wins / sells.length) * 100).replace('+', '')}입니다 —
                나눠 팔면 한 판단이 여러 건으로 세어지므로 참고로만 보세요.</>
            )}
          </p>
        </section>

        {loadError && <ErrorState error={loadError} onRetry={() => setLoadError(null)} inline />}
      </div>
    </main>
  )
}
