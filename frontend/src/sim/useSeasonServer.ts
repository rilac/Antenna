/* 시즌 진행을 서버로 돌린다. G-04 가 쓴다.

   useSeasonSim(브라우저 로컬)을 대체한다 — 예수금·보유·손익은 GET /seasons/{id}/me 가
   주고, 주문은 POST /orders, 진행은 POST /advance, 종료는 POST /finish 다.
   화면이 받는 값의 모양은 그 훅과 같다. 서버 진행일이 올라가야 가격 상한이 풀려 새 봉이
   오므로, 진행 뒤에는 onAdvanced 로 봉 저장소를 비워 다시 받게 한다. */
import { useCallback, useEffect, useState } from 'react'
import { ApiError } from '../api/errors'
import {
  advance as advanceApi, finish as finishApi, getMyStatus, order as orderApi,
  type Candle, type MyStatus, type Side,
} from '../api/seasonPlay'

/** 주문이 거절되는 이유. 서버의 409 code 를 화면 문구 키로 옮긴 것이다 */
export type SimReject = 'CASH' | 'QTY' | 'NO_PRICE' | 'ENDED' | 'NOT_JOINED' | 'UNKNOWN'

const REJECT_BY_CODE: Record<string, SimReject> = {
  INSUFFICIENT_BALANCE: 'CASH',
  SEASON_INSUFFICIENT_QTY: 'QTY',
  SEASON_PRICE_NOT_FOUND: 'NO_PRICE',
  SEASON_ATTEMPT_ENDED: 'ENDED',
  SEASON_NOT_JOINED: 'NOT_JOINED',
}

const rejectOf = (e: unknown): SimReject =>
  e instanceof ApiError ? REJECT_BY_CODE[e.code] ?? 'UNKNOWN' : 'UNKNOWN'

export function useSeasonServer({
  seasonId,
  lengthDays,
  /** 종목 id → 워밍업 포함 전체 봉. 체결가 미리보기·전일 비교에 쓴다 */
  candlesOf,
  /** 진행일이 바뀐 뒤. 봉 저장소를 비워 다시 받게 한다 */
  onAdvanced,
}: {
  seasonId: number
  lengthDays: number
  candlesOf: (tickerId: number) => Candle[] | undefined
  onAdvanced: () => void
}) {
  const [status, setStatus] = useState<MyStatus | null>(null)
  const [error, setError] = useState<ApiError | null>(null)
  const [reject, setReject] = useState<SimReject | null>(null)
  const [busy, setBusy] = useState(false)

  const load = useCallback(() => {
    if (!seasonId) return Promise.resolve()
    return getMyStatus(seasonId)
      .then((s) => { setStatus(s); setError(null) })
      .catch((e: unknown) => setError(e instanceof ApiError ? e : null))
  }, [seasonId])

  useEffect(() => { void load() }, [load])

  const day = status?.currentDay ?? 0

  /** 그 종목의 게임일 종가. 아직 안 온 봉이면 null */
  const priceOf = useCallback(
    (tickerId: number, at = day) =>
      candlesOf(tickerId)?.find((c) => c.gameDay === at)?.close ?? null,
    [candlesOf, day],
  )

  /** 주문. 체결가(그 게임일 종가)를 돌려주고, 거절이면 null 과 함께 reject 를 세운다 */
  const order = useCallback(async (tickerId: number, side: Side, qty: number) => {
    setReject(null)
    setBusy(true)
    try {
      const r = await orderApi(seasonId, tickerId, side, qty)
      await load()
      return r.price
    } catch (e) {
      setReject(rejectOf(e))
      return null
    } finally {
      setBusy(false)
    }
  }, [seasonId, load])

  /** 다음 영업일. expectedDay 가 어긋나면(다른 탭이 먼저 넘김) 현황만 다시 읽는다 */
  const advance = useCallback(async () => {
    if (!status) return
    setReject(null)
    setBusy(true)
    try {
      await advanceApi(seasonId, status.currentDay)
      onAdvanced()
      await load()
    } catch (e) {
      if (e instanceof ApiError && e.code === 'DAY_MISMATCH') {
        onAdvanced()
        await load()
      } else {
        setReject(rejectOf(e))
      }
    } finally {
      setBusy(false)
    }
  }, [seasonId, status, load, onAdvanced])

  const finish = useCallback(() => finishApi(seasonId), [seasonId])

  return {
    /** GET /me 를 받았다. 그 전에는 진행일이 0 이라 워밍업 봉만 그려진다 */
    ready: status !== null,
    error,
    reload: load,
    busy,
    day,
    isLastDay: status !== null && lengthDays > 0 && status.currentDay >= lengthDays,
    cash: status?.cash ?? 0,
    stockValue: status?.stockValue ?? 0,
    totalAsset: status?.totalAsset ?? 0,
    pnl: status?.pnl ?? 0,
    pnlRate: status?.pnlRate ?? 0,
    positions: status?.positions ?? [],
    reject,
    clearReject: () => setReject(null),
    priceOf,
    order,
    advance,
    finish,
  }
}
