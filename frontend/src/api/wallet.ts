/* 지갑·토큰 도메인. API 명세서 §지갑·토큰.

   GET /wallet          { linked, walletAddress }
   GET /wallet/balance  { balance(정수 ANT), symbol, syncedAt }
   GET /wallet/ledger   { items: [{ delta, reason, txHash, createdAt }], nextCursor, hasNext }
   POST /wallet/nonce   { scope } → { nonce, chainId }
   POST /wallet/link    { address, signature } → { walletAddress } */
import { api } from './client'

export type WalletStatus = {
  linked: boolean
  walletAddress: string | null
}

export type WalletBalance = {
  /** 정수 ANT 문자열. 서버가 금액 필드를 문자열로 통일해 내린다 — number 로 좁히지 않는다 */
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

/**
 * ANT 금액 표기. 잔액 · 원장 증감 · 구독료 · 광고비가 모두 이 함수를 쓴다.
 *
 * ANT 는 decimals 0 이다(ANT-CHAIN-03). 서버 금액 필드는 정수 ANT 를 문자열로 싣고
 * "1000" 이 곧 1,000 ANT 라, 10^18 로 나누지 않고 천 단위로만 끊는다(S15P21A507-225).
 * BigInt 로 받는 것은 numeric(30,0) 이 Number 안전 범위를 넘을 수 있어서다.
 *
 * 음수(원장 차감)는 "-" 가 붙어 나온다. "+" 는 붙이지 않는다 — 획득을 드러낼지는 호출부가 정한다.
 */
export function formatToken(amount: string) {
  const raw = amount.trim()
  /* 예상 밖 형식이면 원본을 그대로 둔다. BigInt 에 바로 넣으면 '' 를 0 으로, '0x10' 을 16 으로
     읽어 틀린 금액을 조용히 그린다 */
  if (!/^-?\d+$/.test(raw)) return amount
  return BigInt(raw).toLocaleString('ko-KR')
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
  PREDICTION: 'prediction',
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
