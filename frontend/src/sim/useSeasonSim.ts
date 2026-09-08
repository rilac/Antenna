/* 시즌 진행을 브라우저에서 굴린다. G-04 가 쓴다.

   ── 목업이 아니다 ─────────────────────────────────────────
   가짜 숫자를 박아 넣지 않는다. 체결가는 서버가 준 <b>실제 게임일 종가</b>이고 나머지는
   거기서 곱셈으로 나온다. 명세가 체결 규칙을 이렇게 못박아 뒀기 때문이다.

     게임일 종가 단일가 체결, 부분 체결·슬리피지 없음.
     수량 버튼·예상 금액은 프론트 계산.

   그래서 예수금·평가금액·손익을 서버에 물을 필요가 없다. 서버가 붙으면 같은 값을 응답에서
   받아 오게 갈아끼우기만 하면 되고, 숫자가 튀지 않는다.

   ── 왜 로컬인가 ───────────────────────────────────────────
   POST /orders · /advance · GET /seasons/{id}/me 가 아직 없다(ANT-SEASON-03 · 04).
   그게 붙기 전까지 화면을 못 만들면 G-04 가 통째로 멈춘다.

   ── 서버가 붙으면 지울 것 ─────────────────────────────────
   이 파일 하나다. 화면은 여기서 나오는 값의 모양만 알고 있으므로, 훅 안을 API 호출로
   바꾸거나 훅을 걷어내고 GET /seasons/{id}/me 를 부르면 된다.

   ── 이 훅이 하지 않는 것 ──────────────────────────────────
   새로고침하면 사라진다. 저장하지 않는다 — 반쯤 진행한 상태를 브라우저에 남기면
   서버가 붙었을 때 어느 쪽이 진짜인지 알 수 없게 된다. */
import { useCallback, useMemo, useState } from 'react'
import type { Candle, Side, Ticker } from '../api/seasonPlay'

