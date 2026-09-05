/* C-01 예측 등록 도메인. 설계서 §3 C · §4 C-01 · §5 · §9.2.

     GET  /predictions/slots         이번 주 슬롯 잔여
     POST /predictions               등록 — 201 슬롯 내 / 202 슬롯 초과(소각)
     GET  /stocks/{code}/predictions  이 종목에 걸린 남의 예측 (B-03 예측 탭)

   ── 백엔드가 붙으면 지울 것 ────────────────────────────────
   MOCK 을 false 로 바꾸면 실제 호출로 넘어간다. 서버에 predictions 표와
   엔드포인트가 아직 없다. 화면을 먼저 세우고 아래를 요청한다:

     GET  /predictions/slots   { weeklyLimit, used, remaining, resetsAt }
     POST /predictions         { stockCode, direction, targetPrice, horizon,
                                 note?, evidencePointIds[], signature }
                               201 { predictionId, status:'BASE', commitHash }
                               202 { operationId }              슬롯 초과 → M-02
                               401 서명 주소 불일치 · 409 잔액 부족

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
import type { CursorList, OperationRef } from './types'

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
  evidencePointIds: string[]
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
    `evidencePointIds=${[...draft.evidencePointIds].sort().join(',')}`,
  ].join('\n')
}

/** 근거 본문의 해시. 본문 자체는 payload 에 들어가지 않는다 — 구독자 전용이라서다. */
export async function noteHash(note: string) {
  const bytes = new TextEncoder().encode(note)
  const digest = await crypto.subtle.digest('SHA-256', bytes)
  return `0x${[...new Uint8Array(digest)].map((b) => b.toString(16).padStart(2, '0')).join('')}`
}
