# contracts — CommitAnchor v2 (ANT-CHAIN-01 · ANT-CHAIN-08)

앤테나의 앵커 컨트랙트. 매일 봉인된 예측 커밋들의 **머클루트 1건**을 체인에 박는다.
루트가 체인에 올라간 뒤에는 서버가 예측을 고쳐 쓸 수 없다 — 고치면 머클 증명이 깨진다.

**v2(ANT-CHAIN-08)에서 바뀐 것**: 앵커 tx 에 그 배치의 **커밋 해시 전량**을 싣는다.
컨트랙트가 그 리프들로 루트를 **다시 계산해 서버가 준 루트와 대조**하고(`RootMismatch`),
커밋 해시 목록을 `Anchored` 이벤트로 남긴다. **이벤트가 곧 백업이다** — DB 가 없어도
체인만 읽어 트리와 증명 경로를 되살릴 수 있다(`scripts/rebuild-from-chain.mjs`).

## 명령

```bash
npm install
npm run compile        # solc 0.8.28 · evmVersion=paris (함정 1)
npm test               # 41케이스 — Java 픽스처와 크로스 검증 포함

npm run keygen admin   # 키 생성(출력만 한다. 사람이 옮긴다)
npm run keygen relayer

# 로컬 배포 — signer 0 = 관리자, signer 1 = 릴레이어
npx hardhat node       # 터미널 1 (chainId 31337)
npm run deploy:local   # 터미널 2

# SSAFY 네트워크 배포 (chainId 31221) — 관리자 키로 배포, 릴레이어는 주소만
ANCHOR_ADMIN_PRIVATE_KEY=0x…  ANCHOR_RELAYER=0x…  npm run deploy:ssafy

# 복구 검증 — 체인만 읽어 batchId 의 트리를 재구축해 rootOf 와 대조
npm run rebuild -- --batch-id 1 [--out ./bundles]
# 실체인 E2E — 앵커 → 체인만으로 재구축 → 위조 대조군 → 역할 분리 확인
RELAYER_PRIVATE_KEY=0x… npm run demo:recovery -- --batch-id 2 --leaves 5
```

배포하면 `deployments/<network>.json`(커밋 대상)에 주소·관리자·릴레이어가 기록되고, ABI 가
`backend/src/main/resources/abi/CommitAnchor.json` 으로 복사된다.
주소는 `backend/.env` 의 `CONTRACT_COMMIT_ANCHOR` 에, 릴레이어 키는 `RELAYER_PRIVATE_KEY` 에 손으로 넣는다.

## 키 두 개

| 키 | 롤 | 할 수 있는 것 | 보관 |
|---|---|---|---|
| 관리자 | `DEFAULT_ADMIN_ROLE` | `grantRole` / `revokeRole`. **anchor 는 못 한다** | 서버 밖(비밀번호 관리자) |
| 릴레이어 | `ANCHOR_ROLE` | `anchor` 만. 롤을 나눠 주지 못한다 | `backend/.env` |

릴레이어 키가 새면 피해는 "안 쓴 batchId 에 쓰레기 루트를 올린다"까지다. 관리자 키로
`revokeRole` + 새 키 `grantRole` 하면 재배포 없이 끝난다. 관리자 키까지 서버에 있으면
공격자가 롤을 영구히 가져가 재배포밖에 답이 없다 — 그래서 나눈다.

**지금 상태 (2026-09-04)**: 두 키 모두 `backend/.env` 에 있다(`RELAYER_PRIVATE_KEY` · `ANCHOR_ADMIN_PRIVATE_KEY`).
개발망(가스 0·자산 0)이라 편의를 택했다. 서버 코드는 관리자 키를 읽지 않는다. 운영 전환 시 관리자 키만 빼서 비밀번호 관리자로.

```bash
# 배포 (관리자 키로 서명, 릴레이어는 주소만)
ANCHOR_ADMIN_PRIVATE_KEY=$(grep ^ANCHOR_ADMIN_PRIVATE_KEY= ../backend/.env | cut -d= -f2) \
ANCHOR_RELAYER=0xb7f2De5b4821EE386Aeac037b096f28693cA2a47 npm run deploy:ssafy
```

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
| 운영 DB 복구·이관 | `ALTER TABLE anchor_batches ALTER COLUMN id RESTART WITH <온체인 최대 batchId + 1>` (identity 컬럼이라 `ALTER SEQUENCE` 가 아니다) |

