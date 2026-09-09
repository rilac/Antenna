/* B-03 종목 상세가 읽는 조회 API.
   담당 스토리 [ANT-FE-STOCK-DETAIL] · 설계서 §4 B-03 · §9.2

   블록마다 원천이 다르다. 하나가 실패해도 나머지는 그대로 그린다.

     GET /stocks/{code}/prices       종가 시계열 (from·to)
     GET /stocks/{code}/profile      기업 개요
     GET /stocks/{code}/financials   재무
     GET /stocks/{code}/valuation    밸류에이션
     GET /stocks/{code}/peers        경쟁사
     GET /stocks/{code}/documents    공시·뉴스 요약 + 원문 링크
     GET /stocks/{code}/points       투자 포인트
     GET /briefings?scope=STOCK      AI 브리핑 — api/briefings.ts 가 맡는다

   **2026-09-08 목업을 걷었다.** 위 일곱은 전부 실제 호출이다.

   왜 이제야 걷었나 — 데이터가 없어서였지 코드가 없어서가 아니었다. DART 수집
   배치(개황·재무·공시)가 이 환경에서 한 번도 돌지 않아 응답이 200 인데 내용이
   비어 있었다. 개황은 매월 1일, 재무는 분기 첫날에만 도는 주기라 한 회차를
   놓치면 다음이 한 달·석 달 뒤다. 공시 배치(매일)에 붙은 fillIfEmpty() 를 한 번
   돌리자 개황 311종목 · 재무 916행 · 공시 597건이 들어왔다.

   ── 아직 서버가 없는 둘 ────────────────────────────────
   GET /stocks/{code}          요약 헤더. ANT-DATA-05 인데 **담당자가 없다.**
                               A-03 통합 검색을 막고 있는 바로 그 티켓이다.
                               getSummary 가 있는 것들로 헤더를 짜 맞춘다(아래).
   GET /stocks/{code}/sentiment 예측 심리. ANT-PRED-06 · 담당자 없음.
                               AI 와 무관하다 — predictions 의 UP/DOWN 집계이고
                               표에는 이미 행이 있다. 코드가 없을 뿐이다.

   **이 파일에 목업이 없다.** 없는 것은 화면이 "왜 없는지" 를 적는다 —
   지어낸 값을 실제 값 옆에 두면 어느 쪽이 참인지 알 수 없다.
   ─────────────────────────────────────────────────────── */
import { api } from './client'
import { getWatchlist } from './insight'
import type { ClosePoint } from '../components/CloseChart'
import type { Market } from './stocks'


export type { ClosePoint }

/** 요약 헤더. 목록 한 줄과 겹치지만 단건은 기준일(asOf)을 함께 준다. */
export type StockSummary = {
  code: string
  name: string
  sector: string | null
  market: Market | null
  /** 전일 종가. 거래정지였으면 null — 0 으로 그리지 않는다 */
  prevClose: number | null
  /** 종가가 기준하는 영업일 YYYY-MM-DD */
  asOf: string | null
  changeRate: number | null
  per: number | null
  pbr: number | null
  predictionCount: number
  watched: boolean
}

/**
 * 예측 심리. 서버가 아직 없어(GET /stocks/{code}/sentiment · ANT-PRED-06) 화면이
 * 이 자리에 "아직 제공되지 않는다" 만 적는다. **목업으로 그리지 않는다** — 같은
 * 카드의 브리핑·재무가 실제 값이라 지어낸 비율이 참으로 읽힌다.
 *
 * 타입은 남긴다. 서버가 붙는 날 화면이 이 모양을 그대로 받으면 된다.
 */
export type StockSentiment = {
  upRatio: number | null
  downRatio: number | null
  /** 판정 대기 중인 예측 수 */
  sampleSize: number
  /** 기간별 쏠림. horizon 은 C-01 과 같은 5·10·20·60 고정이다 */
  byHorizon: { horizon: number; upRatio: number | null; sampleSize: number }[]
}

/** 문서 출처. 서버 ResearchDocument.Source 와 짝이다. */
export const DOCUMENT_SOURCES = ['NEWS', 'DART', 'IR'] as const
export type DocumentSource = (typeof DOCUMENT_SOURCES)[number]

export const SOURCE_LABEL: Record<DocumentSource, string> = {
  NEWS: '뉴스',
  DART: '공시',
  IR: 'IR',
}

/** 공시·뉴스. 원문 본문을 저장하지 않으므로 발췌와 링크만 온다(§4 B-03). */
export type StockDocument = {
  id: number
  source: DocumentSource
  title: string
  /* 네이버 검색 API 발췌 그대로. 공시는 발췌가 없어 null — 보고서명이 곧 내용이다.
     AI 요약은 2026-09-09 에 없앴다(서버 DTO 주석). */
  snippet: string | null
  originUrl: string
  publishedAt: string
}

