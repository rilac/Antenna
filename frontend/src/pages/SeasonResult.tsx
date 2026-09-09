/* G-08 결과 · AI 복기 · /sim/:id/result
   담당 스토리 [ANT-FE-SEASON-RESULT]
   설계서 docs/화면설계서.md §3 · §4 G-08 · API 명세 §모의투자

   ── 값이 어디서 오는가 ────────────────────────────────────
   목업이 아니다. 서버가 들고 있는 그 판의 값이다 — GET /seasons/{id}/me 의 총자산·
   손익·포지션과 GET /trades 의 체결 내역이다. 자산 곡선만 그 둘에서 다시 만든다.

   ── 매매한 종목의 봉을 여기서 받는다 ─────────────────────
   자산 곡선은 게임일마다의 잔고를 다시 만드는 것이고, 그러려면 그날 들고 있던 종목의
   그날 종가가 필요하다. 그래서 보유 중인 것만이 아니라 <b>한 번이라도 매매한 종목</b>
   전부를 받는다 — 중간에 사고팔았다 끝낸 종목도 곡선에는 들어가 있어야 한다.
   보통 몇 개다(200종목을 다 받으면 요청이 200번이라 전부 받지는 않는다).

   ── 성적표는 서버가 굳힌 값이다 ──────────────────────────
   시장 대비·최대 낙폭·손익비·평균 보유일과 AI 복기는 POST /finish 가 만들어 저장하고
   GET /seasons/{id}/result/me 가 그대로 내려준다. 이 화면에서 다시 계산하지 않는다 —
   나중에 열어도 같은 값이어야 한다. 아직 없는 것은 배지·순위뿐이고 자리도 잡지 않았다.

   ── 접두어가 rs- 인 이유 ──────────────────────────────────
   sr- 는 검색 화면이 먼저 쓰고 있다. 자세한 사정은 sim-result.css 머리에 적었다. */
import { useEffect, useMemo, useRef, useState, type ReactNode } from 'react'
import { Link, useParams } from 'react-router-dom'
import { ApiError } from '../api/errors'
import {
  getPrices, getTickers, rate, signOf,
  getResult, type Candle, type SeasonFinishResult, type Ticker,
} from '../api/seasonPlay'
import { useApiQuery } from '../api/useApiQuery'
import EquityChart from '../components/sim/EquityChart'
import PortfolioDonut, { type DonutSlice } from '../components/sim/PortfolioDonut'
import ErrorState from '../components/state/ErrorState'
import { useSeasonSim } from '../sim/useSeasonSim'
import '../styles/screens/sim-result.css'

type SeasonHead = {
  id: number
  title: string
  lengthDays: number
  initialCash: number
  tickerCount: number
}

const won = (n: number) => `${Math.round(n).toLocaleString('ko-KR')}원`
const PIE_TOP = 4

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
    <div className={`rs-stat t-${tone}`}>
      <div className="rs-stat-txt">
        <span className="rs-stat-label">{label}</span>
        <b className="rs-stat-val num">{value}</b>
        {sub && <span className="rs-stat-sub num">{sub}</span>}
      </div>
      <span className="rs-stat-ico" aria-hidden="true">{icon}</span>
    </div>
  )
}

