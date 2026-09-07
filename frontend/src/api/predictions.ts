/* C-01 예측 등록 도메인. 설계서 §3 C · §4 C-01 · §5 · §9.2.

     GET  /predictions/slots         이번 주 슬롯 잔여
     POST /predictions               등록 — 201 슬롯 내 / 202 슬롯 초과(소각)
     GET  /predictions/me            내 예측 (C-02) — 붙었다
     GET  /predictions/{id}          예측 상세 (C-03)
     GET  /stocks/{code}/predictions  이 종목에 걸린 남의 예측 (B-03 예측 탭)

   ── 붙은 것 ────────────────────────────────────────────────
   GET /predictions/me (ANT-PRED-06). 요청한 대로 stockName 이 항목에 들어왔고,
   집계 네 값은 status 필터와 무관한 전량 기준이며 hitRate 는 판정 건이 없으면
   null 이다. hitRate 는 **0~1 비율** 로 온다 — 퍼센트가 아니다.

   ⚠ **status 어휘가 ALL·PENDING·HIT·MISS 다 — JUDGED 가 없다.** 그래서 화면의
   거르개를 "판정 완료" 한 칸이 아니라 적중·빗나감 두 칸으로 갈랐다. HIT·MISS 를
   한 번에 받으려면 두 번 불러 합쳐야 하는데, 커서 페이징에서는 페이지마다 줄
   수가 들쭉날쭉해져 못 한다. JUDGED 가 생기면 두 칸을 한 칸으로 되돌린다.

   ── 백엔드가 붙으면 지울 것 ────────────────────────────────
   MOCK 을 false 로 바꾸면 남은 것도 실제 호출로 넘어간다. 아래는 아직 없다:

     GET  /predictions/slots   { weeklyLimit, used, remaining, resetsAt }
     POST /predictions         { stockCode, direction, targetPrice, horizon,
                                 note?, evidencePointIds[], signature }
                               201 { predictionId, status:'BASE', commitHash }
                               202 { operationId }              슬롯 초과 → M-02
                               401 서명 주소 불일치 · 409 잔액 부족

     GET /predictions/me?status=JUDGED
       HIT·MISS 를 묶는 어휘 한 개. 지금 어휘로는 "판정 완료" 를 한 번에 받을 수
       없어 거르개를 적중·빗나감 두 칸으로 갈라 두었다. 집계에 judgedCount 가
       이미 있으니 목록 쪽 어휘만 맞으면 화면이 한 칸으로 돌아간다.

     GET /stocks/{code}/predictions?phase=&cursor=&size=
       종목별 예측 목록이 명세에 아직 없다. 있는 것은 /predictions/me 와
       /channels/{userId}/predictions 뿐이라 "이 종목에 누가 무엇을 걸었나" 를
       모을 수가 없다. 응답은 §5 게이팅을 따라야 한다:
         · 판정 완료(HIT/MISS) — direction·targetPrice·결과까지 전체 공개
         · 미판정(BASE/OPEN)   — 작성자·구독자가 아니면 locked:true 로 내리고
                                 targetPrice 만 null 로 뺀다. 작성자·적중률·방향·
                                 기간은 공개한다(존재를 감추면 구독 유인이 사라진다).
                                 이 조합은 서버 PredictionCardResponse 가 이미
                                 쓰고 있는 규칙이다 — Jira S15P21A507-70 참고.

       phase=PENDING(BASE·OPEN) / JUDGED(HIT·MISS) 로 걸러야 한다. 화면이 두 묶음을
       나눠 보여주는데, 섞인 목록을 클라이언트에서 거르면 페이지마다 개수가
       들쭉날쭉해진다(커서 페이징이라 서버가 잘라 준 뒤에 걸러지기 때문).

       응답에 pendingCount·judgedCount·hitRate 를 함께 내려야 한다. 탭 옆의
       건수와 적중률은 목록 전체에 대한 값이라, 불러온 페이지로 세면 "더 보기" 를
       누를 때마다 숫자가 바뀐다.
   ─────────────────────────────────────────────────────── */
import { api } from './client'
import * as mock from './mock/predictions'
import type { Anchor } from './anchors'
import type { ClosePrice, CursorList, OperationRef } from './types'

