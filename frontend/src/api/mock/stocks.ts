/* B-02 종목 탐색 목업. 백엔드에 필터·정렬·집계가 붙으면 이 파일을 지운다.

   값은 명세서의 응답 스키마를 그대로 따른다 — 나중에 실제 응답으로 바꿔도
   화면 코드가 그대로 돌아가는 게 목적이다. 그래서 스키마에 없는 필드는
   보기 좋더라도 넣지 않는다(예: "현재가" — 실전은 전일 종가만이다).

   필터·정렬·커서 페이징을 여기서 실제로 수행한다. 그렇게 해야 화면이
   "누르면 실제로 걸러진다" 는 것을 검증할 수 있다. 서버가 할 일을 흉내내는
   것이므로 규칙은 §4 B-02 와 같게 맞췄다:
     · 정렬 5종 고정, 기본 PREDICTION_COUNT
     · perMin > perMax 는 400 (화면이 먼저 막지만 계약은 지킨다)
     · 커서는 정렬 결과의 마지막 종목코드 */
import type {
  Market, SectorSummary, Sentiment, StockFilter, StockList, StockListItem, StockSort,
} from '../stocks'

const delay = <T,>(value: T, ms = 260) =>
  new Promise<T>((resolve) => setTimeout(() => resolve(value), ms))

/** 거래정지였던 날은 종가·등락률이 없다. 재무 미수집 종목은 PER·PBR 이 없다. */
type Row = Omit<StockListItem, 'watched'>

