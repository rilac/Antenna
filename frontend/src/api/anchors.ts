/* 온체인 검증 도메인. API 명세서 §온체인 검증 · 설계서 §3 D.

   GET /anchors?cursor=&size=  { items, nextCursor, hasNext }
   GET /anchors/{id}           배치 상세 (리프 전량) — D-02 [ANT-FE-ANCHOR]
   GET /predictions/{id}/proof 3단계 검산            — D-03 [ANT-FE-VERIFY]

   백엔드 ANT-CHAIN-06 으로 셋 다 열려 있다. 목업이 아니다. */

/** 배치가 체인에 올라간 단계. 서버 AnchorBatch.Status 와 짝이다. */
export const ANCHOR_STATUSES = ['PENDING', 'CONFIRMED', 'FAILED'] as const
export type AnchorStatus = (typeof ANCHOR_STATUSES)[number]

/**
 * 앵커 배치 한 줄. 서버 AnchorItemResponse 와 짝이다.
 *
 * txHash · blockNumber · confirmedAt 은 아직 체인에 안 올랐거나 실패하면 비어 있다
 * — PENDING 행에서 이 셋을 그리려 하면 안 된다.
 */
export type Anchor = {
  /** 온체인 batchId 와 같은 값이라 숫자다 */
  id: number
  /** 이 배치가 묶은 영업일 YYYY-MM-DD */
  businessDate: string
  /** 이 배치 커밋들의 머클 루트 */
  merkleRoot: string
  commitCount: number
  status: AnchorStatus
  txHash: string | null
  blockNumber: number | null
  confirmedAt: string | null
  /** 이 배치를 올린 장부 컨트랙트 주소 */
  contractAddress: string
  chainId: number
}

/**
 * 배치에 담긴 커밋 한 건. 서버 AnchorDetailResponse.Leaf 와 짝이다.
 *
 * predictionId 가 함께 오므로 목록에서 커밋 하나를 골라 D-03 검산으로 갈 수 있다
 * (ANT-CHAIN-09 에서 결정 F2 를 뒤집어 동봉하기로 바뀌었다).
 */
export type AnchorLeaf = {
  predictionId: number
  commitHash: string
}

/**
 * 앵커 배치 상세. 서버 AnchorDetailResponse 와 짝이다 — 목록에 없는 넷이 더 온다.
 *
 * commits 는 리프 순서(prediction id 오름차순) 그대로다. 이 순서로 트리를 다시
 * 접으면 merkleRoot 가 나와야 한다 — 순서가 곧 검증 재료라 화면이 정렬을 바꾸면 안 된다.
 */
export type AnchorDetail = Anchor & {
  /** 릴레이어가 트랜잭션을 보낸 시각. 확정 전에도 채워진다 */
  sentAt: string | null
  /** 전송 시도 횟수. 1보다 크면 재시도가 있었다는 뜻이다 */
  attempts: number
  /** FAILED 일 때 실패 사유. 그 외에는 null */
  lastError: string | null
  commits: AnchorLeaf[]
}

export const STATUS_LABEL: Record<AnchorStatus, string> = {
  PENDING: '대기',
  CONFIRMED: '확정',
  FAILED: '실패',
}

/**
 * 해시를 줄여 보여준다. 앞뒤를 남기는 이유는 두 해시를 눈으로 구별하기 위해서다
 * — 앞만 남기면 서로 다른 값이 같아 보인다.
 *
 * 줄여 놓더라도 전체 값을 복사할 수 있어야 한다(설계 제약). 그건 CopyHash 가 맡는다.
 */
export function shortHash(value: string, head = 10, tail = 8) {
  return value.length > head + tail + 1 ? `${value.slice(0, head)}…${value.slice(-tail)}` : value
}

/** 영업일. 배치가 어느 날 것인지만 알면 되므로 날짜까지만 적는다. */
export function formatBusinessDate(iso: string) {
  const d = new Date(iso)
  if (Number.isNaN(d.getTime())) return iso
  return d.toLocaleDateString('ko-KR', { year: 'numeric', month: '2-digit', day: '2-digit' })
}

