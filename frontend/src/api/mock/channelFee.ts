/* 목업 응답. GET·PUT /me/channel/fee 를 여는 컨트롤러가 없어서 둔다
   (2026-09-10 확인 — ANT-TOKEN-05 / S15P21A507-65 미착수).

   지우는 절차 — api/subscriptions.ts 와 같다
   1. api/channelFee.ts 의 MOCK 을 false 로 바꾼다
   2. 이 파일과 channelFee.ts 의 `if (MOCK)` 두 줄, import 한 줄을 지운다
   화면 코드는 손대지 않는다.

   ── 값도 형태도 지어냈다. 다른 목업과 다른 점이라 적어 둔다 ──
   subscriptions 목업은 "형태는 지어내지 않았다" 고 적을 수 있었다. 명세에 응답
   스키마가 있었기 때문이다. 여기는 없다 — 명세가 "200 성공" 이라고만 적어 두었다.
   그래서 필드 이름을 ERD 의 publisher_fees 컬럼에서 따왔다(fee · effective_from ·
   created_at). 서버가 다르게 내리면 api/channelFee.ts 의 타입만 고치면 된다.

   ── 금액은 정수 ANT 다 ──────────────────────────────────
   서버 규약(decimals 0)을 따른다. 1 ANT ≈ 1원이라 구독료를 만 원 안팎으로 두고,
   다른 목업(subscriptions · channels)과 자릿수를 맞췄다 — 화면마다 12 와 12,000 이
   섞여 보이면 어느 쪽이 맞는지 목업만 보고는 알 수 없다.

   세 행을 넣었다. 화면이 서로 다르게 그려야 하는 경우들이다.
     인상 · 인하 · 최초 설정 — 이력 줄의 방향 표시가 갈린다 */
import type { ChannelFee, FeeApplied, FeeChange } from '../channelFee'

/** 목이라도 네트워크처럼 비동기여야 로딩 상태가 실제로 지나간다 */
const LATENCY_MS = 240

function delay<T>(value: T): Promise<T> {
  return new Promise((resolve) => setTimeout(() => resolve(value), LATENCY_MS))
}

/* 모듈 스코프에 두어 변경 결과가 이 세션 동안 유지된다.
   새로고침하면 되돌아간다 — 목이므로 그게 맞다. */
const HISTORY: FeeChange[] = [
  { id: 3, fee: '12000', effectiveFrom: '2026-08-20T00:00:00Z', createdAt: '2026-08-20T04:12:00Z' },
  { id: 2, fee: '15000', effectiveFrom: '2026-07-01T00:00:00Z', createdAt: '2026-07-01T09:30:00Z' },
  { id: 1, fee: '10000', effectiveFrom: '2026-06-02T00:00:00Z', createdAt: '2026-06-02T02:05:00Z' },
]

export function channelFee(): Promise<ChannelFee> {
  return delay({ fee: HISTORY[0]?.fee ?? null, history: [...HISTORY] })
}

export function updateChannelFee(fee: string): Promise<FeeApplied> {
  /* 서버는 차기 주기부터 적용한다. 목에서도 그렇게 둔다 — 지금 시각으로 적으면
     화면이 "오늘부터 적용" 처럼 보여 설계 제약과 반대로 읽힌다. */
  const effectiveFrom = nextCycle()
  HISTORY.unshift({
    id: (HISTORY[0]?.id ?? 0) + 1,
    fee,
    effectiveFrom,
    createdAt: new Date().toISOString(),
  })
  return delay({ fee, effectiveFrom })
}

/** 다음 달 1일. 실제 주기 규칙은 서버가 정하므로 목에서만 쓰는 근사다 */
function nextCycle() {
  const now = new Date()
  return new Date(Date.UTC(now.getUTCFullYear(), now.getUTCMonth() + 1, 1)).toISOString()
}