const MOCK = true

/** UP/DOWN 둘뿐이다. "보합" 을 두지 않는다(§4 C-01). */
export const DIRECTIONS = ['UP', 'DOWN'] as const
export type Direction = (typeof DIRECTIONS)[number]

/** 5·10·20·60 4지 고정. 임의 마감일을 받지 않는다(§4 C-01). */
export const HORIZONS = [5, 10, 20, 60] as const
export type Horizon = (typeof HORIZONS)[number]

export const HORIZON_LABEL: Record<Horizon, string> = {
  5: '5거래일',
  10: '10거래일',
  20: '20거래일',
  60: '60거래일',
}

/** 근거 본문 상한. 구독자 전용이며 만기 리빌 후에도 공개되지 않는다. */
export const NOTE_MAX = 5000

export type SlotStatus = {
  weeklyLimit: number
  used: number
  remaining: number
  /** 슬롯이 다시 차는 시각 */
  resetsAt: string
}

export type PredictionDraft = {
  stockCode: string
  direction: Direction
  targetPrice: number
  horizon: Horizon
  note: string
  /** B-03 투자 포인트에서 인계받은 id. 리포트·재무지표는 근거가 될 수 없다 */
  evidencePointIds: number[]
}

/** 201 응답. basePrice 는 배치 B2 가 다음 영업일 종가로 채운다 — 비어 있다. */
export type PredictionCreated = {
  predictionId: string
  status: 'BASE'
  commitHash: string
}

/** 슬롯을 넘겨 소각으로 넘어간 경우. M-02 가 operationId 를 폴링한다. */
export type PredictionQueued = OperationRef

export type CreateResult =
  | { kind: 'created'; data: PredictionCreated }
  | { kind: 'queued'; data: PredictionQueued }

export function getSlots() {
  if (MOCK) return mock.slots()
  return api.get<SlotStatus>('/predictions/slots')
}

/* ── 이 종목에 걸린 남의 예측 ─────────────────────────────
   §5 게이팅이 타입에 드러나야 한다. 잠긴 예측의 direction·targetPrice 를
   null 로 두면, 화면이 실수로 잠긴 값을 그리려 해도 타입에서 먼저 막힌다.
   404 로 감추지 않는 것이 규칙이다 — 없는 것처럼 보이면 구독 유인이 사라진다. */

/** 상태 전이는 BASE → OPEN → HIT/MISS 다(서버 Prediction.Status). */
export const PREDICTION_STATUSES = ['BASE', 'OPEN', 'HIT', 'MISS'] as const
export type PredictionStatus = (typeof PREDICTION_STATUSES)[number]

/* 목록을 가르는 두 묶음. 잠금 경계와 정확히 같은 자리에서 갈린다 —
   PENDING 은 구독자만 볼 수 있고 JUDGED 는 전체 공개다(§5). */
export const PREDICTION_PHASES = ['PENDING', 'JUDGED'] as const
export type PredictionPhase = (typeof PREDICTION_PHASES)[number]

export const PHASE_LABEL: Record<PredictionPhase, string> = {
  PENDING: '판정 대기',
  JUDGED: '판정 완료',
}

export const phaseOf = (s: PredictionStatus): PredictionPhase =>
  (s === 'HIT' || s === 'MISS' ? 'JUDGED' : 'PENDING')

export type StockPrediction = {
  id: string
  author: { userId: string; nickname: string }
  /** 작성자 적중률 %. 판정 이력이 없으면 null — 0% 로 그리지 않는다 */
  accuracy: number | null
  status: PredictionStatus
  horizon: Horizon
  createdAt: string
  /** 만기 영업일 YYYY-MM-DD */
  dueDate: string
  /** 미판정을 볼 권한이 없으면 true. targetPrice 가 비어 온다 */
  locked: boolean
  /* 방향은 잠긴 예측에서도 온다. 서버 PredictionCardResponse 가 정한 규칙으로
     (Jira S15P21A507-70), 잠글 때 종목·방향까지는 남기고 targetPrice 만 null 로
     뺀다. 방향까지 가리면 "누가 무엇을 걸었는지" 가 통째로 사라져 목록이 빈다. */
  direction: Direction
  /** 잠기면 null. 판정 완료는 항상 채워진다 */
  targetPrice: number | null
  /** 배치 B2 가 확정한 기준가. 등록 직후에는 비어 있다 */
  basePrice: number | null
  /** 판정 완료만 채워진다 */
  closePrice: number | null
  /** 목표가 대비 오차 %. 판정 완료만 */
  errorRate: number | null
  /** 잠금을 푸는 채널. 구독 CTA 가 여기로 간다 */
  channelId: string | null
}

