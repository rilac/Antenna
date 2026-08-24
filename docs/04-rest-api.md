# 엔테나(Antenna) — REST API 명세

작성 2026-08-24 · 기준 문서 「엔테나 구현 기획서」 6장 · Base URL `/api`

---

## 1. 공통 규약

| 항목 | 규약 |
|---|---|
| 인증 | `Authorization: Bearer <access>`. 만료 시 `401` + `WWW-Authenticate`로 재발급 유도 |
| 인증 등급 | **공개**(비로그인) / **JWT**(로그인) / **서명**(JWT + SSAFY WALLET EIP-712 서명 동반) |
| 페이지네이션 | 전부 **커서** 방식 (`?cursor=&size=`). 예측은 append-only라 오프셋이 흔들릴 일은 없지만 커서가 더 싸다 |
| 금액 | **문자열**로 주고받는다 — 토큰이 wei 단위 정수라 JS `number` 범위를 넘는다 |
| 오류 본문 | `{ "code": "SIGNER_MISMATCH", "message": "...", "field": "signature" }` — code는 대문자 스네이크 |
| 온체인 동반 요청 | **202 Accepted** + 상태 폴링. `201`은 오프체인으로 완결되는 경우만 |
| 시각 | ISO-8601, `Asia/Seoul` |

### 인증 등급 요약

| 등급 | 조건 | 대상 |
|---|---|---|
| 공개 | 없음 | 피드·랭킹·원장·시세·프로필 — 심사위원이 지갑 없이 보는 범위 |
| JWT | 로그인 | 근거 열람(+구독), 내 정보, 시즌 주문 |
| 서명 | 지갑 서명 | 토큰이 움직이는 곳 전부 — 예측 등록·구독·증명서·백테스트·시즌 참가 |

---

## 2. 엔드포인트 총람

### 인증 · 지갑

| 메서드 | 경로 | 인증 | 설명 |
|---|---|:---:|---|
| POST | `/api/auth/{provider}/callback` | 공개 | OAuth 콜백 → access·refresh 발급 |
| POST | `/api/auth/refresh` | 공개 | refresh 회전. 재사용 탐지 시 전체 폐기 |
| POST | `/api/auth/logout` | JWT | Redis에서 refresh 삭제 |
| GET | `/api/auth/me` | JWT | 내 프로필 · 지갑 연동 여부 · 잔액 |
| POST | `/api/wallet/nonce` | JWT | SIWE 서명용 nonce 발급 |
| POST | `/api/wallet/link` | JWT | 서명 검증 → SSAFY WALLET 주소 연동 |

### 시세

| 메서드 | 경로 | 인증 | 설명 |
|---|---|:---:|---|
| GET | `/api/stocks` | 공개 | 종목 목록 + 직전 영업일 종가 |
| GET | `/api/stocks/{code}/quotes` | 공개 | `?from&to` 일봉 구간 |

### 예측

| 메서드 | 경로 | 인증 | 설명 |
|---|---|:---:|---|
| GET | `/api/predictions` | 공개 | `?track&status&userId&cursor` 피드. 근거는 제외 |
| POST | `/api/predictions` | 서명 | 등록. 슬롯 초과 시 토큰 소각 동반 |
| GET | `/api/predictions/{id}` | 공개 | 단건 |
| GET | `/api/predictions/{id}/note` | JWT | 분석 근거. 미구독 시 403 |

### 예측자 · 랭킹

| 메서드 | 경로 | 인증 | 설명 |
|---|---|:---:|---|
| GET | `/api/users/{id}` | 공개 | 프로필 + 통계 |
| GET | `/api/users/{id}/stats` | 공개 | `?track&filter` 섹터별 정확도 포함 |
| GET | `/api/users/{id}/reports` | 공개 | 목록은 공개, 본문은 구독자만 |
| POST | `/api/users/{id}/backtest` | 서명 | 팔로우 백테스트. 1,000 PRT 소각 |
| GET | `/api/rankings` | 공개 | `?track&filter` 스냅샷 조회 |

### 구독 · 증명서 · 토큰

| 메서드 | 경로 | 인증 | 설명 |
|---|---|:---:|---|
| POST | `/api/subscriptions` | 서명 | 결제 개시 → PENDING 반환 |
| GET | `/api/subscriptions/{id}` | JWT | 확정 상태 폴링 |
| GET | `/api/subscriptions/me` | JWT | 내 구독 목록 |
| POST | `/api/certificates` | 서명 | SBT 발급. 3,000 PRT 소각 |
| GET | `/api/tokens/balance` | JWT | 온체인 잔액 (캐시 + 동기화 블록) |
| GET | `/api/tokens/ledger` | JWT | 증감 내역 |

