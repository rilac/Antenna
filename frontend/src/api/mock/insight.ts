/* 목업 응답. 백엔드에 조회 API 가 붙으면 이 파일을 지운다.

   값은 명세서의 응답 스키마를 그대로 따른다 — 나중에 실제 응답으로 바꿔도
   화면 코드가 그대로 돌아가는 게 목적이다. 그래서 스키마에 없는 필드는
   보기 좋더라도 넣지 않는다(예: 종목의 "현재가" — 실전은 전일 종가만이다).

   지연을 조금 주는 이유: 로딩 상태를 화면에서 실제로 보게 하려는 것이다. */
import type {
  ActiveAd, Briefing, IndexQuote, RankingRow, SearchResult, WalletBalance, WatchlistRow,
} from '../insight'

const delay = <T,>(value: T, ms = 320) =>
  new Promise<T>((resolve) => setTimeout(() => resolve(value), ms))

/** 시드 기반 의사난수 — 새로고침해도 같은 그래프가 나오게 한다 */
function walk(seed: number, n: number, start: number, drift: number) {
  let v = start
  let s = seed
  const out: number[] = []
  for (let i = 0; i < n; i++) {
    s = (s * 1103515245 + 12345) % 2147483648
    const noise = (s / 2147483648 - 0.5) * drift
    v = Math.max(start * 0.7, v * (1 + noise) + start * 0.0004 * (i % 7 === 0 ? 1 : -0.4))
    out.push(Math.round(v * 100) / 100)
  }
  return out
}

export const marketIndices = () => delay<{ items: IndexQuote[] }>({
  items: [
    { code: 'KOSPI', close: 2663.33, changeRate: 0.70, series: walk(11, 30, 2610, 0.012) },
    { code: 'KOSDAQ', close: 842.67, changeRate: 0.73, series: walk(23, 30, 830, 0.014) },
    { code: 'USDKRW', close: 1363.20, changeRate: -0.25, series: walk(37, 30, 1372, 0.006) },
  ],
})

export const briefings = () => delay<{ items: Briefing[] }>({
  items: [
    { id: 1, scope: 'MARKET', stockCode: null, headline: '반도체 수출 회복이 지수를 끌어올렸습니다', targetDate: '2026-08-31' },
    { id: 2, scope: 'MARKET', stockCode: null, headline: '2차전지는 유럽 보조금 축소 소식에 조정받았습니다', targetDate: '2026-08-31' },
    { id: 3, scope: 'MARKET', stockCode: null, headline: '환율 하락으로 수입 비중이 큰 업종에 여유가 생겼습니다', targetDate: '2026-08-31' },
  ],
})

export const watchlist = () => delay<{ items: WatchlistRow[] }>({
  items: [
    { stockCode: '005930', name: '삼성전자', prevClose: 78600, changeRate: 1.68, series: walk(101, 30, 76800, 0.014) },
    { stockCode: '000660', name: 'SK하이닉스', prevClose: 188700, changeRate: 1.70, series: walk(103, 30, 183000, 0.019) },
    { stockCode: '035420', name: 'NAVER', prevClose: 205000, changeRate: -0.48, series: walk(107, 30, 208000, 0.013) },
    { stockCode: '247540', name: '에코프로비엠', prevClose: 167200, changeRate: -1.36, series: walk(109, 30, 174000, 0.024) },
    { stockCode: '005380', name: '현대차', prevClose: 242000, changeRate: 0.83, series: walk(113, 30, 238000, 0.011) },
  ],
})

