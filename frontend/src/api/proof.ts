/* 3단계 검산 재료. API 명세서 §온체인 검증 · 설계서 §3 D · §4 D-03.

   GET /predictions/{id}/proof → ProofResponse  (백엔드 ANT-CHAIN-06, 구현 완료)

   서버는 검증하지 않는다. 이 응답은 계산의 "입력값" 이고, 세 단계의 계산은
   전부 브라우저가 한다 — 서버가 "검증됨" 이라고 답해 주면 그건 증명이 아니다.

   ── MOCK 을 켜 둔 이유 ─────────────────────────────────
   API 는 열려 있지만 로컬 DB 의 anchor_batches · prediction_commits 가 0행이라
   부를 예측이 없다. 데이터가 들어오면 MOCK 을 false 로 바꾸고 mock/proof.ts 와
   `if (MOCK)` 한 줄만 지우면 된다. 화면 코드는 손대지 않는다.
   목 데이터는 지어낸 값이 아니라 SSAFY 체인에 실제로 올라간 배치 1번이다 —
   자세한 것은 mock/proof.ts 머리말. */
import { api } from './client'
import * as mock from './mock/proof'

const MOCK = true

/** 배치 상태 + "아직 배치에 안 들어감". 서버 ProofResponse.AnchorStatus 와 짝이다. */
export const PROOF_ANCHOR_STATUSES = ['WAITING', 'PENDING', 'CONFIRMED', 'FAILED'] as const
export type ProofAnchorStatus = (typeof PROOF_ANCHOR_STATUSES)[number]

/**
 * 커밋 payload 의 구성 필드. ①단계가 이 값들을 조립해 salt 와 함께 해시한다.
 *
 * 조립 규격(줄 구분 등, 결정 B2)은 아직 정해지지 않았다 — 서버 ProofService 가
 * "PRED-02 가 정한다" 고 적어 두었고, 백엔드 어디에도 commitHash 를 계산하는 코드가 없다.
 * 그래서 ①단계는 만기 전이라 잠기는 것과 별개로, 지금은 누구도 실행할 수 없다.
 * 규격이 정해지면 여기에 조립 함수를 두고 Verify 화면의 ①을 켠다.
 */
export type ProofPayload = {
  stockCode: string | null
  direction: 'UP' | 'DOWN'
  targetPrice: number
  /** 5 · 10 · 20 · 60 영업일 */
  horizon: number
  /** keccak256(note ‖ noteSalt). PRED-02 전까지 null */
  noteHash: string | null
  createdAt: string
}

/**
 * 이 커밋이 배치 안에서 차지한 자리. ②단계의 재료 전부다.
 *
 * anchorStatus 가 WAITING 이면 이 객체 자체가 없다 — 필드 넷을 각각 null 로 두지 않고
 * 객체 하나를 비우는 쪽을 서버가 택했다(09-06 결정).
 */
export type ProofAnchor = {
  /** 온체인 batchId. rootOf · isIncluded 의 첫 인자다 */
  batchId: number
  merkleRoot: string
  /** 아래에서 위로 형제 해시. 정렬 결합이라 좌우 정보가 없다 */
  merkleProof: string[]
  leafIndex: number
  leafCount: number
  status: 'PENDING' | 'CONFIRMED' | 'FAILED'
  txHash: string | null
  blockNumber: number | null
  confirmedAt: string | null
  /** 재배포되면 옛 배치는 옛 주소에 남는다 — 화면에 주소를 박지 않고 이 값을 쓴다 */
  contractAddress: string
  chainId: number
}

/**
 * 판정 결과. HIT/MISS 뒤에만 온다.
 *
 * sourceUrl 에는 인증키가 붙어 있지 않다 — 사용자가 자기 키로 열어 종가를 대조한다.
 * 우리가 대신 불러 주면 ③은 다시 "우리 서버를 믿어라" 가 된다.
 */
export type ProofSettle = {
  status: 'HIT' | 'MISS'
  settleDate: string
  settlePrice: number | null
  basePrice: number | null
  errorRate: number | null
  sourceUrl: string | null
}

/** 서버 ProofResponse 와 짝이다. */
export type Proof = {
  predictionId: number
  payload: ProofPayload
  /** 커밋 전이면 null. 그러면 검산할 대상 자체가 없다 */
  commitHash: string | null
  /** EIP-191 personal_sign. 작성자 부인방지용 재료다 */
  signature: string | null
  signerAddress: string | null
  revealedAt: string | null
  /** 리빌 뒤 회원 전원에게 온다. 비구독자도 ①을 검산해야 하기 때문 */
  salt: string | null
  /** 리빌 뒤에도 작성자·구독자만. PRED-02 전까지는 항상 null */
  noteSalt: string | null
  anchor: ProofAnchor | null
  anchorStatus: ProofAnchorStatus
  settle: ProofSettle | null
}

export function fetchProof(predictionId: string | number): Promise<Proof> {
  if (MOCK) return mock.proof(predictionId)
  return api.get<Proof>(`/predictions/${predictionId}/proof`)
}

export const DIRECTION_LABEL: Record<ProofPayload['direction'], string> = {
  UP: '상승',
  DOWN: '하락',
}

export const SETTLE_LABEL: Record<ProofSettle['status'], string> = {
  HIT: '적중',
  MISS: '빗나감',
}

/* ── 앵커 상태 표시 (C-01 등록 모달 · C-03 예측 상세) ──────────
   D-03 은 증명 전체를 쓰지만, 저 둘은 "어디까지 갔나" 만 알면 된다.
   상태 어휘가 여기 있으니 그 표현도 여기 둔다 — 화면마다 문구를 새로 쓰면 갈린다. */

export const PROOF_STATUS_LABEL: Record<ProofAnchorStatus, string> = {
  WAITING: '앵커 대기',
  PENDING: '블록 확정 대기',
  CONFIRMED: '앵커 확정',
  FAILED: '앵커 실패',
}

/** 더 기다리면 바뀌는가. 폴링을 언제 멈출지 이 하나로 정한다 */
export const isAnchorSettling = (s: ProofAnchorStatus) => s === 'WAITING' || s === 'PENDING'

/**
 * 앵커 **상태만** 실제 서버에서 읽는다.
 *
 * fetchProof 를 쓰지 않는 이유 — 그쪽은 D-03 검산용이라 MOCK 이 켜져 있고,
 * 목 데이터의 anchorStatus 가 CONFIRMED 다. 앵커가 안 잡힌 예측을 "확정" 으로
 * 그리게 된다. 상태는 실제 서버가 이미 답하므로(앵커 전이면 WAITING) 여기서는
 * 목업을 타지 않는다. MOCK 이 false 가 되면 이 함수는 fetchProof 로 합친다.
 */
export function fetchAnchorStatus(predictionId: string | number) {
  return api.get<Proof>(`/predictions/${predictionId}/proof`)
}
