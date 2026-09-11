/* C-01 예측 등록 도메인. 설계서 §3 C · §4 C-01 · §5 · §9.2.

     GET  /predictions/slots         이번 주 슬롯 잔여
     POST /predictions               등록 — 201 슬롯 내 / 202 슬롯 초과(소각)
     GET  /predictions/me            내 예측 (C-02) — 붙었다
     GET  /predictions/{id}          예측 상세 (C-03)
     GET  /stocks/{code}/predictions  이 종목에 걸린 남의 예측 (B-03 예측 탭)
                                      **API 명세서에 이 항목 자체가 없다.** 프론트가
                                      부르는 경로인데 명세에 빠져 있어 함께 올렸다.

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
                                 note?, noteSalt, evidencePointIds[], signature }
                               201 { predictionId, status:'BASE', commitHash }
                               202 { operationId }              슬롯 초과 → M-02
                               401 서명 주소 불일치 · 409 잔액 부족

       noteSalt 와 서명 문자열 규격은 ANT-PRED-02 가 정했다 — 아래 "커밋 봉인" 절.
       scope 는 PREDICTION 이다(결정 B4). 서버 PredictionCreateRequest.scope() 와 같은
       값이라야 nonce 칸 sig:nonce:{userId}:prediction 이 맞는다 — PredictTab 참고.

     GET /predictions/me?status=JUDGED
       HIT·MISS 를 묶는 어휘 한 개. 지금 어휘로는 "판정 완료" 를 한 번에 받을 수
       없어 거르개를 적중·빗나감 두 칸으로 갈라 두었다. 집계에 judgedCount 가
       이미 있으니 목록 쪽 어휘만 맞으면 화면이 한 칸으로 돌아간다.

     GET /stocks/{code}/predictions?phase=&cursor=&size=
       종목별 예측 목록이 명세에 아직 없다. 있는 것은 /predictions/me 와
       /channels/{userId}/predictions 뿐이라 "이 종목에 누가 무엇을 걸었나" 를
       모을 수가 없다. 응답은 §5 게이팅을 따라야 한다:
         · 판정 완료(HIT/MISS) — direction·targetPrice·결과까지 전체 공개
         · 미판정(BASE/OPEN)   — 근거만 잠근다. locked:true 로 내리고
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
import { releaseIdempotencyKey } from './idempotency'
import * as mock from './mock/predictions'
import { keccak256Utf8 } from '../chain/keccak'
import type { Anchor } from './anchors'
import type { ProofAnchorStatus } from './proof'
import type { ClosePrice, CursorList, OperationRef } from './types'
import type { WalletNonce } from './wallet'

/**
 * 남은 목업은 **서버에 그 API 가 없는 것들뿐**이다 — 예측 상세와 종목별 예측 목록.
 * 등록(POST /predictions)과 슬롯(GET /predictions/slots)은 ANT-PRED-01 이 들어와
 * 실제 호출로 넘어갔다. 위 "백엔드가 붙으면 지울 것" 목록의 남은 두 줄이 채워지면
 * 이 상수도 함께 사라진다.
 */
const MOCK = true

/** UP/DOWN 둘뿐이다. "보합" 을 두지 않는다(§4 C-01). */
export const DIRECTIONS = ['UP', 'DOWN'] as const
export type Direction = (typeof DIRECTIONS)[number]

/**
 * 7·14·30·90 4지 고정. 임의 마감일을 받지 않는다(§4 C-01).
 *
 * **거래일이 아니라 캘린더일이다**(결정 B5, 명세 v0.38). 만기일 = 기준일 + horizon 일로
 * 등록 즉시 확정된다 — 휴장일을 세지 않으므로 클라이언트가 그대로 계산할 수 있다.
 * DB CHECK(DB-01)·ERD·서버 HORIZONS 가 모두 이 네 값이라, 다른 값을 보내면
 * 400 INVALID_REQUEST(horizon) 이다.
 */
export const HORIZONS = [7, 14, 30, 90] as const
export type Horizon = (typeof HORIZONS)[number]

export const HORIZON_LABEL: Record<Horizon, string> = {
  7: '7일',
  14: '14일',
  30: '30일',
  90: '90일',
}