export const rankings = (limit: number) => delay<{ computedAt: string; items: RankingRow[] }>({
  computedAt: '2026-08-31T06:00:00+09:00',
  items: ([
    { rank: 1, userId: 41, nickname: '반도체훈련소', score: 921, hitRate: 78.6, avgError: 2.4, doneCount: 128 },
    { rank: 2, userId: 77, nickname: '차트도사', score: 884, hitRate: 74.1, avgError: 2.9, doneCount: 96 },
    { rank: 3, userId: 12, nickname: '느린손', score: 851, hitRate: 71.8, avgError: 3.1, doneCount: 154 },
    { rank: 4, userId: 63, nickname: '배당수집가', score: 828, hitRate: 70.2, avgError: 3.4, doneCount: 71 },
    { rank: 5, userId: 88, nickname: '이차전지관찰', score: 802, hitRate: 68.9, avgError: 3.6, doneCount: 89 },
  ] as RankingRow[]).slice(0, limit),
})

/* 홈 배너는 여러 장을 돌려 보여주므로 목업도 여러 장 준다.
   스키마에 문구가 없어(이미지·링크뿐) 장마다 그림만 달라진다. */
export const activeAds = () => delay<{ items: ActiveAd[] }>({
  items: [
    { id: 1, imageUrl: '/assets/antena-character-transparent.png', linkUrl: '/ads/new' },
    { id: 2, imageUrl: '/assets/antena-character-black.png', linkUrl: '/ads/new' },
    { id: 3, imageUrl: '/assets/antena-profile.png', linkUrl: '/ads/new' },
  ],
})

export const walletBalance = () => delay<WalletBalance>({
  balance: 1250,
  spent30d: 320,
  earned30d: 850,
  valuationKrw: 1213750,
})

const ALL_STOCKS = [
  { code: '005930', name: '삼성전자', sector: '반도체', prevClose: 78600, watching: true },
  { code: '000660', name: 'SK하이닉스', sector: '반도체', prevClose: 188700, watching: true },
  { code: '035420', name: 'NAVER', sector: '인터넷', prevClose: 205000, watching: true },
  { code: '035720', name: '카카오', sector: '인터넷', prevClose: 42150, watching: false },
  { code: '247540', name: '에코프로비엠', sector: '2차전지', prevClose: 167200, watching: true },
  { code: '373220', name: 'LG에너지솔루션', sector: '2차전지', prevClose: 342000, watching: false },
  { code: '005380', name: '현대차', sector: '자동차', prevClose: 242000, watching: true },
  { code: '068270', name: '셀트리온', sector: '바이오', prevClose: 194600, watching: false },
  { code: '207940', name: '삼성바이오로직스', sector: '바이오', prevClose: 968000, watching: false },
  { code: '105560', name: 'KB금융', sector: '금융', prevClose: 87900, watching: false },
]
const ALL_CHANNELS = [
  { userId: 41, nickname: '반도체훈련소', hitRate: 78.6 },
  { userId: 77, nickname: '차트도사', hitRate: 74.1 },
  { userId: 12, nickname: '느린손', hitRate: 71.8 },
  { userId: 88, nickname: '이차전지관찰', hitRate: 68.9 },
]
const ALL_REPORTS = [
  { id: 9, title: 'HBM4 양산 시점과 공급 과점 구조', author: '반도체훈련소' },
  { id: 8, title: '유럽 보조금 축소가 셀 업체에 남기는 것', author: '이차전지관찰' },
  { id: 7, title: '환율 1,350원대에서 다시 보는 수입 원가', author: '느린손' },
]

/** 종목명·종목코드 prefix·닉네임·리포트 제목으로 얕게 찾는다(명세서 §검색) */
export const search = (q: string, limit: number) => {
  const key = q.trim().toLowerCase().replace(/\s+/g, '')
  const hit = (s: string) => s.toLowerCase().replace(/\s+/g, '').includes(key)
  const result: SearchResult = {
    stocks: ALL_STOCKS.filter((s) => hit(s.name) || s.code.startsWith(key) || hit(s.sector)).slice(0, limit),
    channels: ALL_CHANNELS.filter((c) => hit(c.nickname)).slice(0, limit),
    reports: ALL_REPORTS.filter((r) => hit(r.title) || hit(r.author)).slice(0, limit),
  }
  return delay(result, 220)
}
