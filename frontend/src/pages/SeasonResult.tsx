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

   ── 아직 서버가 주지 않는 것 ──────────────────────────────
   AI 복기 리포트 · 배지 · 순위. 자리만 잡고 목업을 넣지 않는다.
   GET /seasons/{id}/result/me 가 붙으면 이 화면의 숫자를 그 응답으로 갈아끼운다. */
import { useEffect, useMemo, useState, type ReactNode } from 'react'
import { Link, useParams } from 'react-router-dom'
import { ApiError } from '../api/errors'
import {
  getPrices, getTickers, rate, signOf,
  type Candle, type SeasonFinishResult, type Ticker,
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
    <div className={`sr-stat t-${tone}`}>
      <div className="sr-stat-txt">
        <span className="sr-stat-label">{label}</span>
        <b className="sr-stat-val num">{value}</b>
        {sub && <span className="sr-stat-sub num">{sub}</span>}
      </div>
      <span className="sr-stat-ico" aria-hidden="true">{icon}</span>
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
        <nav className="sr-crumb" aria-label="위치">
          <Link to="/sim">모의투자 홈</Link>
          <i aria-hidden="true">›</i>
          <Link to="/sim/practice">연습</Link>
          <i aria-hidden="true">›</i>
          <span>결과</span>
        </nav>

        <header className="sr-head">
          <div>
            <h1>{s.title}</h1>
            <p>
              {done ? '끝까지 마쳤습니다' : `아직 진행 중입니다 · DAY ${sim.day}`}
              <span> · 총 {s.lengthDays}게임일</span>
            </p>
          </div>
          <div className="sr-acts">
            {!done && (
              <Link className="sr-go" to={`/sim/${seasonId}/play`}>이어서 하기</Link>
            )}
            <Link className="sr-back" to="/sim/practice">다른 연습 고르기</Link>
          </div>
        </header>

        {!played ? (
          <section className="sr-card">
            <p className="sr-none">
              {sim.joined === false
                ? '아직 이 연습에 참가하지 않았습니다.'
                : '이 시즌은 아직 한 판도 하지 않았습니다.'}
              <small>진행 화면에서 주문을 넣고 다음 영업일로 넘기면 여기에 결과가 쌓입니다.</small>
              <Link className="sr-go" to={`/sim/${seasonId}/play`}>진행하러 가기</Link>
            </p>
          </section>
        ) : (
          <>
            {/* ── 성과 4칸 ─────────────────────────────── */}
            <section className="sr-stats" aria-label="최종 성과">
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
                sub={<span className="sr-dim">시작 {won(s.initialCash)}</span>}
                icon={<Ico><path d="M12 19V5M12 5l-5 5M12 5l5 5" /></Ico>}
              />
              <Stat
                tone="trade"
                label="매매 횟수"
                value={`${sim.trades.length.toLocaleString('ko-KR')}회`}
                sub={<span className="sr-dim">매도 {sells.length}회</span>}
                icon={<Ico><path d="M4 7h13M14 4l3 3-3 3" /><path d="M20 17H7M10 14l-3 3 3 3" /></Ico>}
              />
              {/* "승률" 만 쓰면 무엇 대비인지 알 수 없다. 이 값은 매도 건수 기준이다 —
                  매수는 아직 결과가 없어 셀 수가 없다. 이름과 아랫줄에 기준을 적는다. */}
              <Stat
                tone="rate"
                label="매도 승률"
                value={winRate === null ? '—' : `${winRate.toFixed(0)}%`}
                sub={
                  <span className="sr-dim">
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
            <div className="sr-body">
              {/* ── 자산 곡선 ───────────────────────────── */}
              <section className="sr-card sr-eq">
                <h2>
                  <span className="sr-h-ico t-eq" aria-hidden="true">
                    <Ico size={15}><path d="M4 16.5 9 11l3.5 3.5L20 7" /></Ico>
                  </span>
                  자산 곡선
                  <em>가로선이 시작 예수금 {won(s.initialCash)} · 그 위면 번 것</em>
                </h2>
                {/* 폭이 넓어졌으니 높이를 줄인다. 꺾은선은 넓고 낮아야 오르내림의
                    기울기가 과장되지 않는다 — 좁고 높으면 작은 흔들림도 절벽처럼 보인다. */}
                <EquityChart points={sim.equity} base={s.initialCash} height={165} />
              </section>

              <section className="sr-card sr-split">
                <h2>
                  <span className="sr-h-ico t-split" aria-hidden="true">
                    <Ico size={15}><path d="M12 4v16M4 8h16M4 16h16" /></Ico>
                  </span>
                  손익 나누기
                </h2>
                <dl className="sr-dl">
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
                  <div className="sr-total">
                    <dt>합계</dt>
                    <dd className={`num ${signOf(sim.pnl)}`}>
                      {sim.pnl > 0 ? '+' : ''}{won(sim.pnl)}
                    </dd>
                  </div>
                </dl>
                <p className="sr-note">
                  아직 들고 있으면 값이 계속 움직입니다. 실현 손익만 확정된 몫입니다.
                </p>
              </section>
            </div>

            <section className="sr-card sr-card2">
              <h2>
                <span className="sr-h-ico t-card" aria-hidden="true">
                  <Ico size={15}><path d="M4 6h16v12H4z" /><path d="M8 10h8M8 14h5" /></Ico>
                </span>
                성적표
                {card && <em>서버가 굳힌 값</em>}
              </h2>

              {/* ── POST /finish ─────────────────────────────
                  자동으로 부르지 않는다. finish 는 회차를 DONE 으로 만들어 주문도
                  진행도 막는 되돌릴 수 없는 요청이라, 결과를 보러 들어온 것만으로
                  판이 끝나면 안 된다. 누르는 것은 사람이어야 한다.

                  (GET /me 가 회차 status 를 주면 이미 끝난 회차인지 알 수 있어
                   그때는 물어보지 않고 바로 받아 올 수 있다 — 서버에 요청해 둘 것.) */}
              <div className="sr-card-block">
              {card !== null ? null : !done ? (
                <p className="sr-wait">
                  마지막 게임일에 닿으면 성적표를 받을 수 있습니다.
                  <small>시장 대비 · 최대 낙폭 · 손익비 · 평균 보유일</small>
                </p>
              ) : null}
              {card === null && done ? (
                <p className="sr-none">
                  이 판을 끝내면 성적표가 나옵니다.
                  <small>
                    시장 대비 · 최대 낙폭 · 손익비 · 평균 보유일을 서버가 계산해 굳힙니다.
                    <b>끝내면 되돌릴 수 없습니다</b> — 더 이상 주문도 진행도 할 수 없습니다.
                  </small>
                  <button
                    type="button"
                    className="sr-go"
                    disabled={sim.pending}
                    onClick={() => { void sim.finish().then((r) => { if (r) setCard(r) }) }}
                  >
                    {sim.pending ? '끝내는 중…' : '이 판 끝내고 성적표 받기'}
                  </button>
                </p>
              ) : null}
              {card !== null && (
                <>
                  <dl className="sr-facts">
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
                  <p className="sr-note">
                    시장 대비는 <b>이 시즌 종목을 똑같이 나눠 사서 끝까지 들고 있었다면</b>과
                    견준 것입니다. 코스피 지수가 아니라 그 시즌 종목으로 만든 기준입니다.
                  </p>
                </>
              )}
              </div>
            </section>

            {/* ── 종목별 성적 · 포트폴리오 한 줄 ────────────
                세로로 쌓으면 성적표가 한 줄일 때 오른쪽이 통째로 빈다. 위 줄과 같은
                1.4 : 1 리듬으로 두어 카드 경계가 세로로 맞는다.
                이 줄은 늘이지 않는다(align-items:start) — 접힌 토글을 도넛 높이까지
                늘이면 빈 막대가 된다. */}
            <div className="sr-row">
            {/* ── 종목별 성적 (접이식) ─────────────────────
                접어 둔다. 종목이 한둘이면 표가 한 줄이라 늘 펴 둘 값이 아니고,
                여러 종목이면 길어져 아래 카드를 밀어낸다. 접힌 줄에 종목 수와
                합계를 적어 두면 펴지 않고도 결론은 읽힌다. */}
            <details className="sr-fold" open>
              <summary>
                <span className="sr-h-ico t-board" aria-hidden="true">
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
              <div className="sr-fold-body">
                {byTicker.length === 0 ? (
                  <p className="sr-empty">아직 매매한 종목이 없습니다.</p>
                ) : (
                  <ul className="sr-board">
                    <li className="sr-board-head">
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
                        <span className={`num sr-sum ${signOf(r.total)}`}>
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
                제일 아쉬운 부분이었다. 작아도 "거의 다 현금" 같은 덩어리는 읽힌다. */}
            <details className="sr-fold sr-pie" open>
              <summary>
                <span className="sr-h-ico t-pie" aria-hidden="true">
                  <Ico size={15}><circle cx="12" cy="12" r="8" /><path d="M12 4v8h8" /></Ico>
                </span>
                포트폴리오
                <em>현금 {cashWeight.toFixed(0)}%</em>
                <PortfolioDonut slices={slices} total={sim.totalAsset} mini />
                <i aria-hidden="true">⌄</i>
              </summary>
              <div className="sr-fold-body">
                {sim.positions.length === 0 ? (
                  <p className="sr-empty">
                    끝까지 현금으로 남았습니다.
                    <small>전액 현금 {won(sim.cash)}</small>
                  </p>
                ) : (
                  <PortfolioDonut slices={slices} total={sim.totalAsset} />
                )}
              </div>
            </details>
            </div>

            {/* ── 복기 (접이식) ────────────────────────────
                접힌 줄의 문장은 AI 가 쓴 것이 아니다. 위에서 구한 값으로 만든 사실
                문장이다 — 없는 리포트를 흉내 내지 않는다. 펴면 곡선에서 뽑은 수치가
                나오고, 그 아래가 서버 리포트가 들어올 자리다. */}
            <details className="sr-fold">
              <summary>
                <span className="sr-h-ico t-ai" aria-hidden="true">
                  <Ico size={15}><path d="m12 4 1.6 3.6L17 9.2l-3.4 1.6L12 14.4l-1.6-3.6L7 9.2l3.4-1.6z" /><path d="M18 15.5 18.8 17l1.7.8-1.7.8-.8 1.7-.8-1.7-1.7-.8 1.7-.8z" /></Ico>
                </span>
                복기
                <em className="sr-one">{oneLine}</em>
                <i aria-hidden="true">⌄</i>
              </summary>
              <div className="sr-fold-body">
                <dl className="sr-facts">
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
                <p className="sr-soon">
                  어느 판단이 좋았고 어디서 흔들렸는지는 AI 리포트가 짚어 줍니다.
                  <small>GET /seasons/{'{id}'}/result/me · ANT-SEASON-09</small>
                </p>
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
