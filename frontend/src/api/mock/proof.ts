/* 목업 응답. 로컬 DB 에 커밋·배치가 0행이라 부를 예측이 없어서 둔다.
   GET /predictions/{id}/proof 자체는 백엔드 ANT-CHAIN-06 으로 이미 열려 있다.

   지우는 절차 — api/insight.ts 와 같다
   1. api/proof.ts 의 MOCK 을 false 로 바꾼다
   2. 이 파일과 proof.ts 의 `if (MOCK)` 한 줄, import 한 줄을 지운다
   화면 코드는 손대지 않는다.

   ── 이 목의 값은 지어낸 것이 아니다 ────────────────────────
   배치 1번은 SSAFY 체인에 실제로 올라가 있다.
     tx    0xbb82ebdc8a0fc2f728b6b780654c4c6d9baf260a56765d429e6ca4cee0730fac
     block 11186707 · 커밋 7건 · chainId 31221
   Anchored 이벤트에서 리프 7개를 그대로 읽어 3번째(leafIndex 2)의 proof 를 접었고,
   컨트랙트 isIncluded 가 온체인에서 true 를 돌려주는 것까지 확인했다.
   그래서 이 목으로도 ②단계는 진짜 체인을 읽어 진짜로 통과한다 — 목 데이터에
   맞춰 초록불이 켜지도록 화면을 속인 것이 아니다.

   payload · settle · 서명 쪽 값은 지어냈다. 커밋 원문이 남아 있지 않아서인데,
   ①단계가 어차피 규격 미확정으로 잠겨 있어(proof.ts ProofPayload 주석) 검산에 쓰이지 않는다.

   일부러 세 가지를 넣었다
   - 1번 판정 완료 + 앵커 확정 → ② 전부 통과, ③ 재료 있음
   - 2번 미판정 + 배치 대기   → 만기 전이라 ①③ 잠기고 ②도 "아직 배치 전"
   - 3번 proof 한 칸 훼손     → 복원 루트가 어긋나는 붉은 화면을 실제로 보기 위한 것 */
import { ApiError } from '../errors'
import type { Proof } from '../proof'

/** 목이라도 네트워크처럼 비동기여야 로딩 상태가 실제로 지나간다 */
const LATENCY_MS = 220

function delay<T>(value: T): Promise<T> {
  return new Promise((resolve) => setTimeout(() => resolve(value), LATENCY_MS))
}

/** 체인에 실제로 올라간 배치 1번의 3번째 리프 */
const COMMIT_HASH = '0x9070d5d078a005515eb9f429e807c986a58005b3e4971c476223a54f4f8b9d30'
const MERKLE_PROOF = [
  '0xf7af94deec6a656ebed6395998932d188cac28dc5dd4baf1e0dd5365a96f43ea',
  '0xadf117e0c76ef7cd0ad2946ca895b1d4b779714b85b459b1c7492b169b6bd20f',
  '0xf0a8cf27fff14fb0b40f5f51a658c21fe92879876f592bda60d5c8e9ffa69cfc',
]
const CONTRACT = '0x07f8CfE2bc6174D62BE8226E5e8699ffBf0d6D6a'

const ANCHORED: Proof['anchor'] = {
  batchId: 1,
  merkleRoot: '0x5c9ab3575dd06778a212d688fffa23728ddc6a1eb23a84d7d91c7e62ba365163',
  merkleProof: MERKLE_PROOF,
  leafIndex: 2,
  leafCount: 7,
  status: 'CONFIRMED',
  txHash: '0xbb82ebdc8a0fc2f728b6b780654c4c6d9baf260a56765d429e6ca4cee0730fac',
  blockNumber: 11186707,
  confirmedAt: '2026-09-04T09:31:12Z',
  contractAddress: CONTRACT,
  chainId: 31221,
}

/** 판정까지 끝나 salt 가 공개된 예측 */
const SETTLED: Proof = {
  predictionId: 1,
  payload: {
    stockCode: '005930',
    direction: 'UP',
    targetPrice: 78000,
    horizon: 10,
    noteHash: null,
    createdAt: '2026-08-21T00:41:07Z',
  },
  commitHash: COMMIT_HASH,
  signature:
    '0x9d2f4a1c8b7e05d3a6f19c4b2e8d70a5c3f61b9e4d28a7c05f3b1e6d94a2c8f7'
    + '1b4e7d0a3c96f25b8e1d4a7c0f3b6e9d2a5c8f1b4e7d0a3c96f25b8e1d4a7c0f1b',
  signerAddress: '0x8f3c2a1d9e7b4c60f5a8d2e1b7c40395a6f1d2e8',
  revealedAt: '2026-09-04T15:02:00Z',
  salt: 'b1f0a94c73d8e26510af3c8d97b24e05',
  noteSalt: null,
  anchor: ANCHORED,
  anchorStatus: 'CONFIRMED',
  settle: {
    status: 'HIT',
    settleDate: '2026-09-04',
    settlePrice: 79400,
    basePrice: 74100,
    errorRate: 1.79,
    sourceUrl:
      'https://apis.data.go.kr/1160100/service/GetStockSecuritiesInfoService/getStockPriceInfo'
      + '?resultType=json&basDt=20260904&likeSrtnCd=005930',
  },
}

/** 아직 만기 전 — salt 도 판정도 없고 배치에도 안 들어갔다 */
const WAITING: Proof = {
  predictionId: 2,
  payload: {
    stockCode: '000660',
    direction: 'DOWN',
    targetPrice: 214000,
    horizon: 20,
    noteHash: null,
    createdAt: '2026-09-06T01:12:44Z',
  },
  commitHash: '0x2c7b41e0a95d38f6410c8ba27e5d09f3b6a1c4e8d720f5a93b1c6e0d47a82f35',
  signature: null,
  signerAddress: '0x8f3c2a1d9e7b4c60f5a8d2e1b7c40395a6f1d2e8',
  revealedAt: null,
  salt: null,
  noteSalt: null,
  anchor: null,
  anchorStatus: 'WAITING',
  settle: null,
}

/** proof 마지막 칸을 한 글자 바꿨다 — 복원 루트가 어긋나는 화면을 보기 위한 것 */
const TAMPERED: Proof = {
  ...SETTLED,
  predictionId: 3,
  anchor: {
    ...ANCHORED,
    merkleProof: [
      MERKLE_PROOF[0],
      MERKLE_PROOF[1],
      '0xf0a8cf27fff14fb0b40f5f51a658c21fe92879876f592bda60d5c8e9ffa69cfd',
    ],
  },
}

const CASES: Record<string, Proof> = { 1: SETTLED, 2: WAITING, 3: TAMPERED }

export function proof(predictionId: string | number): Promise<Proof> {
  const found = CASES[String(predictionId)]
  if (!found) {
    // 없는 예측은 실제 서버와 같은 404 다. 화면의 ErrorState 경로도 목에서 밟아 본다.
    return Promise.reject(
      new ApiError({ code: 'PREDICTION_NOT_FOUND', message: 'mock', field: 'predictionId' }, 404),
    )
  }
  return delay(found)
}