`BatchAlreadyAnchored` 가 갑자기 계속 난다면 컨트랙트 버그가 아니라 십중팔구 이 상황이다.

**⚠️ 현재 SSAFY 배포본(`deployments/ssafy.json`, `0x07f8CfE2…6D6a`)의 소모된 batchId — 여기에 계속 적는다:**

| batchId | 누가 | 언제 | 루트(앞 8자) |
|---|---|---|---|
| 1 | CHAIN-08 복구 데모(`demo-recovery.mjs`, 7리프) | 2026-09-04 | `0x5c9ab357` |
| 2 | CHAIN-05 Live 테스트 첫 실행(3리프) | 2026-09-04 | — |
| 3 | CHAIN-05 Live 테스트(3리프) | 2026-09-04 | `0x95a9ac4e` |
| 4 | CHAIN-02 PostgreSQL 실기 — 서버 스케줄러가 보낸 첫 배치(2리프) | 2026-09-04 | `0xe44f2a9f` |

서버가 이 컨트랙트에 처음 앵커할 때 DB `anchor_batches` 시퀀스가 위 번호와 겹치면 서버는 체인 루트와 자기 루트를 비교해
**`BATCH_ID_COLLISION` 으로 FAILED** 처리한다(잘못 확정하진 않는다 — ANT-CHAIN-02). 그래도 그 배치는 사람이 풀어야 한다. 대응은 둘 중 하나 — ① 컨트랙트를 새로 배포하고 `CONTRACT_COMMIT_ANCHOR` 교체(운영 권장, 가스 0)
② `ALTER TABLE anchor_batches ALTER COLUMN id RESTART WITH <표의 최대 + 1>` (로컬 개발용). 로컬 실기에서 새 번호를 태웠으면 표에 추가한다.

인덱서(ANT-CHAIN-04)는 이 표의 번호를 전부 `Anchored` 이벤트로 받는다. 내 DB 에 없는 번호는 "DB 에 없는 배치" 경고 한 줄로 남고
`chain_events` 에는 적재된다 — 정상이다. 내 DB 의 배치와 번호는 같은데 루트가 다르면 그 배치를 `BATCH_ID_COLLISION` FAILED 로 바꾼다.
재배포하면 `INDEXER_FROM_BLOCK` 을 새 배포 블록(`deployments/ssafy.json` 의 `blockNumber`)으로 올려라 — 옛 주소의 이벤트는 주소 필터로 어차피 안 오지만, 첫 동기화가 배포 이전 구간을 헛되이 훑는다.

## 함정 3 — SSAFY 배포는 Hardhat 을 거치지 않는다

SSAFY 가 공개한 RPC 는 `wss://ws.ssafy-blockchain.com` 웹소켓 하나뿐인데, Hardhat 2 의
네트워크 `url` 은 HTTP 전용이다. 그래서 `deploy-ssafy.mjs` 는 ethers 로 직접 붙는다.
컴파일 산출물은 같은 `artifacts/` 를 읽으므로 두 경로의 바이트코드는 동일하다.

## 함정 4 — `RootMismatch` 는 재시도할 일이 아니다

서버 `MerkleTree.java` 와 컨트랙트 `_computeRoot` 가 같은 규격(리프 = keccak256(commitHash) ·
정렬 결합 · 홀수 승격)이어야 한다. 어긋나면 첫 앵커가 `RootMismatch(expected, computed)` 로 revert 한다.
서버 쪽 규격이나 리프 순서(prediction id 오름차순)가 틀어진 것이다. 픽스처 `test/fixtures/merkle-cross-fixture.json` 을
Java 테스트와 Solidity 테스트가 같이 읽으므로 CI 에서 먼저 잡혀야 정상이다.

## 설계 요약 (근거: `.claude/docs-personal/impl/ANT-CHAIN-08/plan.md`)