const ROWS: Row[] = [
  { code: '000660', name: 'SK하이닉스', sector: '반도체', market: 'KOSPI', prevClose: 198500, changeRate: 2.85, per: 9.8, pbr: 1.72, predictionCount: 22, upRatio: 82 },
  { code: '247540', name: '에코프로비엠', sector: '2차전지', market: 'KOSDAQ', prevClose: 167200, changeRate: -1.36, per: 48.5, pbr: 5.24, predictionCount: 19, upRatio: 32 },
  { code: '373220', name: 'LG에너지솔루션', sector: '2차전지', market: 'KOSPI', prevClose: 342000, changeRate: -1.87, per: 72.4, pbr: 3.61, predictionCount: 19, upRatio: 32 },
  { code: '005930', name: '삼성전자', sector: '반도체', market: 'KOSPI', prevClose: 71800, changeRate: 1.42, per: 14.3, pbr: 1.31, predictionCount: 17, upRatio: 71 },
  { code: '035420', name: 'NAVER', sector: '인터넷', market: 'KOSPI', prevClose: 176300, changeRate: -0.62, per: 18.7, pbr: 1.12, predictionCount: 16, upRatio: 44 },
  { code: '005380', name: '현대차', sector: '자동차', market: 'KOSPI', prevClose: 242000, changeRate: 0.83, per: 5.4, pbr: 0.68, predictionCount: 15, upRatio: 60 },
  { code: '035720', name: '카카오', sector: '인터넷', market: 'KOSPI', prevClose: 42150, changeRate: -1.04, per: 25.2, pbr: 1.08, predictionCount: 14, upRatio: 39 },
  { code: '068270', name: '셀트리온', sector: '바이오', market: 'KOSPI', prevClose: 194600, changeRate: -0.28, per: 41.9, pbr: 2.54, predictionCount: 12, upRatio: 55 },
  { code: '207940', name: '삼성바이오로직스', sector: '바이오', market: 'KOSPI', prevClose: 968000, changeRate: 0.37, per: 62.1, pbr: 6.48, predictionCount: 9, upRatio: 67 },
  { code: '105560', name: 'KB금융', sector: '금융', market: 'KOSPI', prevClose: 87900, changeRate: 0.11, per: 6.2, pbr: 0.59, predictionCount: 8, upRatio: 63 },
  { code: '000270', name: '기아', sector: '자동차', market: 'KOSPI', prevClose: 101500, changeRate: 1.24, per: 4.8, pbr: 0.71, predictionCount: 7, upRatio: 58 },
  { code: '051910', name: 'LG화학', sector: '2차전지', market: 'KOSPI', prevClose: 378000, changeRate: -2.11, per: 33.6, pbr: 1.04, predictionCount: 7, upRatio: 29 },
  { code: '006400', name: '삼성SDI', sector: '2차전지', market: 'KOSPI', prevClose: 321000, changeRate: -0.93, per: 29.4, pbr: 1.48, predictionCount: 6, upRatio: 35 },
  { code: '055550', name: '신한지주', sector: '금융', market: 'KOSPI', prevClose: 49800, changeRate: 0.42, per: 5.9, pbr: 0.52, predictionCount: 6, upRatio: 61 },
  { code: '005490', name: 'POSCO홀딩스', sector: '철강', market: 'KOSPI', prevClose: 412000, changeRate: -0.55, per: 12.7, pbr: 0.61, predictionCount: 5, upRatio: 48 },
  { code: '000100', name: '유한양행', sector: '바이오', market: 'KOSPI', prevClose: 118400, changeRate: 1.71, per: 38.2, pbr: 1.96, predictionCount: 5, upRatio: 72 },
  { code: '086520', name: '에코프로', sector: '2차전지', market: 'KOSDAQ', prevClose: 89300, changeRate: -3.02, per: 55.1, pbr: 4.12, predictionCount: 4, upRatio: 25 },
  { code: '091990', name: '셀트리온헬스케어', sector: '바이오', market: 'KOSDAQ', prevClose: 71200, changeRate: 0.28, per: 44.7, pbr: 2.21, predictionCount: 4, upRatio: 50 },
  { code: '036570', name: '엔씨소프트', sector: '인터넷', market: 'KOSPI', prevClose: 182500, changeRate: -1.19, per: 21.8, pbr: 1.27, predictionCount: 3, upRatio: 33 },
  { code: '015760', name: '한국전력', sector: '금융', market: 'KOSPI', prevClose: 21450, changeRate: 0.94, per: null, pbr: 0.34, predictionCount: 3, upRatio: 67 },
  { code: '096770', name: 'SK이노베이션', sector: '철강', market: 'KOSPI', prevClose: 108900, changeRate: -1.63, per: 17.2, pbr: 0.72, predictionCount: 2, upRatio: 50 },
  /* 그날 거래정지 — 종가·등락률이 비었을 때 화면이 0 으로 그리지 않는지 본다 */
  { code: '032830', name: '삼성생명', sector: '금융', market: 'KOSPI', prevClose: null, changeRate: null, per: 8.4, pbr: 0.38, predictionCount: 2, upRatio: 50 },
  /* 예측이 아직 없는 종목 — 집계 막대가 비는 경로 */
  { code: '009150', name: '삼성전기', sector: '반도체', market: 'KOSPI', prevClose: 143800, changeRate: 0.63, per: 16.9, pbr: 1.19, predictionCount: 0, upRatio: null },
  { code: '018260', name: '삼성에스디에스', sector: '인터넷', market: 'KOSPI', prevClose: 156200, changeRate: -0.32, per: 19.4, pbr: 1.11, predictionCount: 0, upRatio: null },
]

/* 관심 여부는 서버 상태라 목업도 한곳에 두고 토글이 남게 한다.
   새로고침하면 초기값으로 돌아간다 — 진짜 저장은 백엔드가 할 일이다. */
const watched = new Set(['000660', '373220', '005930', '035420', '005380'])

export function setWatch(stockCode: string, on: boolean) {
  if (on) watched.add(stockCode)
  else watched.delete(stockCode)
  return delay<void>(undefined, 180)
}

const withWatched = (r: Row): StockListItem => ({ ...r, watched: watched.has(r.code) })

/** 우세 방향. 예측이 없으면 어느 쪽도 아니다. */
function leaning(r: Row): Sentiment | null {
  if (r.upRatio === null) return null
  return r.upRatio >= 50 ? 'UP' : 'DOWN'
}