/* 투자 포인트는 세 열이다. 서버가 열마다 배열을 따로 내려주므로 화면이 kind 로
   다시 나누지 않고, 한 열이 비어도 나머지 두 열은 그대로 그린다. */
export const POINT_KINDS = ['positive', 'risk', 'check'] as const
export type PointKind = (typeof POINT_KINDS)[number]

export const POINT_LABEL: Record<PointKind, string> = {
  positive: '긍정 요인',
  risk: '위험 요인',
  check: '확인할 점',
}

/**
 * 포인트 한 장. 여기서 고른 id 가 C-01 의 evidencePointIds 로 넘어간다.
 *
 * 제목이 따로 없고 body 한 덩어리다. documentId 가 있으면 그 문서가 근거이고,
 * 없으면 시세·재무 수치에서 나온 종합 포인트다.
 */
export type InvestPoint = {
  id: number
  body: string
  documentId: number | null
  source: DocumentSource | null
}

/* 서버가 없으면 그 자리에서 만든다(2026-09-09 요청 시점 생성). 첫 요청은 2~4초 걸리고,
   실패하면 503 POINT_GENERATION_FAILED — 다시 부르면 다시 만든다. 같은 종목·같은 거래일은
   한 번만 만들어 두 번째부터는 바로 온다. targetDate 는 어느 거래일 기준인지 — 시세·공시·
   뉴스가 들어오는 시각이 제각각이라 화면이 "9/8 종가 기준" 처럼 적어 줘야 한다. 없으면 null */
export type PointGroups = Record<PointKind, InvestPoint[]> & { targetDate: string | null }

/**
 * 기업 개요.
 *
 * industry 는 DART 업종코드가 아니라 stocks.sector(KRX 분류)다 — 탐색 화면의 섹터 칩과
 * 경쟁사 비교가 같은 분류를 쓰므로 여기만 다른 체계를 쓰면 화면끼리 어긋난다.
 * listedAt 은 원천이 없어 항상 null 이다(DART 기업개황에 상장일이 없다). 명세 키를
 * 지키려고 남아 있을 뿐이라 화면에 자리를 만들지 않는다.
 */
export type CompanyProfile = {
  corpName: string
  ceo: string | null
  establishedAt: string | null
  listedAt: string | null
  homepage: string | null
  address: string | null
  industry: string | null
}

/**
 * 재무 한 기수. **금액 단위는 원이다**(억원이 아니다).
 *
 * fsDiv 는 연결(CFS)·별도(OFS) 구분이다. 같은 회사도 둘이 배로 차이 나고 연도마다
 * 어느 쪽이 수집됐는지 다를 수 있어, 행마다 싣고 화면이 배지로 밝힌다.
 * 계정이 보고서에 없으면 null 이다 — 0 은 "실적 0" 으로 읽힌다.
 */
export type FinancialRow = {
  year: number
  quarter: number
  fsDiv: string | null
  revenue: number | null
  operatingProfit: number | null
  netIncome: number | null
  assets: number | null
  liabilities: number | null
  equity: number | null
}

/* 업종 평균 PER/PBR · EPS · 배당수익률은 두지 않는다(§9.2).
   비율은 가장 최근 연간 재무 하나로만 계산한다 — 연도를 섞으면 basedOn.fiscal 이
   어느 숫자의 기준인지 말할 수 없다. */
export type Valuation = {
  per: number | null
  pbr: number | null
  roe: number | null
  debtRatio: number | null
  /** 어느 날 종가·어느 연도 재무로 계산했는지. note 는 값이 빈 이유를 적는다 */
  basedOn: {
    priceDate: string | null
    prevClose: number | null
    fiscal: number | null
    fsDiv: string | null
    note: string | null
  }
}

/* marketCap 은 응답에 있지만 그리지 않는다 — 시가총액은 §9.2 의 "두지 않는 것" 이다.
   키를 타입에서 빼면 서버 계약과 어긋나므로 남겨 두고 화면에서만 쓰지 않는다. */
export type Peer = {
  code: string
  name: string
  prevClose: number | null
  marketCap: number | null
  per: number | null
  pbr: number | null
}

/* ── 호출부 ─────────────────────────────────────────────── */