- `anchor(batchId, merkleRoot, commitHashes[])` — `ANCHOR_ROLE` 만. 루트 재계산·대조. **덮어쓰기 불가.**
- `rootOf(batchId)` — 누구나. 미앵커면 `bytes32(0)`.
- `isIncluded(batchId, commitHash, proof[])` — 누구나. 검증 API·FE 가 `eth_call` 로 쓴다.
- `Anchored(batchId indexed, merkleRoot, commitHashes[])` — 인덱서 구독 이벤트. 순서 = 리프 순서.
- batchId 는 서버가 정한다 = 온체인 멱등키. 업그레이더블 프록시 안 씀. 리프는 storage 에 안 둔다.

---

# PredictToken — ANT 서비스 토큰 (ANT-CHAIN-03)

앤테나의 토큰. **전송 불가**이고, 잔액을 움직이는 길은 서버 오퍼레이터의 `mint / burn / subscribe` 와
보유자 자가 소각 `burnSelf`, 그리고 위성 컨트랙트용 `operatorTransfer`(MOVER_ROLE) 뿐이다.
설계 근거: `.claude/docs-personal/impl/ANT-CHAIN-03/plan.md`.

**SSAFY 배포본 (2026-09-10)** — `deployments/ssafy.PredictToken.json`

| 항목 | 값 |
|---|---|
| 주소 | `0xe11d728b157240DCf4a8c831176D248BAFD33077` |
| 배포 블록 | `11233486` → 토큰 인덱서(ANT-CHAIN-11) 시작 블록 |
| 이름 / 심볼 / decimals | `Antenna` / `ANT` / **0** |
| 관리자 | `0x22B5…DEA1` (CommitAnchor 와 같은 키, `ANCHOR_ADMIN_PRIVATE_KEY`) |
| 오퍼레이터 | `0xb7f2…2a47` (CommitAnchor 릴레이어와 같은 키, `RELAYER_PRIVATE_KEY`) |
| 수납(treasury) | `0x893D…7fb3` (`TOKEN_TREASURY_PRIVATE_KEY`, 서버는 안 읽음) |
| 플랫폼 몫 | 30% 상수 (`PLATFORM_SHARE_BPS = 3000`), 잔돈은 예측가에게 |

## 명령

```bash
npm test                                   # CommitAnchor 41 + PredictToken 46
npm run keygen treasury                    # 수납 주소 키 (출력만 한다)
ANCHOR_ADMIN_PRIVATE_KEY=0x… TOKEN_OPERATOR=0x… TOKEN_TREASURY=0x… npm run deploy:token:ssafy
```

배포하면 `deployments/ssafy.PredictToken.json` 과 `backend/src/main/resources/abi/PredictToken.json` 이 갱신된다.
주소는 `backend/.env` 의 `CONTRACT_PREDICT_TOKEN` 에 넣는다.

## 단위 — decimals 0, 1 ANT = 1 원 상당

`2000` 이 곧 2,000 ANT 다. 10¹⁸ 을 곱하지 않는다. DB `numeric(30,0)` 과 API 문자열에 이 정수가 그대로 들어간다.
지갑(MetaMask 등)에 토큰을 추가하면 `1000 ANT` 로 보인다. 원화 스테이블코인을 나중에 붙여도 정수 1:1 매핑이다.

## 역할 셋

| 역할 | 누가 | 할 수 있는 것 |
|---|---|---|
| `DEFAULT_ADMIN_ROLE` | 관리자 키 | 역할 주고 뺏기 · `setTreasury`. **잔액은 못 건드린다** |
| `OPERATOR_ROLE` | 서버 릴레이어 키 | `mint(to, amount, reason)` · `burn(from, amount, reason)` · `subscribe(subscriber, creator, amount)` |
| `MOVER_ROLE` | **위성 컨트랙트 주소만** (배포 시 0명) | `operatorTransfer(from, to, amount)` |

- **서버는 사용자 토큰을 승인 없이 태울 수 있다.** 사용자 동의는 서버가 EIP-191 서명으로 검증하고 체인은 서명을 모른다
  (CommitAnchor 와 같은 구조). 팀이 알아야 하는 사실이라 여기 적는다.