/** 확정 시각. 체인에 언제 올랐는지가 중요해 분까지 적는다. */
export function formatConfirmedAt(iso: string) {
  const d = new Date(iso)
  if (Number.isNaN(d.getTime())) return iso
  return d.toLocaleString('ko-KR', {
    year: 'numeric', month: '2-digit', day: '2-digit',
    hour: '2-digit', minute: '2-digit',
  })
}

/* ── 예측 한 건의 커밋 증명 (GET /predictions/{id}/proof) ──
   서버 ProofResponse 와 짝이다. D-03 3단계 검산의 재료이자, C-01·C-03 이
   **앵커가 어디까지 갔는지** 를 아는 유일한 길이다.

   여기 목업이 없다. 백엔드 ANT-CHAIN-06 으로 이미 열려 있다. */

/** 커밋 배치 상태에 "아직 배치에 안 들어감"(WAITING) 이 하나 더 붙는다.
    서버가 anchor 를 null 로 두고 이 값 하나로 대기를 말한다 — 배지 입력이 상태값 하나면 된다. */
export const PROOF_ANCHOR_STATUSES = ['WAITING', 'PENDING', 'CONFIRMED', 'FAILED'] as const
export type ProofAnchorStatus = (typeof PROOF_ANCHOR_STATUSES)[number]

/** 커밋에 잠긴 값. 등록 시점 그대로이며 바뀌지 않는다 */
export type ProofPayload = {
  stockCode: string
  direction: 'UP' | 'DOWN'
  targetPrice: number
  horizon: number
  /** PRED-02 가 keccak256(note ‖ noteSalt) 를 저장하기 전까지 null 이다 */
  noteHash: string | null
  createdAt: string
}

/** 배치 안에서 이 커밋의 자리. 앵커 전에는 통째로 null 이다 */
export type ProofAnchor = {
  batchId: number
  merkleRoot: string
  /** 아래에서 위로 형제 해시. 정렬 결합이라 좌우 정보가 없다 */
  merkleProof: string[]
  leafIndex: number
  leafCount: number
  status: AnchorStatus
  txHash: string | null
  blockNumber: number | null
  confirmedAt: string | null
  /** 브라우저가 rootOf 를 부를 장부. 재배포 뒤에도 이 주소에서 찾아야 한다 */
  contractAddress: string
  chainId: number
}

/** 판정 결과. HIT/MISS 뒤에만 채워진다 */
export type ProofSettle = {
  status: 'BASE' | 'OPEN' | 'HIT' | 'MISS'
  settleDate: string
  settlePrice: number
  basePrice: number
  errorRate: number
  /** 공공데이터 원본 조회 주소. 인증키가 붙지 않아 사용자가 자기 키로 연다 */
  sourceUrl: string
}

export type Proof = {
  predictionId: number
  payload: ProofPayload
  /* 아래 넷은 등록 경로(POST /predictions)를 거쳐야 채워진다.
     SQL 로 직접 넣은 행에서는 비어 있다 — 화면이 빈 값을 그리지 않게 null 을 허용한다. */
  commitHash: string | null
  signature: string | null
  signerAddress: string | null
  revealedAt: string | null
  /** 리빌 뒤 회원 전원에게 간다. 비구독자도 ①(해시 재계산)을 검산해야 한다 */
  salt: string | null
  /** 리빌 뒤에도 작성자·구독자만 받는다(결정 D6) */
  noteSalt: string | null
  /** 앵커 전에는 null. 그때는 anchorStatus 가 WAITING 이다 */
  anchor: ProofAnchor | null
  anchorStatus: ProofAnchorStatus
  settle: ProofSettle | null
}

export const PROOF_STATUS_LABEL: Record<ProofAnchorStatus, string> = {
  WAITING: '앵커 대기',
  PENDING: '블록 확정 대기',
  CONFIRMED: '앵커 확정',
  FAILED: '앵커 실패',
}

/** 이 상태에서 더 기다리면 바뀌는가. 폴링을 언제 멈출지 여기서 정한다 */
export const isAnchorSettling = (s: ProofAnchorStatus) => s === 'WAITING' || s === 'PENDING'

/* 조회 경로만 둔다. 이 파일은 타입·헬퍼 전용이고 호출은 화면이 useApiQuery 로
   한다 — D-01·D-02 가 '/anchors' 를 그렇게 쓰고 있어 관례를 따른다. */
export const proofPath = (predictionId: string | number) => `/predictions/${predictionId}/proof`
