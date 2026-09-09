/* 시즌 진행. G-04 진행 화면과 G-08 결과 화면이 쓴다.

   ── 브라우저에서 굴리던 것을 서버로 옮겼다 (2026-09-08) ────
   전에는 이 훅이 주문·진행을 브라우저 안에서 굴리고 localStorage 에 남겼다.
   POST /orders · /advance · GET /me 가 없었기 때문이다. 그 셋이 들어왔으므로
   (ANT-SEASON-03 · 04) 이제 서버가 진짜 값을 들고 있다.

   그때 order 와 advance 를 Promise 로 만들어 둔 덕에 화면은 고치지 않았다 —
   바뀐 것은 이 파일 안쪽뿐이다.

   저장도 걷어냈다. 키가 sim:{시즌id}:v1 이었고 이제 아무도 v1 을 읽지 않으므로
   남아 있는 값은 죽은 값이다. 지우는 코드를 넣지 않는다 — 남의 브라우저에 있는
   것까지 쫓아다닐 수 없고, 서버가 진짜라서 읽지 않으면 그걸로 끝이다.

   ── 값의 출처 ─────────────────────────────────────────────
   예수금·총자산·평가금액·손익·포지션   GET /seasons/{id}/me
   체결 내역                          GET /seasons/{id}/trades
   성적표(끝낸 뒤)                     POST /seasons/{id}/finish
   자산 곡선                          체결 내역 + 봉으로 다시 만든다(아래)

   ── 자산 곡선은 왜 다시 만드는가 ──────────────────────────
   서버가 게임일마다의 총자산을 주지 않는다. 대신 체결 내역과 봉이 있으면 어느
   게임일의 잔고든 다시 계산할 수 있다 — 그날까지의 체결로 현금과 보유 수량이
   정해지고, 보유 수량에 그날 종가를 곱하면 평가금액이다. 서버 값에서 파생되므로
   기기를 옮겨도 같은 곡선이 나온다.

   봉이 없는 종목은 평균 매입가로 센다. 0 으로 두면 총자산이 갑자기 꺼진 것처럼
   보이기 때문이다. 화면이 매매한 종목의 봉을 받아 두면 그 근사는 사라진다. */
import { useCallback, useEffect, useMemo, useState } from 'react'
import { ApiError } from '../api/errors'
import {
  advance as advanceApi,
  finish as finishApi,
  getMyStatus,
  getTrades,
  join as joinApi,
  order as orderApi,
  type Candle,
  type MyStatus,
  type SeasonFinishResult,
  type Side,
  type Ticker,
  type Trade,
} from '../api/seasonPlay'

export type SimPosition = {
  tickerId: number
  displayName: string
  qty: number
  avgPrice: number
  /** 평가금액 */
  value: number
  /** 평가손익 */
  pnl: number
}

export type SimTrade = {
  id: number
  tickerId: number
  displayName: string
  side: Side
  qty: number
  /** 체결가 = 그 게임일 종가 */
  price: number
  amount: number
  gameDay: number
  /** 매도에만 값이 있다 */
  realizedPnl: number | null
}

/**
 * 주문·진행이 막히는 이유. 서버 오류 코드를 화면이 아는 몇 가지로 좁힌 것이다.
 *
 * <p>코드를 그대로 화면까지 올리지 않는다 — 화면은 "무엇을 고쳐야 하나" 만 알면 되고,
 * 서버가 코드를 늘려도 문구 표를 매번 따라 고치지 않아도 된다.
 */
export type SimReject =
  | 'CASH'          // 예수금 부족
  | 'QTY'           // 수량이 잘못됐거나 보유 수량이 부족
  | 'NO_PRICE'      // 그 게임일 가격이 없다
  | 'NOT_JOINED'    // 참가하지 않았다
  | 'ENDED'         // 끝난 회차다
  | 'LAST_DAY'      // 마지막 게임일이라 더 못 넘긴다
  | 'DAY_MISMATCH'  // 보고 있는 게임일이 서버와 다르다
  | 'REVIEW'        // 종료는 됐어야 하는데 AI 복기를 못 만들어 아무것도 저장되지 않았다
  | 'FAILED'        // 그 밖

/* 서버 코드 → 화면이 아는 이유. 표에 없는 코드는 FAILED 로 모은다 */
const REJECT_OF: Record<string, SimReject> = {
  INSUFFICIENT_BALANCE: 'CASH',
  SEASON_INSUFFICIENT_QTY: 'QTY',
  VALIDATION_FAILED: 'QTY',
  SEASON_PRICE_NOT_FOUND: 'NO_PRICE',
  SEASON_NOT_JOINED: 'NOT_JOINED',
  SEASON_ATTEMPT_ENDED: 'ENDED',
  SEASON_LAST_DAY: 'LAST_DAY',
  SEASON_NOT_LAST_DAY: 'LAST_DAY',
  SEASON_REVIEW_FAILED: 'REVIEW',
  DAY_MISMATCH: 'DAY_MISMATCH',
}

