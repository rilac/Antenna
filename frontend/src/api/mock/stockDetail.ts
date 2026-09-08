/* B-03 종목 상세 목업. 백엔드에 블록별 엔드포인트가 붙으면 이 파일을 지운다.

   값은 명세서 응답 스키마를 그대로 따른다 — 실제 응답으로 바꿔도 화면이
   그대로 돌아가는 게 목적이다. 스키마에 없는 필드는 보기 좋더라도 넣지 않는다
   (시가총액 · EPS · 배당수익률 · 52주 범위 · 업종 평균 — §9.2 두지 않는 것).

   종목 우주는 mock/stocks.ts 한 곳에서 가져온다. 종가를 두 파일에 각각 적으면
   목록에서 누른 종목과 상세 헤더가 어긋난다.

   블록마다 지연 시간을 다르게 줬다. 화면이 정말로 블록 단위로 따로 뜨는지
   눈으로 확인하려는 것이다(§4 B-03 "블록 단위로 로딩·실패를 독립 처리"). */
import type { StockListItem } from '../stocks'
import type {
  ClosePoint, CompanyProfile, FinancialRow, Peer, PointGroups,
  StockDocument, StockSentiment, StockSummary, Valuation,
} from '../stockDetail'

/* ── 종목 우주 ─────────────────────────────────────────────
   전에는 B-02 목업(mock/stocks.ts)에서 가져다 썼는데, 종목 탐색이 실제 API 로
   넘어가며 그 파일이 사라졌다(dev 09b055d). 그래서 상세 목업이 제 표를 들고 있는다.
   백엔드에 GET /stocks/{code} 가 생기면 이 표도 함께 사라진다.

   앞의 열다섯은 실제 DB 에 있는 코드다 — 종목 탐색에서 눌러 들어오면 반드시 이 안에
   떨어지므로 404 가 나지 않는다. 뒤의 몇은 DB 에 없어 주소로만 닿는데, 거래정지
   (prevClose null) · 예측 없음(upRatio null) 같은 빈 값 경로를 눈으로 확인하려고 남겼다. */

/** 목록·상세가 함께 기준하는 영업일 */
const BASE_DATE = '2026-08-31'

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
  /* 여기부터는 DB 에 없다 — 빈 값 경로 확인용 */
  /* 그날 거래정지: 종가·등락률이 없을 때 화면이 0 으로 그리지 않는지 본다 */
  { code: '032830', name: '삼성생명', sector: '금융', market: 'KOSPI', prevClose: null, changeRate: null, per: 8.4, pbr: 0.38, predictionCount: 2, upRatio: 50 },
  /* 예측이 아직 없는 종목: 집계 막대가 비는 경로 */
  { code: '009150', name: '삼성전기', sector: '반도체', market: 'KOSPI', prevClose: 143800, changeRate: 0.63, per: 16.9, pbr: 1.19, predictionCount: 0, upRatio: null },
  /* 재무 미수집: PER 이 없으면 밸류·재무가 함께 비어야 한다 */
  { code: '015760', name: '한국전력', sector: '금융', market: 'KOSPI', prevClose: 21450, changeRate: 0.94, per: null, pbr: 0.34, predictionCount: 3, upRatio: 67 },
]

/* 관심 여부는 서버 상태라 목업도 한곳에 두고 토글이 남게 한다.
   새로고침하면 초기값으로 돌아간다 — 진짜 저장은 백엔드가 한다. */
const watched = new Set(['000660', '373220', '005930', '035420', '005380'])
const withWatched = (r: Row): StockListItem => ({ ...r, watched: watched.has(r.code) })

const findRow = (code: string): StockListItem | null => {
  const r = ROWS.find((x) => x.code === code)
  return r ? withWatched(r) : null
}

/** 브리핑 목업(mock/briefings.ts)이 헤드라인에 종목명을 넣을 때 쓴다.
    표를 두 곳에 두면 같은 코드가 서로 다른 이름으로 뜬다. */
export const stockName = (code: string): string | null => ROWS.find((r) => r.code === code)?.name ?? null

/** 경쟁사 나열에 쓴다 — 같은 섹터의 다른 종목들 */
const rowsInSector = (sector: string, exclude: string): StockListItem[] =>
  ROWS.filter((r) => r.sector === sector && r.code !== exclude).map(withWatched)