/* ── 내 예측 (C-02) ───────────────────────────────────────
   서버 MyPredictionItemResponse 와 짝을 이룬다(ANT-PRED-06). 명세가 못 박은
   아홉 필드에 stockName 이 더해진 열 개이고, **그 밖의 값을 화면에서 만들어
   내지 않는다.**

   잠금이 없다. 내 예측이라 전부 보인다 — 남의 예측(StockPrediction)과 필드부터
   다르므로 타입을 합치지 않았다. */
export type MyPrediction = {
  /** 서버 PK(long). 커서도 이 값이라 문자열로 바꾸지 않는다 */
  id: number
  /* 종목이 지워진 예측이 있을 수 있어 서버가 둘 다 null 로 내린다
     (MyPredictionItemResponse: stock == null ? null : ...). 화면은 이름이
     없으면 코드로, 코드까지 없으면 그 자리를 비운다 — "null" 을 그리지 않는다. */
  stockCode: string | null
  stockName: string | null
  direction: Direction
  targetPrice: number
  horizon: Horizon
  status: PredictionStatus
  /** 만기까지 남은 일수. 판정이 끝난 건은 null — 0 으로 그리지 않는다 */
  dday: number | null
  /** 목표가 대비 오차 %. 판정 완료만 채워진다 */
  errorRate: number | null
  /** 만기 영업일 YYYY-MM-DD. 기준가 배치가 채우기 전에는 null 이다 */
  settleDate: string | null
}

/* 상태 필터. 서버가 status 로 거른다 — 커서 페이징이라 클라이언트에서 거르면
   페이지마다 줄 수가 들쭉날쭉해진다(B-02 · B-03 과 같은 이유).

   서버 MyPredictionQueryService.StatusFilter 와 1:1 이다. **JUDGED 가 없어서**
   판정 완료를 적중·빗나감 두 칸으로 갈랐다 — 어휘 밖 값은 서버가 400 으로
   거절하므로, 여기서 없는 값을 만들어내면 화면이 눌리는 즉시 깨진다.
   PENDING 은 BASE·OPEN 을 묶는 조회용 어휘이고 저장된 상태가 아니다. */
export const MY_FILTERS = ['ALL', 'PENDING', 'HIT', 'MISS'] as const
export type MyFilter = (typeof MY_FILTERS)[number]

export const MY_FILTER_LABEL: Record<MyFilter, string> = {
  ALL: '전체',
  PENDING: '판정 대기',
  HIT: '적중',
  MISS: '빗나감',
}

/** 목록 전체에 걸리는 값. 필터를 바꿔도 네 숫자는 그대로여야 한다 */
export type MyPredictionMeta = {
  total: number
  pendingCount: number
  judgedCount: number
  /** 판정 완료 중 적중 **비율(0~1)**. 판정 건이 없으면 null */
  hitRate: number | null
}

/* 서버가 hit/judged 를 소수 넷째 자리까지 내린다(0.6 · 0.6667). 퍼센트로
   바꾸는 자리를 여기 하나로 둔다 — 화면마다 100 을 곱하면 한 곳에서 빼먹는
   순간 "0.6%" 가 그려지고, 그게 틀렸다는 걸 아무도 눈치채지 못한다. */
export const hitRatePercent = (ratio: number) => Math.round(ratio * 1000) / 10

export type MyPredictionList = CursorList<MyPrediction> & MyPredictionMeta

export const MY_PREDICTION_PAGE_SIZE = 12

