/* 서버 오류를 사용자 문구로 옮긴다. 설계서 §6.

   서버가 준 message 는 절대 그대로 쓰지 않는다. 내부 사정이 새어 나가고,
   문구가 서버 배포마다 흔들린다. code 로 골라 여기 적힌 문구만 보여준다. */
import type { ApiError } from '../../api/errors'

type Text = { title: string; hint?: string; retryable?: boolean }

/* code 우선. 같은 status 라도 사유가 다르면 안내가 달라야 한다. */
const BY_CODE: Record<string, Text> = {
  // 409 — 사유별로 사용자가 할 일이 다르다
  WALLET_ALREADY_LINKED: { title: '이미 다른 계정에 연동된 지갑입니다', hint: '다른 지갑으로 시도해 주세요' },
  INSUFFICIENT_BALANCE: { title: '토큰 잔액이 부족합니다', hint: '지갑에서 잔액을 확인해 주세요' },
  INSUFFICIENT_DEPOSIT: { title: '예수금이 부족합니다' },
  ALREADY_JOINED: { title: '이미 참가한 시즌입니다', hint: '이어하기로 진행할 수 있습니다' },
  SEASON_MANUAL_ADVANCE_FORBIDDEN: { title: '대회 모드는 직접 진행할 수 없습니다', hint: '시간표에 따라 자동으로 넘어갑니다' },
  DAY_MISMATCH: { title: '진행 상황이 달라졌습니다', hint: '최신 상태로 맞춘 뒤 다시 시도합니다', retryable: true },
  IDEMPOTENCY_KEY_REUSED: { title: '이미 처리된 요청입니다', hint: '결과를 다시 불러옵니다', retryable: true },

  // 백엔드 연동 전 SPA fallback 을 걸렀을 때
  NOT_JSON: { title: '서버에 연결하지 못했습니다', hint: '백엔드 연동 전이거나 응답이 올바르지 않습니다', retryable: true },
}

/* code 를 못 찾으면 status 로 떨어진다. */
const BY_STATUS: Record<number, Text> = {
  400: { title: '입력값을 다시 확인해 주세요' },
  401: { title: '로그인이 필요합니다' },
  403: { title: '접근 권한이 없습니다' },
  404: { title: '찾을 수 없습니다' },
  409: { title: '요청을 처리할 수 없습니다', hint: '상태가 바뀌었을 수 있습니다', retryable: true },
  // 대상·한도가 아직 미정이라 구체 수치를 적지 않는다
  429: { title: '요청이 너무 잦습니다', hint: '잠시 후 다시 시도해 주세요', retryable: true },
  500: { title: '서버에 문제가 생겼습니다', hint: '잠시 후 다시 시도해 주세요', retryable: true },
}

const FALLBACK: Text = { title: '문제가 발생했습니다', hint: '잠시 후 다시 시도해 주세요', retryable: true }

export function errorText(error: ApiError): Text {
  return BY_CODE[error.code] ?? BY_STATUS[error.status] ?? FALLBACK
}

/* 클라이언트 결함이라 사용자에게 보이면 안 되는 오류.
   화면은 이걸 확인해 조용히 재요청한다(설계서 §6). */
export function isClientDefect(error: ApiError) {
  return error.code === 'IDEMPOTENCY_KEY_REQUIRED'
}
