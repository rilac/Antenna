/* 목업 응답. /subscriptions 를 여는 컨트롤러가 없어서 둔다(2026-09-09 확인).

   지우는 절차 — api/insight.ts 와 같다
   1. api/subscriptions.ts 의 MOCK 을 false 로 바꾼다
   2. 이 파일과 subscriptions.ts 의 `if (MOCK)` 두 줄, import 한 줄을 지운다
   화면 코드는 손대지 않는다.

   값은 지어냈지만 형태는 지어내지 않았다 — 명세 응답 스키마
   { id, publisher, fee, status, expiresAt, renewEnabled } 여섯 필드 그대로다.

   네 줄을 넣었다. 화면이 서로 다르게 그려야 하는 경우들이다.
     1  ACTIVE  · 갱신 켬   → 해지 버튼이 보인다
     2  ACTIVE  · 갱신 껐음 → "만료일에 종료" · 해지 버튼 없음
     3  PENDING · expiresAt null → AnchorBadge 대기. 만료일 자리를 비운다
     4  EXPIRED · 갱신 껐음 → 지난 구독. 다시 구독은 채널에서 한다

   해지는 목에서도 상태가 남는다 — 눌러 보고 목록이 바뀌는지 확인해야 하기 때문이다. */
import type { Subscription } from '../subscriptions'

/** 목이라도 네트워크처럼 비동기여야 로딩 상태가 실제로 지나간다 */
const LATENCY_MS = 240

function delay<T>(value: T): Promise<T> {
  return new Promise((resolve) => setTimeout(() => resolve(value), LATENCY_MS))
}

/* 모듈 스코프에 두어 해지 결과가 이 세션 동안 유지된다.
   새로고침하면 되돌아간다 — 목이므로 그게 맞다. */
const ROWS: Subscription[] = [
  {
    id: 1,
    publisher: { userId: '1', nickname: '반도체관측소' },
    fee: '12000',
    status: 'ACTIVE',
    expiresAt: '2026-10-02T00:00:00Z',
    renewEnabled: true,
  },
  {
    id: 2,
    publisher: { userId: '2', nickname: '실적읽는사람' },
    // 10,000 ANT 로 결제한 구독. 채널이 값을 올려도 이 행은 그대로다(박제)
    fee: '10000',
    status: 'ACTIVE',
    expiresAt: '2026-09-18T00:00:00Z',
    renewEnabled: false,
  },
  {
    id: 3,
    publisher: { userId: '3', nickname: '차트말고실적' },
    fee: '12000',
    status: 'PENDING',
    // 아직 개시 전이라 만료일이 없다
    expiresAt: null,
    renewEnabled: true,
  },
  {
    id: 4,
    publisher: { userId: '4', nickname: '월간반도체' },
    fee: '8000',
    status: 'EXPIRED',
    expiresAt: '2026-08-11T00:00:00Z',
    renewEnabled: false,
  },
]

export function list(): Promise<{ items: Subscription[] }> {
  // 복사해서 준다 — 화면이 받은 배열을 직접 고쳐도 목 원본이 흔들리지 않는다
  return delay({ items: ROWS.map((r) => ({ ...r })) })
}

export function cancelRenewal(subscriptionId: number): Promise<void> {
  const found = ROWS.find((r) => r.id === subscriptionId)
  if (found) found.renewEnabled = false
  return delay(undefined)
}
