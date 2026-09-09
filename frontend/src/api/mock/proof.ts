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

   1번의 payload · settle · 서명 쪽 값은 지어냈다. 저 commitHash 는 체인에 실제로 올라간
   리프라 값을 바꿀 수 없고(바꾸면 ②가 깨진다) 커밋 원문은 남아 있지 않다. 그래서 1번은
   noteHash 가 없고 ①이 "재료 없음" 으로 잠긴다 — 여기에 아무 noteHash 나 넣으면 ①이
   붉게 뜨는데, 그건 목업 사정을 조작 고발로 그리는 것이다.

   일부러 네 가지를 넣었다
   - 1번 판정 완료 + 앵커 확정 → ② 전부 통과, ③ 재료 있음. ①은 재료가 없어 잠김
   - 2번 미판정 + 배치 대기   → **①이 실제로 통과한다.** 만기 전이라 ③은 잠기고
                                ②도 "아직 배치 전". 아래 값은 규격대로 계산한 진짜다
   - 3번 proof 한 칸 훼손     → 복원 루트가 어긋나는 붉은 화면(②)을 보기 위한 것
   - 4번 commitHash 한 글자 훼손 → ①이 어긋나는 붉은 화면을 보기 위한 것 */
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
  /* 근거 salt. 서버 ERD 가 varchar(64) 라 32바이트 = 64 hex 다 */
  salt: 'b1f0a94c73d8e26510af3c8d97b24e05c6a70b3f19d82e4c50a7b1936d2ef048',
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

/* 2번의 ①단계 재료. 규격(api/predictions.ts "커밋 봉인")대로 실제로 계산한 값이라
   화면이 계산한 것과 맞아떨어진다 — 초록불이 켜지도록 화면을 속인 것이 아니다.

     note       = "메모리 가격이 3분기 정점을 지났다고 본다. 서버 교체 수요가 한 박자 쉬어 간다."
     noteSalt   = 4f1b7e2c90a3d5648f0c1e7b2a9d3546c8e0b1f7a24d69538c0e1b7a2f9d4653
     noteHash   = keccak256(utf8(note) ‖ utf8(noteSalt))
     commitHash = keccak256("antenna:commit:v1\nstockCode=000660\n…\nnoteHash=…")

   본문과 salt 는 응답에 담지 않는다 — 본문은 proof 가 주는 값이 아니고(GET /predictions/{id}
   쪽이다), salt 는 리빌 전이라 작성자에게도 가지 않는다. 여기 적어 두는 것은 이 해시가
   어디서 나왔는지 나중에 되짚기 위해서다. */
const NOTE_HASH = '0xdca767152c0fcc3d1bce0c6f5f6d992ef5c560faca41dd103e35e3328bd38354'
const COMMIT_OF_2 = '0x2c29d2749eccd8c8486d99616b01a929a7563b0516754db58358cda5b344fe28'

/** 아직 만기 전 — 판정도 없고 배치에도 안 들어갔다. salt 는 리빌 전이라 오지 않는다 */
const WAITING: Proof = {
  predictionId: 2,
  payload: {
    stockCode: '000660',
    direction: 'DOWN',
    targetPrice: 214000,
    horizon: 20,
    noteHash: NOTE_HASH,
    createdAt: '2026-09-06T01:12:44Z',
  },
  commitHash: COMMIT_OF_2,
  signature: null,
  signerAddress: '0x8f3c2a1d9e7b4c60f5a8d2e1b7c40395a6f1d2e8',
  revealedAt: null,
  salt: null,
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

/* 2번에서 commitHash 마지막 글자만 바꿨다 — 커밋 문자열은 그대로라 ①만 어긋난다.
   ②는 2번과 같이 앵커 전이라 잠긴 채다: ① 하나만 붉은 화면을 보기 위한 것이다. */
const RESEALED: Proof = {
  ...WAITING,
  predictionId: 4,
  commitHash: `${COMMIT_OF_2.slice(0, -1)}9`,
}

const CASES: Record<string, Proof> = { 1: SETTLED, 2: WAITING, 3: TAMPERED, 4: RESEALED }

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