const delay = <T,>(value: T, ms: number) =>
  new Promise<T>((resolve) => setTimeout(() => resolve(value), ms))

const notFound = (code: string) =>
  Promise.reject(Object.assign(new Error('STOCK_NOT_FOUND'), {
    status: 404, code: 'STOCK_NOT_FOUND', message: `종목 ${code} 을(를) 찾을 수 없습니다`,
  }))

/* 종목코드를 씨앗으로 쓰는 결정적 난수. 새로고침해도 같은 그림이 나와야
   "값이 바뀌었나" 하는 착각이 없다. */
function seeded(seed: string) {
  let h = 2166136261
  for (let i = 0; i < seed.length; i++) {
    h ^= seed.charCodeAt(i)
    h = Math.imul(h, 16777619)
  }
  return () => {
    h ^= h << 13; h ^= h >>> 17; h ^= h << 5
    return ((h >>> 0) % 100000) / 100000
  }
}

/** 주말을 건너뛰며 영업일을 거꾸로 센다 */
function businessDaysBack(end: string, count: number): string[] {
  const out: string[] = []
  const d = new Date(`${end}T00:00:00Z`)
  while (out.length < count) {
    const day = d.getUTCDay()
    if (day !== 0 && day !== 6) out.push(d.toISOString().slice(0, 10))
    d.setUTCDate(d.getUTCDate() - 1)
  }
  return out.reverse()
}

/* ── 요약 헤더 ─────────────────────────────────────────── */
export function summary(code: string): Promise<StockSummary> {
  const r = findRow(code)
  if (!r) return notFound(code)
  return delay({
    code: r.code,
    name: r.name,
    sector: r.sector,
    market: r.market,
    prevClose: r.prevClose,
    /* 거래정지였던 종목은 기준일도 없다. 0 이나 오늘 날짜로 메우지 않는다 */
    asOf: r.prevClose === null ? null : BASE_DATE,
    changeRate: r.changeRate,
    per: r.per,
    pbr: r.pbr,
    predictionCount: r.predictionCount,
    watched: r.watched,
  }, 180)
}

/* ── 종가 시계열 ───────────────────────────────────────── */
export function prices(code: string, from?: string): Promise<{ items: ClosePoint[] }> {
  const r = findRow(code)
  if (!r) return notFound(code)
  /* 거래정지 종목은 시계열도 비운다 — 화면이 빈 차트를 어떻게 그리는지 본다 */
  if (r.prevClose === null) return delay({ items: [] }, 240)

  const days = from
    ? Math.max(2, Math.round(
      ((Date.parse(`${BASE_DATE}T00:00:00Z`) - Date.parse(`${from}T00:00:00Z`)) / 86400000) * (5 / 7)))
    : 30
  const dates = businessDaysBack(BASE_DATE, Math.min(days, 260))
  const rnd = seeded(code)

  /* 마지막 값이 실제 전일 종가와 같아야 헤더와 차트가 어긋나지 않는다.
     그래서 뒤에서 앞으로 걸어 올라간 뒤 뒤집는다. */
  const back: number[] = [r.prevClose]
  for (let i = 1; i < dates.length; i++) {
    const drift = (rnd() - 0.48) * 0.028
    back.push(Math.round(back[i - 1] / (1 + drift)))
  }
  const closes = back.reverse()

  return delay({
    items: dates.map((tradeDate, i) => ({ tradeDate, close: closes[i] })),
  }, 320)
}

/* ── 예측 심리 ─────────────────────────────────────────── */
export function sentiment(code: string): Promise<StockSentiment> {
  const r = findRow(code)
  if (!r) return notFound(code)
  const rnd = seeded(`${code}s`)
  const up = r.upRatio

  return delay({
    upRatio: up,
    downRatio: up === null ? null : 100 - up,
    sampleSize: r.predictionCount,
    /* 예측이 없으면 기간별도 비운다. 0% 막대를 그리면 "다 하락" 으로 읽힌다 */
    byHorizon: [5, 10, 20, 60].map((horizon) => {
      const n = up === null ? 0 : Math.round(r.predictionCount * (0.15 + rnd() * 0.35))
      return {
        horizon,
        upRatio: n === 0 || up === null
          ? null
          : Math.max(0, Math.min(100, Math.round(up + (rnd() - 0.5) * 24))),
        sampleSize: n,
      }
    }),
  }, 400)
}

