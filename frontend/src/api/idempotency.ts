/* Idempotency-Key. 설계서 §1 — 8개 POST 에 필수다.

   핵심은 "재시도 시 같은 키를 보낸다" 는 것이다. 매번 새 키를 만들면
   네트워크가 끊겼다 재전송될 때 예측이 두 번 등록되거나 구독료가 두 번 나간다.
   그래서 요청 단위로 키를 발급해 보관하고, 성공을 확인한 뒤에야 버린다. */

const store = new Map<string, string>()

function newKey() {
  // crypto.randomUUID 는 보안 컨텍스트(https·localhost)에서만 있다
  if (typeof crypto !== 'undefined' && 'randomUUID' in crypto) return crypto.randomUUID()
  return `${Date.now()}-${Math.random().toString(16).slice(2)}`
}

/**
 * scope 하나에 키 하나를 물려 둔다.
 * 같은 scope 로 다시 부르면 같은 키가 나오므로 재시도가 안전하다.
 *
 * scope 예: `prediction:005930`, `subscription:42`
 */
export function idempotencyKey(scope: string) {
  const found = store.get(scope)
  if (found) return found
  const key = newKey()
  store.set(scope, key)
  return key
}

/** 요청이 확정된 뒤 호출한다. 다음 요청은 새 키를 받는다. */
export function releaseIdempotencyKey(scope: string) {
  store.delete(scope)
}