const rejectOf = (e: unknown): SimReject =>
  e instanceof ApiError ? REJECT_OF[e.code] ?? 'FAILED' : 'FAILED'

export function useSeasonSim({
  seasonId,
  initialCash,
  lengthDays,
  tickers,
  /** 종목 id → 워밍업 포함 전체 봉. 아직 못 받은 종목은 없어도 된다 */
  candlesOf,
}: {
  seasonId: number
  initialCash: number
  lengthDays: number
  tickers: Ticker[]
  candlesOf: (tickerId: number) => Candle[] | undefined
}) {
  const [status, setStatus] = useState<MyStatus | null>(null)
  const [trades, setTrades] = useState<Trade[]>([])
  /** null 은 아직 모르는 상태다. false 면 참가 버튼을 보여야 한다 */
  const [joined, setJoined] = useState<boolean | null>(null)
  const [reject, setReject] = useState<SimReject | null>(null)
  const [loadError, setLoadError] = useState<ApiError | null>(null)
  /** 서버를 기다리는 중. 버튼을 잠가 두어 두 번 눌리지 않게 한다 */
  const [pending, setPending] = useState(false)

  /* 현황과 체결 내역을 함께 받는다. 주문 하나에 예수금·포지션·내역이 모두 바뀌므로
     따로 받으면 화면의 두 곳이 다른 시점을 보여 줄 수 있다. */
  const reload = useCallback(async () => {
    if (!seasonId) return
    try {
      const [me, list] = await Promise.all([getMyStatus(seasonId), getTrades(seasonId)])
      setStatus(me)
      setTrades(list.items)
      setJoined(true)
      setLoadError(null)
    } catch (e) {
      if (e instanceof ApiError && e.code === 'SEASON_NOT_JOINED') {
        setJoined(false)
        setStatus(null)
        setTrades([])
        setLoadError(null)
        return
      }
      setLoadError(e instanceof ApiError ? e : null)
    }
  }, [seasonId])

  // oxlint-disable-next-line react/set-state-in-effect -- 서버에서 받아 오는 것이라 상태 갱신이 목적이다. setState 는 모두 await 뒤에 있다(useApiQuery 와 같은 규칙).
  useEffect(() => { void reload() }, [reload])

  const day = status?.currentDay ?? 0
  const cash = status?.cash ?? initialCash
  const stockValue = status?.stockValue ?? 0
  const totalAsset = status?.totalAsset ?? initialCash
  const pnl = status?.pnl ?? 0
  const pnlRate = status?.pnlRate ?? 0
  const isLastDay = day > 0 && day >= lengthDays

  const positions = useMemo<SimPosition[]>(
    () =>
      (status?.positions ?? [])
        .map((p) => ({
          tickerId: p.tickerId,
          /* 서버가 이름을 주지만 못 왔을 때를 대비해 종목 목록으로 메운다 */
          displayName:
            p.displayName ||
            tickers.find((t) => t.tickerId === p.tickerId)?.displayName ||
            `#${p.tickerId}`,
          qty: p.qty,
          avgPrice: p.avgPrice,
          value: p.value,
          pnl: p.pnl,
        }))
        .sort((a, b) => b.value - a.value),
    [status, tickers],
  )

  /* 화면이 쓰는 모양으로 맞춘다. 서버는 tradeId·tickerName 이고 화면은 id·displayName 이다 */
  const simTrades = useMemo<SimTrade[]>(
    () =>
      trades.map((t) => ({
        id: t.tradeId,
        tickerId: t.tickerId,
        displayName:
          t.tickerName ||
          tickers.find((x) => x.tickerId === t.tickerId)?.displayName ||
          `#${t.tickerId}`,
        side: t.side,
        qty: t.qty,
        price: t.price,
        amount: t.amount,
        gameDay: t.gameDay,
        realizedPnl: t.realizedPnl,
      })),
    [trades, tickers],
  )

  /** 그 종목의 그 게임일 종가. 봉을 아직 못 받았으면 null */
  const priceOf = useCallback(
    (tickerId: number, at = day) =>
      candlesOf(tickerId)?.find((c) => c.gameDay === at)?.close ?? null,
    [candlesOf, day],
  )

  /* 자산 곡선. 게임일 1 부터 지금까지 하루씩 잔고를 다시 만든다.
     체결을 게임일 순으로 훑으면서 현금과 보유 수량을 굴리고, 하루가 끝날 때
     그날 종가로 평가한다. 마지막 날의 총자산은 서버가 준 값으로 바꿔 둔다 —
     봉이 없는 종목이 있으면 근사가 섞이는데, 지금 값만은 정확해야 한다. */
  const equity = useMemo(() => {
    if (day <= 0) return []

    const byDay = new Map<number, SimTrade[]>()
    simTrades.forEach((t) => {
      const rows = byDay.get(t.gameDay) ?? []
      rows.push(t)
      byDay.set(t.gameDay, rows)
    })

    const held = new Map<number, { qty: number; avgPrice: number }>()
    let run = initialCash
    const rows: { gameDay: number; totalAsset: number }[] = []

    for (let d = 1; d <= day; d += 1) {
      ;(byDay.get(d) ?? []).forEach((t) => {
        const cur = held.get(t.tickerId) ?? { qty: 0, avgPrice: 0 }
        if (t.side === 'BUY') {
          const q = cur.qty + t.qty
          held.set(t.tickerId, {
            qty: q,
            avgPrice: (cur.avgPrice * cur.qty + t.price * t.qty) / q,
          })
          run -= t.amount
        } else {
          const left = cur.qty - t.qty
          if (left <= 0) held.delete(t.tickerId)
          else held.set(t.tickerId, { qty: left, avgPrice: cur.avgPrice })
          run += t.amount
        }
      })

      let mark = 0
      held.forEach((h, tickerId) => {
        /* 봉이 없으면 평균 매입가로 센다. 0 으로 두면 그날 총자산이 꺼진 것처럼 보인다 */
        mark += h.qty * (priceOf(tickerId, d) ?? h.avgPrice)
      })
      rows.push({ gameDay: d, totalAsset: run + mark })
    }

    if (status && rows.length > 0) rows[rows.length - 1].totalAsset = status.totalAsset
    return rows
  }, [day, simTrades, initialCash, priceOf, status])

  /* 서버를 부르고 성공하면 현황을 다시 받는다. 실패는 이유로 좁혀 화면에 넘긴다.
     한 번에 하나만 보낸다 — 두 번 눌러 두 건이 체결되면 되돌릴 수가 없다. */
  const run = useCallback(
    async <T,>(work: () => Promise<T>): Promise<T | null> => {
      if (pending) return null
      setPending(true)
      setReject(null)
      try {
        const out = await work()
        await reload()
        return out
      } catch (e) {
        setReject(rejectOf(e))
        /* 게임일이 어긋났으면 서버 쪽이 맞다. 다시 받아 화면을 맞춘다 */
        if (e instanceof ApiError && e.code === 'DAY_MISMATCH') await reload()
        return null
      } finally {
        setPending(false)
      }
    },
    [pending, reload],
  )

  /** 주문. 성공하면 체결가를 돌려준다 */
  const order = useCallback(
    async (tickerId: number, side: Side, qty: number): Promise<number | null> => {
      if (qty <= 0) {
        setReject('QTY')
        return null
      }
      const r = await run(() => orderApi(seasonId, tickerId, side, qty))
      return r?.price ?? null
    },
    [run, seasonId],
  )

  /**
   * 다음 영업일. 보고 있는 게임일을 함께 보낸다 — 서버의 진행일과 다르면 서버가
   * 409 DAY_MISMATCH 로 막는다. 두 번 눌러도, 탭이 둘이어도 하루만 넘어간다.
   */
  const advance = useCallback(async () => {
    if (day <= 0) return
    await run(() => advanceApi(seasonId, day))
  }, [run, seasonId, day])

  /** 참가. 이미 끝낸 회차가 있으면 서버가 새 회차를 연다 */
  const join = useCallback(async () => {
    await run(() => joinApi(seasonId))
  }, [run, seasonId])

  /**
   * 회차를 끝내고 성적표를 받는다. <b>되돌릴 수 없다</b> — 부르면 주문도 진행도 막힌다.
   * 부르는 쪽이 반드시 확인을 받고 불러야 한다.
   *
   * <p>이미 끝난 회차면 서버가 저장해 둔 같은 결과를 다시 준다. 그래서 다시 눌러도
   * 값이 바뀌지 않는다.
   */
  const finish = useCallback(
    async (): Promise<SeasonFinishResult | null> => run(() => finishApi(seasonId)),
    [run, seasonId],
  )

  return {
    day,
    isLastDay,
    cash,
    stockValue,
    totalAsset,
    pnl,
    pnlRate,
    positions,
    trades: simTrades,
    equity,
    /** 참가 여부. null 은 아직 모르는 상태다 */
    joined,
    /** 서버를 기다리는 중 */
    pending,
    /** 현황을 못 받았다. 참가하지 않은 것과는 다르다 */
    loadError,
    reject,
    clearReject: () => setReject(null),
    priceOf,
    order,
    advance,
    join,
    finish,
    reload,
  }
}