export type SimPosition = {
  tickerId: number
  displayName: string
  qty: number
  /** 평균 매입가. 추가 매수하면 수량 가중으로 다시 계산된다 */
  avgPrice: number
  /** 현재 게임일 종가 × 수량 */
  value: number
  /** value − 수량 × 평균 매입가 */
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

/** 주문이 거절되는 이유. 서버의 409 와 같은 자리다 */
export type SimReject = 'CASH' | 'QTY' | 'NO_PRICE'

type Holding = { qty: number; avgPrice: number }

export function useSeasonSim({
  initialCash,
  lengthDays,
  tickers,
  /** 종목 id → 워밍업 포함 전체 봉. 아직 못 받은 종목은 없어도 된다 */
  candlesOf,
}: {
  initialCash: number
  lengthDays: number
  tickers: Ticker[]
  candlesOf: (tickerId: number) => Candle[] | undefined
}) {
  /* 진행일. 참가하면 DAY 1 부터다. 서버의 season_participants.current_day 자리다 */
  const [day, setDay] = useState(1)
  const [holdings, setHoldings] = useState<Record<number, Holding>>({})
  const [trades, setTrades] = useState<SimTrade[]>([])
  const [reject, setReject] = useState<SimReject | null>(null)

  /** 그 종목의 현재 게임일 종가. 아직 안 온 봉이면 null */
  const priceOf = useCallback(
    (tickerId: number, at = day) =>
      candlesOf(tickerId)?.find((c) => c.gameDay === at)?.close ?? null,
    [candlesOf, day],
  )

  const positions = useMemo<SimPosition[]>(() => {
    return Object.entries(holdings)
      .map(([id, h]) => {
        const tickerId = Number(id)
        const price = priceOf(tickerId)
        /* 가격을 못 받았으면 평가금액을 0 으로 만들지 않는다 — 매입가로 둔다.
           0 으로 두면 총자산이 갑자기 줄어든 것처럼 보인다. */
        const mark = price ?? h.avgPrice
        const value = mark * h.qty
        return {
          tickerId,
          displayName:
            tickers.find((t) => t.tickerId === tickerId)?.displayName ?? `#${tickerId}`,
          qty: h.qty,
          avgPrice: h.avgPrice,
          value,
          pnl: value - h.avgPrice * h.qty,
        }
      })
      .filter((p) => p.qty > 0)
      .sort((a, b) => b.value - a.value)
  }, [holdings, priceOf, tickers])

  const cash = useMemo(
    () =>
      trades.reduce(
        (acc, t) => (t.side === 'BUY' ? acc - t.amount : acc + t.amount),
        initialCash,
      ),
    [trades, initialCash],
  )

  const stockValue = positions.reduce((acc, p) => acc + p.value, 0)
  const totalAsset = cash + stockValue
  const pnl = totalAsset - initialCash
  const pnlRate = initialCash > 0 ? (pnl / initialCash) * 100 : 0

  const isLastDay = day >= lengthDays

  /**
   * 주문. 성공하면 체결가를 돌려준다.
   *
   * <p>검증이 서버와 같은 순서다 — 가격이 있는가, 예수금이 되는가(매수), 보유 수량이
   * 되는가(매도). 서버가 붙으면 이 자리가 409 응답으로 바뀐다.
   */
  const order = useCallback(
    (tickerId: number, side: Side, qty: number): number | null => {
      setReject(null)
      if (qty <= 0) {
        setReject('QTY')
        return null
      }
      const price = priceOf(tickerId)
      if (price === null) {
        setReject('NO_PRICE')
        return null
      }
      const amount = price * qty
      const held = holdings[tickerId]

      if (side === 'BUY' && amount > cash) {
        setReject('CASH')
        return null
      }
      if (side === 'SELL' && (held?.qty ?? 0) < qty) {
        setReject('QTY')
        return null
      }

      /* 매도 실현손익은 평균 매입가 기준이다. 평균가는 남은 수량에 그대로 이어진다 —
         일부만 팔았다고 평균가가 바뀌지는 않는다. */
      const realizedPnl = side === 'SELL' && held ? (price - held.avgPrice) * qty : null

      setHoldings((prev) => {
        const cur = prev[tickerId] ?? { qty: 0, avgPrice: 0 }
        if (side === 'BUY') {
          const nextQty = cur.qty + qty
          return {
            ...prev,
            [tickerId]: {
              qty: nextQty,
              // 수량 가중 평균. 추가 매수가 평균가를 끌어올리거나 내린다
              avgPrice: (cur.avgPrice * cur.qty + price * qty) / nextQty,
            },
          }
        }
        const left = cur.qty - qty
        if (left <= 0) {
          const next = { ...prev }
          delete next[tickerId]
          return next
        }
        return { ...prev, [tickerId]: { qty: left, avgPrice: cur.avgPrice } }
      })

      setTrades((prev) => [
        {
          id: prev.length + 1,
          tickerId,
          displayName:
            tickers.find((t) => t.tickerId === tickerId)?.displayName ?? `#${tickerId}`,
          side,
          qty,
          price,
          amount,
          gameDay: day,
          realizedPnl,
        },
        ...prev,
      ])
      return price
    },
    [priceOf, holdings, cash, day, tickers],
  )

  /**
   * 다음 영업일. 마지막 게임일에서는 더 가지 않는다.
   *
   * <p>서버가 붙으면 {@code POST /advance} 로 바뀌고 expectedDay 낙관적 잠금이 붙는다 —
   * 그때도 화면이 받는 값은 {@code currentDay} 하나라 이 자리만 갈아끼우면 된다.
   */
  const advance = useCallback(() => {
    setReject(null)
    setDay((d) => Math.min(lengthDays, d + 1))
  }, [lengthDays])

  return {
    day,
    isLastDay,
    cash,
    stockValue,
    totalAsset,
    pnl,
    pnlRate,
    positions,
    trades,
    reject,
    clearReject: () => setReject(null),
    priceOf,
    order,
    advance,
  }
}
