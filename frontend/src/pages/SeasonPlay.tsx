/* G-04 시즌 진행 · /sim/:id/play
   담당 스토리 [ANT-FE-SEASON-PLAY]
   설계서 docs/화면설계서.md §3 · §4 G-04 · API 명세 §모의투자

   배치는 프로토타입 4·5번 화면(리플레이 투자하기)을 따른다 — 머리줄, 지표 4칸,
   차트, 주문. 셸(상단바·사이드바)은 프로토타입이 아니라 우리 것을 쓴다.

   본문은 세 칸이다: 포트폴리오 · 차트 · 주문. 차트를 주문 바로 옆에 붙이고
   포트폴리오를 왼쪽에 세웠다 — 주문할 때 보는 것이 차트와 내 비중 둘이고,
   그 둘이 주문 패널과 붙어 있어야 눈이 멀리 가지 않는다.

   차트에 폭을 다 주지 않는다. 시즌이 30게임일이라 워밍업까지 합쳐도 봉이 60개라
   넓게 늘어놓으면 오히려 성기게 보인다.

   뉴스·AI 힌트는 아래 두 칸으로 내리고 접어 뒀다(2026-09-08). season_news 가
   0행이라 빈 상자 둘이 화면 가운데를 차지하고 있었다 — 접힌 줄에 무엇이 올
   자리인지만 적어 둔다. ANT-SEASON-07 이 들어오면 펴진 모습이 채워진다.

   ── 프로토타입에서 일부러 뺀 것 ──────────────────────────
   1. 날짜 배지(2008.09.19 금). 시기를 알려주면 결과를 아는 사람이 유리해진다.
      DAY n 만 센다 — 시즌 가격에 실제 날짜 자체가 없다(ERD).
   2. 제목의 연도·사건명(2008 금융위기). 같은 이유다. 시즌의 성격만 쓴다.
   3. 종목코드(005930)와 로고. GET /tickers 응답에 없다.
   4. 수수료·목표가·손절가. 체결 규칙이 게임일 종가 단일가라 수수료도 예약도
      없다(명세 §모의투자). 자리를 만들어 두면 있는 기능처럼 보인다.
   5. 주봉·월봉 탭. 서버가 일봉만 준다.
   6. 초보자 모드 · 심급적용 토글. 아직 정의가 없다.

   ── 서버에서 오는 것 · 아직 안 오는 것 ────────────────────
   온다   GET /seasons/{id} · /tickers · /tickers/{tickerId}/prices
          GET /seasons/{id}/me · /trades · POST /orders · /advance · /join
   안 온다 GET /news (ANT-SEASON-07)

   주문·진행·투자현황은 useSeasonSim 이 서버를 부른다. 브라우저에서 굴리던 것을
   2026-09-08 에 서버로 옮겼다 — 그때 order·advance 를 Promise 로 만들어 둔 덕에
   이 화면은 await 두 줄 말고는 고치지 않았다. */
import { useCallback, useEffect, useMemo, useRef, useState, type ReactNode } from 'react'
import { Link, useParams } from 'react-router-dom'
import { ApiError } from '../api/errors'
import {
  getPrices, getTickers, maxBuyQty, orderAmount, rate, signOf,
  type Candle, type Side, type Ticker,
} from '../api/seasonPlay'
import { useApiQuery } from '../api/useApiQuery'
import CandleChart from '../components/sim/CandleChart'
import IndicatorPane, { type PaneKind } from '../components/sim/IndicatorPane'
import PortfolioDonut, { type DonutSlice } from '../components/sim/PortfolioDonut'
import TickerPickerModal from '../components/sim/TickerPickerModal'
import ErrorState from '../components/state/ErrorState'
import { useSeasonSim } from '../sim/useSeasonSim'
import type { IndicatorKind } from '../sim/indicators'
import '../styles/screens/sim-play.css'

/** GET /seasons/{id} 중 이 화면이 쓰는 것만. 시기는 오지 않는다 */
type SeasonHead = {
  id: number
  title: string
  lengthDays: number
  initialCash: number
  tickerCount: number
}

