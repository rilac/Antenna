# 배포 기록 — 운영 한 벌 + 로컬 Hardhat (ANT-CHAIN-12)

SSAFY 체인(chainId 31221, `wss://ws.ssafy-blockchain.com`, 가스 0)에는 **운영 한 벌만** 쓴다.
실서버 테스트도 운영 컨트랙트로 하고, 그대로 서비스한다. 로컬 개발은 **Hardhat 로컬 체인**(chainId 31337)에서 한다.

역할(`ANCHOR_ROLE` · `OPERATOR_ROLE`)은 컨트랙트마다 **특정 주소**에 주어진다. 그래서 키와 컨트랙트는 한 벌로 움직이고,
노트북 키에는 운영 권한을 주지 않는다 — 로컬이 운영 장부를 건드릴 길 자체를 없앤다.

**이 파일이 단일 진실이다.** 주소를 바꾸면 여기와 "넣는 곳" 을 같이 바꾼다.

> 2026-09-11 오후엔 개발/운영 두 벌이었다. 같은 날 저녁 유저 결정으로 개발 세트를 폐기했다 — 운영 서버를 테스트 코인에 붙이면
> 로컬 테스트가 운영 장부로 흘러들고, 서버 한 대는 한 세트만 가리키므로 "세트는 하나, 로컬은 체인 밖(Hardhat)" 이 가장 단순하다.

## 운영 (prod) — SSAFY 체인

| 항목 | 값 | 보관 · 넣는 곳 |
|---|---|---|
| CommitAnchor | `0x8fDb4010b120DFf5c2d9b4c990821aeA00331C7e` (블록 11241834) | ANT-CHAIN-13 에서 v3 로 교체 예정(한 번도 안 씀) |
| PredictToken (ANT) | `0xEee56721cd2c0383139756c88B6DB06024a412Cb` (블록 11241835) | **서비스 ANT. 재배포 금지 — 잔액이 여기 산다** |
| 관리자 (`DEFAULT_ADMIN_ROLE`, 두 컨트랙트 공통) | `0xed9A22D3c39e5ddf161638b82a18a96134707491` | 비밀번호 관리자. 서버·저장소에 없다 |
| 릴레이어 = 오퍼레이터 (`ANCHOR_ROLE` · `OPERATOR_ROLE`) | `0x55085F3F5568ED3936A734DbaEF179F8045322E3` | GitLab CI/CD 변수 `RELAYER_PRIVATE_KEY` (Masked · Protected) |
| 수납 (플랫폼 30%) | `0x1E189420b1Abb9F1ff3A556bd068C5B03b6B17bB` | 비밀번호 관리자 |
| 인덱서 시작 블록 | 11241834 | — |
| 주소를 넣는 곳 | `.gitlab-ci.yml` 의 `.env` 블록 | — |

- **테스트 흔적은 체인에 영구히 남는다.** 서비스 화면에서 걸러 보여 준다. 서비스 시작 때의 정리 방식(테스트 잔액을 오퍼레이터 `burn` 으로 소각 ·
  DB 초기화 vs 기준 시점 필터)은 보류다.
- 체인은 RPC 로 누구나 읽을 수 있다. "안 보여 준다" 는 화면 이야기지 비밀이 아니다. 앵커에는 해시만 올라가 예측 내용은 안 읽힌다.

## 로컬 — Hardhat 로컬 체인

```bash
cd contracts
npx hardhat node          # 터미널 1 — chainId 31337, 계정 20개(키가 화면에 찍힌다. 공개된 테스트 키다)
npm run deploy:local      # 터미널 2 — deployments/local/CommitAnchor.json (signer 0 = 관리자, signer 1 = 릴레이어)
```

`backend/.env` 에 `CHAIN_RPC_URL=ws://127.0.0.1:8545` · `CHAIN_ID=31337` · `CONTRACT_COMMIT_ANCHOR=<local 주소>` ·
`RELAYER_PRIVATE_KEY=<hardhat node 가 찍은 Account #1 키>` · `INDEXER_FROM_BLOCK=0`. 노드를 재시작하면 체인이 새로 생기므로 다시 배포한다.
PredictToken 의 로컬 배포 스크립트는 아직 없다 — 토큰 기능을 로컬에서 돌릴 때 `deploy.js` 에 붙인다.

## 폐기 — 옛 dev 배포본 (2026-09-11 저녁)