/* ── 공시·뉴스 ─────────────────────────────────────────── */
export function documents(code: string): Promise<{
  items: StockDocument[]; nextCursor: number | null; hasNext: boolean
}> {
  const r = findRow(code)
  if (!r) return notFound(code)
  const dart = `https://dart.fss.or.kr/dsab007/main.do?textCrpNm=${encodeURIComponent(r.name)}`
  const n = Number(code)
  return delay({
    items: [
      {
        id: n + 1, source: 'DART' as const,
        title: '반기보고서 (2026.06)',
        summary: '반기 매출과 영업이익, 부문별 실적과 주요 계약 현황이 담겼습니다. 전년 동기 대비 변동 사유는 본문 3장에 있습니다.',
        originUrl: dart,
        publishedAt: '2026-08-14T16:05:00+09:00',
      },
      {
        id: n + 2, source: 'DART' as const,
        title: '단일판매·공급계약 체결',
        summary: '신규 공급계약 체결 사실과 계약 금액·기간이 공시됐습니다. 최근 매출액 대비 비중은 공시 본문에 기재돼 있습니다.',
        originUrl: dart,
        publishedAt: '2026-08-06T09:31:00+09:00',
      },
      {
        id: n + 3, source: 'NEWS' as const,
        title: `${r.sector ?? '업종'} 업황 회복 신호, 하반기 전망은`,
        summary: '업종 전반의 수요 지표가 반등했다는 분석과, 이를 개별 종목 실적으로 연결하기에는 이르다는 반론이 함께 실렸습니다.',
        originUrl: 'https://news.einfomax.co.kr/',
        publishedAt: '2026-08-29T11:42:00+09:00',
      },
      {
        /* 요약 배치가 아직 안 돈 건. 화면이 제목만으로 줄을 그리는지 본다 */
        id: n + 4, source: 'NEWS' as const,
        title: `${r.name}, 설비 투자 계획 발표`,
        summary: null,
        originUrl: 'https://www.hankyung.com/',
        publishedAt: '2026-08-25T07:15:00+09:00',
      },
    ],
    nextCursor: null,
    hasNext: false,
  }, 460)
}

/* ── 투자 포인트 ───────────────────────────────────────── */
export function points(code: string): Promise<PointGroups> {
  const r = findRow(code)
  if (!r) return notFound(code)
  const s = r.sector ?? '업종'
  const n = Number(code) * 10

  /* 서버는 열마다 배열을 따로 내린다. 제목 없이 body 한 덩어리이고,
     documentId 가 있으면 그 문서가 근거다 — 없으면 시세·재무에서 나온 종합 포인트다. */
  return delay({
    positive: [
      { id: n + 1, body: `${s} 전방 수요 지표가 2개 분기 연속 개선됐습니다. 가동률이 함께 오르면 고정비 부담이 줄어 영업이익률에 먼저 나타납니다.`, documentId: Number(code) + 3, source: 'NEWS' as const },
      { id: n + 2, body: '공급이 제한된 품목의 비중이 높아, 원가가 오를 때 판가로 옮길 여지가 경쟁사 대비 큽니다.', documentId: null, source: null },
      { id: n + 3, body: '차입금 상환이 이어지며 이자비용이 줄었습니다. 순이익 변동성이 낮아지는 요인입니다.', documentId: Number(code) + 1, source: 'DART' as const },
    ],
    risk: [
      { id: n + 4, body: '매출의 상당 부분이 외화 결제라 환율이 내려가면 원화 환산 매출이 줄어듭니다.', documentId: null, source: null },
      { id: n + 5, body: '설비 투자 집행이 시작되면 감가상각비가 늘어 초기 몇 개 분기 동안 영업이익률을 누릅니다.', documentId: Number(code) + 4, source: 'NEWS' as const },
    ],
    check: [
      { id: n + 6, body: `${s} 내 신규 진입이 이어지고 있습니다. 점유율 방어에 마케팅비가 더 들어가는지 다음 분기 판관비로 확인해야 합니다.`, documentId: null, source: null },
      { id: n + 7, body: '공급계약의 최근 매출액 대비 비중이 공시 본문에만 있습니다. 실제 기여도는 원문에서 확인이 필요합니다.', documentId: Number(code) + 2, source: 'DART' as const },
    ],
  }, 380)
}