const won = (n: number) => `${Math.round(n).toLocaleString('ko-KR')}원`
/** 시고저는 "원" 을 붙이지 않는다 — 네 칸이 나란히 서는 자리라 단위가 반복되면 시끄럽다 */
const cut = (n: number) => Math.round(n).toLocaleString('ko-KR')
const QTY_STEPS = [10, 100, 1000]
/** 도넛에 이름을 남길 종목 수. 나머지는 "기타" 로 접는다 */
const PIE_TOP = 4
/** 캔들 차트 높이. RSI 칸이 붙으면 이 값을, 아니면 여기에 PANE_H 를 더해 쓴다 */
const CHART_H = 200
/** IndicatorPane 이 차지하는 총 높이 — 여백·머리줄·그림 */
const PANE_H = 130

/* 켤 수 있는 지표.

   이평선을 5·15·30 으로 둔다. 워밍업이 30봉이라 셋 다 DAY 1 부터 값이 나온다 —
   HTS 관례값(5·20·60)을 그대로 쓰면 MA60 이 플레이 중반에야 나와서, 켜도 선이 안
   그려지는 버튼이 된다. 워밍업이 늘면 그때 관례값으로 되돌린다.

   MACD 는 뺐다(2026-09-08). RSI 는 축이 0~100 이라 캔들에 얹지 못하고 아래 패널로
   간다(IndicatorPane). */
const OVERLAYS: IndicatorKind[] = ['MA5', 'MA15', 'MA30', 'BOLL']
const PANES: PaneKind[] = ['RSI']
const INDICATORS: IndicatorKind[] = [...OVERLAYS, ...PANES]

/* 서버가 막은 이유를 사람 말로 바꾼다. 훅이 오류 코드를 이 몇 가지로 좁혀 준다 */
const REJECT_TEXT: Record<string, string> = {
  CASH: '예수금이 부족합니다',
  QTY: '수량을 확인해 주세요',
  NO_PRICE: '이 게임일의 가격이 아직 없습니다',
  NOT_JOINED: '참가하지 않은 시즌입니다',
  ENDED: '이미 끝난 회차입니다',
  LAST_DAY: '마지막 게임일입니다',
  DAY_MISMATCH: '다른 창에서 진행됐습니다. 화면을 새로 맞췄습니다',
  FAILED: '주문을 처리하지 못했습니다. 다시 시도해 주세요',
}

const Ico = ({ size = 18, children }: { size?: number; children: ReactNode }) => (
  <svg width={size} height={size} viewBox="0 0 24 24" fill="none" stroke="currentColor"
       strokeWidth="1.7" strokeLinecap="round" strokeLinejoin="round" aria-hidden="true">
    {children}
  </svg>
)

/** 지표 4칸 중 하나. 라벨 · 값 · 아랫줄 · 오른쪽 아이콘 배지 */
function Stat({ tone, label, value, sub, icon }: {
  tone: string
  label: string
  value: string
  sub?: ReactNode
  icon: ReactNode
}) {
  return (
    <div className={`sp-stat t-${tone}`}>
      <div className="sp-stat-txt">
        <span className="sp-stat-label">{label}</span>
        <b className="sp-stat-val num">{value}</b>
        {sub && <span className="sp-stat-sub num">{sub}</span>}
      </div>
      <span className="sp-stat-ico" aria-hidden="true">{icon}</span>
    </div>
  )
}