| 항목 | 값 |
|---|---|
| CommitAnchor v2 | `0x07f8CfE2bc6174D62BE8226E5e8699ffBf0d6D6a` (batchId 1~4 소모) |
| PredictToken | `0xe11d728b157240DCf4a8c831176D248BAFD33077` (테스트 mint 흔적) |
| 관리자 · 릴레이어 · 수납 | `0x22B5…DEA1` · `0xb7f2…2a47` · `0x893D…7fb3` |

기록(`dev/*.json`)과 흔적표(`contracts/README.md`)는 역사로 남긴다. 새로 쓰지 않는다 — `DEPLOY_ENV=dev` 배포도 하지 않는다.

## 폴더

```
deployments/
├── prod/    CommitAnchor.json · PredictToken.json   ← 운영
├── local/   CommitAnchor.json                        ← Hardhat (npm run deploy:local)
└── dev/     CommitAnchor.json · PredictToken.json   ← 폐기(역사)
```

- 각 JSON 의 `environment` 필드와 폴더 이름이 같다.
- SSAFY 배포 스크립트는 `DEPLOY_ENV` 가 없으면 tx 를 보내기 전에 멈춘다.
- `prod/` 에 기록이 있으면 `DEPLOY_ENV=prod` 배포도 멈춘다 — 잔액(PredictToken)과 batchId(CommitAnchor v2)가 그 주소에 묶여 있다.

## 짝이 맞는지 확인하는 법

- **서버 부팅 로그** (`ChainRoleCheck`) — 키·RPC·주소가 다 있으면 부팅 직후 한 번:
  ```
  체인 환경 확인 — 릴레이어 0x5508…
    CommitAnchor 0x8fdb… ANCHOR_ROLE ✓
    PredictToken 0xeee5… OPERATOR_ROLE ✓
  ```
  ✗ 가 하나라도 있으면 ERROR 로 뜬다. 부팅은 막지 않는다.
- **실체인 테스트** `ChainRoleCheckE2ELiveTest` — env 의 키·주소 짝이 ✓ 인지(Hardhat 로컬 체인에도 돌릴 수 있다).

## prod 를 만든 절차 (2026-09-11, 한 번 돌렸다)

```bash
cd contracts && npm ci && npx hardhat compile
# 키 3개 — 주소만 화면에, 비밀키는 저장소 밖 파일로
node scripts/keygen.mjs --out <저장소 밖>/prod-keys.env prod-admin prod-relayer prod-treasury
set -a; . <저장소 밖>/prod-keys.env; set +a
DEPLOY_ENV=prod ANCHOR_ADMIN_PRIVATE_KEY=$PROD_ADMIN_PRIVATE_KEY ANCHOR_RELAYER=$PROD_RELAYER_ADDRESS npm run deploy:ssafy
DEPLOY_ENV=prod ANCHOR_ADMIN_PRIVATE_KEY=$PROD_ADMIN_PRIVATE_KEY TOKEN_OPERATOR=$PROD_RELAYER_ADDRESS \
  TOKEN_TREASURY=$PROD_TREASURY_ADDRESS npm run deploy:token:ssafy
```

그 뒤: 관리자·수납 키 → 비밀번호 관리자, 릴레이어 키 → GitLab CI/CD 변수, 키 파일 삭제.

## 운영 컨트랙트를 바꿔야 할 때

| 상황 | 할 일 |
|---|---|
| **릴레이어 키가 샜다** | 재배포하지 않는다. 운영 관리자 키로 두 컨트랙트에 `revokeRole(옛 주소)` + `grantRole(새 주소)` → GitLab 변수 교체 → 서버 재배포 |
| **관리자 키가 샜다** | 공격자가 역할을 가져갈 수 있다. 재배포밖에 없다 — 아래 두 줄 |
| **CommitAnchor 를 바꿔야 한다** | 옛 앵커는 옛 주소에 남는다(`anchor_batches.contract_address` 가 배치별 주소를 든다). `prod/CommitAnchor.json` 을 `prod/archive/` 로 옮긴 뒤 배포 |
| **PredictToken 을 바꿔야 한다** | 재배포가 아니라 v2 이관(`contracts/README.md` 함정 5). 잔액이 옛 주소에 산다 |

어느 경우든 이 표 · `.gitlab-ci.yml` · API 명세 §4 를 같이 바꾼다.
