/* AI 브리핑 — M-10 [ANT-FE-BRIEFING] · 설계서 §4 M-10 · §7
   서버 스토리 ANT-RESEARCH-03 (BriefingController · BriefingService)

   왜 파일을 따로 두는가
   브리핑을 읽는 곳이 셋이다 — B-01 홈 띠 · B-03 종목 상세 · M-10 상세 모달.
   그런데 타입이 두 벌로 갈라져 있었다. api/insight.ts 는 서버와 같은 모양이었고
   api/stockDetail.ts 는 { title, summary, tone, computedAt } 이라는 **서버에 없는**
   모양이었다. 파일이 다르면 tsc 가 겹침을 잡지 못한다(Proof 때 같은 일을 겪었다).
   그래서 계약을 여기 한 곳에 두고, 두 화면은 여기서 가져다 쓴다.

   서버 응답 (2026-09-08 로컬 dev 에서 직접 확인)
     GET /briefings?scope=&stockCode=&date=
       200 { items: [{ id, scope, stockCode, headline, targetDate }] }
       400 STOCK_NOT_FOUND  없는 stockCode
     GET /briefings/{id}
       200 { id, headline, body, targetDate }
       404 BRIEFING_NOT_FOUND

   목록이 주는 것은 헤드라인뿐이다
   BriefingItemResponse 주석이 못 박고 있다 — "본문은 상세에서만. 카드에는 헤드라인
   한 줄이 실린다." M-10 이 따로 있는 이유가 이것이다. 카드에서 본문을 보여줄 수
   없으니 모달이 필요하다.

   목록은 하루치다
   BriefingService.list 는 date 가 없으면 **필터 안에서 가장 최신 영업일 하루**만
   돌려준다. 페이징이 없는 것도 그래서다 — 한 날짜에 MARKET 1건 + 종목당 1건이라는
   전제다. 화면이 "지난 브리핑 더 보기" 를 만들려면 date 를 직접 넘겨야 한다.

   ── 백엔드가 붙으면 지울 것 ────────────────────────────────
   엔드포인트는 **이미 살아 있다.** 막힌 건 데이터다 — 생성 배치 B6 가 아직 없고
   AI 키가 없어 ai_briefings 가 0행이라, 실제로 부르면 항상 { items: [] } 다.
   그래서 화면을 세우는 동안만 목업을 탄다. 행이 생기면 MOCK 을 false 로 바꾸고
   api/mock/briefings.ts 와 각 함수의 `if (MOCK)` 한 줄씩만 지우면 끝이다.
   목업은 위 응답 스키마를 글자 그대로 따르므로 화면 코드는 손대지 않는다.
   ─────────────────────────────────────────────────────── */
import { api } from './client'
import * as mock from './mock/briefings'

const MOCK = true

export const BRIEFING_SCOPES = ['MARKET', 'STOCK'] as const
export type BriefingScope = (typeof BRIEFING_SCOPES)[number]

/** 목록 항목. 카드가 쓰는 것은 headline 과 targetDate 뿐이다. */
export type BriefingItem = {
  id: number
  scope: BriefingScope
  /** MARKET 이면 null. 서버 CHECK 제약이 그렇게 강제한다 */
  stockCode: string | null
  headline: string
  /** 어느 영업일 기준인가 YYYY-MM-DD. 생성 시각과 다른 값이다 */
  targetDate: string
}

/** 상세. 목록에 없는 것은 body 하나이고, 그것 때문에 M-10 이 있다. */
export type BriefingDetail = {
  id: number
  headline: string
  /** 엔티티에서 nullable 이다(@Column 에 nullable=false 가 없다). 빈 본문을 만들지 않는다 */
  body: string | null
  targetDate: string
}

type Query = {
  scope?: BriefingScope
  /** STOCK 스코프를 좁힌다. 없는 종목이면 400 STOCK_NOT_FOUND */
  stockCode?: string
  /** 없으면 최신 영업일 하루치 */
  date?: string
}

export function getBriefings(query: Query = {}) {
  if (MOCK) return mock.briefings(query)
  return api.get<{ items: BriefingItem[] }>('/briefings', { query })
}

/** B-03 종목 상세용. 서버에 /stocks/{code}/briefings 는 없다 — 스코프로 좁힌다. */
export function getStockBriefings(stockCode: string) {
  return getBriefings({ scope: 'STOCK', stockCode })
}

export function getBriefing(id: number) {
  if (MOCK) return mock.briefing(id)
  return api.get<BriefingDetail>(`/briefings/${id}`)
}
