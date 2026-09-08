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
   바꾸면 된다.

   order 와 advance 가 Promise 를 돌려주는 것도 그래서다. 지금은 안에서 즉시 끝나지만
   부르는 쪽이 await 로 쓰고 있으면, 서버가 붙어 진짜로 기다리게 될 때 훅 안쪽만
   바뀐다 — 화면은 한 줄도 안 고친다.

   ── 브라우저에 저장한다 ───────────────────────────────────
   처음에는 저장하지 않았다. 반쯤 진행한 상태가 브라우저에 남으면 서버가 붙었을 때
   어느 쪽이 진짜인지 알 수 없다는 이유였다. 그런데 저장하지 않으면 새로고침 한 번에
   30게임일이 날아가고, G-08 결과 화면으로 넘어가는 순간에도 날아간다 — 결과를 볼 수가
   없다. 서버가 없는 동안은 저장이 없는 쪽이 더 나쁘다.

   충돌은 키의 판 번호로 막는다. sim:{시즌id}:v1 이고, 서버가 붙는 날 이 파일이
   v1 을 읽지 않게 되면 남은 값은 아무도 읽지 않는 죽은 값이 된다. 지우는 코드도
   필요 없다.

   ── 저장하는 것은 넷뿐이다 ────────────────────────────────
   day · holdings · trades · equity. 예수금도 평가금액도 손익도 이 넷에서 파생되므로
   저장하지 않는다 — 저장하면 두 벌이 되고 어긋날 수 있다. */
import { useCallback, useEffect, useMemo, useState } from 'react'
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

/** 브라우저에 남기는 것. 나머지 값은 전부 여기서 파생된다 */
type SimState = {
  day: number
  holdings: Record<number, Holding>
  trades: SimTrade[]
  /** 게임일 → 그날 마감 총자산. 다음 영업일로 넘어갈 때 한 줄씩 적는다 */
  equity: Record<number, number>
}

const EMPTY: SimState = { day: 1, holdings: {}, trades: [], equity: {} }

/* 판 번호를 키에 둔다. 서버가 붙어 이 이름을 안 읽게 되면 남은 값은 죽은 값이다 */
const keyOf = (seasonId: number) => `sim:${seasonId}:v1`

/* 저장소는 사파리 비공개 모드나 차단 설정에서 던진다. 게임 진행 하나 때문에
   화면이 죽으면 안 되므로 실패하면 빈 상태로 시작한다. */
function load(seasonId: number): SimState {
  if (!seasonId) return EMPTY
  try {
    const raw = localStorage.getItem(keyOf(seasonId))
    if (!raw) return EMPTY
    const v = JSON.parse(raw) as Partial<SimState>
    return {
      day: typeof v.day === 'number' && v.day >= 1 ? v.day : 1,
      holdings: v.holdings ?? {},
      trades: Array.isArray(v.trades) ? v.trades : [],
      equity: v.equity ?? {},
    }
  } catch {
    return EMPTY
  }
}

function save(seasonId: number, st: SimState) {
  if (!seasonId) return
  try {
    localStorage.setItem(keyOf(seasonId), JSON.stringify(st))
  } catch {
    /* 저장에 실패해도 이번 판은 계속 굴러간다 */
  }
}

