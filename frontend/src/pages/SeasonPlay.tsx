/* G-04 시즌 진행 · /sim/:id/play
   담당 스토리 [ANT-FE-SEASON-PLAY]
   설계서 docs/화면설계서.md §3 · §4 G-04 · API 명세 §모의투자

   구조는 프로토타입 4·5번 화면(리플레이 투자하기)을 따른다. 왼쪽이 차트, 오른쪽이 주문과
   투자현황, 아래가 이슈·AI 힌트다.

   ── 프로토타입에서 바뀐 것 ────────────────────────────────
   1. 종목 이름 자리가 [종목 바꾸기] 다. 시즌 종목이 200개라(구간 첫날 시총 상위) 한 종목만
      크게 두고 나머지는 모달에서 고른다.
   2. x 축이 날짜(06/18 · 07/02)가 아니라 DAY n 이다. 시즌 가격에 실제 날짜가 없다.
   3. 제목이 "2008 금융위기" 가 아니라 시즌의 성격이다. 연도·사건명을 쓰지 않는다.
   4. 뉴스와 AI 힌트는 자리만 잡았다. season_news 가 0행이고 목업을 넣지 않는다 —
      가짜 뉴스는 나중에 통째로 버려야 하고, 문장이 시대 단서 규칙과도 엉킨다.

   ── 서버에서 오는 것 · 아직 안 오는 것 ────────────────────
   온다   GET /seasons/{id} · /tickers · /tickers/{tickerId}/prices
   안 온다 POST /orders · /advance · GET /seasons/{id}/me · /news (ANT-SEASON-03 · 04 · 07)

   그래서 주문·진행·투자현황은 useSeasonSim 이 브라우저에서 굴린다. 목업이 아니다 —
   체결가가 서버가 준 실제 게임일 종가이고 나머지는 곱셈으로 나온다. 서버가 붙으면
   그 훅 하나만 걷어낸다. */
import { useCallback, useEffect, useMemo, useState } from 'react'
import { Link, useParams } from 'react-router-dom'
import { ApiError } from '../api/errors'
import {
  getPrices, getTickers, maxBuyQty, orderAmount, rate, signOf,
  type Candle, type Side, type Ticker,
} from '../api/seasonPlay'
import { useApiQuery } from '../api/useApiQuery'
import CandleChart from '../components/sim/CandleChart'
import IndicatorPane, { type PaneKind } from '../components/sim/IndicatorPane'
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
/* 켤 수 있는 지표.

   이평선을 5·15·30 으로 둔다. 워밍업이 30봉이라 셋 다 DAY 1 부터 값이 나온다 —
   HTS 관례값(5·20·60)을 그대로 쓰면 MA60 이 플레이 중반에야 나와서, 켜도 선이 안
   그려지는 버튼이 된다. 워밍업이 늘면 그때 관례값으로 되돌린다.

   MACD 는 뺐다(2026-09-08). RSI 는 축이 0~100 이라 캔들에 얹지 못하고 아래 패널로
   간다(IndicatorPane). */
const OVERLAYS: IndicatorKind[] = ['MA5', 'MA15', 'MA30', 'BOLL']
const PANES: PaneKind[] = ['RSI']
const INDICATORS: IndicatorKind[] = [...OVERLAYS, ...PANES]

