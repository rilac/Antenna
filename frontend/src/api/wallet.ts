/* 지갑·토큰 도메인. API 명세서 §지갑·토큰.

   GET /wallet          { linked, walletAddress }
   GET /wallet/balance  { balance(wei), symbol, syncedAt }
   GET /wallet/ledger   { items: [{ delta, reason, txHash, createdAt }], nextCursor, hasNext } */

export type WalletStatus = {
  linked: boolean
  walletAddress: string | null
}

export type WalletBalance = {
  /** wei 단위 문자열. 18자리라 number 로 받으면 정밀도가 깨진다 */
  balance: string
  symbol: string
  /** 온체인 대사 시각. 잔액 옆에 반드시 함께 보인다 */
  syncedAt: string
}

/* 명세에 적힌 7종이 전부다. 임의 사유를 만들지 않는다. */
export const LEDGER_REASONS = [
  'SIGNUP_BONUS',
  'SLOT_OVER',
  'SUBSCRIBE',
  'SUBSCRIBE_INCOME',
  'AD_PAY',
  'SEASON_ENTRY',
  'SEASON_REWARD',
] as const

export type LedgerReason = (typeof LEDGER_REASONS)[number]

export type LedgerEntry = {
  /** 부호 있는 증감. 양수 획득 · 음수 사용 */
  delta: string
  reason: LedgerReason
  txHash: string | null
  createdAt: string
}

export const REASON_LABEL: Record<LedgerReason, string> = {
  SIGNUP_BONUS: '가입 보너스',
  SLOT_OVER: '예측 슬롯 초과',
  SUBSCRIBE: '구독 결제',
  SUBSCRIBE_INCOME: '구독 수익',
  AD_PAY: '광고 비용',
  SEASON_ENTRY: '대회 참가비',
  SEASON_REWARD: '대회 보상',
}

/** 토큰은 18자리 소수를 쓴다. wei 문자열을 사람이 읽는 수량으로 바꾼다. */
const DECIMALS = 18n

export function formatToken(wei: string, fractionDigits = 2) {
  let negative = false
  let raw = wei.trim()
  if (raw.startsWith('-')) { negative = true; raw = raw.slice(1) }
  if (!/^\d+$/.test(raw)) return wei // 예상 밖 형식이면 원본을 그대로 둔다

  const base = 10n ** DECIMALS
  const value = BigInt(raw)
  const whole = value / base
  const rest = value % base

  // 소수부를 원하는 자리까지 반올림 없이 자른다 — 잔액을 부풀리지 않는다
  const cut = rest.toString().padStart(Number(DECIMALS), '0').slice(0, fractionDigits)
  const trimmed = cut.replace(/0+$/, '')

  const text = trimmed
    ? `${whole.toLocaleString('ko-KR')}.${trimmed}`
    : whole.toLocaleString('ko-KR')

  return negative ? `-${text}` : text
}

/** KST ISO-8601 을 화면 문구로. 대사 시각·내역 시각에 함께 쓴다. */
export function formatDateTime(iso: string) {
  const d = new Date(iso)
  if (Number.isNaN(d.getTime())) return iso
  return d.toLocaleString('ko-KR', {
    year: 'numeric', month: '2-digit', day: '2-digit',
    hour: '2-digit', minute: '2-digit',
  })
}