### 커밋 원장

| 메서드 | 경로 | 인증 | 설명 |
|---|---|:---:|---|
| GET | `/api/ledger/blocks` | 공개 | `?cursor` 원장 탐색기 |
| GET | `/api/ledger/anchors/{id}` | 공개 | 머클루트 · 포함 커밋 · tx |
| GET | `/api/ledger/verify/{predictionId}` | 공개 | 리빌 검증 근거 일체 |

### 리플레이 시즌

| 메서드 | 경로 | 인증 | 설명 |
|---|---|:---:|---|
| GET | `/api/seasons/current` | 공개 | 진행 중 시즌 + 게임일 |
| POST | `/api/seasons/{id}/join` | 서명 | 랭킹 시즌 참가비 1,000 PRT |
| GET | `/api/seasons/{id}/tickers` | 공개 | 종목 목록. 랭킹 시즌은 블라인드 |
| GET | `/api/seasons/{id}/me` | JWT | 예수금 · 포지션 · 평가금액 |
| POST | `/api/seasons/{id}/orders` | JWT | 당일 종가 매수·매도 |
| POST | `/api/seasons/{id}/predictions` | 서명 | 리플레이 트랙 예측 등록 |
| POST | `/api/seasons/{id}/advance` | JWT | 게임일 진행. 시연용 |

---

## 3. 주요 엔드포인트 상세

### 3-1. `POST /api/predictions` — 예측 등록 <sub>서명</sub>

서버는 서명에서 주소를 복원해 연동된 지갑과 일치하는지 확인한다. 불일치면 401. 하루 무료 슬롯 3건을 넘기면 2,000 PRT 소각 트랜잭션이 함께 나간다.

**Request**

```json
{
  "track": "REAL",
  "stockCode": "005930",
  "direction": "UP",
  "targetPrice": 82000,
  "horizon": 20,
  "confidence": 73,
  "note": "외국인 순매수 5일 연속 + 20일선 정배열 전환",
  "signature": "0x8f2c…",
  "signerAddress": "0x7a3f…c21b"
}
```

**201 Created**

```json
{
  "id": 4821,
  "status": "BASE",
  "commitHash": "df1ce27812e47a7c4dbcf89dbe…",
  "anchorStatus": "PENDING",
  "baseDate": "2026-08-25",
  "settleDate": "2026-09-22",
  "slotFeeTxHash": null
}
```

`baseDate` — 이 날 종가로 기준가 확정. `slotFeeTxHash` — 무료 슬롯 내면 `null`.

**오류**

| 코드 | 상태 | 조건 |
|---|:---:|---|
| `TARGET_DIRECTION_MISMATCH` | 400 | 상승 예측의 목표가가 직전 종가 이하 |
| `SIGNER_MISMATCH` | 401 | 서명 주소가 연동 지갑과 불일치 |
| `INSUFFICIENT_TOKEN` | 402 | 슬롯 초과분 잔액 부족 |
| `SEASON_HORIZON_EXCEEDED` | 409 | 남은 시즌보다 예측 기간이 김 (REPLAY) |

### 3-2. `POST /api/subscriptions` — 구독 결제 <sub>서명</sub>

즉시 완료되지 않는다. `PENDING`으로 응답하고 블록 확정 후 인덱서가 `ACTIVE`로 바꾼다. 클라이언트는 `GET /api/subscriptions/{id}`로 폴링한다.

**Request**

```json
{ "publisherId": 12, "signature": "0x4b1a…" }
```

**202 Accepted**

```json
{
  "id": 903,
  "status": "PENDING",
  "fee": "14900",
  "publisherShare": "10430",
  "txHash": "0x2ef9…",
  "expectedConfirmSec": 6
}
```

`publisherShare` — 70%, **컨트랙트가 계산한 값의 사본**. 금액은 wei 정수라 문자열.

### 3-3. `GET /api/ledger/verify/{predictionId}` — 제3자 검증 <sub>공개</sub>

플랫폼을 신뢰하지 않고도 검증할 수 있도록 필요한 값을 전부 내려준다. 머클 증명과 온체인 트랜잭션 해시가 함께 나가므로 브라우저에서 재계산이 가능하다.

**200 OK**

```json
{
  "predictionId": 4821,
  "payload": "005930|UP|82000|20",
  "salt": "9c4e1f77a2b30d58",
  "commitHash": "df1ce278…",
  "merkleProof": ["a91f…", "77c2…", "0b3e…"],
  "merkleRoot": "5d8a…",
  "anchorTxHash": "0x91bd…",
  "basePrice": 71800,
  "actualClose": 83100,
  "quoteSource": "공공데이터포털 금융위원회_주식시세정보",
  "verdict": "HIT"
}
```

