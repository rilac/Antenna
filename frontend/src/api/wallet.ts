/* 지갑·토큰 도메인. API 명세서 §지갑·토큰.

   GET /wallet          { linked, walletAddress }
   GET /wallet/balance  { balance(wei), symbol, syncedAt }
   GET /wallet/ledger   { items: [{ delta, reason, txHash, createdAt }], nextCursor, hasNext }
   POST /wallet/nonce   { scope } → { nonce, chainId }
   POST /wallet/link    { address, signature } → { walletAddress } */
import { api } from './client'

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

/* ── 지갑 연동 (M-01) ─────────────────────────────────────────
   POST /wallet/nonce  { scope }            → { nonce, chainId }
   POST /wallet/link   { address, signature } → { walletAddress } */

/** 서명이 붙는 요청의 종류. payload 첫 줄의 용도 태그와 서버 nonce 칸을 동시에 정한다. */
export const SIGNATURE_SCOPES = {
  WALLET_LINK: 'wallet-link',
  PREDICTION_BURN: 'prediction-burn',
  SUBSCRIBE: 'subscribe',
  AD: 'ad',
  SEASON_JOIN: 'season-join',
} as const

export type SignatureScope = keyof typeof SIGNATURE_SCOPES

export type WalletNonce = {
  nonce: string
  /** 지갑에서 eth_chainId 로 읽지 말고 이 값을 쓴다 — 서버 조립본과 어긋나면 401 */
  chainId: number
}

/**
 * 서버 WalletLinkRequest.signingPayload() 와 같은 문자열을 만든다.
 *
 * 서버는 본문 필드로 payload 를 다시 조립해 대조한다. 줄바꿈·순서·소문자
 * 중 하나만 어긋나도 복원 주소가 달라져 원인이 로그에 남지 않는 401 이 난다.
 * 그래서 이 함수는 서버 구현을 그대로 옮긴 것이고, 손대려면 양쪽을 같이 고쳐야 한다.
 */
export function signingPayload(scope: SignatureScope, address: string, nonce: WalletNonce) {
  return [
    `antenna:${SIGNATURE_SCOPES[scope]}:v1`,
    `address=${address.toLowerCase()}`,
    `chainId=${nonce.chainId}`,
    `nonce=${nonce.nonce}`,
  ].join('\n')
}

/** 1회성 nonce 발급(Redis TTL 5분). 같은 scope 에 재발급하면 그 칸의 이전 값만 죽는다. */
export function requestNonce(scope: SignatureScope) {
  return api.post<WalletNonce>('/wallet/nonce', { scope })
}

/** 서명 검증 → 주소 등록. nonce 는 본문에 넣지 않는다 — 서버가 userId 로 꺼내 쓴다. */
export function linkWallet(address: string, signature: string) {
  return api.post<{ walletAddress: string }>('/wallet/link', { address, signature })
}

/** 0x1234…cdef 로 줄인다. 주소 전체는 좁은 자리에 넣으면 줄바꿈으로 깨진다. */
export function shortAddress(address: string) {
  return address.length > 14 ? `${address.slice(0, 6)}…${address.slice(-4)}` : address
}