- 서버 키에는 MOVER 를 주지 않는다. 그래서 "서버도 마음대로 이체 못 한다" 가 유지되고, 이체할 수 있는 건 코드가 공개된 컨트랙트뿐이다.
- 키 유출 대응은 CommitAnchor 와 같다 — 관리자 키로 `revokeRole` + `grantRole`. 재배포 없음.

## 함정 5 — 이 컨트랙트는 재배포하지 않는다. 잔액이 여기 산다

CommitAnchor 는 재배포가 공짜였다(과거 앵커는 옛 주소에 남는다). 토큰은 다르다 — **재배포하면 모든 잔액이 새 주소에 없다.**

| 바꾸는 것 | 길 |
|---|---|
| 금액표 · reason 어휘 · 정책 | 서버만 고친다. 컨트랙트는 정책을 모른다 |
| 새 잔액 이동 패턴(상금 분배·경매·수익 재분배·스테이블코인 교환) | **위성 컨트랙트**를 배포하고 관리자가 그 주소에 `MOVER_ROLE` 을 준다. 토큰은 안 바뀐다 |
| 토큰 규칙 자체(decimals·이름·전송 규칙·70:30) | **v2 이관**: v1 `Transfer` 로그로 잔액 스냅샷 → v2 에 `mint(…, "MIGRATION")` 일괄 → v1 OPERATOR·MOVER 회수(동결) → `.env`·인덱서 시작 블록·지갑 재등록. 가스 0·사용자 수백 명이라 몇 분짜리다. 스크립트는 필요해질 때 만든다 |

`.env` 의 `CONTRACT_PREDICT_TOKEN` 을 바꾸는 순간 서버가 보는 잔액 원천이 바뀐다. 이관 없이 바꾸지 마라.

## 함정 6 — `burn` 은 오버로드가 아니다

보유자 자가 소각은 `burnSelf(amount, reason)` 다. `burn` 과 같은 이름으로 두면 ethers 가 `token['burn(uint256,bytes32)']` 처럼
시그니처 문자열로만 부를 수 있어 프론트·스크립트 호출이 전부 그 꼴이 된다. web3j 도 같은 이름이면 인코딩 시 헷갈린다.

## 팀 공용 배포본에 남긴 흔적 — 여기에 계속 적는다

가스가 0 이라 tx 는 공짜지만 이벤트는 영원히 남는다. 인덱서 ② 가 붙으면 아래 행이 `token_ledger` 후보로 올라온다(DB 에 없는 지갑이라 경고로 남는 게 정상).

| 블록 | 무엇 | 누구에게 | reason | tx |
|---|---|---|---|---|
| 11233493 | `mint 1000` | `0x9795…9dC0` (일회용 테스트 지갑) | `SIGNUP_BONUS` | `0x663377b8…` |
| 11233494 | `burn 1000` | 같은 지갑 → 잔액 0, totalSupply 0 | `SLOT_OVER` | `0x7f930bd5…` |

그 사이에 그 지갑으로 `transfer`·`approve` 를 시도해 둘 다 `TransferDisabled()` 로 revert 하는 것을 확인했다(eth_call).

## 설계 요약

- `decimals() = 0`. `transfer / transferFrom / approve` → `TransferDisabled()`. `allowance` 는 항상 0.
- `subscribe` 는 한 tx 안에서 예측가 몫·플랫폼 몫 두 번 `_transfer`. 어느 쪽에서 막혀도 전체 되감김.
  `platformShare = amount × 3000 / 10000` (내림), `creatorShare = amount − platformShare`.
- 이벤트 `Minted(to, amount, reason)` · `Burned(from, amount, reason)` · `Subscribed(subscriber, creator, amount, creatorShare, platformShare)` —
  인덱서는 이 셋만 읽는다. `reason` 은 `bytes32 indexed`(ASCII 왼쪽 정렬, `token_ledger.reason` varchar(32) 와 길이가 같다).
  ERC-20 표준 `Transfer` 는 지갑 표시용이다. `operatorTransfer` 는 사유 이벤트를 내지 않는다 — 사유는 위성 컨트랙트가 자기 이벤트로 남긴다.
- 커스텀 에러: `TransferDisabled · SelfSubscribe · ZeroAmount · ZeroAddress · SameAddress` + OZ `ERC20InsufficientBalance · AccessControlUnauthorizedAccount`.