/* 정렬 규칙. 값이 없는 행(null)은 어느 정렬에서든 뒤로 보낸다 —
   0 으로 취급하면 "PER 낮은 순" 맨 앞이 재무 미수집 종목으로 채워진다. */
function compare(sort: StockSort, a: Row, b: Row): number {
  const nullsLast = (x: number | null, y: number | null, cmp: (p: number, q: number) => number) => {
    if (x === null && y === null) return 0
    if (x === null) return 1
    if (y === null) return -1
    return cmp(x, y)
  }
  switch (sort) {
    case 'PREDICTION_COUNT':
      return b.predictionCount - a.predictionCount
    case 'UP_RATIO':
      return nullsLast(a.upRatio, b.upRatio, (p, q) => q - p)
    case 'DOWN_RATIO':
      return nullsLast(a.upRatio, b.upRatio, (p, q) => p - q)
    case 'CHANGE_RATE':
      return nullsLast(a.changeRate, b.changeRate, (p, q) => q - p)
    case 'PER':
      return nullsLast(a.per, b.per, (p, q) => p - q)
  }
}

type Query = Record<string, string | number | boolean | undefined>

/** GET /stocks. 필터 → 정렬 → 커서 자르기 순으로 서버가 할 일을 흉내낸다. */
export function stockList(query: Query): Promise<StockList> {
  const f = query as unknown as StockFilter & { cursor?: string; size?: number }
  const size = Number(f.size ?? 10)

  /* 계약상 400 이다. 화면이 먼저 막지만 목업도 같이 거절해야 계약이 지켜진다. */
  if (f.perMin != null && f.perMax != null && Number(f.perMin) > Number(f.perMax)) {
    return Promise.reject(
      Object.assign(new Error('INVALID_REQUEST'), {
        status: 400, code: 'INVALID_REQUEST', field: 'perMin',
      }),
    )
  }

  let rows = ROWS.filter((r) => {
    if (f.sector && r.sector !== f.sector) return false
    if (f.market && r.market !== (f.market as Market)) return false
    if (f.sentiment && leaning(r) !== f.sentiment) return false
    if (f.perMin != null && (r.per === null || r.per < Number(f.perMin))) return false
    if (f.perMax != null && (r.per === null || r.per > Number(f.perMax))) return false
    if (f.hasOpenPrediction && r.predictionCount === 0) return false
    if (f.watchedOnly && !watched.has(r.code)) return false
    return true
  })

  rows = [...rows].sort((a, b) => compare(f.sort ?? 'PREDICTION_COUNT', a, b))

  const start = f.cursor ? rows.findIndex((r) => r.code === f.cursor) + 1 : 0
  const page = rows.slice(start, start + size)
  const last = page[page.length - 1]
  const hasNext = last ? rows.indexOf(last) < rows.length - 1 : false

  return delay<StockList>({
    items: page.map(withWatched),
    baseDate: '2026-08-31',
    nextCursor: hasNext && last ? last.code : null,
    hasNext,
  })
}

/** GET /stocks/sectors. 섹터별 종목 수와 평균 등락률. 첫 칸은 전체다. */
export function sectorSummary() {
  const names = [...new Set(ROWS.map((r) => r.sector).filter((s): s is string => s !== null))]
  const avg = (rs: Row[]) => {
    const vs = rs.map((r) => r.changeRate).filter((v): v is number => v !== null)
    return vs.length ? Math.round((vs.reduce((a, b) => a + b, 0) / vs.length) * 100) / 100 : 0
  }

  const items: SectorSummary[] = [
    { sector: null, count: ROWS.length, changeRate: avg(ROWS) },
    ...names.map((s) => {
      const rs = ROWS.filter((r) => r.sector === s)
      return { sector: s, count: rs.length, changeRate: avg(rs) }
    }),
  ]
  return delay({ items }, 200)
}
