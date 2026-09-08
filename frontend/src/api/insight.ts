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

/* 목업으로 되돌렸다(2026-09-08 결정). 엔드포인트는 살아 있고 200 을 주지만
   index_quotes 가 0행이라 items 가 늘 비어 있다 — 지수·환율 수집(ANT-DATA-04)이
   아직 안 돌았다. 그대로 두면 홈 첫 카드가 통째로 빈 채로 보인다.
   광고(getActiveAds)에 내린 것과 같은 판단이고, 수집이 붙으면 이 줄만 지운다.

   **오류가 아니라 빈 목록이라는 점이 중요하다.** 화면이 "불러오지 못했습니다" 를
   띄우는 게 아니라 아무것도 안 그린다 — 그래서 빈 상태 문구로도 덮이지 않는다. */
export function getMarketIndices(days = 30) {
  if (MOCK) return mock.marketIndices()
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