/**
 * 요약 헤더. **GET /stocks/{code} 가 없어서 있는 것들로 짜 맞춘다.**
 *
 * 값을 지어내지 않는다 — 서버가 세 곳에 흩어 놓은 같은 사실을 모을 뿐이다.
 *
 *   name · sector    ← /profile 의 corpName · industry
 *   prevClose · asOf ← /valuation 의 basedOn (그 블록이 계산 기준으로 이미 싣는다)
 *   changeRate       ← /prices 의 마지막 두 종가
 *
 * 목록에서 행을 들고 오지 않는 이유 — 주소로 바로 들어오는 길이 있다(딥링크·
 * 관심 종목·예측 상세에서 온 링크). 목록을 거친 경우에만 헤더가 뜨면 나머지
 * 경로에서 화면 위쪽이 빈다. 어느 길로 오든 같게 만든다.
 *
 * **이름이 목록과 다르게 나온다.** corpName 은 DART 법인명이라 목록의 KRX 종목명과
 * 어긋난다 — 목록에서 "NAVER" 를 눌렀는데 상세는 "네이버(주)" 다. 둘 다 맞는
 * 이름이지만 같은 흐름 안에서 갈리는 건 좋지 않다. KRX 이름(stocks.name)을 주는
 * 엔드포인트가 없어 지금은 어쩔 수 없고, 단건 조회가 생기면 함께 풀린다.
 * (DART 에 매칭이 안 된 우선주는 서버가 KRX 이름으로 되돌려 주므로 그쪽은 맞는다.)
 *
 * **market 은 어느 응답에도 없어 null 이다** — 헤더가 그 칩만 그리지 않는다.
 * per·pbr 도 서버가 항상 null 을 준다(상장주식수를 안 쓴다고 basedOn.note 에
 * 적어 둔다). predictionCount 는 원천이 예측 심리뿐인데 그게 목업이라 0 으로
 * 둔다 — 실제 값 옆에 목업 숫자를 배지로 붙이지 않는다.
 *
 * 셋 중 하나만 실패해도 헤더를 못 그리므로 Promise.all 로 함께 기다린다.
 * 없는 종목이면 셋 다 404 STOCK_NOT_FOUND 라 그대로 위로 올라간다.
 *
 * 단건 조회가 생기면 이 몸통을 api.get 한 줄로 되돌린다.
 */
export async function getSummary(code: string): Promise<StockSummary> {
  const [profile, valuation, prices, watchlist] = await Promise.all([
    getProfile(code),
    getValuation(code),
    /* 등락률에 필요한 건 마지막 두 점뿐이다. 구간을 안 주면 서버가 최근 20일을
       주는데, 차트 블록이 어차피 제 구간으로 다시 부르므로 여기서 넓히지 않는다. */
    getPrices(code),
    /* 별을 채울지 여기서 안다. 목록에서 온 경우에만 아는 값으로 두면 딥링크로
       들어왔을 때 이미 담은 종목이 빈 별로 보인다. */
    getWatchlist(),
  ])

  const items = prices.items
  const last = items[items.length - 1]
  const prev = items[items.length - 2]
  /* 점이 둘 미만이면 등락률이 없다. 0 으로 두면 "보합" 으로 읽힌다(B-04 와 같은 규칙) */
  const changeRate = last && prev && prev.close !== 0
    ? Math.round(((last.close - prev.close) / prev.close) * 10000) / 100
    : null

  return {
    code,
    name: profile.corpName,
    sector: profile.industry,
    market: null,
    prevClose: valuation.basedOn.prevClose,
    asOf: valuation.basedOn.priceDate,
    changeRate,
    per: valuation.per,
    pbr: valuation.pbr,
    predictionCount: 0,
    watched: watchlist.items.some((w) => w.stockCode === code),
  }
}

/** 구간을 주지 않으면 서버가 최근 30일을 준다. 화면은 기간 버튼으로 from 을 계산한다. */
export function getPrices(code: string, from?: string, to?: string) {
  return api.get<{ items: ClosePoint[] }>(`/stocks/${code}/prices`, { query: { from, to } })
}

/* 최신순 고정이라 커서는 id 하나다. 지금 화면은 첫 페이지만 쓰지만
   응답 계약은 커서 목록이므로 타입을 줄이지 않는다. */
export function getDocuments(code: string) {
  return api.get<{ items: StockDocument[]; nextCursor: number | null; hasNext: boolean }>(
    `/stocks/${code}/documents`,
  )
}

export function getPoints(code: string) {
  return api.get<PointGroups>(`/stocks/${code}/points`)
}

export function getProfile(code: string) {
  return api.get<CompanyProfile>(`/stocks/${code}/profile`)
}

export function getFinancials(code: string) {
  return api.get<{ items: FinancialRow[] }>(`/stocks/${code}/financials`)
}

export function getValuation(code: string) {
  return api.get<Valuation>(`/stocks/${code}/valuation`)
}

export function getPeers(code: string) {
  return api.get<{ items: Peer[] }>(`/stocks/${code}/peers`)
}