export default function SeasonResult() {
  const { id } = useParams<{ id: string }>()
  const seasonId = Number(id)
  const season = useApiQuery<SeasonHead>(`/seasons/${id}`)

  const [tickers, setTickers] = useState<Ticker[]>([])
  const [candles, setCandles] = useState<Record<number, Candle[]>>({})
  const [loadError, setLoadError] = useState<ApiError | null>(null)
  /** 서버 성적표. 회차를 끝내야 나온다 */
  const [card, setCard] = useState<SeasonFinishResult | null>(null)
  /** AI 복기. 성적표와 함께 finish 가 만든다. 서버에 키가 없던 회차는 null */
  const [review, setReview] = useState<string | null>(null)
  /** 성적표가 없다 — 마지막 날이면 자동으로 굳힌다 */
  const [needsFinish, setNeedsFinish] = useState(false)
  /** 자동 종료를 이미 한 번 시도했다 */
  const tried = useRef(false)

  /* 성적표·복기를 화면에 들어오는 즉시 채운다.

     ① 이미 끝난 회차면 GET /result/me 가 바로 준다.
     ② 아직 안 끝났는데 마지막 게임일에 닿아 있으면 여기서 POST /finish 를 부른다 —
        결과를 보러 들어온 사람에게 버튼을 한 번 더 누르게 할 이유가 없다.

     ②가 되돌릴 수 없는 요청인 것은 그대로다. 다만 <b>마지막 게임일에서만</b> 부른다.
     그 자리에서는 더 넘길 날도 없고, 남은 것은 굳히는 일뿐이다. 진행 중인 회차는
     ②를 타지 않으므로 결과 화면을 열어 봤다고 판이 끝나지는 않는다.
     아래 버튼은 ②가 실패했을 때의 길로 남긴다. */
  useEffect(() => {
    if (!seasonId) return
    let alive = true
    getResult(seasonId)
      .then((r) => { if (alive) { setCard(r); setReview(r.review) } })
      .catch((e) => {
        if (!alive) return
        if (e instanceof ApiError && e.code === 'SEASON_RESULT_NOT_FOUND') {
          setNeedsFinish(true)
          return
        }
        setLoadError(e instanceof ApiError ? e : null)
      })
    return () => { alive = false }
  }, [seasonId])

  /* 버튼으로 끝낸 경우 — 성적표는 finish 응답에 있고 복기는 result/me 로 한 번 더 받는다 */
  function finishHere() {
    void sim.finish().then((r) => {
      if (!r) return
      setCard(r)
      getResult(seasonId).then((d) => setReview(d.review)).catch(() => {})
    })
  }

  useEffect(() => {
    if (!seasonId) return
    getTickers(seasonId)
      .then((r) => setTickers(r.items))
      .catch((e) => setLoadError(e instanceof ApiError ? e : null))
  }, [seasonId])

  const sim = useSeasonSim({
    seasonId,
    initialCash: season.data?.initialCash ?? 0,
    lengthDays: season.data?.lengthDays ?? 0,
    tickers,
    candlesOf: (tickerId) => candles[tickerId],
  })

  /* 마지막 날인데 성적표가 없으면 여기서 굳힌다. sim 이 만들어진 뒤라야 진행일을
     알 수 있어 효과를 나눴다 — 위 효과는 시즌 번호만 알면 돈다. */
  useEffect(() => {
    if (!needsFinish || card !== null || sim.pending) return
    if (sim.day <= 0 || sim.day < (season.data?.lengthDays ?? 0)) return
    /* 한 번만 부른다. 되돌릴 수 없는 요청이라 렌더가 겹쳐도 두 번 가면 안 된다 —
       상태가 아니라 ref 를 쓰는 이유는 이 값이 화면을 다시 그릴 이유가 없어서다. */
    if (tried.current) return
    tried.current = true
    void sim.finish().then((r) => {
      if (!r) return
      setCard(r)
      getResult(seasonId).then((d) => setReview(d.review)).catch(() => {})
    })
  }, [needsFinish, card, sim, season.data, seasonId])

  /* 자산 곡선에 필요한 종목. 지금 들고 있는 것과 한 번이라도 매매한 것을 합친다 —
     중간에 사고팔았다 끝낸 종목도 그 구간의 곡선에는 들어가 있어야 한다. */
  const curveIds = useMemo(() => {
    const ids = new Set<number>()
    sim.positions.forEach((p) => ids.add(p.tickerId))
    sim.trades.forEach((t) => ids.add(t.tickerId))
    return [...ids]
  }, [sim.positions, sim.trades])

  useEffect(() => {
    if (!seasonId) return
    const missing = curveIds.filter((t) => !candles[t])
    if (missing.length === 0) return
    Promise.all(missing.map((t) => getPrices(seasonId, t).then((r) => [t, r.items] as const)))
      .then((rows) => setCandles((prev) => ({ ...prev, ...Object.fromEntries(rows) })))
      .catch((e) => setLoadError(e instanceof ApiError ? e : null))
  }, [seasonId, curveIds, candles])

  /* 종목별 성적. 실현손익은 매도에서, 미실현은 지금 보유에서 온다 —
     둘을 합쳐야 "이 종목으로 얼마 벌었나" 가 된다.
     훅은 조기 반환보다 위에 있어야 한다 — 아래에 두면 로딩 중일 때만 안 불려서
     리액트가 훅 순서를 잃는다. */
  const byTicker = useBoard(sim.trades, sim.positions)

  if (season.loading) {
    return (
      <main className="main">
        <div className="main-inner sim-result">
          <div className="placeholder tall">{'시즌을 불러오는 중…'}</div>
        </div>
      </main>
    )
  }

  if (season.error || !season.data) {
    return (
      <main className="main">
        <div className="main-inner sim-result">
          {season.error
            ? <ErrorState error={season.error} onRetry={season.reload} />
            : <div className="placeholder tall">{'시즌을 찾을 수 없습니다'}</div>}
        </div>
      </main>
    )
  }

  const s = season.data
  const done = sim.day > 0 && sim.day >= s.lengthDays
  /* 참가하지 않았으면 결과가 있을 수 없다. 훅이 joined=false 로 알려 준다 */
  const played = sim.joined === true && (sim.trades.length > 0 || sim.day > 1)

  /* 매매 요약. 승률은 매도 중 이익으로 끝난 비율이다 — 매수는 아직 결과가 없다 */
  const sells = sim.trades.filter((t) => t.realizedPnl !== null)
  const wins = sells.filter((t) => (t.realizedPnl ?? 0) > 0).length
  const winRate = sells.length > 0 ? (wins / sells.length) * 100 : null
  const realized = sells.reduce((a, t) => a + (t.realizedPnl ?? 0), 0)
  const unrealized = sim.pnl - realized

  /* 곡선에서 뽑아 쓰는 값들. 최고점 대비 어디서 끝났는지가 결과 화면의 핵심 문장이다 —
     "벌었다" 만으로는 최고점에서 얼마를 놓쳤는지가 안 보인다. */
  const curve = sim.equity.map((e) => e.totalAsset)
  const peak = curve.length ? Math.max(...curve) : s.initialCash
  const trough = curve.length ? Math.min(...curve) : s.initialCash
  const fromPeak = peak > 0 ? ((sim.totalAsset - peak) / peak) * 100 : 0
  const cashWeight = sim.totalAsset > 0 ? (sim.cash / sim.totalAsset) * 100 : 100

  /* 한 줄 요약. AI 가 쓴 문장이 아니라 위 값들로 만든 사실 문장이다 —
     목업을 넣지 않기로 했으므로 없는 리포트를 흉내 내지 않는다. */
  const oneLine =
    `${byTicker.length}종목을 ${sim.trades.length}회 매매해 ${rate(sim.pnlRate)} 로 마쳤습니다.` +
    (Math.abs(fromPeak) >= 0.05
      ? ` 최고점보다 ${Math.abs(fromPeak).toFixed(1)}% 낮은 자리에서 끝났습니다.`
      : ' 최고점에서 끝났습니다.')

  const sorted = [...sim.positions].sort((a, b) => b.value - a.value)
  const top = sorted.slice(0, PIE_TOP)
  const rest = sorted.slice(PIE_TOP)
  const slices: DonutSlice[] = [
    ...top.map((p) => ({
      key: `t${p.tickerId}`,
      label: p.displayName,
      value: p.value,
      detail: `${p.qty.toLocaleString('ko-KR')}주 · 평균 ${won(p.avgPrice)}`,
      pnl: p.pnl,
    })),
    ...(rest.length
      ? [{ key: 'ETC', label: `기타 ${rest.length}종목`, value: rest.reduce((a, p) => a + p.value, 0) }]
      : []),
    { key: 'CASH', label: '현금', value: sim.cash },
  ]

  return (
    <main className="main">
      <div className="main-inner sim-result">
        <nav className="rs-crumb" aria-label="위치">
          <Link to="/sim">모의투자 홈</Link>
          <i aria-hidden="true">›</i>
          <Link to="/sim/practice">연습</Link>
          <i aria-hidden="true">›</i>
          <span>결과</span>
        </nav>

        <header className="rs-head">
          <div>
            <h1>{s.title}</h1>
            <p>
              {done ? '끝까지 마쳤습니다' : `아직 진행 중입니다 · DAY ${sim.day}`}
              <span> · 총 {s.lengthDays}게임일</span>
            </p>
          </div>
          <div className="rs-acts">
            {!done && (
              <Link className="rs-go" to={`/sim/${seasonId}/play`}>이어서 하기</Link>
            )}
            <Link className="rs-back" to={`/sim/${seasonId}/trades`}>매매일지</Link>
            <Link className="rs-back" to="/sim/practice">다른 연습 고르기</Link>
          </div>
        </header>

        {!played ? (
          <section className="rs-card">
            <p className="rs-none">
              {sim.joined === false
                ? '아직 이 연습에 참가하지 않았습니다.'
                : '이 시즌은 아직 한 판도 하지 않았습니다.'}
              <small>진행 화면에서 주문을 넣고 다음 영업일로 넘기면 여기에 결과가 쌓입니다.</small>
              <Link className="rs-go" to={`/sim/${seasonId}/play`}>진행하러 가기</Link>
            </p>
          </section>
        ) : (
          <>
            {/* ── 성과 4칸 ─────────────────────────────── */}
            <section className="rs-stats" aria-label="최종 성과">
              <Stat
                tone={sim.pnl > 0 ? 'up' : sim.pnl < 0 ? 'down' : 'flat'}
                label="최종 총자산"
                value={won(sim.totalAsset)}
                sub={<span className={signOf(sim.pnl)}>{rate(sim.pnlRate)}</span>}
                icon={<Ico><path d="M4 16.5 9 11l3.5 3.5L20 7" /><path d="M15.5 7H20v4.5" /></Ico>}
              />
              <Stat
                tone={sim.pnl > 0 ? 'up' : sim.pnl < 0 ? 'down' : 'flat'}
                label="누적 손익"
                value={`${sim.pnl > 0 ? '+' : ''}${won(sim.pnl)}`}
                sub={<span className="rs-dim">시작 {won(s.initialCash)}</span>}
                icon={<Ico><path d="M12 19V5M12 5l-5 5M12 5l5 5" /></Ico>}
              />
              <Stat
                tone="trade"
                label="매매 횟수"
                value={`${sim.trades.length.toLocaleString('ko-KR')}회`}
                sub={<span className="rs-dim">매도 {sells.length}회</span>}
                icon={<Ico><path d="M4 7h13M14 4l3 3-3 3" /><path d="M20 17H7M10 14l-3 3 3 3" /></Ico>}
              />
              {/* "승률" 만 쓰면 무엇 대비인지 알 수 없다. 이 값은 매도 건수 기준이다 —
                  매수는 아직 결과가 없어 셀 수가 없다. 이름과 아랫줄에 기준을 적는다. */}
              <Stat
                tone="rate"
                label="매도 승률"
                value={winRate === null ? '—' : `${winRate.toFixed(0)}%`}
                sub={
                  <span className="rs-dim">
                    {sells.length === 0
                      ? '아직 판 적이 없습니다'
                      : `매도 ${sells.length}건 중 ${wins}건 이익`}
                  </span>
                }
                icon={<Ico><circle cx="12" cy="14" r="6" /><path d="M9 4h6M12 8V4" /></Ico>}
              />
            </section>

            {/* 왼쪽에 곡선, 오른쪽에 손익. 곡선을 넓고 낮게 두어 오르내림의 기울기가
                과장되지 않게 하고, 그만큼 두 카드의 바닥도 가까워진다. */}
            <div className="rs-body">
              {/* ── 자산 곡선 ───────────────────────────── */}
              <section className="rs-card rs-eq">
                <h2>
                  <span className="rs-h-ico t-eq" aria-hidden="true">
                    <Ico size={15}><path d="M4 16.5 9 11l3.5 3.5L20 7" /></Ico>
                  </span>
                  자산 곡선
                  <em>가로선이 시작 예수금 {won(s.initialCash)} · 그 위면 번 것</em>
                </h2>
                {/* 폭이 넓어졌으니 높이를 줄인다. 꺾은선은 넓고 낮아야 오르내림의
                    기울기가 과장되지 않는다 — 좁고 높으면 작은 흔들림도 절벽처럼 보인다. */}
                <EquityChart points={sim.equity} base={s.initialCash} height={165} />
              </section>

              <section className="rs-card rs-split">
                <h2>
                  <span className="rs-h-ico t-split" aria-hidden="true">
                    <Ico size={15}><path d="M12 4v16M4 8h16M4 16h16" /></Ico>
                  </span>
                  손익 나누기
                </h2>
                <dl className="rs-dl">
                  <div>
                    <dt>실현 손익<small>판 것에서 확정된 몫</small></dt>
                    <dd className={`num ${signOf(realized)}`}>
                      {realized > 0 ? '+' : ''}{won(realized)}
                    </dd>
                  </div>
                  <div>
                    <dt>미실현 손익<small>아직 들고 있는 것</small></dt>
                    <dd className={`num ${signOf(unrealized)}`}>
                      {unrealized > 0 ? '+' : ''}{won(unrealized)}
                    </dd>
                  </div>
                  <div className="rs-total">
                    <dt>합계</dt>
                    <dd className={`num ${signOf(sim.pnl)}`}>
                      {sim.pnl > 0 ? '+' : ''}{won(sim.pnl)}
                    </dd>
                  </div>
                </dl>
                <p className="rs-note">
                  아직 들고 있으면 값이 계속 움직입니다. 실현 손익만 확정된 몫입니다.
                </p>
              </section>
            </div>


            {/* ── 종목별 성적 · 포트폴리오 한 줄 ────────────
                세로로 쌓으면 성적표가 한 줄일 때 오른쪽이 통째로 빈다. 위 줄과 같은
                1.4 : 1 리듬으로 두어 카드 경계가 세로로 맞는다.
                이 줄은 늘이지 않는다(align-items:start) — 접힌 토글을 도넛 높이까지
                늘이면 빈 막대가 된다. */}
            <div className="rs-row">
            {/* ── 종목별 성적 (접이식) ─────────────────────
                접힌 채로 시작한다. 위에 성과 4칸 · 자산 곡선 · 손익 · 성적표가
                이미 결론을 다 말했으므로, 여기부터는 더 볼 사람만 편다.
                접힌 줄에 종목 수와 합계를 적어 두면 펴지 않고도 결론은 읽힌다. */}
            <details className="rs-fold">
              <summary>
                <span className="rs-h-ico t-board" aria-hidden="true">
                  <Ico size={15}><path d="M5 19V11M12 19V5M19 19v-5" /></Ico>
                </span>
                종목별 성적
                <em>
                  {byTicker.length}종목 · 합계{' '}
                  <b className={signOf(sim.pnl)}>
                    {sim.pnl > 0 ? '+' : ''}{won(sim.pnl)}
                  </b>
                </em>
                <i aria-hidden="true">⌄</i>
              </summary>
              <div className="rs-fold-body">
                {byTicker.length === 0 ? (
                  <p className="rs-empty">아직 매매한 종목이 없습니다.</p>
                ) : (
                  <ul className="rs-board">
                    <li className="rs-board-head">
                      <span>종목</span>
                      <span>매매</span>
                      <span>실현</span>
                      <span>미실현</span>
                      <span>합계</span>
                    </li>
                    {byTicker.map((r) => (
                      <li key={r.tickerId}>
                        <b>{r.displayName}</b>
                        <span className="num">{r.count}회</span>
                        <span className={`num ${signOf(r.realized)}`}>
                          {r.realized === 0 ? '—' : `${r.realized > 0 ? '+' : ''}${won(r.realized)}`}
                        </span>
                        <span className={`num ${signOf(r.unrealized)}`}>
                          {r.unrealized === 0 ? '—' : `${r.unrealized > 0 ? '+' : ''}${won(r.unrealized)}`}
                        </span>
                        <span className={`num rs-sum ${signOf(r.total)}`}>
                          {r.total > 0 ? '+' : ''}{won(r.total)}
                        </span>
                      </li>
                    ))}
                  </ul>
                )}
              </div>
            </details>

            {/* ── 포트폴리오 (접이식) ───────────────────────
                옆의 종목별 성적과 같은 토글로 둔다. 하나는 막대고 하나는 큰 카드면
                접힌 상태에서 두 칸이 어긋난다.

                접힌 줄에 작은 고리를 넣는다 — 접으면 원이 사라지는 게 이 화면에서
                제일 아쉬운 부분이었다. 작아도 "거의 다 현금" 같은 덩어리는 읽힌다.
                셋 다 접힌 채로 시작하므로 이 고리가 접힌 상태의 유일한 그림이다. */}
            <details className="rs-fold rs-pie">
              <summary>
                <span className="rs-h-ico t-pie" aria-hidden="true">
                  <Ico size={15}><circle cx="12" cy="12" r="8" /><path d="M12 4v8h8" /></Ico>
                </span>
                포트폴리오
                <em>현금 {cashWeight.toFixed(0)}%</em>
                <PortfolioDonut slices={slices} total={sim.totalAsset} mini />
                <i aria-hidden="true">⌄</i>
              </summary>
              <div className="rs-fold-body">
                {sim.positions.length === 0 ? (
                  <p className="rs-empty">
                    끝까지 현금으로 남았습니다.
                    <small>전액 현금 {won(sim.cash)}</small>
                  </p>
                ) : (
                  <PortfolioDonut slices={slices} total={sim.totalAsset} />
                )}
              </div>
            </details>
            </div>

            {/* 옆 두 칸(종목별 성적·포트폴리오)과 같은 접이식으로 둔다. 셋이 나란히
                접혀 있어야 아래쪽이 한 덩어리로 읽힌다.

                접힌 줄에 한 줄 요약을 실어 두므로 펴지 않아도 결론은 보인다 —
                "몇 종목을 몇 번 매매해 몇 % 로 마쳤나" 가 그 문장이다. */}
            <details className="rs-fold rs-card2">
              <summary>
                <span className="rs-h-ico t-card" aria-hidden="true">
                  <Ico size={15}><path d="M4 6h16v12H4z" /><path d="M8 10h8M8 14h5" /></Ico>
                </span>
                성적표
                <em className="rs-one-sum">{oneLine}</em>
                <i aria-hidden="true">⌄</i>
              </summary>
              <div className="rs-fold-body">

              {/* 곡선에서 나오는 값은 서버를 기다릴 것이 없다. 끝내기 전에도 보여 준다.
                  한 줄 요약은 접힌 줄에 있으므로 여기서 되풀이하지 않는다. */}
              <dl className="rs-facts">
                <div>
                  <dt>가장 높았을 때</dt>
                  <dd className="num">{won(peak)}</dd>
                </div>
                <div>
                  <dt>가장 낮았을 때</dt>
                  <dd className="num">{won(trough)}</dd>
                </div>
                <div>
                  <dt>최고점 대비 마감</dt>
                  <dd className={`num ${signOf(fromPeak)}`}>{rate(fromPeak)}</dd>
                </div>
                <div>
                  <dt>끝났을 때 현금 비중</dt>
                  <dd className="num">{cashWeight.toFixed(1)}%</dd>
                </div>
              </dl>

              <div className="rs-card-block">
              {card !== null ? null : !done ? (
                <p className="rs-wait">
                  마지막 게임일에 닿으면 성적표를 받을 수 있습니다.
                  <small>시장 대비 · 최대 낙폭 · 손익비 · 평균 보유일</small>
                </p>
              ) : null}
              {card === null && done ? (
                <p className="rs-none">
                  이 판을 끝내면 성적표가 나옵니다.
                  <small>
                    시장 대비 · 최대 낙폭 · 손익비 · 평균 보유일을 서버가 계산해 굳힙니다.{' '}
                    <b>끝내면 되돌릴 수 없습니다</b> — 더 이상 주문도 진행도 할 수 없습니다.
                  </small>
                  <button
                    type="button"
                    className="rs-go"
                    disabled={sim.pending}
                    onClick={finishHere}
                  >
                    {sim.pending ? '성적표·복기 만드는 중…' : '이 판 끝내고 성적표 받기'}
                  </button>
                  {sim.reject && (
                    <b className="rs-reject" role="alert">
                      {sim.reject === 'REVIEW'
                        ? 'AI 복기를 만들지 못해 종료하지 않았습니다. 다시 시도해 주세요.'
                        : '종료하지 못했습니다. 다시 시도해 주세요.'}
                    </b>
                  )}
                </p>
              ) : null}
              {card !== null && (
                <>
                  <p className="rs-sub">서버가 굳힌 값</p>
                  <dl className="rs-facts">
                    <div>
                      <dt>시장 대비</dt>
                      <dd className={`num ${
                        card.benchmarkReturn === null
                          ? 'flat'
                          : signOf(card.returnRate - card.benchmarkReturn)}`}
                      >
                        {card.benchmarkReturn === null
                          ? '—'
                          : rate(card.returnRate - card.benchmarkReturn)}
                      </dd>
                      <small>
                        {card.benchmarkReturn === null
                          ? '견줄 값이 없습니다'
                          : `시장 ${rate(card.benchmarkReturn)} · 나 ${rate(card.returnRate)}`}
                      </small>
                    </div>
                    <div>
                      <dt>최대 낙폭</dt>
                      <dd className="num down">-{card.maxDrawdown.toFixed(2)}%</dd>
                      <small>최고점에서 가장 깊게 파인 곳</small>
                    </div>
                    <div>
                      <dt>손익비</dt>
                      <dd className={`num ${
                        card.profitFactor === null
                          ? 'flat'
                          : card.profitFactor >= 1 ? 'up' : 'down'}`}
                      >
                        {card.profitFactor === null ? '—' : card.profitFactor.toFixed(2)}
                      </dd>
                      <small>
                        {card.profitFactor === null
                          ? '손해 본 매도가 없습니다'
                          : '번 돈 ÷ 잃은 돈. 1 미만이면 잃은 것'}
                      </small>
                    </div>
                    <div>
                      <dt>평균 보유일</dt>
                      <dd className="num">
                        {card.avgHoldingDays === null ? '—' : `${card.avgHoldingDays.toFixed(1)}일`}
                      </dd>
                      <small>
                        {card.avgHoldingDays === null ? '아직 판 적이 없습니다' : '사서 팔 때까지'}
                      </small>
                    </div>
                  </dl>

                  {/* AI 복기 — 서버가 finish 때 만들어 저장한 본문(ANT-SEASON-09).
                      세 단락(잘한 판단 / 아쉬운 판단 / 개선 제안)이라 줄바꿈을 그대로 살린다. */}
                  <div className="rs-review">
                    <h3>AI 복기</h3>
                    {review ? (
                      <p>{review}</p>
                    ) : (
                      <p className="rs-review-none">이 회차에는 AI 복기가 없습니다.</p>
                    )}
                  </div>
                  <p className="rs-note">
                    시장 대비는 <b>이 시즌 종목을 똑같이 나눠 사서 끝까지 들고 있었다면</b>과
                    견준 것입니다. 코스피 지수가 아니라 그 시즌 종목으로 만든 기준입니다.
                  </p>
                </>
              )}
              </div>
              </div>
            </details>
          </>
        )}

        {sim.loadError && <ErrorState error={sim.loadError} onRetry={() => { void sim.reload() }} inline />}
        {loadError && <ErrorState error={loadError} onRetry={() => setLoadError(null)} inline />}
      </div>
    </main>
  )
}

/** 종목별로 실현·미실현을 합친다 */
function useBoard(
  trades: { tickerId: number; displayName: string; realizedPnl: number | null }[],
  positions: { tickerId: number; displayName: string; pnl: number }[],
) {
  return useMemo(() => {
    const map = new Map<number, { tickerId: number; displayName: string; count: number; realized: number; unrealized: number }>()
    trades.forEach((t) => {
      const cur = map.get(t.tickerId) ?? {
        tickerId: t.tickerId, displayName: t.displayName, count: 0, realized: 0, unrealized: 0,
      }
      cur.count += 1
      cur.realized += t.realizedPnl ?? 0
      map.set(t.tickerId, cur)
    })
    positions.forEach((p) => {
      const cur = map.get(p.tickerId) ?? {
        tickerId: p.tickerId, displayName: p.displayName, count: 0, realized: 0, unrealized: 0,
      }
      cur.unrealized += p.pnl
      map.set(p.tickerId, cur)
    })
    /* 많이 번 것부터. 성적표라 위에서부터 읽는다 */
    return [...map.values()]
      .map((r) => ({ ...r, total: r.realized + r.unrealized }))
      .sort((a, b) => b.total - a.total)
  }, [trades, positions])
}
