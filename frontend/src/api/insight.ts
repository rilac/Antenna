/* B-01 인사이트 홈 · A-03 통합 검색이 쓰는 조회 API.

   타입은 API 명세서의 응답 스키마를 그대로 옮겼다. 지금 백엔드에는 조회 API 가
   하나도 없어(AuthController · AbuseReportController 뿐) 목업으로 화면을 먼저 만든다.

   ── 백엔드가 붙으면 지울 것 ────────────────────────────────
   아래 MOCK 을 false 로 바꾸면 전부 실제 호출로 넘어간다. 그다음
   api/mock/insight.ts 와 각 함수의 `if (MOCK)` 한 줄씩만 지우면 흔적이 없다.
   각 함수의 실제 호출부는 이미 명세서 경로·쿼리대로 적어 두었다.

   담당 백엔드 티켓:
     /market/indices  → ANT-DATA-04 (지수·환율 수집 선행)
     /stocks          → ANT-DATA-03 (3년치 백필 선행)
     /search          → ANT-DATA-05
     /watchlist       → ANT-DATA-06
     /users/me        → ANT-AUTH-05
     /rankings        → 티켓 없음
     /ads/active      → 티켓 없음
   ─────────────────────────────────────────────────────── */
import { api } from './client'
import * as mock from './mock/insight'

const MOCK = true

/* ── 시장 Overview · GET /market/indices ─────────────────── */

/** 지수 한 종목. series 는 미니차트용 최근 N일 종가. */
export type IndexQuote = {
  /** KOSPI · KOSDAQ · USDKRW */
  code: string
  close: number
  changeRate: number
  series: number[]
}

/* 지수는 실제 API 를 탄다.

   잠깐 목업으로 되돌렸다가(d203f1a) 다시 되돌린 자리다. items 가 늘 비어 있는 걸
   보고 "수집이 아직 안 돌았다" 고 읽었는데, **원인은 백엔드가 아니라 우리 쪽
   재기동이었다.** application.yaml 이 .env 를 optional:file 로 기동할 때 한 번만
   읽는데, 그날 백엔드가 09:08 에 뜨고 .env 에 키가 09:59 에 들어왔다. 재기동하니
   같은 코드가 지수 3,278행을 받아 왔다.

   **다음에 "수집이 안 된다" 싶으면 .env 시각과 기동 시각부터 견주어 볼 것.**

   USDKRW 는 아직 안 온다 — KOREAEXIM_AUTH_KEY 가 .env 에 없어 환율만 건너뛴다.
   그래서 카드에 지수 셋이 아니라 둘이 뜬다. 빈 자리를 만들지 않고 온 것만 그린다 —
   화면이 개수를 정하지 않고 서버가 준 만큼 그린다. */
export function getMarketIndices(days = 30) {
  return api.get<{ items: IndexQuote[] }>('/market/indices', { query: { days } })
}

/* AI 브리핑은 여기 없다. 홈 띠(B-01) · 종목 상세(B-03) · 상세 모달(M-10)이
   같은 응답을 읽으므로 계약을 api/briefings.ts 한 곳에 뒀다. */

/* ── 관심 종목 · GET /watchlist ───────────────────────────── */

/** 실전 시세는 전일 종가만이다(§7). "현재가" 를 만들지 않는다. */
export type WatchlistRow = {
  stockCode: string
  name: string
  /** 수집 범위(KOSPI 300) 밖으로 밀려 시세가 없으면 null */
  prevClose: number | null
  /** 점이 둘 미만이면 0 */
  changeRate: number
  /** 최근 30 영업일 종가, 오래된 순. 시세가 없으면 빈 배열 */
  series: number[]
}

export function getWatchlist() {
  return api.get<{ items: WatchlistRow[] }>('/watchlist')
}

/* ── 주목할 예측가 · GET /rankings ────────────────────────── */

export type RankingRow = {
  rank: number
  userId: number
  nickname: string
  score: number
  hitRate: number
  avgError: number
  doneCount: number
}

/** 홈 위젯은 track=REAL&limit=5 를 재사용한다(명세서 §랭킹). */
export function getTopPredictors(limit = 5) {
  if (MOCK) return mock.rankings(limit)
  return api.get<{ computedAt: string; items: RankingRow[] }>('/rankings', {
    query: { track: 'REAL', limit },
  })
}

/* ── 스폰서드 · GET /ads/active ───────────────────────────── */

export type ActiveAd = { id: number; imageUrl: string; linkUrl: string }

/* 광고는 등록된 배너가 아직 없어 화면이 비어 보인다 — 시연 동안 목업을 둔다(2026-09-03 결정). */
export function getActiveAds() {
  if (MOCK) return mock.activeAds()
  return api.get<{ items: ActiveAd[] }>('/ads/active')
}

/* ── 내 자산 · GET /wallet/balance ────────────────────────── */

/** 보유 ANT 는 프로필이 아니라 지갑에서 온다. /users/me 에는 없다. */
export type WalletBalance = {
  balance: number
  spent30d: number
  earned30d: number
  /** 원화 환산 — 서버가 계산해 내려준다 */
  valuationKrw: number
}

export function getWalletBalance() {
  if (MOCK) return mock.walletBalance()
  return api.get<WalletBalance>('/wallet/balance')
}

/* ── 통합 검색 · GET /search ──────────────────────────────── */

export type SearchResult = {
  stocks: { code: string; name: string; sector: string; prevClose: number; watching: boolean }[]
  channels: { userId: number; nickname: string; hitRate: number }[]
  reports: { id: number; title: string; author: string }[]
}

/** 타입별 상위 N건만 온다. 필터·정렬·페이징은 전용 목록의 책임이다(명세서 §검색). */
export function search(q: string, limit = 5) {
  if (MOCK) return mock.search(q, limit)
  return api.get<SearchResult>('/search', { query: { q, limit } })
}