export default function SeasonPlay() {
  const { id } = useParams<{ id: string }>()
  const season = useApiQuery<SeasonHead>(`/seasons/${id}`)
  const seasonId = Number(id)

  const [tickers, setTickers] = useState<Ticker[]>([])
  const [tickerError, setTickerError] = useState<ApiError | null>(null)
  const [selected, setSelected] = useState<number | null>(null)
  const [picking, setPicking] = useState(false)

  /* 종목별 봉 저장소. 고른 종목만 받아 두고 다시 고르면 그대로 쓴다 —
     200종목을 한 번에 받으면 요청이 200번이다. 보유 종목의 평가금액도 여기서 나온다. */
  const [candles, setCandles] = useState<Record<number, Candle[]>>({})
  const [priceError, setPriceError] = useState<ApiError | null>(null)

  const [side, setSide] = useState<Side>('BUY')
  const [qty, setQty] = useState(0)
  const [on, setOn] = useState<IndicatorKind[]>(['MA5', 'MA15', 'MA30'])
  const [indOpen, setIndOpen] = useState(false)
  const [filled, setFilled] = useState<string | null>(null)
  const indRef = useRef<HTMLDivElement>(null)

  useEffect(() => {
    if (!seasonId) return
    getTickers(seasonId)
      .then((r) => {
        setTickers(r.items)
        setSelected((cur) => cur ?? r.items[0]?.tickerId ?? null)
      })
      .catch((e) => setTickerError(e instanceof ApiError ? e : null))
  }, [seasonId])

  /* 고른 종목의 봉을 받는다. 이미 있으면 다시 부르지 않는다 —
     진행일이 바뀌면 위에서 통째로 버리므로 그때 다시 받는다.
     uptoDay 를 넘기지 않는다. 서버가 내 진행일까지만 준다(커닝 차단). */
  useEffect(() => {
    if (!seasonId || selected === null || candles[selected]) return
    getPrices(seasonId, selected)
      .then((r) => setCandles((prev) => ({ ...prev, [selected]: r.items })))
      .catch((e) => setPriceError(e instanceof ApiError ? e : null))
  }, [seasonId, selected, candles])

  /* 지표 목록은 접어 둔다. 열려 있는 동안 바깥을 누르거나 Escape 를 치면 닫는다 —
     팝오버가 열린 채 남으면 차트를 가린다. */
  useEffect(() => {
    if (!indOpen) return
    const onDown = (e: MouseEvent) => {
      if (!indRef.current?.contains(e.target as Node)) setIndOpen(false)
    }
    const onKey = (e: KeyboardEvent) => { if (e.key === 'Escape') setIndOpen(false) }
    document.addEventListener('mousedown', onDown)
    document.addEventListener('keydown', onKey)
    return () => {
      document.removeEventListener('mousedown', onDown)
      document.removeEventListener('keydown', onKey)
    }
  }, [indOpen])

  const candlesOf = useCallback((tickerId: number) => candles[tickerId], [candles])

  const sim = useSeasonSim({
    seasonId,
    initialCash: season.data?.initialCash ?? 0,
    lengthDays: season.data?.lengthDays ?? 0,
    tickers,
    candlesOf,
  })

  /* 진행일이 바뀌면 받아 둔 봉을 버린다.

     서버는 내 진행일까지만 봉을 준다(커닝 차단). 그래서 DAY 8 에 받아 둔 배열에는
     DAY 9 부터의 봉이 없다 — 다음 영업일로 넘겨도 캐시가 그대로면 차트가 어제에
     멈추고, 오늘 종가가 없어 <b>매수 가능이 0주가 되어 주문이 막힌다</b>.
     하루에 요청 한 번이면 되는 값이라 통째로 버리고 다시 받는다.

     렌더 중에 갈아끼운다(리액트가 권하는 방식이다. 효과로 하면 한 프레임 동안
     옛 봉이 그려진다). */
  const [barsDay, setBarsDay] = useState(sim.day)
  if (barsDay !== sim.day) {
    setBarsDay(sim.day)
    setCandles({})
  }

  const heldQty = useMemo(
    () => Object.fromEntries(sim.positions.map((p) => [p.tickerId, p.qty])),
    [sim.positions],
  )

  /* 어제 종가로 다시 평가한 주식 금액. "전일 대비" 는 이것과의 차이다.

     오늘 산 주식도 어제 값으로 세므로 엄밀한 어제 잔고는 아니다. 이 줄이 답하는
     질문이 "어제 이후 얼마 벌었나" 가 아니라 "지금 들고 있는 것들이 오늘 얼마나
     움직였나" 여서, 매매를 섞지 않는 이 계산이 오히려 읽기 쉽다. */
  const prevStock = useMemo(
    () => sim.positions.reduce(
      (acc, p) => acc + p.qty * (sim.priceOf(p.tickerId, sim.day - 1) ?? p.avgPrice),
      0,
    ),
    [sim],
  )

  if (season.loading) {
    return (
      <main className="main">
        <div className="main-inner sim-play">
          <div className="placeholder tall">{'시즌을 불러오는 중…'}</div>
        </div>
      </main>
    )
  }

  if (season.error || !season.data) {
    return (
      <main className="main">
        <div className="main-inner sim-play">
          {season.error
            ? <ErrorState error={season.error} onRetry={season.reload} />
            : <div className="placeholder tall">{'시즌을 찾을 수 없습니다'}</div>}
        </div>
      </main>
    )
  }

  const s = season.data

  /* 참가하지 않았으면 진행할 것이 없다. 서버가 GET /me 에 SEASON_NOT_JOINED 를 주고
     훅이 joined=false 로 알려 준다 — 이때 차트만 띄워 두면 주문 버튼이 다 막힌
     화면이 되어 왜 안 되는지 알 수 없다. */
  if (sim.joined === false) {
    return (
      <main className="main">
        <div className="main-inner sim-play">
          <nav className="sp-crumb" aria-label="위치">
            <Link to="/sim">모의투자 홈</Link>
            <i aria-hidden="true">›</i>
            <Link to="/sim/practice">연습</Link>
            <i aria-hidden="true">›</i>
            <span>진행</span>
          </nav>
          <section className="sp-card">
            <p className="sp-none">
              <b>{s.title}</b>
              아직 이 연습에 참가하지 않았습니다.
              <small>참가하면 예수금 {won(s.initialCash)} 으로 DAY 1 부터 시작합니다.</small>
              <button type="button" className="sp-join" disabled={sim.pending}
                      onClick={() => { void sim.join() }}>
                {sim.pending ? '참가 중…' : '참가하기'}
              </button>
            </p>
          </section>
        </div>
      </main>
    )
  }

  const ticker = tickers.find((t) => t.tickerId === selected) ?? null
  const bars = selected === null ? undefined : candles[selected]
  const price = selected === null ? null : sim.priceOf(selected)
  const prev = selected === null ? null : sim.priceOf(selected, sim.day - 1)
  /** 오늘 봉. 진행일과 같은 game_day 를 찾는다 */
  const today = bars?.find((c) => c.gameDay === sim.day) ?? null
  const diff = price !== null && prev !== null ? price - prev : null
  const diffRate = diff !== null && prev ? (diff / prev) * 100 : null

  const held = sim.positions.find((p) => p.tickerId === selected)
  const amount = price === null ? 0 : orderAmount(price, qty)
  const maxQty =
    price === null ? 0 : side === 'BUY' ? maxBuyQty(sim.cash, price) : held?.qty ?? 0
  const percent = s.lengthDays > 0 ? Math.round((sim.day / s.lengthDays) * 100) : 0

  /* RSI 를 켜면 아래에 칸이 하나 더 붙는다. 그때만 카드가 길어지면 오른쪽 주문
     패널과의 높이 균형이 켤 때마다 달라져 화면이 뛴다. 차트가 그만큼 양보해서
     카드 높이를 같게 둔다 — 껐을 때는 봉이 커지므로 손해도 아니다. */
  const paneOn = PANES.some((k) => on.includes(k))
  const chartH = paneOn ? CHART_H : CHART_H + PANE_H

  const stockDiff = sim.stockValue - prevStock
  const stockRate = prevStock > 0 ? (stockDiff / prevStock) * 100 : null
  const prevTotal = sim.cash + prevStock
  const assetRate = prevTotal > 0 ? (stockDiff / prevTotal) * 100 : null

  /** 전일 대비 한 줄. 보유가 없으면 비교할 게 없어 자리만 지킨다 */
  const dayOver = (r: number | null) =>
    r === null ? <span className="flat">전일 대비 —</span>
      : <span className={signOf(stockDiff)}>전일 대비 {rate(r)}</span>

  /* 도넛 조각. 큰 것부터 넷만 이름을 남기고 나머지는 기타로 접는다.
     현금은 늘 마지막이다 — 종목들 사이에 끼면 종목처럼 읽힌다. */
  const sorted = [...sim.positions].sort((a, b) => b.value - a.value)
  const top = sorted.slice(0, PIE_TOP)
  const rest = sorted.slice(PIE_TOP)
  const slices: DonutSlice[] = [
    ...top.map((p) => ({
      key: `t${p.tickerId}`,
      label: p.displayName,
      value: p.value,
      tickerId: p.tickerId,
      detail: `${p.qty.toLocaleString('ko-KR')}주 · 평균 ${won(p.avgPrice)}`,
      pnl: p.pnl,
    })),
    ...(rest.length
      ? [{
          key: 'ETC',
          label: `기타 ${rest.length}종목`,
          value: rest.reduce((a, p) => a + p.value, 0),
        }]
      : []),
    { key: 'CASH', label: '현금', value: sim.cash },
  ]

  /* await 로 부른다. 지금은 훅 안에서 즉시 끝나지만 서버가 붙으면 진짜로 기다리게
     된다 — 그때 이 자리를 안 고치려고 지금부터 비동기로 쓴다. */
  async function submit() {
    if (selected === null) return
    const at = await sim.order(selected, side, qty)
    if (at !== null) {
      setFilled(`${side === 'BUY' ? '매수' : '매도'} ${qty.toLocaleString('ko-KR')}주 · ${won(at)} 체결`)
      setQty(0)
    }
  }

  return (
    <main className="main">
      <div className="main-inner sim-play">
        <nav className="sp-crumb" aria-label="위치">
          <Link to="/sim">모의투자 홈</Link>
          <i aria-hidden="true">›</i>
          <Link to="/sim/practice">연습</Link>
          <i aria-hidden="true">›</i>
          <span>진행</span>
        </nav>

        {/* 게임일 헤더 — 날짜를 쓰지 않는다. 시즌 가격에 실제 날짜가 없다 */}
        <header className="sp-head">
          <div className="sp-title">
            <h1>{s.title}</h1>
            <p>연습 시즌 · 종목 {s.tickerCount}개</p>
          </div>
          <div className="sp-day">
            <span className="sp-daychip num">
              <Ico size={15}><path d="m10 8.5 6 3.5-6 3.5z" /><circle cx="12" cy="12" r="9" /></Ico>
              DAY {sim.day}
            </span>
            <span className="sp-bar" role="progressbar" aria-label="진행률"
                  aria-valuenow={percent} aria-valuemin={0} aria-valuemax={100}>
              <i style={{ width: `${percent}%` }} />
            </span>
            <span className="sp-dayof num">{sim.day} / {s.lengthDays}일</span>
          </div>
        </header>

        {/* ── 지표 4칸 ─────────────────────────────────── */}
        <section className="sp-stats" aria-label="투자 현황">
          <Stat
            tone="cash"
            label="남은 모의투자금 (현금)"
            value={won(sim.cash)}
            icon={<Ico><rect x="3" y="6" width="18" height="13" rx="2.5" /><path d="M3 10h18M16.5 14.5h1.5" /></Ico>}
          />
          <Stat
            tone="asset"
            label="총 자산"
            value={won(sim.totalAsset)}
            sub={dayOver(assetRate)}
            icon={<Ico><path d="M4 16.5 9 11l3.5 3.5L20 7" /><path d="M15.5 7H20v4.5" /></Ico>}
          />
          <Stat
            tone="stock"
            label="주식 평가금액"
            value={won(sim.stockValue)}
            sub={dayOver(stockRate)}
            icon={<Ico><path d="M5 19V11M12 19V5M19 19v-5" /></Ico>}
          />
          <Stat
            tone={sim.pnl > 0 ? 'up' : sim.pnl < 0 ? 'down' : 'flat'}
            label="누적 손익"
            value={`${sim.pnl > 0 ? '+' : ''}${won(sim.pnl)}`}
            sub={<span className={signOf(sim.pnl)}>{rate(sim.pnlRate)}</span>}
            icon={<Ico><path d="M12 19V5M12 5l-5 5M12 5l5 5" /></Ico>}
          />
        </section>

        <div className="sp-body">
          {/* ── 왼쪽 · 포트폴리오 ────────────────────────
              접지 않는다. 도넛은 주문을 정하는 동안 늘 옆에 있어야 하는 그림이다 —
              "얼마나 들어가 있나" 를 보고 다음 주문을 정한다. 접어 두면 그때마다
              펴야 하고, 펴 두면 접이식일 이유가 없다. */}
          <section className="sp-card sp-pie" aria-label="포트폴리오">
            <h2>
              <span className="sp-h-ico t-pie" aria-hidden="true">
                <Ico size={15}><circle cx="12" cy="12" r="8" /><path d="M12 4v8h8" /></Ico>
              </span>
              포트폴리오
            </h2>
            {/* 보유가 없어도 그린다. 전액 현금이면 회색 원 하나인데, 그것도 "아직
                아무것도 안 넣었다" 를 말하는 그림이다 — 글자 두 줄만 남기면 카드가
                비어 보이고, 사고 나서야 원이 생기면 화면이 튄다. */}
            {/* 좁은 칸이라 셋까지만 펴 둔다. 더 있으면 "더 보기" 로 넘긴다 —
                목록이 길어지면 이 카드가 줄 높이를 정해 버려 차트 아래가 빈다. */}
            <PortfolioDonut
              slices={slices}
              total={sim.totalAsset}
              onPick={setSelected}
              maxRows={3}
            />
            {sim.positions.length === 0 && (
              <p className="sp-empty">
                아직 보유한 종목이 없습니다.
                <small>종목을 사면 여기에 비중이 쌓입니다</small>
              </p>
            )}
          </section>

          {/* ── 가운데 · 차트. 주문 패널 바로 옆이다 ────── */}
          <section className="sp-chart-card" aria-label="시세">
            {/* 이름·가격·등락을 한 줄에 둔다. 프로토타입과 같은 자리다 —
                가격을 아랫줄로 내리면 종목을 바꿨을 때 눈이 두 번 움직인다. */}
            <div className="sp-ticker">
              <div className="sp-tname">
                {/* 종목 이름이 곧 고르기 버튼이다. 옆에 버튼을 따로 두면 차트 머리에
                    상자가 하나 더 서고, 주문 카드로 내리면 무엇을 보는 중인지와
                    무엇을 사는지가 갈라진다. HTS 들이 이름을 눌러 바꾼다. */}
                <button type="button" className="sp-tpick" onClick={() => setPicking(true)}>
                  <b>{ticker?.displayName ?? '종목을 고르세요'}</b>
                  <i aria-hidden="true">⌄</i>
                </button>
                {ticker?.sector && <span className="sp-sector">{ticker.sector}</span>}
                <strong className="num">{price === null ? '—' : won(price)}</strong>
                {diff !== null && diffRate !== null && (
                  <span className={`sp-diff num ${signOf(diff)}`}>
                    {diff > 0 ? '▲' : diff < 0 ? '▼' : ''}
                    {Math.abs(diff).toLocaleString('ko-KR')} ({rate(diffRate)})
                  </span>
                )}
              </div>
              <div className="sp-tools">
                {/* 지표를 접어 둔다. 버튼 다섯 개를 늘 펴 두면 차트 위 두 줄을 먹는데,
                    지표를 바꾸는 일은 종목을 바꾸는 것보다도 드물다. */}
                <div className="sp-ind-wrap" ref={indRef}>
                  <button
                    type="button"
                    className={indOpen ? 'sp-indbtn on' : 'sp-indbtn'}
                    aria-expanded={indOpen}
                    onClick={() => setIndOpen((v) => !v)}
                  >
                    지표
                    <span className="num">{on.length}</span>
                    <i aria-hidden="true">⌄</i>
                  </button>
                  {indOpen && (
                    <div className="sp-indpop" role="group" aria-label="보조지표">
                      {INDICATORS.map((k) => (
                        <button
                          key={k}
                          type="button"
                          aria-pressed={on.includes(k)}
                          className={on.includes(k) ? 'on' : undefined}
                          onClick={() =>
                            setOn((cur) =>
                              cur.includes(k) ? cur.filter((x) => x !== k) : [...cur, k])
                          }
                        >
                          <em aria-hidden="true" />
                          {k === 'BOLL' ? '볼린저밴드' : k}
                        </button>
                      ))}
                    </div>
                  )}
                </div>
              </div>
            </div>

            {/* 오늘 하루의 네 값. 종가만 보면 같은 53,900 원이라도 하루가 완전히 다르다 —
                55,400 에서 밀려 내려온 것과 53,200 에서 올라온 것은 다음 판단이 반대다.
                캔들에 마우스를 올려야만 보이던 걸 오늘 것만은 늘 보이게 둔다. */}
            {today && (
              <dl className="sp-ohlc num" aria-label="오늘 시세">
                <div><dt>시가</dt><dd>{today.open == null ? '—' : cut(today.open)}</dd></div>
                <div><dt>고가</dt><dd>{today.high == null ? '—' : cut(today.high)}</dd></div>
                <div><dt>저가</dt><dd>{today.low == null ? '—' : cut(today.low)}</dd></div>
                <div>
                  <dt>거래량</dt>
                  <dd>{today.volume == null ? '—' : today.volume.toLocaleString('ko-KR')}</dd>
                </div>
              </dl>
            )}

            {priceError && <ErrorState error={priceError} onRetry={() => setPriceError(null)} inline />}
            {!priceError && bars === undefined && <p className="sp-state">봉을 불러오는 중…</p>}
            {bars !== undefined && (
              <CandleChart candles={bars} currentDay={sim.day} indicators={on} height={chartH} />
            )}

            {/* RSI 는 축이 0~100 이라 캔들 위에 얹지 못한다 — 아래에 칸을 따로 낸다.
                가로 좌표를 캔들과 공유하므로 같은 봉이 같은 x 에 선다. */}
            {bars !== undefined &&
              PANES.filter((k) => on.includes(k)).map((k) => (
                <IndicatorPane key={k} candles={bars} currentDay={sim.day} kind={k} />
              ))}
          </section>

          {/* ── 오른쪽 · 주문 ───────────────────────────── */}
          <div className="sp-side">
            <section className="sp-card sp-order" aria-label="주문">
              <div className="sp-sides" role="tablist">
                <button type="button" role="tab" aria-selected={side === 'BUY'}
                        className={side === 'BUY' ? 'buy on' : 'buy'}
                        onClick={() => { setSide('BUY'); setQty(0); sim.clearReject() }}>
                  매수
                </button>
                <button type="button" role="tab" aria-selected={side === 'SELL'}
                        className={side === 'SELL' ? 'sell on' : 'sell'}
                        onClick={() => { setSide('SELL'); setQty(0); sim.clearReject() }}>
                  매도
                </button>
              </div>

              {/* 체결 방식을 한 줄로 밝혀 둔다. 프로토타입에는 시장가·지정가 선택이
                  있었지만 이 게임의 체결은 게임일 종가 하나뿐이라 고를 게 없다 —
                  고를 수 없는 걸 버튼으로 두면 눌러 보게 된다. */}
              <div className="sp-fixed">
                <span>주문 유형</span>
                <b>종가 단일가</b>
              </div>

              <div className="sp-qty-row">
                <span>주문 수량</span>
                <div className="sp-stepper">
                  <button type="button" aria-label="1주 줄이기"
                          disabled={qty <= 0}
                          onClick={() => setQty((q) => Math.max(0, q - 1))}>−</button>
                  <input
                    type="number"
                    min={0}
                    max={maxQty}
                    value={qty || ''}
                    onChange={(e) => setQty(Math.max(0, Math.floor(Number(e.target.value) || 0)))}
                    aria-label="주문 수량"
                  />
                  <i>주</i>
                  <button type="button" aria-label="1주 늘리기"
                          onClick={() => setQty((q) => q + 1)}>+</button>
                </div>
              </div>

              <div className="sp-steps">
                {QTY_STEPS.map((n) => (
                  <button key={n} type="button" onClick={() => setQty((q) => q + n)}>
                    +{n.toLocaleString('ko-KR')}
                  </button>
                ))}
                <button type="button" disabled={maxQty <= 0} onClick={() => setQty(maxQty)}>
                  최대
                </button>
              </div>

              {/* 예상 금액은 프론트 계산이다(명세 §모의투자). 종가 단일가라 곱하기 하나다.
                  수수료 줄이 없는 게 맞다 — 이 게임에 수수료가 없다. */}
              <dl className="sp-amount">
                <div>
                  <dt>{side === 'BUY' ? '매수 가능' : '보유 수량'}</dt>
                  <dd className="num">
                    {maxQty.toLocaleString('ko-KR')}주
                    {side === 'SELL' && held && <small>평균 {won(held.avgPrice)}</small>}
                  </dd>
                </div>
                <div className="sp-total">
                  <dt>예상 주문 금액</dt>
                  <dd className="num">{won(amount)}</dd>
                </div>
              </dl>

              {sim.reject && (
                <p className="sp-reject" role="alert">{REJECT_TEXT[sim.reject]}</p>
              )}
              {filled && !sim.reject && (
                <p className="sp-filled" aria-live="polite">{filled}</p>
              )}

              <button
                type="button"
                className={`sp-submit ${side === 'BUY' ? 'buy' : 'sell'}`}
                disabled={qty <= 0 || price === null || sim.pending}
                onClick={() => { void submit() }}
              >
                {/* 꺼진 버튼은 왜 꺼졌는지 말해야 한다. "매수 주문" 이 회색으로 있으면
                    누를 수 없는 이유가 수량인지 가격인지 서버인지 알 수 없다. */}
                {sim.pending
                  ? '주문 중…'
                  : price === null
                    ? '가격을 불러오는 중…'
                    : qty <= 0
                      ? '수량을 입력하세요'
                      : side === 'BUY' ? '매수 주문' : '매도 주문'}
              </button>

              {/* 마지막 날에는 버튼이 결과로 바뀐다. 전에는 "마지막 게임일입니다" 라고
                  꺼진 버튼만 남아 흐름이 거기서 끊겼다 — 다 돌린 사람에게 다음 걸음이
                  없으면 얼마 벌었는지도 못 본다. */}
              {sim.isLastDay ? (
                <Link className="sp-advance is-done" to={`/sim/${seasonId}/result`}>
                  결과 보기
                  <em aria-hidden="true">›</em>
                </Link>
              ) : (
                <button
                  type="button"
                  className="sp-advance"
                  disabled={sim.pending}
                  onClick={() => { void sim.advance(); setFilled(null) }}
                >
                  <em aria-hidden="true">▶</em>
                  {sim.pending ? '진행 중…' : '다음 영업일 진행'}
                </button>
              )}
              <p className="sp-note">
                {sim.isLastDay
                  ? '마지막 게임일입니다. 진행은 여기까지입니다.'
                  : '주문은 그 게임일 종가로 한 번에 체결됩니다.'}
              </p>
            </section>
          </div>
        </div>

        {/* ── 아래 두 칸 (접이식) ──────────────────────────
            뉴스와 AI 힌트는 자리만 잡는다. season_news 가 0행이고 목업을 넣지 않는다 —
            가짜 뉴스는 나중에 통째로 버려야 하고, 문장이 시대 단서 규칙과도 엉킨다.

            접어 둔다. 빈 상자 둘이 펴져 있으면 화면을 차지하기만 하고, 접힌 줄에
            무엇이 올 자리인지 적어 두면 그것만으로 뜻이 전해진다. */}
        <div className="sp-later">
          <details className="sp-fold">
            <summary>
              <span className="sp-h-ico t-ai" aria-hidden="true">
                <Ico size={15}><path d="m12 4 1.6 3.6L17 9.2l-3.4 1.6L12 14.4l-1.6-3.6L7 9.2l3.4-1.6z" /><path d="M18 15.5 18.8 17l1.7.8-1.7.8-.8 1.7-.8-1.7-1.7-.8 1.7-.8z" /></Ico>
              </span>
              AI 한줄 요약
              <em>아직 준비 중입니다</em>
              <i aria-hidden="true">⌄</i>
            </summary>
            <div className="sp-fold-body">
              <p className="sp-soon">
                그 시점 사건을 요약해 판단 근거를 짚어 줍니다.
                <small>배치 생성 · ANT-SEASON-07</small>
              </p>
            </div>
          </details>

          <details className="sp-fold">
            <summary>
              <span className="sp-h-ico t-news" aria-hidden="true">
                <Ico size={15}><path d="M5 4h11v16H5z" /><path d="M16 8h3v9.5a2.5 2.5 0 0 1-5 0" /><path d="M8 8h5M8 11.5h5M8 15h3" /></Ico>
              </span>
              당시 주요 이슈
              <em>아직 준비 중입니다</em>
              <i aria-hidden="true">⌄</i>
            </summary>
            <div className="sp-fold-body">
              <p className="sp-soon">
                게임일 시점의 뉴스·공시가 이 자리에 옵니다.
                <small>GET /seasons/{'{id}'}/news · ANT-SEASON-07</small>
              </p>
            </div>
          </details>
        </div>

        {sim.loadError && <ErrorState error={sim.loadError} onRetry={() => { void sim.reload() }} inline />}
        {tickerError && <ErrorState error={tickerError} onRetry={() => setTickerError(null)} inline />}

        {picking && (
          <TickerPickerModal
            tickers={tickers}
            selectedId={selected}
            heldQty={heldQty}
            priceOf={sim.priceOf}
            onPick={setSelected}
            onClose={() => setPicking(false)}
          />
        )}
      </div>
    </main>
  )
}
