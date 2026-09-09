/* H-04 광고 등록. API 명세서 §광고 · 설계서 §3 H · §4 H-04.

   POST /ads         202 { operationId, adId, status } — 서명 · Idempotency-Key 필수
   GET  /ads/active  200 { items: [{ id, imageUrl, linkUrl }] }  — B-01 홈이 쓴다

   백엔드 ANT-COMMUNITY-05 로 둘 다 열려 있다. 목업이 아니다.

   ── 서명 문자열이 지갑 연동용과 다르다 ──────────────────────
   api/wallet.ts 의 signingPayload() 는 address·chainId·nonce 세 줄이다. 광고는 그 모양이
   아니라 **신청 내용 자체**가 들어간다(서버 AdCreateRequest.signingPayload).

     antenna:ad:v1 / imageFileId= / linkUrl= / days= / chainId= / nonce=

   그래서 그 함수를 재사용하면 서버가 복원한 주소가 달라져 401 이 난다. 서버 주석이 이유를
   적어 두었다 — "days 가 빠지면 3일치 서명으로 30일치를 등록할 수 있다".
   한 글자만 어긋나도 원인이 로그에 남지 않는 401 이므로, 아래 함수는 서버 구현을 그대로
   옮긴 것이고 손대려면 양쪽을 같이 고쳐야 한다. */
import { api } from './client'
import type { OperationStatus } from './operations'
import type { WalletNonce } from './wallet'

/* ── 신청 ────────────────────────────────────────────── */

export type AdDraft = {
  /** POST /uploads (purpose=AD) 가 준 값만 쓴다. 외부 URL 을 받지 않는다(설계 제약) */
  imageFileId: string
  /** https 만. 서버가 ^https://\S+$ 로 막고 길이는 500 자다 */
  linkUrl: string
  /** 노출 일수. 1~30 */
  days: number
}

/** 202 응답. adId 가 함께 오는 이유는 폴링 전에도 화면이 "신청한 배너" 를 가리키기 위해서다 */
export type AdAccepted = {
  operationId: string
  adId: number
  status: OperationStatus
}

/**
 * 서명 대상 문자열. **서버 AdCreateRequest.signingPayload 와 바이트가 같아야 한다.**
 *
 * days 를 숫자 그대로 넣는다(자릿수 규칙 없음) — 서버가 Integer 를 문자열 이어붙이기로
 * 만들기 때문이다. imageFileId·linkUrl 도 받은 값 그대로, 정규화하지 않는다.
 */
export function adSigningPayload(draft: AdDraft, nonce: WalletNonce) {
  return [
    'antenna:ad:v1',
    `imageFileId=${draft.imageFileId}`,
    `linkUrl=${draft.linkUrl}`,
    `days=${draft.days}`,
    `chainId=${nonce.chainId}`,
    `nonce=${nonce.nonce}`,
  ].join('\n')
}

/**
 * 멱등 키 scope. 명세 §1 의 필수 대상이다.
 *
 * 신청 내용이 바뀌면 다른 요청이므로 키도 갈라야 한다 — 같은 키로 다른 본문을 보내면
 * 409 IDEMPOTENCY_KEY_REUSED 다. 반대로 같은 내용의 재시도는 같은 키여야 한다:
 * 서버 주석이 "타임아웃 뒤 한 번의 재시도로 게재료가 두 번 나가면 복구 경로가 없다" 고 적었다.
 */
export const adScope = (draft: AdDraft) =>
  `ad:${draft.imageFileId}:${draft.days}:${draft.linkUrl}`

export function createAd(draft: AdDraft, signature: string) {
  return api.post<AdAccepted>('/ads', { ...draft, signature }, {
    idempotencyScope: adScope(draft),
  })
}

/* ── 노출 중인 배너 (B-01) ───────────────────────────── */

export type ActiveAd = {
  id: number
  imageUrl: string
  linkUrl: string
}

export function fetchActiveAds() {
  return api.get<{ items: ActiveAd[] }>('/ads/active')
}

/* ── 게재 조건 ───────────────────────────────────────────
   **어떤 API 도 이 값들을 내려주지 않는다.** 서버 app.ads 설정(AdProperties)에만 있고
   응답에 실리지 않아, 화면이 비용을 보여주려면 같은 수를 여기 적는 수밖에 없다.

   설계 제약이 "서명 전 비용을 명확히 보여준다" 라 비워 둘 수 없다. 다만 서버가 값을 바꾸면
   화면이 거짓을 말하게 된다 — 사용자는 3 ANT 로 알고 서명했는데 30 ANT 가 나갈 수 있다.
   가격이 서명 문자열에 들어가지 않아(days 만 들어간다) 서명으로도 막히지 않는다.
   백엔드에 조회 경로를 요청해 두었고, 생기면 이 상수들을 지운다. */

/** 1 ANT/일. 서버 pricePerDayWei 기본값(10^18 wei) 과 같다 */
export const PRICE_PER_DAY_ANT = 1

/** 서버 maxDays. AdService 가 이 값을 넘으면 400 이다 */
export const MAX_DAYS = 30
export const MIN_DAYS = 1

/** 서버 ad_banners.link_url 이 varchar(500) 이다 — 넘기면 400 이어야 할 것이 500 이 된다 */
export const LINK_MAX = 500

export function priceOf(days: number) {
  return days * PRICE_PER_DAY_ANT
}

/**
 * https 만 받는다(설계 제약). 서버 정규식과 같은 판정을 한다 — http 링크는 중간에서 갈아
 * 끼울 수 있고, 배너는 클릭을 유도하는 자리라 피싱 대상이 된다(서버 주석).
 */
export function isValidLink(url: string) {
  return /^https:\/\/\S+$/.test(url) && url.length <= LINK_MAX
}
