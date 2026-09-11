/* E-02 채널 프로필 · 구독. API 명세서 §채널·구독 · 설계서 §3 E · §4 E-02 · §5.

   GET  /channels/{userId}              채널 프로필 + 내 구독 상태
   GET  /channels/{userId}/predictions  채널 예측 목록 (§5 게이팅)
   GET  /channels/{userId}/reports      채널 리포트 목록  ← 유일하게 실제로 있다
   GET  /channels/{userId}/backtest     백테스트 (무료 · 파라미터 고정)
   POST /channels/{userId}/subscriptions 구독 개시 → 202 { operationId }

   ── 백엔드 현황 (2026-09-08 확인) ────────────────────────
   /channels 아래에 열려 있는 것은 ChannelReportController 의 {userId}/reports 하나뿐이다.
   나머지 넷은 컨트롤러가 없다. 엔티티(Subscription · PublisherFee)와 Operation.Kind.SUBSCRIBE,
   SignatureScope.SUBSCRIBE 는 이미 있으니 서비스·컨트롤러만 남은 것으로 보인다.

   그래서 조회 넷은 MOCK 을 켜 두고, **서명은 실제로 한다** — POST /wallet/nonce 는
   "온체인 동반 요청 전부가 쓰는 공용 창구"(서버 WalletController 주석)라 scope=SUBSCRIBE
   가 지금도 동작한다. 진짜인 부분을 목으로 덮으면 나중에 붙일 때 무엇이 검증됐는지 알 수 없다.

   MOCK 을 false 로 바꾸고 mock/channels.ts 와 각 함수의 `if (MOCK)` 한 줄씩만 지우면 된다. */
import { api } from './client'
import * as mock from './mock/channels'
import type { CursorList } from './types'
import type { Direction, PredictionStatus } from './predictions'

const MOCK = true

/* ── 채널 프로필 ─────────────────────────────────────── */

/**
 * 채널 실적. **세 개 고정이다**(설계 제약).
 *
 * 셋 다 판정 표본이 없으면 null 이다 — 0 으로 그리면 "한 번도 못 맞혔다" 로 읽힌다.
 * doneCount 만 표본 수 자체라 0 이 정상값이다.
 */
export type ChannelStats = {
  /** 적중률 % */
  hitRate: number | null
  /** 판정 완료 건수 */
  doneCount: number
  /** 평균 오차 % */
  avgError: number | null
}

/** 외부 채널 링크. 라벨과 주소만 온다 — 플랫폼별 아이콘을 우리가 정하지 않는다 */
export type ExternalLink = {
  label: string
  url: string
}

/**
 * 내 구독 상태. 이 채널에 대한 **내** 상태이지 채널의 속성이 아니다.
 *
 * status 가 null 이면 구독한 적이 없다. PENDING 은 결제가 체인에서 확정되기를
 * 기다리는 중이라 아직 열람 권한이 없다 — ACTIVE 와 같게 그리면 안 된다.
 */
export type MySubscription = {
  status: 'PENDING' | 'ACTIVE' | 'EXPIRED' | null
  /** 개시 +30일. status 가 null 이면 없다 */
  expiresAt: string | null
  /** false 면 만료일에 끝난다(E-03 자동 갱신 해지) */
  autoRenew: boolean
  /** 결제 시점에 박제된 가격(정수 ANT 문자열). 채널이 가격을 바꿔도 이 값은 그대로다 */
  paidFee: string | null
}

/**
 * 채널 프로필. 서버 응답에 없는 것을 화면이 만들어 내지 않는다.
 *
 * 두지 않는 것 — 핸들(@) · 구독자 수 · 작성 카운트 · 무료 팔로우 · 신뢰도 점수.
 * 채널 관계는 유료 구독뿐이라 팔로우 개념이 없고, 나머지는 응답에 없다(설계 제약).
 */
export type Channel = {
  userId: string
  nickname: string
  avatarUrl: string | null
  /** 소개. 비어 있을 수 있다 */
  bio: string | null
  /** 관심 분야(섹터명). 서버가 주는 문자열을 그대로 쓴다 */
  interests: string[]
  externalLinks: ExternalLink[]
  /**
   * 현재 구독료(정수 ANT 문자열). 내 구독의 paidFee 와 다를 수 있다.
   *
   * **아직 정하지 않았으면 null 이다.** 채널은 가입과 함께 존재하지만(publisher_fees 의
   * publisher_id 가 users.id 를 직접 가리킨다 — 별도 channels 테이블이 없다) 구독료는
   * 정해야 생긴다. 그 사이 상태가 이 null 이다.
   *
   * 0 이 아니다. 0 으로 내리면 무료 채널로 읽힌다(E-04 도 같은 판단, S15P21A507-186).
   */
  fee: string | null
  stats: ChannelStats
  mySubscription: MySubscription
  /** 내 채널이면 구독 카드 대신 설정 안내를 그린다 */
  isMe: boolean
}

export function fetchChannel(userId: string) {
  if (MOCK) return mock.channel(userId)
  return api.get<Channel>(`/channels/${userId}`)
}

