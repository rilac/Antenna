/* 시즌 진행(G-04) · 리서치(G-05) · 매매일지(G-06) 조회 API.

   타입은 API 명세서 v0.25 의 응답 스키마를 그대로 옮겼다. 담기지 않은 값은 만들지 않는다.

   실제 날짜·연도 필드를 타입에 두지 않는다. 축이 날짜가 아니라 gameDay 이기 때문이고,
   타입에 없으면 실수로 그릴 수도 없다.

   연습은 종목이 실명이라 시기가 사실상 드러난다 — 실명과 실제 주가가 함께 나가면 검색
   한 번에 날짜가 나온다. 그래도 날짜를 안 받는 이유는 위와 같다. 대회는 진짜로 가린다.

   담당 백엔드 티켓
   - GET /tickers · /prices — 됨(ANT-SEASON-10 · 이번 판)
   - POST /join — 연습만 됨. 대회는 참가비 소각 서명이 붙어 아직 없다
   - GET /me · POST /orders · POST /advance — ANT-SEASON-03 · 04, 아직 없다
   - GET /news — ANT-SEASON-07, season_news 가 0행이다
   - GET /indicators — MVP 에서 빼기로 했다. 절대값 하나로 구간이 특정된다 */
import { api } from './client'

/* ── 시즌 종목 · GET /seasons/{id}/tickers ────────────────── */

/** 원본 종목코드는 오지 않는다. 모드와 무관하게 서버가 내리지 않는다. */
export type Ticker = {
  tickerId: number
  /**
   * 화면에 그리는 이름. 연습은 실제 종목명("삼성전자"), 대회는 가명("A사")이다.
   * 화면은 모드를 보지 않고 이 값을 그대로 쓴다.
   */
  displayName: string
  /** 섹터 힌트. 대회에서는 상위 분류로만 온다 */
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

/* ── 참가 · POST /seasons/{id}/join ───────────────────────── */

/** 새 회차가 열린다. 이미 끝낸 회차가 있으면 attemptNo 가 올라간다 */
export type JoinResult = {
  participantId: number
  attemptNo: number
  currentDay: number
}

/**
 * 연습·시연은 즉시 201 이다. 대회는 참가비 소각 서명이 붙어 아직 열려 있지 않다
 * (501 SEASON_JOIN_NOT_SUPPORTED).
 *
 * <p>멱등키를 붙인다 — 두 번 눌려 회차가 둘 생기면 어느 쪽이 내 판인지 알 수 없다.
 */
export const join = (seasonId: number) =>
  api.post<JoinResult>(`/seasons/${seasonId}/join`, undefined, {
    idempotencyScope: `join:${seasonId}`,
  })

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

/* ── 회차 종료 · POST /seasons/{id}/finish ────────────────── */

/**
 * 성적표. 서버가 체결 내역을 처음부터 다시 돌려 계산하고 DB 에 굳힌다.
 *
 * <p>정의할 수 없는 값은 null 이다 — 매도가 없으면 승률·평균 보유일이 없고,
 * 손실이 하나도 없으면 손익비의 분모가 0 이다.
 */
export type SeasonFinishResult = {
  participantId: number
  /** 마지막 게임일 종가로 평가한 총자산 */
  finalAsset: number
  /** 시작 예수금 대비 % */
  returnRate: number
  /**
   * 등가중 벤치마크(%). 시즌 종목을 똑같이 나눠 사서 끝까지 들고 있었다면.
   * 코스피 지수가 아니라 <b>그 시즌 종목으로 만든 지수</b>다.
   */
  benchmarkReturn: number | null
  /**
   * 최대 낙폭(%). 최고점에서 가장 깊게 파인 곳까지다 —
   * 화면이 따로 세는 "최고점 대비 마감" 과 다르다. 그건 끝값이고 이건 도중의 바닥이다.
   */
  maxDrawdown: number
  /** 매도 건수 중 이익으로 끝난 비율(%) */
  winRate: number | null
  /** 이익 본 매도의 합 ÷ 손해 본 매도의 합. 1 미만이면 잃은 것이다 */
  profitFactor: number | null
  /** (판 날 − 처음 산 날) 의 평균. 단타였는지 길게 들었는지 */
  avgHoldingDays: number | null
}

/**
 * 이 회차를 끝낸다. <b>되돌릴 수 없다</b> — 회차가 DONE 이 되어 주문도 진행도 막힌다.
 * 마지막 게임일에서만 부를 수 있고(아니면 409 SEASON_NOT_LAST_DAY), 이미 끝난 회차면
 * 저장해 둔 같은 결과를 다시 준다.
 */
export const finish = (seasonId: number) =>
  api.post<SeasonFinishResult>(`/seasons/${seasonId}/finish`)

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

/** 커서는 체결 id 다. 서버가 숫자로 준다 */
export const getTrades = (seasonId: number, cursor?: number) =>
  api.get<{ items: Trade[]; nextCursor: number | null; hasNext: boolean }>(
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