export function useSeasonSim({
  seasonId,
  initialCash,
  lengthDays,
  tickers,
  /** 종목 id → 워밍업 포함 전체 봉. 아직 못 받은 종목은 없어도 된다 */
  candlesOf,
}: {
  /** 저장 키를 가른다. 시즌마다 진행이 따로다 */
  seasonId: number
  initialCash: number
  lengthDays: number
  tickers: Ticker[]
  candlesOf: (tickerId: number) => Candle[] | undefined
}) {
  const [st, setSt] = useState<SimState>(() => load(seasonId))
  const [reject, setReject] = useState<SimReject | null>(null)

  /* 같은 라우트에서 시즌 번호만 바뀌면 컴포넌트가 다시 만들어지지 않는다 —
     그때 옛 시즌의 진행이 그대로 남으면 남의 판을 이어 하는 꼴이 된다.
     렌더 중에 갈아끼운다(리액트가 권하는 방식이다. 효과로 하면 한 프레임 어긋난다). */
  const [loadedFor, setLoadedFor] = useState(seasonId)
  if (loadedFor !== seasonId) {
    setLoadedFor(seasonId)
    setSt(load(seasonId))
  }

  const { day, holdings, trades } = st

  /* 바뀔 때마다 남긴다. 저장에 실패해도 이번 판은 계속 굴러간다 */
  useEffect(() => {
    if (loadedFor !== seasonId) return
    save(seasonId, st)
  }, [seasonId, loadedFor, st])

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
    async (tickerId: number, side: Side, qty: number): Promise<number | null> => {
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

      const name =
        tickers.find((t) => t.tickerId === tickerId)?.displayName ?? `#${tickerId}`

      setSt((prev) => {
        const cur = prev.holdings[tickerId] ?? { qty: 0, avgPrice: 0 }
        const nextHoldings = { ...prev.holdings }
        if (side === 'BUY') {
          const nextQty = cur.qty + qty
          nextHoldings[tickerId] = {
            qty: nextQty,
            // 수량 가중 평균. 추가 매수가 평균가를 끌어올리거나 내린다
            avgPrice: (cur.avgPrice * cur.qty + price * qty) / nextQty,
          }
        } else {
          const left = cur.qty - qty
          if (left <= 0) delete nextHoldings[tickerId]
          else nextHoldings[tickerId] = { qty: left, avgPrice: cur.avgPrice }
        }
        return {
          ...prev,
          holdings: nextHoldings,
          /* id 는 지금까지 쌓인 수 + 1. 목록이 최신 순이라 앞에 붙인다 */
          trades: [
            {
              id: prev.trades.length + 1,
              tickerId,
              displayName: name,
              side,
              qty,
              price,
              amount,
              gameDay: prev.day,
              realizedPnl,
            },
            ...prev.trades,
          ],
        }
      })
      return price
    },
    [priceOf, holdings, cash, tickers],
  )

  /**
   * 다음 영업일. 마지막 게임일에서는 더 가지 않는다.
   *
   * <p>서버가 붙으면 {@code POST /advance} 로 바뀌고 expectedDay 낙관적 잠금이 붙는다 —
   * 그때도 화면이 받는 값은 {@code currentDay} 하나라 이 자리만 갈아끼우면 된다.
   */
  const advance = useCallback(async () => {
    setReject(null)
    setSt((prev) => {
      if (prev.day >= lengthDays) return prev
      return {
        ...prev,
        /* 넘어가기 전에 그날 마감 총자산을 한 줄 적는다. 이게 없으면 나중에
           자산 곡선을 그릴 수 없다 — 지난 게임일의 평가금액은 보유했던 종목의
           봉을 전부 다시 받아야 나오는데, 그건 종목마다 요청 한 번이다. */
        equity: { ...prev.equity, [prev.day]: totalAsset },
        day: prev.day + 1,
      }
    })
  }, [lengthDays, totalAsset])

  /** 처음부터 다시. 브라우저에 남은 것도 같이 지운다 */
  const reset = useCallback(() => {
    setReject(null)
    setSt(EMPTY)
    try {
      localStorage.removeItem(keyOf(seasonId))
    } catch {
      /* 지우기에 실패해도 화면 상태는 이미 비었다 */
    }
  }, [seasonId])

  /* 자산 곡선. 지나온 날은 저장된 값이고, 오늘은 아직 안 적혔으니 지금 값을 붙인다 */
  const equity = useMemo(() => {
    const rows = Object.entries(st.equity)
      .map(([d, v]) => ({ gameDay: Number(d), totalAsset: v }))
      .filter((r) => r.gameDay !== day)
    rows.push({ gameDay: day, totalAsset })
    return rows.sort((a, b) => a.gameDay - b.gameDay)
  }, [st.equity, day, totalAsset])

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
    equity,
    reject,
    clearReject: () => setReject(null),
    priceOf,
    order,
    advance,
    reset,
  }
}
