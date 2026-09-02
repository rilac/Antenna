# contracts — CommitAnchor (ANT-CHAIN-01)

앤테나의 유일한 자체 Solidity. 매 영업일 봉인된 예측 커밋들의 **머클루트 1건**을 체인에 박는다.
루트가 체인에 올라간 뒤에는 서버가 예측을 고쳐 쓸 수 없다 — 고치면 머클 증명이 깨진다.
서비스 토큰은 SSAFY 토큰증권 API 가 맡고, 앵커링만 그 API 범위 밖이라 직접 배포한다.

## 명령

```bash
npm install
npm run compile        # solc 0.8.28 · evmVersion=paris (아래 함정 참고)
npm test               # 단위 테스트 21케이스

# 로컬 배포
npx hardhat node       # 터미널 1 — 로컬 체인 (chainId 31337)
npm run deploy:local   # 터미널 2

# SSAFY 네트워크 배포 (chainId 31221)
RELAYER_PRIVATE_KEY=0x… npm run deploy:ssafy
```

배포하면 주소가 `deployments/<network>.json`(커밋 대상)에 기록되고, ABI 가
`backend/src/main/resources/abi/CommitAnchor.json` 으로 복사된다.
주소는 `backend/.env` 의 `CONTRACT_COMMIT_ANCHOR` 에 손으로 넣는다.

## 함정 1 — evmVersion 은 paris 에서 올리면 안 된다

SSAFY 체인엔 Shanghai 가 도입한 `PUSH0` opcode 가 없다. solc 0.8.20+ 기본 설정은 PUSH0 를
뱉기 때문에, **컴파일은 아무 경고 없이 성공하고 SSAFY 배포 트랜잭션만 revert 한다.**
에러 메시지도 `missing revert data` 뿐이라 컨트랙트 코드를 의심하게 된다. 코드 문제가 아니다.
`hardhat.config.js` 의 `evmVersion: 'paris'` 가 그 방어선이다.

## 함정 2 — DB 를 초기화하면 컨트랙트도 새로 배포해야 한다

온체인 batchId 는 DB 의 `anchor_batches.id` 를 그대로 쓴다(서버가 멱등키로 넘긴다).
그래서 **DB 수명과 컨트랙트 수명이 묶인다**:

```
docker compose down -v  →  id 시퀀스가 1 로 리셋  →  다음 앵커가 batchId=1 로 나감
                        →  체인엔 이미 batchId=1 이 있음  →  BatchAlreadyAnchored 영구 revert
```

| 상황 | 대응 |
|---|---|
| 개발 중 DB 초기화 | **컨트랙트를 새로 배포**하고 `CONTRACT_COMMIT_ANCHOR` 교체. 가스가 0 이라 비용 없음 |
| 운영 DB 복구·이관 | `ALTER SEQUENCE anchor_batches_id_seq RESTART WITH <온체인 최대 batchId + 1>` |

`BatchAlreadyAnchored` 가 갑자기 계속 난다면 컨트랙트 버그가 아니라 십중팔구 이 상황이다.

## 함정 3 — SSAFY 배포는 Hardhat 을 거치지 않는다

SSAFY 가 공개한 RPC 는 `wss://ws.ssafy-blockchain.com` 웹소켓 하나뿐인데, Hardhat 2 의
네트워크 `url` 은 HTTP 전용이다. 그래서 `deploy-ssafy.mjs` 는 ethers 로 직접 붙는다.
컴파일 산출물은 같은 `artifacts/` 를 읽으므로 두 경로의 바이트코드는 동일하다.

## 설계 요약 (자세한 근거: `.claude/docs-personal/impl/ANT-CHAIN-01/plan.md`)

- `anchor(batchId, merkleRoot, commitCount)` — `ANCHOR_ROLE` 만. **덮어쓰기 불가.**
- `rootOf(batchId)` — 누구나. 미앵커면 `bytes32(0)` (revert 하지 않는다).
- batchId 는 서버가 정한다 = 온체인 멱등키. 릴레이어 재전송이 두 번 박히는 걸 막는다.
- 업그레이더블 프록시 안 씀 — 앵커 컨트랙트에서 "관리자가 로직을 바꿀 수 있다"는
  기능이 아니라 취약점이다.
- 릴레이어 키 교체는 재배포가 아니라 `grantRole` / `revokeRole` 로 한다.
