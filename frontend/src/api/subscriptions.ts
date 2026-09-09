/* E-03 내 구독. API 명세서 §채널·구독 · 설계서 §3 E · §4 E-03.

   GET    /subscriptions/me            내 구독 목록
   DELETE /subscriptions/{id}/renewal  자동 갱신 해지 (204)

   ── 백엔드 현황 (2026-09-09 확인) ────────────────────────
   /subscriptions 를 여는 컨트롤러가 없다. monetize 도메인에 있는 것은
   AdController · ChannelReportController · ReportController 셋뿐이다.
   Subscription 엔티티는 있으니 서비스·컨트롤러만 남은 것으로 보인다.

   그래서 MOCK 을 켜 둔다. 타입은 명세서가 준 스키마 그대로다 —
     GET  200 { items: [{ id, publisher, fee, status, expiresAt, renewEnabled }] }
     DELETE 204
   MOCK 을 false 로 바꾸고 mock/subscriptions.ts 와 각 함수의 `if (MOCK)` 한 줄씩만
   지우면 흔적이 없다(api/insight.ts 와 같은 방식).

   ── 이 화면이 다루는 것이 "구독" 이 아니라 "갱신 예약" 이다 ──
   DELETE .../renewal 은 구독을 끊지 않는다. renew_enabled 를 false 로 만들 뿐이고
   만료일까지는 ACTIVE 그대로다. 라벨을 "구독 취소" 로 쓰면 사용자는 지금 접근이
   끊긴다고 읽는다 — 설계서 §4 E-03 이 못 박은 제약이다. */
import { api } from './client'
import * as mock from './mock/subscriptions'

const MOCK = true

/**
 * 구독 상태. 서버 Subscription.Status 와 짝이다.
 *
 * PENDING 은 결제가 블록 확정을 기다리는 중이라 **아직 열람 권한이 없다.**
 * ACTIVE 와 같게 그리면 잠긴 예측을 보고 고장으로 읽는다(E-02 와 같은 판단).
 * EXPIRED 는 만료됐거나 배치 B4 의 갱신 결제가 실패한 상태다 — 명세가 둘을
 * 같은 값으로 내리므로 화면이 사유를 구별해 말하지 않는다.
 */
export const SUBSCRIPTION_STATUSES = ['PENDING', 'ACTIVE', 'EXPIRED'] as const
export type SubscriptionStatus = (typeof SUBSCRIPTION_STATUSES)[number]

/** 구독 대상 채널. 목록에서 채널로 나가는 링크에 쓴다 */
export type Publisher = {
  userId: string
  nickname: string
}

/**
 * 내 구독 한 줄. 명세 응답의 여섯 필드 그대로다.
 *
 * fee 는 **결제 시점에 박제된 값**이라 채널의 현재 구독료와 다를 수 있다(E-02 참고).
 * 그래서 이 화면은 채널의 현재 가격을 함께 보여주지 않는다 — 두 숫자를 나란히 두면
 * 어느 쪽이 내가 내는 값인지 흐려진다.
 */
export type Subscription = {
  id: number
  publisher: Publisher
  /** wei 문자열. 결제 시점 박제값 */
  fee: string
  status: SubscriptionStatus
  /** 개시 +30일. PENDING 은 아직 개시 전이라 null 이다 */
  expiresAt: string | null
  /** 만료일에 배치 B4 가 재결제할지. 해지하면 false */
  renewEnabled: boolean
}

export function fetchMySubscriptions(): Promise<{ items: Subscription[] }> {
  if (MOCK) return mock.list()
  return api.get<{ items: Subscription[] }>('/subscriptions/me')
}

/**
 * 자동 갱신 해지. 204 라 돌려줄 것이 없다.
 *
 * **되돌리는 API 가 없다.** 명세에 DELETE 만 있고 다시 켜는 경로가 없어, 해지하면
 * 이 화면에서 자동 갱신을 되살릴 방법이 없다. 그래서 확인 단계를 둔다 —
 * 되돌릴 수 있는 토글처럼 보이게 만들지 않는다.
 */
export function cancelRenewal(subscriptionId: number): Promise<void> {
  if (MOCK) return mock.cancelRenewal(subscriptionId)
  return api.delete<void>(`/subscriptions/${subscriptionId}/renewal`)
}

export const STATUS_LABEL: Record<SubscriptionStatus, string> = {
  PENDING: '결제 확인 중',
  ACTIVE: '구독 중',
  EXPIRED: '만료됨',
}