/* ── 채널 예측 목록 ──────────────────────────────────── */

/**
 * 채널이 건 예측 한 건. 서버 PredictionCardResponse 계열과 같은 게이팅 규칙을 따른다
 * (Jira S15P21A507-70) — 잠글 때 종목·방향까지는 남기고 targetPrice 만 null 로 뺀다.
 *
 * 설계서와 티켓이 이 부분을 조금 다르게 적었다. 설계서 §4 E-02 는 "미판정 예측은
 * 구독자에게만 응답에 포함", 티켓은 "비구독자에게는 잠금 카드로 존재만 알린다" 다.
 * §5 게이팅 원칙("없는 것처럼 보이면 구독 유인이 사라진다")과 B-03 의 선례를 따라
 * **잠금 카드로 내려오는 쪽**으로 잡았다. 서버가 아예 빼고 내리면 화면은 그냥
 * 목록이 짧아질 뿐이라 이 타입은 그대로 쓸 수 있다.
 */
export type ChannelPrediction = {
  id: string
  stockCode: string | null
  stockName: string | null
  direction: Direction
  status: PredictionStatus
  /** 잠기면 null */
  targetPrice: number | null
  errorRate: number | null
  horizon: number
  dueDate: string | null
  locked: boolean
}

export type ChannelPredictionPage = CursorList<ChannelPrediction>

export function fetchChannelPredictions(userId: string, cursor?: string | number | null) {
  if (MOCK) return mock.predictions(userId, cursor)
  return api.get<ChannelPredictionPage>(`/channels/${userId}/predictions`, {
    query: { cursor: cursor ?? undefined },
  })
}

/* ── 백테스트 ────────────────────────────────────────── */

/**
 * 백테스트 결과. **파라미터가 고정이다**(설계 제약) — 3개월 · 상수 원금 · 수수료 0.
 * 사용자 입력 칸을 두지 않는다. 무료라 잠그지도 않는다.
 *
 * 고정값을 화면에 하드코딩하지 않고 응답에서 받는 이유 — 나중에 기간이 6개월로
 * 바뀌면 화면 문구와 계산 근거가 어긋난다.
 */
export type Backtest = {
  /** 기간 개월 수. 지금은 3 고정 */
  months: number
  /** 시작 원금(원) */
  principal: number
  /** 누적 수익률 % */
  returnRate: number
  /** 같은 기간 KOSPI 수익률 % — 비교 기준이 없으면 숫자를 읽을 수 없다 */
  benchmarkRate: number | null
  /** 검증에 쓴 판정 완료 예측 수. 0 이면 결과를 그리지 않는다 */
  sampleCount: number
  computedAt: string
}

export function fetchBacktest(userId: string) {
  if (MOCK) return mock.backtest(userId)
  return api.get<Backtest>(`/channels/${userId}/backtest`)
}

/* ── 구독 개시 ───────────────────────────────────────── */

/**
 * 구독 개시. 202 + operationId 로 오고 M-02 폴링으로 이어진다(설계서 §3 원칙 4).
 *
 * 200 이 아닌 이유 — 결제가 체인에 올라가야 끝나서다. 여기서 성공을 그리면
 * 블록이 되돌려질 때 화면만 구독 상태가 된다.
 */
export type SubscribeBody = {
  /** EIP-191 personal_sign 결과 */
  signature: string
}

/**
 * 멱등 키 scope. 채널마다 따로 둔다 — 두 채널을 잇달아 구독할 때 같은 키가 나가면
 * 뒤엣것이 앞엣것의 결과를 그대로 돌려받는다.
 *
 * 성공을 확인한 뒤 releaseIdempotencyKey 로 버린다. 서명을 다시 받아 재시도할 때는
 * 키를 유지해야 결제가 두 번 나가지 않는다(설계서 §1).
 */
export const subscribeScope = (userId: string) => `subscription:${userId}`

export function subscribe(userId: string, body: SubscribeBody) {
  if (MOCK) return mock.subscribe(userId, body)
  return api.post<{ operationId: string }>(`/channels/${userId}/subscriptions`, body, {
    idempotencyScope: subscribeScope(userId),
  })
}

/* ── 표시 도우미 ───────────────────────────────────────
   구독료 표기는 api/wallet.ts 의 formatToken 을 쓴다. 여기 있던 formatFee 는 뒤 18자리를
   소수부로 잘라 정수 ANT 를 "0.0000" 으로 만들었다(S15P21A507-225) — 금액 표기를 한 곳에 둔다. */

/** 구독 만료일. 남은 날짜가 중요해 날짜까지만 적는다 */
export function formatExpiry(iso: string) {
  const d = new Date(iso)
  if (Number.isNaN(d.getTime())) return iso
  return d.toLocaleDateString('ko-KR', { year: 'numeric', month: '2-digit', day: '2-digit' })
}

/** 만료까지 남은 일수. 지났으면 0 */
export function daysLeft(iso: string) {
  const ms = new Date(iso).getTime() - Date.now()
  return Number.isNaN(ms) ? 0 : Math.max(0, Math.ceil(ms / 86_400_000))
}
