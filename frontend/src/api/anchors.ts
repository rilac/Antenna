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