**검증 절차** — ① `hash(payload + salt)`가 `commitHash`와 같은지 확인 ② `merkleProof`로 `merkleRoot` 복원 후 `anchorTxHash`의 온체인 값과 대조 ③ `actualClose`는 공공데이터포털 원본과 직접 비교. 만기 전(salt 미공개)에는 `salt`·`verdict`가 `null`로 나간다.

### 3-4. `POST /api/auth/refresh` — 토큰 재발급 <sub>공개</sub>

refresh token(HttpOnly Cookie)을 회전시킨다. **회전된 refresh가 재제출되면 재사용 탐지** — 해당 계정 세션 전체 폐기 여부는 미결정 항목.

**200 OK**

```json
{ "accessToken": "eyJhbGciOi…", "expiresIn": 1800 }
```

### 3-5. `POST /api/wallet/nonce` → `POST /api/wallet/link` — 지갑 연동 <sub>JWT</sub>

```json
// POST /api/wallet/nonce → 200
{ "nonce": "f47ac10b…", "expiresIn": 300 }

// POST /api/wallet/link (SSAFY WALLET로 nonce 서명)
{ "address": "0x7a3f…c21b", "signature": "0x1d8e…" }
// → 200 { "linked": true, "address": "0x7a3f…c21b" }
```

| 코드 | 상태 | 조건 |
|---|:---:|---|
| `NONCE_EXPIRED` | 400 | nonce 5분 초과 |
| `WALLET_ALREADY_LINKED` | 409 | 주소가 다른 계정에 연동됨 |

### 3-6. `GET /api/predictions` — 피드 <sub>공개</sub>

`?track=REAL&status=OPEN&userId=12&cursor=…&size=20`

**200 OK**

```json
{
  "items": [
    {
      "id": 4821,
      "user": { "id": 7, "handle": "chart_fairy", "displayName": "차트요정" },
      "track": "REAL",
      "stockCode": "005930",
      "stockName": "삼성전자",
      "direction": "UP",
      "targetPrice": 82000,
      "refClose": 71800,
      "horizon": 20,
      "confidence": 73,
      "status": "OPEN",
      "basePrice": 71950,
      "anchorStatus": "CONFIRMED",
      "hasNote": true,
      "registeredAt": "2026-08-24T10:12:00+09:00"
    }
  ],
  "nextCursor": "eyJpZCI6NDgyMX0"
}
```

예측 내용(방향·목표가·직전 종가)은 **미구독자에게도 공개** — 랭킹 검증 가능성 유지. `note` 본문만 제외된다.

### 3-7. `GET /api/predictions/{id}/note` — 근거 열람 <sub>JWT</sub>

| 코드 | 상태 | 조건 |
|---|:---:|---|
| `SUBSCRIPTION_REQUIRED` | 403 | 해당 예측자 구독이 `ACTIVE` 아님. **404가 아니다** — 존재 자체는 공개해야 랭킹 검증이 가능 |

본인 예측이면 구독 없이 열람 가능.

### 3-8. `GET /api/rankings` — 랭킹 <sub>공개</sub>

`?track=REAL&filter=total` (filter: `total` · `short` · `long` · 섹터명)

**200 OK**

```json
{
  "track": "REAL",
  "filter": "total",
  "computedAt": "2026-08-24T13:40:00+09:00",
  "items": [
    {
      "rank": 1,
      "user": { "id": 7, "handle": "chart_fairy" },
      "score": 84.2,
      "hitRate": 0.78,
      "avgError": 2.31,
      "doneCount": 46
    }
  ]
}
```

신뢰도 = (적중률×0.7 + 목표가 정확도×0.3) × 표본 가중치. 배치(13:30)에서만 갱신되며 Redis 스냅샷을 그대로 서빙한다. **트랙 분리 집계** — REPLAY 실적은 REAL 랭킹에 절대 섞이지 않는다.

### 3-9. `POST /api/users/{id}/backtest` — 팔로우 백테스트 <sub>서명</sub>

1,000 PRT 소각 동반. "적중률 78%"보다 "그대로 따라 샀으면 +12.4%"가 설득력이 크고, 작게 여러 번 맞고 크게 한 번 틀리는 예측자를 걸러낸다.