/** useCursorList 가 커서를 관리하므로 함수를 넘긴다 */
export function fetchMyPredictions(filter: MyFilter) {
  return (query: Record<string, string | number | boolean | undefined>) => {
    /* ALL 은 파라미터를 아예 붙이지 않는다 — 서버가 "전체" 를 기본으로 본다 */
    const q = {
      ...(filter === 'ALL' ? {} : { status: filter }),
      size: MY_PREDICTION_PAGE_SIZE,
      ...query,
    }
    return api.get<MyPredictionList>('/predictions/me', { query: q })
  }
}

/* ── 예측 상세 (C-03) ─────────────────────────────────────
   잠금이 두 겹이다(§5 게이팅 표).

     ① 예측 전체   미판정(BASE/OPEN) + 작성자·구독자 아님 → locked
     ② 근거 본문   미구독 → noteLocked. **만기 리빌 후에도 풀리지 않는다**
                    (payload 에 noteHash 만 들어가므로 애초에 공개 대상이 아니다)

   무엇을 잠그지 않는가 — commitHash · 앵커 · 서명 주소는 **언제나 공개**다.
   이 셋이 "조작하지 않았다" 를 스스로 증명하는 재료이고, 잠그면 이 화면의
   존재 이유가 사라진다. 그래서 locked 여도 이 값들은 내려온다.

   404 로 감추지 않는다. 존재 자체는 공개이며 잠금 카드와 구독 CTA 로 그린다. */
export type PredictionDetail = {
  id: string
  stockCode: string
  /** C-02 와 같은 사정으로 서버에 아직 없다. 비면 종목코드로 대체한다 */
  stockName: string | null
  author: { userId: string; nickname: string }
  status: PredictionStatus
  horizon: Horizon
  createdAt: string
  /** 만기 영업일 */
  settleDate: string
  /** 만기까지 남은 일수. 판정이 끝났거나 기준가 확정 전이면 null */
  dday: number | null

  /* ── 잠금 대상 ────────────────────────────────────────
     locked 면 아래 넷이 비어 온다. 0 으로 그리지 않는다. */
  locked: boolean
  direction: Direction | null
  targetPrice: number | null
  /** 배치 B2 가 다음 영업일 종가로 확정한다. 등록 직후(BASE)는 비어 있다 */
  basePrice: number | null
  /** 만기 종가. 판정 완료만 채워진다 */
  settlePrice: number | null
  /** 목표가 대비 오차 %. 판정 완료만 */
  errorRate: number | null

  /* 진행률을 그리는 재료. 실전 시세는 전일 종가뿐이라 그 값으로 어디까지 왔는지
     보여준다 — "현재가" 를 만들지 않으려고 기준일을 함께 받는다(§7 legal). */
  lastClose: ClosePrice | null

  /* ── 근거 ─────────────────────────────────────────────
     본문은 구독자 전용이다. 잠기면 note 가 null 이고 미리보기도 주지 않는다 —
     리포트(§5)와 달리 예측 근거는 3줄 미리보기 규칙이 없다. */
  noteLocked: boolean
  note: string | null
  /** C-01 에서 인계받아 커밋에 묶인 근거. 등록 후 바뀌지 않는다 */
  evidencePoints: { id: number; body: string }[]

  /* ── 항상 공개 ────────────────────────────────────────
     잠금 여부와 무관하게 내려온다(§4 C-03). */
  commitHash: string
  /** 서명한 지갑 주소 */
  signerAddress: string
  /** 이 커밋이 묶인 앵커 배치. 아직 안 묶였으면 null */
  anchor: Anchor | null

  /** 잠금을 푸는 채널. 구독 CTA 가 여기로 간다 */
  channelId: string | null
}

export function getPredictionDetail(id: string) {
  if (MOCK) return mock.predictionDetail(id)
  return api.get<PredictionDetail>(`/predictions/${id}`)
}

/**
 * 목표까지 얼마나 왔는가. 기준가에서 목표가까지를 100 으로 본다.
 *
 * 서버가 주는 값이 아니라 기준가·목표가·전일 종가로 여기서 계산한다 — 명세가
 * 진행률을 화면 구성으로 못 박았고(§4 C-03), 셋이 다 응답에 있으므로 없는 값을
 * 만들어내는 것이 아니다. 셋 중 하나라도 비면 그릴 수 없어 null 이다.
 *
 * 100 을 넘길 수 있다 — 목표를 지나쳤다는 뜻이라 자르지 않는다. 음수도 그렇다.
 */
