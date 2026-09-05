/* B-03 종목 상세 도메인. 설계서 §3 B · §4 B-03 · §9.2.

   블록마다 원천이 다르다. 화면이 블록 단위로 로딩·실패를 독립 처리하므로
   여기서도 호출을 하나로 합치지 않는다 — 합치면 한 곳이 죽을 때 전부 빈다.

     GET /stocks/{code}              요약 헤더
     GET /stocks/{code}/prices       종가 시계열 (from·to)
     GET /stocks/{code}/sentiment    예측 심리
     GET /stocks/{code}/briefings    AI 브리핑
     GET /stocks/{code}/documents    공시·뉴스 요약 + 원문 링크
     GET /stocks/{code}/points       투자 포인트
     GET /stocks/{code}/profile      기업 개요
     GET /stocks/{code}/financials   재무
     GET /stocks/{code}/valuation    밸류에이션
     GET /stocks/{code}/peers        경쟁사

   ── 백엔드가 붙으면 지울 것 ────────────────────────────────
   MOCK 을 false 로 바꾸면 전부 실제 호출로 넘어간다. 그다음 api/mock/stockDetail.ts
   와 각 함수의 `if (MOCK)` 한 줄씩만 지우면 흔적이 없다.

   화면을 세우는 동안 서버가 상당히 따라왔다. dev 기준 이미 있는 것:

     GET /stocks/{code}/prices      StockController
     GET /stocks/{code}/profile     CorpInfoController
     GET /stocks/{code}/financials  CorpInfoController
     GET /stocks/{code}/valuation   CorpInfoController
     GET /stocks/{code}/peers       CorpInfoController
     GET /stocks/{code}/documents   ResearchDocumentController
     GET /stocks/{code}/points      ResearchPointController

   아직 없는 것 — 이 셋 때문에 화면이 아직 통째로 목업이다:

     GET /stocks/{code}            단건 조회가 없다. 헤더(종목명·전일 종가·등락률)를
                                   채울 길이 목록뿐인데, 목록은 커서 페이징이라
                                   코드 하나를 집어낼 수 없다
     GET /stocks/{code}/sentiment  예측 집계 — predictions 표가 아직 없다
     GET /stocks/{code}/briefings  BriefingController 에 GET /briefings 와
                                   /briefings/{id} 는 있으나 종목별 목록이 없다

   연결은 헤더부터다. 헤더가 목업인 채로 /prices 만 실제로 바꾸면 전일 종가와
   차트 끝점이 어긋난다 — 서로 다른 원천에서 오기 때문이다.
   ─────────────────────────────────────────────────────── */
import { api } from './client'
import * as mock from './mock/stockDetail'
import type { ClosePoint } from '../components/CloseChart'
import type { Market } from './stocks'

const MOCK = true

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

/** 예측 심리. 표본이 적으면 화면이 흐리게 그린다(§7 SentimentBadge). */
export type StockSentiment = {
  upRatio: number | null
  downRatio: number | null
  /** 판정 대기 중인 예측 수 */
  sampleSize: number
  /** 기간별 쏠림. horizon 은 C-01 과 같은 5·10·20·60 고정이다 */
  byHorizon: { horizon: number; upRatio: number | null; sampleSize: number }[]
}

export type Briefing = {
  id: string
  title: string
  summary: string
  /** 배치가 매긴 논조 */
  tone: 'UP' | 'DOWN' | 'NEUTRAL'
  /** 배치 산출 시각 — SnapshotStamp 로 병기한다(§7) */
  computedAt: string
}

/** 문서 출처. 서버 ResearchDocument.Source 와 짝이다. */
export const DOCUMENT_SOURCES = ['NEWS', 'DART', 'IR'] as const
export type DocumentSource = (typeof DOCUMENT_SOURCES)[number]

export const SOURCE_LABEL: Record<DocumentSource, string> = {
  NEWS: '뉴스',
  DART: '공시',
  IR: 'IR',
}

/** 공시·뉴스. 원문 본문을 저장하지 않으므로 요약과 링크만 온다(§4 B-03). */
export type StockDocument = {
  id: number
  source: DocumentSource
  title: string
  /* 수집은 됐는데 요약 배치가 아직 안 돈 건은 null 이다. 그때는 제목만 보여준다 —
     요약을 기다리느라 감추면 방금 난 기사가 가장 늦게 뜬다(서버 DTO 주석). */
  summary: string | null
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

export type PointGroups = Record<PointKind, InvestPoint[]>

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

export function getSummary(code: string) {
  if (MOCK) return mock.summary(code)
  return api.get<StockSummary>(`/stocks/${code}`)
}

/** 구간을 주지 않으면 서버가 최근 30일을 준다. 화면은 기간 버튼으로 from 을 계산한다. */
export function getPrices(code: string, from?: string, to?: string) {
  if (MOCK) return mock.prices(code, from)
  return api.get<{ items: ClosePoint[] }>(`/stocks/${code}/prices`, { query: { from, to } })
}

export function getSentiment(code: string) {
  if (MOCK) return mock.sentiment(code)
  return api.get<StockSentiment>(`/stocks/${code}/sentiment`)
}

export function getBriefings(code: string) {
  if (MOCK) return mock.briefings(code)
  return api.get<{ items: Briefing[] }>(`/stocks/${code}/briefings`)
}

/* 최신순 고정이라 커서는 id 하나다. 지금 화면은 첫 페이지만 쓰지만
   응답 계약은 커서 목록이므로 타입을 줄이지 않는다. */
export function getDocuments(code: string) {
  if (MOCK) return mock.documents(code)
  return api.get<{ items: StockDocument[]; nextCursor: number | null; hasNext: boolean }>(
    `/stocks/${code}/documents`,
  )
}

export function getPoints(code: string) {
  if (MOCK) return mock.points(code)
  return api.get<PointGroups>(`/stocks/${code}/points`)
}

export function getProfile(code: string) {
  if (MOCK) return mock.profile(code)
  return api.get<CompanyProfile>(`/stocks/${code}/profile`)
}

export function getFinancials(code: string) {
  if (MOCK) return mock.financials(code)
  return api.get<{ items: FinancialRow[] }>(`/stocks/${code}/financials`)
}

export function getValuation(code: string) {
  if (MOCK) return mock.valuation(code)
  return api.get<Valuation>(`/stocks/${code}/valuation`)
}

export function getPeers(code: string) {
  if (MOCK) return mock.peers(code)
  return api.get<{ items: Peer[] }>(`/stocks/${code}/peers`)
}