/* ── 기업 개요 ─────────────────────────────────────────── */
export function profile(code: string): Promise<CompanyProfile> {
  const r = findRow(code)
  if (!r) return notFound(code)
  const rnd = seeded(`${code}p`)
  return delay({
    corpName: r.name,
    ceo: '대표이사',
    establishedAt: `19${70 + Math.floor(rnd() * 25)}-03-12`,
    /* 원천이 없어 서버가 항상 null 로 내린다. 화면도 자리를 만들지 않는다 */
    listedAt: null,
    homepage: 'https://example.co.kr',
    address: '경기도 성남시 분당구 판교로 000',
    /* DART 업종코드가 아니라 stocks.sector(KRX 분류)다 */
    industry: r.sector,
  }, 500)
}

/* ── 재무 ─────────────────────────────────────────────── */
export function financials(code: string): Promise<{ items: FinancialRow[] }> {
  const r = findRow(code)
  if (!r) return notFound(code)
  /* PER 이 없는 종목은 재무도 수집 전이다. 두 곳이 어긋나면 안 된다 */
  if (r.per === null) return delay({ items: [] }, 540)

  const rnd = seeded(`${code}f`)
  /* 금액 단위는 원이다(억원이 아니다). 조 단위를 원으로 만든다 */
  let revenue = Math.round((2 + rnd() * 40) * 1e12)

  /* 오래된 연도가 먼저다 — 차트가 왼쪽에서 오른쪽으로 그린다 */
  const items = [
    { year: 2023, quarter: 4 },
    { year: 2024, quarter: 4 },
    { year: 2025, quarter: 4 },
    { year: 2026, quarter: 2 },
  ].map(({ year, quarter }, i) => {
    if (i) revenue = Math.round(revenue * (0.94 + rnd() * 0.24))
    const half = quarter === 2
    const rev = half ? Math.round(revenue * 0.5) : revenue
    const op = Math.round(rev * (0.04 + rnd() * 0.16))
    const equity = Math.round(rev * (0.8 + rnd() * 1.4))
    return {
      year,
      quarter,
      fsDiv: 'CFS',
      revenue: rev,
      operatingProfit: op,
      netIncome: Math.round(op * (0.62 + rnd() * 0.3)),
      assets: Math.round(equity * (1.4 + rnd() * 0.6)),
      liabilities: Math.round(equity * (0.4 + rnd() * 0.6)),
      equity,
    }
  })
  return delay({ items }, 540)
}

/* ── 밸류에이션 ────────────────────────────────────────── */
export function valuation(code: string): Promise<Valuation> {
  const r = findRow(code)
  if (!r) return notFound(code)
  const rnd = seeded(`${code}v`)
  const hasFinancials = r.per !== null

  return delay({
    per: r.per,
    pbr: r.pbr,
    roe: hasFinancials ? Math.round((2 + rnd() * 22) * 10) / 10 : null,
    debtRatio: hasFinancials ? Math.round((30 + rnd() * 140) * 10) / 10 : null,
    basedOn: {
      priceDate: r.prevClose === null ? null : BASE_DATE,
      prevClose: r.prevClose,
      fiscal: hasFinancials ? 2025 : null,
      fsDiv: hasFinancials ? 'CFS' : null,
      /* 값이 빈 이유를 서버가 문장으로 준다. 화면은 이걸 그대로 보여준다 */
      note: hasFinancials ? null : '최신 연간 재무가 수집되지 않아 비율을 계산하지 않았습니다.',
    },
  }, 470)
}

/* ── 경쟁사 ───────────────────────────────────────────── */
export function peers(code: string): Promise<{ priceDate: string | null; items: Peer[] }> {
  const r = findRow(code)
  if (!r) return notFound(code)
  /* 섹터가 없으면 비교 대상을 만들 수 없다. 억지로 채우지 않는다 */
  const list = r.sector ? rowsInSector(r.sector, code) : []
  return delay({
    priceDate: BASE_DATE,
    items: list.slice(0, 5).map((p) => ({
      code: p.code,
      name: p.name,
      prevClose: p.prevClose,
      /* 시가총액은 §9.2 의 "두지 않는 것" 이라 화면이 그리지 않는다.
         서버도 상장주식수 미수집으로 항상 null 이다 */
      marketCap: null,
      per: p.per,
      pbr: p.pbr,
    })),
  }, 430)
}
