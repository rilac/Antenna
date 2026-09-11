/* E-04 내 채널 구독료. API 명세서 §채널·구독 · 설계서 §3 E · §4 E-04.
   (화면은 H-03 환경 설정 안의 '내 채널' 섹션이다 — 2026-09-07 통합)

   GET /me/channel/fee   내 구독료 조회 — 현재값(최신 행) + 변경 이력(publisher_fees)
   PUT /me/channel/fee   구독료 변경 — 차기 결제 주기부터 적용

   ── 백엔드 현황 (2026-09-10 확인) ────────────────────────
   둘 다 없다. 여는 티켓 ANT-TOKEN-05(S15P21A507-65)가 아직 '해야 할 일'이고
   담당자도 없다. monetize 도메인의 컨트롤러는 Ad · ChannelReport · Report 셋뿐이다.

   그래서 MOCK 을 켠다(api/subscriptions.ts 와 같은 방식). MOCK 을 false 로 바꾸고
   mock/channelFee.ts 와 각 함수의 `if (MOCK)` 한 줄씩만 지우면 흔적이 없다.

   ── 응답 형태는 명세서에 없다. 지어낸 부분을 밝혀 둔다 ────
   명세서가 준 것은 **경로와 요청 본문까지**다.
     GET  /me/channel/fee  → "200 성공"          (필드 없음)
     PUT  /me/channel/fee  → "200 적용 예정 정보" (필드 없음)
     PUT  요청 본문        → { fee: 정수 ANT 문자열 }  ← 이것만 확정
   그래서 응답 타입은 ERD 의 publisher_fees 컬럼에서 그대로 따왔다.
     id · publisher_id · fee numeric(30,0) · effective_from · created_at
   서버가 열릴 때 필드 이름이 다르면 이 파일만 고치면 된다 — 화면은 이 타입만 본다.

   ── 단위: 정수 ANT 다. 10^18 이 아니다 ───────────────────
   fee 는 정수 ANT 문자열이다 — ANT 는 decimals 0 이라(ANT-CHAIN-03) "12000" 이 곧
   12,000 ANT 다. 서버가 PredictionSlotResponse · AdPricingResponse 주석에 "정수 그대로가
   최소 단위다, 10^18 을 곱하지 않는다" 라고 못 박아 두었다.

   표시는 api/wallet.ts 의 formatToken 을 쓴다. 이 파일에 따로 두었던 formatAnt 는
   18자리를 자르던 formatFee 를 피하려던 임시 함수라, S15P21A507-225 에서 그 함수와 함께
   formatToken 하나로 합쳤다. */
import { api } from './client'
import * as mock from './mock/channelFee'

const MOCK = true

/** publisher_fees 한 행. 현재가는 이 중 가장 최근 행이다(명세 "현재값 = 최신 행") */
export type FeeChange = {
  id: number
  /** 정수 ANT 문자열. numeric(30,0) 이라 number 로 좁히지 않는다 */
  fee: string
  /** 적용 시각. 기존 구독은 만료까지 옛 가격이다 */
  effectiveFrom: string
  createdAt: string
}

export type ChannelFee = {
  /** 현재 구독료(정수 ANT 문자열). 한 번도 정한 적이 없으면 null */
  fee: string | null
  /** 최신순. 처음이면 빈 배열 */
  history: FeeChange[]
}

/** PUT 응답 — 명세 "적용 예정 정보" */
export type FeeApplied = {
  fee: string
  effectiveFrom: string
}

export function fetchChannelFee() {
  if (MOCK) return mock.channelFee()
  return api.get<ChannelFee>('/me/channel/fee')
}

/**
 * 구독료 변경. **되돌리기 어려운 행동이다** — 구독자 전원에게 FEE_CHANGED 알림이 나가고
 * 이력에 행이 하나 남는다. 화면이 확인 단계를 두는 이유다(설계 제약).
 */
export function updateChannelFee(fee: string) {
  if (MOCK) return mock.updateChannelFee(fee)
  return api.put<FeeApplied>('/me/channel/fee', { fee })
}

/* ── 입력 규칙 ───────────────────────────────────────── */

/**
 * 상한. 서버 제약이 아니라 **오타 방어**다.
 *
 * 명세·ERD 어디에도 최대 구독료가 없다. 그런데 0 을 하나 더 붙인 값을 그대로 저장하면
 * 구독자 전원에게 인상 알림이 나가고 이력에 남아 되돌릴 수 없다. 서버가 값을 정하면
 * 그 값으로 바꾼다.
 */
export const FEE_MAX = 100_000

/** 정수만 받는다 — ANT 는 decimals 0 이라 소수 구독료라는 것이 없다 */
export function isValidFee(text: string) {
  if (!/^\d+$/.test(text)) return false
  const n = Number(text)
  return n >= 0 && n <= FEE_MAX
}

/** 이력 줄에 쓰는 날짜. 시각까지는 필요 없다 */
export function formatDay(iso: string) {
  const d = new Date(iso)
  if (Number.isNaN(d.getTime())) return iso
  return d.toLocaleDateString('ko-KR', { year: 'numeric', month: '2-digit', day: '2-digit' })
}
