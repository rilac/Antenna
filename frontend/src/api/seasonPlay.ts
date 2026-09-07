/* 시즌 진행(G-04) · 리서치(G-05) · 매매일지(G-06) 조회 API.

   타입은 API 명세서 v0.25 의 응답 스키마를 그대로 옮겼다. 담기지 않은 값은 만들지 않는다.

   시기가 오지 않는다. 실제 날짜·연도 필드를 타입에 두지 않는 것이 그 규칙을 지키는
   방법이다 — 타입에 없으면 실수로 그릴 수 없다. 축은 날짜가 아니라 gameDay 다.

   담당 백엔드 티켓
   - GET /tickers · /prices — 됨(ANT-SEASON-10 · 이번 판)
   - POST /join — 연습만 됨. 대회는 참가비 소각 서명이 붙어 아직 없다
   - GET /me · POST /orders · POST /advance — ANT-SEASON-03 · 04, 아직 없다
   - GET /news — ANT-SEASON-07, season_news 가 0행이다
   - GET /indicators — MVP 에서 빼기로 했다. 절대값 하나로 구간이 특정된다 */
import { api } from './client'

/* ── 시즌 종목 · GET /seasons/{id}/tickers ────────────────── */

/** 정답 원본 종목은 오지 않는다. CLOSED 전까지 서버가 내리지 않는다. */
export type Ticker = {
  tickerId: number
  /** 블라인드 이름. "A사" 처럼 정체를 지운 값이다 */
  displayName: string
  /** 참가자에게 보이는 유일한 힌트. 상위 분류로만 온다 */
  sector?: string
}

export const getTickers = (seasonId: number) =>
  api.get<{ items: Ticker[] }>(`/seasons/${seasonId}/tickers`)

/* ── 시즌 가격 · GET /seasons/{id}/tickers/{tickerId}/prices ── */

/** 한 게임일의 봉. 실제 날짜가 없고 gameDay 인덱스만 있다. */
export type Candle = {
  gameDay: number
  /** OHLC 는 원천에 없으면 null 이다 — 심지가 빠질 수 있다 */
  open: number | null
  high: number | null
  low: number | null
  /** 체결·판정·표시가 쓰는 유일한 가격. 항상 있다 */
  close: number
  volume: number | null
}

/**
 * uptoDay 를 넘기지 않으면 서버가 내 진행일까지만 준다. 진행일 초과분은 커닝이라
 * 서버가 아예 내리지 않는다 — 화면에서 자르는 것으로는 부족하다.
 */
export const getPrices = (seasonId: number, tickerId: number, uptoDay?: number) =>
  api.get<{ items: Candle[] }>(`/seasons/${seasonId}/tickers/${tickerId}/prices`, {
    query: { uptoDay },
  })

/* ── 내 현황·포트폴리오 · GET /seasons/{id}/me ────────────── */

export type Position = {
  tickerId: number
  displayName: string
  qty: number
  avgPrice: number
  /** 평가금액 */
  value: number
  /** 평가손익 */
  pnl: number
  /** 비중. 단위가 명세에 없어 화면은 weightOf() 를 쓴다 */
  weight: number
}

export type MyStatus = {
  /** 예수금 */
  cash: number
  totalAsset: number
  /** 주식 평가금액 */
  stockValue: number
  pnl: number
  pnlRate: number
  currentDay: number
  positions: Position[]
}

export const getMyStatus = (seasonId: number) =>
  api.get<MyStatus>(`/seasons/${seasonId}/me`)

/* ── 주문 · POST /seasons/{id}/orders ─────────────────────── */

export type Side = 'BUY' | 'SELL'

/** 게임일 종가 단일가로 체결된다. 부분 체결도 슬리피지도 없다. */
export type OrderResult = {
  tradeId: number
  /** 체결가 = 그 게임일 종가 */
  price: number
  gameDay: number
}

export const order = (seasonId: number, tickerId: number, side: Side, qty: number) =>
  api.post<OrderResult>(
    `/seasons/${seasonId}/orders`,
    { tickerId, side, qty },
    { idempotencyScope: `order:${seasonId}:${tickerId}:${side}:${qty}` },
  )

/* ── 게임일 진행 · POST /seasons/{id}/advance ─────────────── */

export type AdvanceResult = {
  currentDay: number
  /** 마지막 게임일에 닿았다. 화면은 결과로 넘긴다 */
  isLastDay: boolean
}

/**
 * expectedDay 는 낙관적 잠금이다. 내가 보고 있는 게임일을 함께 보내고, 서버의 현재
 * 게임일과 다르면 409 DAY_MISMATCH 로 전진하지 않는다 — 두 번 눌러도, 탭이 둘이어도
 * 하루만 넘어간다.
 *
 * Idempotency-Key 를 쓰지 않는다. 진행은 반복 호출이 정상인 연산이라 키를 발급할
 * 시점이 없다(명세 §1).
 */
export const advance = (seasonId: number, expectedDay: number) =>
  api.post<AdvanceResult>(`/seasons/${seasonId}/advance`, { expectedDay })

/* ── 게임일 뉴스 · GET /seasons/{id}/news ─────────────────── */

export type NewsKind = 'NEWS' | 'DISCLOSURE' | 'IR' | 'EVENT'

/** 본문은 종목명·연도 치환본이다. 원문이 아니다 */
export type SeasonNews = {
  kind: NewsKind
  title: string
  body: string
  gameDay: number
}

export const getNews = (seasonId: number, day: number, tickerId?: number) =>
  api.get<{ items: SeasonNews[] }>(`/seasons/${seasonId}/news`, {
    query: { day, tickerId },
  })

/* ── 체결 내역 · GET /seasons/{id}/trades ─────────────────── */

export type Trade = {
  tradeId: number
  tickerId: number
  tickerName: string
  side: Side
  qty: number
  price: number
  amount: number
  gameDay: number
  /** 매도에만 값이 있다 */
  realizedPnl: number | null
}

export const getTrades = (seasonId: number, cursor?: string) =>
  api.get<{ items: Trade[]; nextCursor: string | null; hasNext: boolean }>(
    `/seasons/${seasonId}/trades`,
    { query: { cursor } },
  )

/* ── 화면이 함께 쓰는 계산 ────────────────────────────────── */

/**
 * 비중(%). 응답의 weight 를 쓰지 않는 이유는 명세에 단위가 적혀 있지 않아서다.
 * 평가금액과 총자산에서 직접 구하면 도넛 조각과 옆에 적는 금액이 어긋날 수 없다.
 */
export const weightOf = (value: number, totalAsset: number) =>
  totalAsset > 0 ? (value / totalAsset) * 100 : 0

/** 예상 주문 금액. 명세가 프론트 계산으로 지정한 값이다. 종가 단일가라 곱하기 하나다. */
export const orderAmount = (price: number, qty: number) => price * qty

/**
 * 이 가격에 최대 몇 주를 살 수 있는가. 수량 버튼의 "최대" 가 쓴다.
 * 내림이다 — 예수금을 넘기면 409 로 돌아온다.
 */
export const maxBuyQty = (cash: number, price: number) =>
  price > 0 ? Math.floor(cash / price) : 0

/** 손익 부호. 색을 정하는 자리가 한 곳이어야 빨강·파랑이 화면마다 뒤집히지 않는다. */
export const signOf = (n: number): 'up' | 'down' | 'flat' =>
  n > 0 ? 'up' : n < 0 ? 'down' : 'flat'

/** 등락률 표기. 0 도 부호 없이 그대로 적는다 */
export const rate = (n: number) => `${n > 0 ? '+' : ''}${n.toFixed(2)}%`
