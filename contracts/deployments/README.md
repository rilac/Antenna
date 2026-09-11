# 배포 기록 — 환경 두 벌 (ANT-CHAIN-12)

같은 SSAFY 체인(chainId 31221, `wss://ws.ssafy-blockchain.com`, 가스 0)에 컨트랙트가 **환경마다 한 벌씩** 산다. 키도 환경마다 따로다.

역할(`ANCHOR_ROLE` · `OPERATOR_ROLE`)은 컨트랙트마다 **특정 주소**에 주어진다. 그래서 한 환경의 키로 다른 환경의 컨트랙트는
못 움직인다 — 키와 컨트랙트는 한 벌로 움직인다.

**이 파일이 단일 진실이다.** 주소를 바꾸면 여기와 아래 "넣는 곳" 을 같이 바꾼다.

## 두 벌

| | dev (개발·테스트) | prod (운영) |
|---|---|---|
| 누가 쓰나 | 팀원 로컬 서버 · `*E2ELiveTest` · 데모 스크립트 | master 배포 서버(stock-antenna.com)만 |
| CommitAnchor v2 | `0x07f8CfE2bc6174D62BE8226E5e8699ffBf0d6D6a` | `0x8fDb4010b120DFf5c2d9b4c990821aeA00331C7e` |
| └ 배포 블록 | 11186682 (2026-09-04) | 11241834 (2026-09-11) |
| PredictToken (ANT) | `0xe11d728b157240DCf4a8c831176D248BAFD33077` | `0xEee56721cd2c0383139756c88B6DB06024a412Cb` |
| └ 배포 블록 | 11233486 (2026-09-10) | 11241835 (2026-09-11) |
| 관리자 (`DEFAULT_ADMIN_ROLE`, 두 컨트랙트 공통) | `0x22B51fF89E742234A0A107C5E3e8B487D91dDEA1` | `0xed9A22D3c39e5ddf161638b82a18a96134707491` |
| 릴레이어 = 오퍼레이터 (`ANCHOR_ROLE` · `OPERATOR_ROLE`) | `0xb7f2De5b4821EE386Aeac037b096f28693cA2a47` | `0x55085F3F5568ED3936A734DbaEF179F8045322E3` |
| 수납 (플랫폼 30%) | `0x893D17a14FA7eC3Ef4249c2AC53B1DD5e1ec7fb3` | `0x1E189420b1Abb9F1ff3A556bd068C5B03b6B17bB` |
| 인덱서 시작 블록 | 11186682 | 11241834 |
| 관리자 키 보관 | `backend/.env` `ANCHOR_ADMIN_PRIVATE_KEY` (개발망 편의, 09-04 결정) | 비밀번호 관리자. **서버·저장소에 없다** |
| 릴레이어 키 보관 | `backend/.env` `RELAYER_PRIVATE_KEY` | GitLab CI/CD 변수 `RELAYER_PRIVATE_KEY` (Masked · Protected) |
| 수납 키 보관 | `backend/.env` `TOKEN_TREASURY_PRIVATE_KEY` | 비밀번호 관리자 |
| 주소를 넣는 곳 | 각자 `backend/.env` (`backend/.env.example` 주석 참고) | `.gitlab-ci.yml` 의 `.env` 블록 |
| 다시 배포 | 된다(가스 0). PredictToken 은 테스트 잔액이 사라지니 알리고 한다 | **스크립트가 거부한다** — 아래 "운영 컨트랙트를 바꿔야 할 때" |
| 소모된 batchId | 1~4 (`contracts/README.md` 함정 2 표) | 운영 DB `anchor_batches.id` 만 쓴다. 손으로 태우지 않는다 |

## 폴더

```
deployments/
├── dev/     CommitAnchor.json · PredictToken.json
├── prod/    CommitAnchor.json · PredictToken.json
└── local/   CommitAnchor.json   (Hardhat 로컬 노드 — npm run deploy:local)
```

- 각 JSON 의 `environment` 필드와 폴더 이름이 같다.
- 배포 스크립트는 `DEPLOY_ENV=dev|prod` 가 없으면 tx 를 보내기 전에 멈춘다.
- `prod/` 에 기록이 있으면 `DEPLOY_ENV=prod` 배포도 멈춘다 — 잔액(PredictToken)과 batchId(CommitAnchor)가 그 주소에 묶여 있다.

## 짝이 맞는지 확인하는 법

- **서버 부팅 로그** (`ChainRoleCheck`) — 키·RPC·주소가 다 있으면 부팅 직후 한 번:
  ```
  체인 환경 확인 — 릴레이어 0x5508…
    CommitAnchor 0x8fdb… ANCHOR_ROLE ✓
    PredictToken 0xeee5… OPERATOR_ROLE ✓
  ```
  ✗ 가 하나라도 있으면 ERROR 로 뜬다. 부팅은 막지 않는다.
- **실체인 테스트** — 개발 키가 개발 컨트랙트엔 ✓, 운영 컨트랙트엔 ✗ 인지:
  ```bash
  set -a; . backend/.env; set +a
  cd backend && ./gradlew test --tests '*ChainRoleCheckE2ELiveTest*'
  ```

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
| **릴레이어 키가 샜다** | 재배포하지 않는다. prod 관리자 키로 두 컨트랙트에 `revokeRole(옛 주소)` + `grantRole(새 주소)` → GitLab 변수 교체 → 서버 재배포 |
| **관리자 키가 샜다** | 공격자가 역할을 가져갈 수 있다. 재배포밖에 없다 — 아래 두 줄 |
| **CommitAnchor 를 바꿔야 한다** | 새 컨트랙트는 batchId 가 비어 있지만 운영 DB 번호는 이어진다 — 충돌은 없다(새 컨트랙트엔 옛 번호가 없다). 옛 앵커는 옛 주소에 남는다(`anchor_batches.contract_address`). `prod/CommitAnchor.json` 을 `prod/archive/` 로 옮긴 뒤 배포 |
| **PredictToken 을 바꿔야 한다** | 재배포가 아니라 v2 이관(`contracts/README.md` 함정 5). 잔액이 옛 주소에 산다 |

어느 경우든 이 표 · `.gitlab-ci.yml` · API 명세 §4 를 같이 바꾼다.