```json
// Request
{ "from": "2026-02-01", "to": "2026-08-01", "initialCash": "10000000", "signature": "0x…" }

// 202 Accepted → GET 폴링
{
  "id": 55, "status": "DONE",
  "result": {
    "totalReturn": 0.124, "mdd": -0.067, "winRate": 0.71,
    "trades": 18, "equityCurve": [ ... ]
  },
  "feeTxHash": "0x77aa…"
}
```

### 3-10. `POST /api/certificates` — 검증 증명서 SBT <sub>서명</sub>

3,000 PRT 소각 → ERC-721 SBT 발급. 발급 시점 지표가 토큰 메타데이터로 굳는다.

```json
// 202 Accepted
{
  "id": 31, "status": "PENDING", "txHash": "0x90cd…",
  "snapshot": { "verifiedCount": 46, "hitRate": 0.78, "avgError": 2.31 }
}
// 확정 후 GET → status CONFIRMED + tokenId
```

### 3-11. `GET /api/tokens/balance` <sub>JWT</sub>

```json
{ "address": "0x7a3f…c21b", "balance": "182000", "syncedBlock": 8412345 }
```

잔액은 온체인이 진실 — 응답은 인덱서 캐시(`token_balances`)이고 `syncedBlock`으로 신선도를 밝힌다.

### 3-12. `POST /api/seasons/{id}/orders` — 시즌 주문 <sub>JWT</sub>

```json
// Request
{ "tickerId": 3, "side": "BUY", "qty": 10 }

// 201 Created — 당일 종가 단일가 체결, 부분 체결 없음
{
  "orderId": 771, "status": "FILLED",
  "fillPrice": 45300, "gameDay": 30,
  "cash": "5470000", "position": { "qty": 10, "avgPrice": 45300 }
}
```

| 코드 | 상태 | 조건 |
|---|:---:|---|
| `INSUFFICIENT_CASH` | 402 | 예수금 부족 |
| `SEASON_NOT_RUNNING` | 409 | 시즌 종료·미시작 |
| `NOT_PARTICIPANT` | 403 | 랭킹 시즌 미참가 |

### 3-13. `POST /api/seasons/{id}/advance` — 게임일 진행 <sub>JWT · 시연용</sub>

ReplayClock을 1게임일 전진 — 종가 체결·예측 기준가 확정·만기 판정이 압축 실행된다. 시연에서 "시간 가속" 버튼이 이 엔드포인트다. 운영 배포 시 관리자 권한으로 제한.

---

## 4. 오류 코드 총람

| 코드 | HTTP | 의미 |
|---|:---:|---|
| `INVALID_TOKEN` | 401 | access 만료·위조 |
| `REFRESH_REUSED` | 401 | 회전된 refresh 재제출 (재사용 탐지) |
| `SIGNER_MISMATCH` | 401 | 서명 주소 ≠ 연동 지갑 |
| `SUBSCRIPTION_REQUIRED` | 403 | 근거·리포트 미구독 접근 |
| `NOT_PARTICIPANT` | 403 | 시즌 미참가 |
| `TARGET_DIRECTION_MISMATCH` | 400 | 방향·목표가 모순 |
| `NONCE_EXPIRED` | 400 | SIWE nonce 만료 |
| `INSUFFICIENT_TOKEN` | 402 | PRT 잔액 부족 |
| `INSUFFICIENT_CASH` | 402 | 시즌 예수금 부족 |
| `WALLET_ALREADY_LINKED` | 409 | 지갑 주소 중복 연동 |
| `SEASON_HORIZON_EXCEEDED` | 409 | 예측 기간 > 남은 시즌 |
| `SEASON_NOT_RUNNING` | 409 | 시즌 상태 오류 |
| `CHAIN_UNAVAILABLE` | 503 | RPC 장애 — 온체인 동반 요청만 실패, 조회는 정상 |

---

## 5. 상태 폴링 패턴 (온체인 동반 요청 공통)

```
POST (서명) → 202 { status: "PENDING", txHash }
                     │  블록 확정 (수 초)
                     ▼
컨트랙트 이벤트 → 체인 인덱서 → chain_events → 도메인 상태 갱신
                     │
GET {id} 폴링 → 200 { status: "ACTIVE" | "CONFIRMED" | "FAILED" }
```

적용 대상: 구독(`PENDING→ACTIVE`) · 증명서(`PENDING→CONFIRMED`) · 슬롯 초과 등록 · 시즌 참가 · 백테스트. 예측 커밋의 "앵커 대기 → 앵커 완료"와 같은 패턴 — 화면 언어를 재사용한다.

---

## 6. 문서 링크

- [기술 스택 · 아키텍처](01-tech-stack-architecture.md)
- [ERD](02-erd.md)
- [사용자 플로우](03-user-flow.md)