export function targetProgress(d: PredictionDetail): number | null {
  const { basePrice, targetPrice, lastClose } = d
  if (basePrice === null || targetPrice === null || lastClose === null) return null
  const span = targetPrice - basePrice
  /* 기준가와 목표가가 같으면 나눌 수 없다. 등록 자체가 막히지만 계약상 가능하다 */
  if (span === 0) return null
  return Math.round(((lastClose.close - basePrice) / span) * 1000) / 10
}

/* 목록 전체에 한 번만 해당하는 값. useCursorList 의 meta 로 온다.
   불러온 페이지로 세면 "더 보기" 를 누를 때마다 숫자가 바뀌므로 서버가 준다. */
export type StockPredictionMeta = {
  pendingCount: number
  judgedCount: number
  /** 판정 완료 중 적중 비율 %. 판정 건이 없으면 null — 0% 로 그리지 않는다 */
  hitRate: number | null
}

export type StockPredictionList = CursorList<StockPrediction> & StockPredictionMeta

/** 한 화면에 담는 줄 수 */
export const STOCK_PREDICTION_PAGE_SIZE = 8

/** useCursorList 가 커서를 관리하므로 함수를 넘긴다 */
export function fetchStockPredictions(code: string, phase: PredictionPhase) {
  return (query: Record<string, string | number | boolean | undefined>) => {
    const q = { phase, size: STOCK_PREDICTION_PAGE_SIZE, ...query }
    if (MOCK) return mock.stockPredictions(code, q)
    return api.get<StockPredictionList>(`/stocks/${code}/predictions`, { query: q })
  }
}

/* 201 과 202 를 응답 본문 모양으로 가른다. api.post 는 상태 코드를 돌려주지 않는데,
   그것 하나 때문에 공용 client 를 고치면 다른 화면까지 영향이 간다. 두 응답은
   키가 겹치지 않아(operationId ↔ predictionId) 모양만으로 확실히 갈린다. */
export function createPrediction(draft: PredictionDraft, signature: string) {
  if (MOCK) return mock.create(draft)
  return api.post<PredictionCreated | PredictionQueued>('/predictions', { ...draft, signature })
    .then((body): CreateResult => ('operationId' in body
      ? { kind: 'queued', data: body }
      : { kind: 'created', data: body }))
}

/* ── 커밋 미리보기 ────────────────────────────────────────
   commitHash 는 서버 salt 와 결합해 만들어지므로 클라이언트가 계산할 수 없다.
   그래서 미리보기는 payload 와 noteHash 까지만 보여준다(§4 C-01). 여기서
   commitHash 를 흉내내면 화면에 뜬 값과 원장에 남는 값이 달라진다. */

/** 등록 본문에서 해시 대상이 되는 부분. 서버 조립 순서를 그대로 따른다. */
export function commitPayload(draft: PredictionDraft) {
  return [
    'antenna:prediction:v1',
    `stockCode=${draft.stockCode}`,
    `direction=${draft.direction}`,
    `targetPrice=${draft.targetPrice}`,
    `horizon=${draft.horizon}`,
    /* 숫자 id 라 사전순이 아니라 값 순으로 세운다 — 기본 sort() 는 문자열 비교라
       10 이 2 보다 앞서고, 그러면 같은 근거를 고르고도 payload 가 달라진다 */
    `evidencePointIds=${[...draft.evidencePointIds].sort((a, b) => a - b).join(',')}`,
  ].join('\n')
}

/** 근거 본문의 해시. 본문 자체는 payload 에 들어가지 않는다 — 구독자 전용이라서다. */
export async function noteHash(note: string) {
  const bytes = new TextEncoder().encode(note)
  const digest = await crypto.subtle.digest('SHA-256', bytes)
  return `0x${[...new Uint8Array(digest)].map((b) => b.toString(16).padStart(2, '0')).join('')}`
}