/** 근거 본문 상한. 구독자 전용이며 만기 리빌 후에도 공개되지 않는다. */
export const NOTE_MAX = 5000

/** 슬롯은 **하루** 단위다(서버 app.prediction.slot.free-per-day). 주 단위가 아니다. */
export type SlotStatus = {
  /** 이 셈이 걸린 날(KST). 자정에 넘어간다 */
  date: string
  freeLimit: number
  used: number
  remaining: number
  /**
   * 슬롯을 넘겨 등록할 때 소각할 금액(wei 문자열). 10²¹ 이 JS number 정밀도를 넘어
   * 문자열이다 — 숫자로 바꾸지 않는다. ANT-TOKEN-08 확정 전 잠정값이다.
   */
  overCost: string
}

export type PredictionDraft = {
  stockCode: string
  direction: Direction
  targetPrice: number
  horizon: Horizon
  note: string
  /**
   * 근거를 봉인하는 난수. 클라이언트가 만들어 본문에 함께 보낸다(ANT-PRED-02).
   * 소문자 64 hex, `0x` 없음 — {@link newNoteSalt} 참고.
   */
  noteSalt: string
  /** B-03 투자 포인트에서 인계받은 id. 리포트·재무지표는 근거가 될 수 없다 */
  evidencePointIds: number[]
}

/** 201 응답. basePrice 는 배치 B2 가 다음 영업일 종가로 채운다 — 비어 있다. */
export type PredictionCreated = {
  id: number
  commitHash: string
  /** 등록 직후에는 항상 WAITING. 배치 B2 가 묶으면 넘어간다 */
  anchorStatus: ProofAnchorStatus
  status: 'BASE'
  /** 등록 시점의 다음 평일. 기준가는 이 날 종가로 배치가 채운다 */
  baseDate: string
  /** baseDate + horizon 캘린더일 — 등록 즉시 확정된다 */
  settleDate: string
}

/**
 * 슬롯을 넘겨 소각으로 넘어간 경우. M-02 가 operationId 를 폴링한다.
 *
 * **서버가 아직 이 응답을 내지 않는다.** 소각할 토큰(ANT-CHAIN-03)이 없어 슬롯 초과는
 * 409 PREDICTION_SLOT_EXCEEDED 로 끝난다(명세 v0.41). 규격에는 남아 있어 타입도 남기되,
 * 지금 이 갈래로 오는 응답은 없다.
 */
export type PredictionQueued = OperationRef

export type CreateResult =
  | { kind: 'created'; data: PredictionCreated }
  | { kind: 'queued'; data: PredictionQueued }

export function getSlots() {
  return api.get<SlotStatus>('/predictions/slots')
}

/* ── 공개 규칙 (§5 개정, 2026-09-10) ──────────────────────
   판정 전후로 갈리고, 근거만 끝까지 잠긴다.

     필드          판정 전(BASE·OPEN)   판정 후(HIT·MISS)
     ─────────────────────────────────────────────────────
     어느 종목      전체 공개            전체 공개
     방향          구독자만             전체 공개
     목표가         구독자만             전체 공개
     근거 본문      구독자만             구독자만

   판정 후 방향을 가리지 않는 이유 — 목표가가 공개되면 기준가와 비교해 방향이
   그대로 드러난다. 가려도 가려지지 않으므로 잠그는 시늉만 남는다.

   근거가 판정 후에도 잠기는 것은 서버 결정 D6 과 같다(PredictionCommit 주석).

   타입에 null 을 그대로 적는다 — 화면이 잠긴 값을 그리려 하면 tsc 가 먼저 막는다.
   404 로 감추지 않는 것도 그대로다. 없는 것처럼 보이면 구독 유인이 사라진다. */

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

     ① 근거 본문   미구독 → noteLocked. **만기 리빌 후에도 풀리지 않는다**
                   (payload 에 noteHash 만 들어가므로 애초에 공개 대상이 아니다)
     ② 예측 자체   **잠그지 않는다**(2026-09-08 결정). 예측가·종목·방향·목표가·
                   기준가·만기는 누구에게나 보인다 — 목표가까지 가리면 "누가
                   무언가를 예측했다" 만 남아 예측가를 고를 수 없다.
                   서버 명세(§5 · /channels/{userId}/predictions)가 아직 옛 규칙이라
                   locked 필드는 남겨 두었다.
                    (payload 에 noteHash 만 들어가므로 애초에 공개 대상이 아니다)

   무엇을 잠그지 않는가 — commitHash · 앵커 · 서명 주소는 **언제나 공개**다.
   이 셋이 "조작하지 않았다" 를 스스로 증명하는 재료이고, 잠그면 이 화면의
   존재 이유가 사라진다. 그래서 locked 여도 이 값들은 내려온다.

   404 로 감추지 않는다. 존재 자체는 공개이며 잠금 카드와 구독 CTA 로 그린다. */