const REJECT_TEXT: Record<string, string> = {
  CASH: '예수금이 부족합니다',
  QTY: '수량을 확인해 주세요',
  NO_PRICE: '이 게임일의 가격이 아직 없습니다',
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
  const [filled, setFilled] = useState<string | null>(null)

  useEffect(() => {
    if (!seasonId) return
    getTickers(seasonId)
      .then((r) => {
        setTickers(r.items)
        setSelected((cur) => cur ?? r.items[0]?.tickerId ?? null)
      })
      .catch((e) => setTickerError(e instanceof ApiError ? e : null))
  }, [seasonId])

  /* 고른 종목의 봉을 받는다. 이미 있으면 다시 부르지 않는다.
     uptoDay 를 넘기지 않는다 — 서버가 내 진행일까지만 준다(커닝 차단). */
  useEffect(() => {
    if (!seasonId || selected === null || candles[selected]) return
    getPrices(seasonId, selected)
      .then((r) => setCandles((prev) => ({ ...prev, [selected]: r.items })))
      .catch((e) => setPriceError(e instanceof ApiError ? e : null))
  }, [seasonId, selected, candles])

  const candlesOf = useCallback((tickerId: number) => candles[tickerId], [candles])

  const sim = useSeasonSim({
    initialCash: season.data?.initialCash ?? 0,
    lengthDays: season.data?.lengthDays ?? 0,
    tickers,
    candlesOf,
  })

  const heldQty = useMemo(
    () => Object.fromEntries(sim.positions.map((p) => [p.tickerId, p.qty])),
    [sim.positions],
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

  function submit() {
    if (selected === null) return
    const at = sim.order(selected, side, qty)
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
          <h1>{s.title}</h1>
          <div className="sp-day">
            <b className="num">DAY {sim.day}</b>
            <span className="num">/ 총 {s.lengthDays}일</span>
            <span className="sp-bar" role="progressbar" aria-label="진행률"
                  aria-valuenow={percent} aria-valuemin={0} aria-valuemax={100}>
              <i style={{ width: `${percent}%` }} />
            </span>
            <span className="num">{percent}%</span>
          </div>
        </header>

        <div className="sp-body">
          {/* ── 왼쪽 · 차트 ─────────────────────────────── */}
          <section className="sp-chart-card" aria-label="시세">
            <div className="sp-ticker">
              <div>
                <b>{ticker?.displayName ?? '종목을 고르세요'}</b>
                {ticker?.sector && <span className="sp-sector">{ticker.sector}</span>}
              </div>
              <button type="button" className="sp-swap" onClick={() => setPicking(true)}>
                종목 바꾸기
                <span className="num">{s.tickerCount}</span>
              </button>
            </div>

            <div className="sp-price">
              <b className="num">{price === null ? '—' : won(price)}</b>
              {diff !== null && diffRate !== null && (
                <span className={`num ${signOf(diff)}`}>
                  {diff > 0 ? '▲' : diff < 0 ? '▼' : ''}
                  {Math.abs(diff).toLocaleString('ko-KR')} ({rate(diffRate)})
                </span>
              )}
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

            <div className="sp-inds" role="group" aria-label="보조지표">
              {INDICATORS.map((k) => (
                <button
                  key={k}
                  type="button"
                  aria-pressed={on.includes(k)}
                  className={on.includes(k) ? 'on' : undefined}
                  onClick={() =>
                    setOn((cur) => (cur.includes(k) ? cur.filter((x) => x !== k) : [...cur, k]))
                  }
                >
                  {k === 'BOLL' ? '볼린저' : k}
                </button>
              ))}
            </div>

            {priceError && <ErrorState error={priceError} onRetry={() => setPriceError(null)} inline />}
            {!priceError && bars === undefined && <p className="sp-state">봉을 불러오는 중…</p>}
            {bars !== undefined && (
              <CandleChart candles={bars} currentDay={sim.day} indicators={on} height={330} />
            )}

            {/* RSI 는 축이 0~100 이라 캔들 위에 얹지 못한다 — 아래에 칸을 따로 낸다.
                가로 좌표를 캔들과 공유하므로 같은 봉이 같은 x 에 선다. */}
            {bars !== undefined &&
              PANES.filter((k) => on.includes(k)).map((k) => (
                <IndicatorPane key={k} candles={bars} currentDay={sim.day} kind={k} />
              ))}
          </section>

          {/* ── 오른쪽 · 현황과 주문 ─────────────────────── */}
          <div className="sp-side">
            <section className="sp-card sp-status" aria-label="투자 현황">
              <dl>
                <div>
                  <dt>예수금</dt>
                  <dd className="num">{won(sim.cash)}</dd>
                </div>
                <div>
                  <dt>총자산</dt>
                  <dd className="num">{won(sim.totalAsset)}</dd>
                </div>
                <div>
                  <dt>누적 손익</dt>
                  <dd className={`num ${signOf(sim.pnl)}`}>
                    {sim.pnl > 0 ? '+' : ''}{won(sim.pnl)}
                    <small>{rate(sim.pnlRate)}</small>
                  </dd>
                </div>
              </dl>
            </section>

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

              <label className="sp-qty">
                <span>주문 수량</span>
                <input
                  type="number"
                  min={0}
                  max={maxQty}
                  value={qty || ''}
                  onChange={(e) => setQty(Math.max(0, Math.floor(Number(e.target.value) || 0)))}
                  aria-label="주문 수량"
                />
                <i>주</i>
              </label>

              <div className="sp-steps">
                {QTY_STEPS.map((n) => (
                  <button key={n} type="button" onClick={() => setQty((q) => q + n)}>
                    +{n.toLocaleString('ko-KR')}
                  </button>
                ))}
                <button type="button" onClick={() => setQty(maxQty)}>최대</button>
              </div>

              {/* 예상 금액은 프론트 계산이다(명세 §모의투자). 종가 단일가라 곱하기 하나다 */}
              <dl className="sp-amount">
                <div>
                  <dt>{side === 'BUY' ? '매수 가능' : '보유 수량'}</dt>
                  <dd className="num">{maxQty.toLocaleString('ko-KR')}주</dd>
                </div>
                <div>
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
                disabled={qty <= 0 || price === null}
                onClick={submit}
              >
                {side === 'BUY' ? '매수 주문' : '매도 주문'}
              </button>

              <button
                type="button"
                className="sp-advance"
                disabled={sim.isLastDay}
                onClick={() => { sim.advance(); setFilled(null) }}
              >
                {sim.isLastDay ? '마지막 게임일입니다' : '다음 영업일 진행'}
                {!sim.isLastDay && <em aria-hidden="true">▶</em>}
              </button>
              <p className="sp-note">주문은 그 게임일 종가로 한 번에 체결됩니다.</p>
            </section>
          </div>
        </div>

        {/* ── 아래 · 보유 현황 ─────────────────────────── */}
        <section className="sp-card sp-holdings" aria-label="보유 현황">
          <h2>보유 현황</h2>
          {sim.positions.length === 0 ? (
            <p className="sp-empty">아직 보유한 종목이 없습니다.</p>
          ) : (
            <ul>
              {sim.positions.map((p) => (
                <li key={p.tickerId}>
                  <button type="button" onClick={() => setSelected(p.tickerId)}>
                    <b>{p.displayName}</b>
                    <span className="num">{p.qty.toLocaleString('ko-KR')}주</span>
                    <span className="num">평균 {won(p.avgPrice)}</span>
                    <span className="num">{won(p.value)}</span>
                    <span className={`num ${signOf(p.pnl)}`}>
                      {p.pnl > 0 ? '+' : ''}{won(p.pnl)}
                    </span>
                  </button>
                </li>
              ))}
            </ul>
          )}
        </section>

        {/* ── 아래 · 이슈와 AI 힌트 (자리만) ──────────────
            season_news 가 0행이고 목업을 넣지 않는다. 가짜 뉴스는 나중에 통째로 버려야 하고,
            문장이 시대 단서 규칙(연도·사건 고유명사)과도 엉킨다. */}
        <div className="sp-later">
          <section className="sp-card">
            <h2>당시 주요 이슈</h2>
            <p className="sp-soon">
              게임일 시점의 뉴스·공시가 이 자리에 옵니다.
              <small>GET /seasons/{'{id}'}/news · ANT-SEASON-07</small>
            </p>
          </section>
          <section className="sp-card">
            <h2>AI 힌트</h2>
            <p className="sp-soon">
              그 시점 사건을 요약해 판단 근거를 짚어 줍니다.
              <small>배치 생성 · ANT-SEASON-07</small>
            </p>
          </section>
        </div>

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