export type PredictionDetail = {
  id: string
  stockCode: string
  /**
   * **명세에 없다.** GET /predictions/{id} 응답 스키마가 stockCode 만 적고 있어
   * 서버가 붙어도 이름이 오지 않는다 — 제목이 "005930" 으로 뜬다. C-02 에서
   * 같은 요청을 해 받아냈으므로(위 "붙은 것") 여기도 넣어 달라고 올렸다.
   *
   * 그때까지 화면(C-03)이 이름이 비면 종목 개요에서 가져온다. 그건 목업을 메우는
   * 우회가 아니라 **이름이 없을 때의 폴백** 이다 — 종목이 지워진 건은 서버가
   * 붙어도 이름이 비고, 그때도 코드보다는 그 값이 낫다.
   */
  stockName: string | null
  author: { userId: string; nickname: string }
  status: PredictionStatus
  horizon: Horizon
  createdAt: string
  /** 만기 영업일 */
  settleDate: string
  /** 만기까지 남은 일수. 판정이 끝났거나 기준가 확정 전이면 null */
  dday: number | null

  /* ── 잠금 ────────────────────────────────────────────
     **아래 값들은 잠기지 않는다**(2026-09-08 결정). 예측가를 고를 근거가 되어야
     하므로 방향·목표가·기준가·만기는 누구에게나 온다. 구독으로 사는 것은
     판단의 이유(noteLocked 가 가리는 note)뿐이다.

     locked 는 남긴다 — 서버가 아직 옛 규칙(예측 전체 잠금)으로 답할 수 있고,
     그때는 화면이 잠금 카드로 그린다. 명세가 정리되면 지운다. */
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

/* ── 이 종목의 예측 분포 (호가창) ────────────────────────
   설계 변경 2026-09-10. 종목 상세는 **누가 걸었는지를 보여주지 않는다** —
   목표가를 구간으로 잘라 각 구간에 몇 명이 걸었는지만 낸다. 개인은 작성자
   채널에서만 본다.

   그래서 이 응답에는 예측 id·작성자·목표가 원본이 없다. 있으면 구간을 되짚어
   개인을 복원할 수 있고, 그건 이 화면을 만든 이유를 무너뜨린다.

   구간은 **전일 종가 대비 %** 로 자른다. 주식 호가창과 달리 상·하한이 없어
   절대가로는 자를 수 없고, 비율이면 1,000원 종목과 100만원 종목이 같은 개수의
   구간으로 나뉜다. 기준가는 전일 종가다 — 실전 시세는 그것뿐이다(§7 legal). */

/** 한 구간. 아래에서 위로(싼 가격 → 비싼 가격) 온다 */
export type PredictionBucket = {
  /** 전일 종가 대비 하한 %(포함). 맨 아래 구간은 null — "그 이하 전부" */
  fromPct: number | null
  /** 상한 %(미포함). 맨 위 구간은 null — "그 이상 전부" */
  toPct: number | null
  /** 구간 경계의 실제 가격. 화면이 다시 계산하지 않게 서버가 함께 준다 */
  fromPrice: number | null
  toPrice: number | null
  /** 이 구간에 걸린 예측 수 */
  count: number
}

export type PredictionDistribution = {
  stockCode: string
  /** 구간을 나눈 기준. 전일 종가다 — "현재가"가 아니다(§7 legal) */
  basePrice: number
  /** 그 종가의 날짜 */
  asOf: string
  /** 구간 폭 % */
  stepPct: number
  /** 아래(싼 쪽)에서 위(비싼 쪽) 순서 */
  buckets: PredictionBucket[]
  /** 구간 합계. 화면이 더해서 쓰지 않는다 — 서버가 센 값과 어긋날 수 있다 */
  total: number
}

/* ── 오늘 판정된 예측 (B-03 헤더) ─────────────────────────
   판정 배치 B2 가 13:30 에 돌면서 그날 만기가 온 예측을 HIT/MISS 로 확정한다.
   그 결과만 종목 머리에 띄운다 — "이 종목에 걸었던 사람들이 오늘 어떻게 됐나".

   판정 완료는 전체 공개다(§5 개정). 작성자·적중 여부·오차율에 잠금이 없어
   locked 같은 칸이 필요 없다. */
export type SettledPrediction = {
  id: string
  author: { userId: string; nickname: string; avatarUrl: string | null }
  /** 판정 후라 방향·목표가에 잠금이 없다(§5 개정) */
  direction: Direction
  targetPrice: number
  /** 판정된 것만 오므로 BASE·OPEN 은 없다 */
  status: 'HIT' | 'MISS'
  /** 목표가 대비 오차 %. 판정 완료라 항상 채워진다 */
  errorRate: number
}

/**
 * 오늘 판정된 이 종목의 예측.
 *
 * 없으면 빈 목록이다 — 화면은 그때 아무것도 그리지 않는다. 장이 쉬는 날이나
 * 배치 전에는 늘 비어 있으므로, 빈 상태를 "없습니다" 로 알릴 일이 아니다.
 */
export function getSettledToday(code: string) {
  if (MOCK) return mock.settledToday(code)
  return api.get<{ items: SettledPrediction[] }>(`/stocks/${code}/predictions/settled-today`)
}

/** 목업이 기준가를 지어내지 않도록 화면이 건네는 값. 서버가 붙으면 무시된다 */
export type DistributionBase = { basePrice: number | null; asOf: string | null }

/**
 * 판정 대기 중인 예측의 분포.
 *
 * 판정이 끝난 예측은 세지 않는다 — 호가창은 지금 걸려 있는 물량을 보는 것이고,
 * 끝난 예측은 기록이다. 둘을 한 그림에 더하면 어느 쪽도 읽을 수 없다.
 *
 * `base` 는 **목업 전용**이다. 목업이 기준가를 난수로 지어내면 같은 화면 머리의
 * 전일 종가와 다른 값이 호가창 아래에 뜬다 — 실제로 269,500원과 205,000원이
 * 나란히 보였다. 서버가 붙으면 응답의 basePrice 를 쓰므로 이 인자는 버려진다.
 */
export function getPredictionDistribution(code: string, base?: DistributionBase) {
  if (MOCK) return mock.distribution(code, base)
  return api.get<PredictionDistribution>(`/stocks/${code}/predictions/distribution`)
}

/**
 * 멱등 키 scope. 서버가 `Idempotency-Key` 를 **필수**로 받는다(없으면 400
 * IDEMPOTENCY_KEY_REQUIRED) — 예측에는 삭제 API 가 없고 슬롯도 되돌아오지 않아,
 * 타임아웃 뒤 한 번의 재시도가 예측 둘을 만들면 복구할 길이 없다.
 *
 * 봉인 재료가 바뀌면 다른 요청이므로 키도 갈라야 한다. noteSalt 가 등록마다 새로
 * 뽑히므로 그것만으로도 갈리지만, 무엇에 서명했는지가 키에 드러나도록 함께 적는다.
 */
export const createScope = (d: PredictionDraft) =>
  `prediction:${d.stockCode}:${d.noteSalt}`

/* 201 과 202 를 응답 본문 모양으로 가른다. api.post 는 상태 코드를 돌려주지 않는데,
   그것 하나 때문에 공용 client 를 고치면 다른 화면까지 영향이 간다. 두 응답은
   키가 겹치지 않아(operationId ↔ id) 모양만으로 확실히 갈린다.

   지금 서버는 202 를 내지 않는다 — 슬롯 초과는 409 다(위 PredictionQueued 주석). */
export function createPrediction(draft: PredictionDraft, signature: string) {
  const scope = createScope(draft)
  return api.post<PredictionCreated | PredictionQueued>(
    '/predictions', { ...draft, signature }, { idempotencyScope: scope },
  )
    .then((body): CreateResult => ('operationId' in body
      ? { kind: 'queued', data: body }
      : { kind: 'created', data: body }))
    /* 확정된 뒤에야 키를 버린다. 실패한 재시도는 같은 키로 가야 서버가 중복으로
       세지 않는다 — 성공 응답을 받은 다음 요청만 새 키를 받는다. */
    .then((r) => { releaseIdempotencyKey(scope); return r })
}

/* ── 커밋 봉인 (ANT-PRED-02) ──────────────────────────────
   규격 원본은 서버 CommitPayload · CommitHashes 이고, 기준값은
   backend/src/test/resources/commit/commit-cross-fixture.json 다섯 건이다.
   여기 조립 규칙은 그 다섯 건을 그대로 통과한다 — 고치면 다시 대조해야 한다.

   한 글자만 어긋나도 두 가지가 난다. 서명 문자열이 다르면 복원 주소가 달라져
   이유가 로그에 남지 않는 401(SIGNER_MISMATCH), 커밋 문자열이 다르면 D-03 ①단계가
   "불일치" 를 그린다 — 사용자에게는 "네 예측이 조작됐다" 로 읽힌다.

   ── 미리보기가 진짜 commitHash 를 보여 준다 ────────────────
   09-09 결정으로 커밋 salt 가 없어졌다. 유일한 난수인 noteSalt 를 클라이언트가
   만들고, noteHash 가 그 난수성을 물려받아 salt 역할을 겸한다. 그래서 등록 전
   미리보기가 원장에 남을 값과 **같은** commitHash 를 계산할 수 있다 — 서버 salt 가
   있을 때는 불가능했다. */

/** 커밋·서명 문자열에 함께 들어가는 다섯 값. 등록 폼(C-01)과 검산(D-03)이 나눠 쓴다. */
export type CommitFields = {
  stockCode: string
  direction: Direction
  targetPrice: number
  /** 서버가 short 로 받는다. proof 응답은 Horizon 어휘 밖 값도 낼 수 있어 number 다 */
  horizon: number
  /** keccak256(note ‖ noteSalt) — `0x` + 소문자 64 hex */
  noteHash: string
}

/**
 * 이 시스템의 **유일한 난수**. 32바이트 CSPRNG → 소문자 64 hex, `0x` 없음.
 *
 * 커밋 salt 가 없어져(09-09) 예측 전체의 비밀성이 이 값 하나에 걸린다. 약하면
 * 리빌 전에 남이 근거를 맞춰 볼 수 있다. crypto.getRandomValues 는 OS 엔트로피를
 * 쓰는 브라우저 내장 CSPRNG 이고, crypto.subtle 과 달리 비보안 컨텍스트(http)에서도
 * 돈다. Math.random() 은 암호용이 아니고 crypto.randomUUID() 는 122비트에 형식도
 * 달라 둘 다 쓰면 안 된다.
 *
 * 예측 한 건마다 새로 뽑는다 — 재사용하면 같은 근거를 쓴 두 예측의 noteHash 가 같아져
 * "같은 말을 두 번 했다" 가 원장에 그대로 드러난다.
 */
export function newNoteSalt() {
  const bytes = crypto.getRandomValues(new Uint8Array(32))
  return [...bytes].map((b) => b.toString(16).padStart(2, '0')).join('')
}

/**
 * 근거 해시 `keccak256(utf8(note) ‖ utf8(noteSalt))`. 서버 CommitHashes.noteHash 와 짝이다.
 *
 * 본문은 받은 그대로 해시한다 — trim 도 `\r\n` → `\n` 정규화도 하지 않는다. 서버도
 * 받은 바이트를 그대로 저장하고 해시하므로, 여기서 손대면 두 값이 갈린다.
 * noteSalt 는 hex **문자열** 로 이어 붙인다(바이트로 디코드하지 않는다). 구분자 없음.
 */
export function noteHash(note: string, noteSalt: string) {
  return keccak256Utf8(note + noteSalt)
}

/**
 * 커밋·서명 문자열에 쓰는 목표가 표기. **항상 소수 둘째 자리** 다.
 *
 * 서버 CommitPayload.formatPrice 가 DB numeric(14,2) 모양으로 조립한다. 82000 과
 * 82000.00 이 갈리면 서명 복원 주소가 달라져 원인이 남지 않는 401 이 난다.
 */
export const formatTargetPrice = (price: number) => price.toFixed(2)

/**
 * 셋째 자리 이하가 있는가. 서버는 **반올림하지 않고** 400 으로 거절하므로
 * (CommitPayload.formatPrice, RoundingMode.UNNECESSARY) 등록 전에 폼에서 막는다.
 * 조용히 반올림하면 서명한 값과 서버가 해시한 값이 달라진다.
 */
export const targetPriceInScale = (price: number) => Number(price.toFixed(2)) === price

/**
 * 커밋 문자열의 필드 줄 다섯 개. 서버 CommitPayload.lines() 와 같다.
 *
 * 커밋과 서명이 이 다섯 줄을 **함께** 쓴다. 두 곳에서 따로 목표가를 문자열로 만들면
 * 서명은 통과하고 해시는 안 맞는 사고가 나므로, 포맷 규칙을 여기 한 곳에 둔다.
 */
function commitLines(f: CommitFields) {
  return [
    `stockCode=${f.stockCode}`,
    `direction=${f.direction}`,
    `targetPrice=${formatTargetPrice(f.targetPrice)}`,
    `horizon=${f.horizon}`,
    `noteHash=${f.noteHash}`,
  ]
}

/**
 * commitHash 의 대상 문자열. 서버 CommitPayload.canonical() 과 바이트가 같아야 한다.
 * 구분은 `\n` 하나, 마지막 줄 뒤 개행 없음, UTF-8.
 *
 * 없는 줄 셋과 그 이유
 * - `salt=`             커밋 salt 자체가 없다(09-09). noteHash 가 그 역할을 겸한다.
 * - `evidencePointIds=` 리서치 포인트 내부 id 는 외부 검증자에게 의미가 없고, 봉인 뒤
 *                       바꾸는 API 도 없다. 등록 **본문** 에는 그대로 들어간다.
 * - `createdAt=`        서버 시각이라 검증자가 재현할 수 없다. "이때 있었다" 는 앵커가
 *                       증명한다.
 *
 * 첫 줄이 서명 문자열(`antenna:prediction:v1`)과 다른 것도 규격이다 — 서명이 커밋으로,
 * 커밋이 서명으로 오인·재사용되지 않게 한다.
 */
export function commitPayload(f: CommitFields) {
  return ['antenna:commit:v1', ...commitLines(f)].join('\n')
}

/** 커밋 문자열의 해시. 등록 전 미리보기와 D-03 ①단계가 같은 함수를 쓴다. */
export const commitHash = (payload: string) => keccak256Utf8(payload)

/**
 * personal_sign 대상 문자열 (결정 B3 — 예측 내용에 서명한다).
 *
 * 지갑 연동용 문자열(wallet.ts signingPayload)에 서명하면 예측 내용이 서명에 들어가지
 * 않아 서버가 SIGNER_MISMATCH 로 거절한다. 여기서는 커밋과 같은 다섯 줄에 머리·꼬리만
 * 다르게 붙인다.
 *
 * chainId 는 POST /wallet/nonce 응답값을 그대로 쓴다 — 지갑에서 eth_chainId 로 읽으면
 * 서버 조립본과 어긋난다(wallet.ts WalletNonce 주석과 같은 이유).
 */
export function predictionSigningPayload(f: CommitFields, nonce: WalletNonce) {
  return [
    'antenna:prediction:v1',
    ...commitLines(f),
    `chainId=${nonce.chainId}`,
    `nonce=${nonce.nonce}`,
  ].join('\n')
}
