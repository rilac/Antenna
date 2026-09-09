/* 서버 오류를 사용자 문구로 옮긴다. 설계서 §6.

   서버가 준 message 는 절대 그대로 쓰지 않는다. 내부 사정이 새어 나가고,
   문구가 서버 배포마다 흔들린다. code 로 골라 여기 적힌 문구만 보여준다.

   code 는 API 명세서의 어휘표에만 있는 값을 쓴다. 표에 없는 code 를 여기서
   지어내면 영영 맞지 않는 죽은 분기가 된다. 도메인 code 가 추가되면
   백엔드가 표에 올린 뒤 여기에 함께 반영한다. */
import type { ApiError } from '../../api/errors'
import { CLIENT_ERROR_CODE, ERROR_CODE } from '../../api/errors'

type Text = { title: string; hint?: string; retryable?: boolean }

/* code 우선. 같은 status 라도 사유가 다르면 안내가 달라야 한다. */
const BY_CODE: Record<string, Text> = {
  [ERROR_CODE.INVALID_REQUEST]: { title: '입력값을 다시 확인해 주세요' },
  [ERROR_CODE.UNAUTHENTICATED]: { title: '로그인이 필요합니다' },
  [ERROR_CODE.INTERNAL_ERROR]: { title: '서버에 문제가 생겼습니다', hint: '잠시 후 다시 시도해 주세요', retryable: true },

  // 멱등성 — KEY_REQUIRED 는 클라이언트 결함이라 화면에 띄우지 않는다(isClientDefect)
  [ERROR_CODE.IDEMPOTENCY_KEY_REUSED]: { title: '이미 처리된 요청입니다', hint: '결과를 다시 불러옵니다', retryable: true },

  // 신고
  [ERROR_CODE.TARGET_NOT_FOUND]: { title: '신고 대상을 찾을 수 없습니다', hint: '이미 삭제되었을 수 있습니다' },
  [ERROR_CODE.SELF_REPORT]: { title: '자신을 신고할 수 없습니다' },
  [ERROR_CODE.DUPLICATE_REPORT]: { title: '이미 신고한 대상입니다', hint: '처리 결과를 기다려 주세요' },

  // 예측 · 잔액 · 모의투자
  [ERROR_CODE.PREDICTION_SLOT_EXCEEDED]: { title: '무료 예측 슬롯을 모두 썼습니다', hint: '토큰을 소각하고 계속 등록할 수 있습니다' },
  [ERROR_CODE.INSUFFICIENT_BALANCE]: { title: '토큰 잔액이 부족합니다', hint: '지갑에서 잔액을 확인해 주세요' },
  [ERROR_CODE.DAY_MISMATCH]: { title: '진행 상황이 달라졌습니다', hint: '최신 상태로 맞춘 뒤 다시 시도합니다', retryable: true },
  [ERROR_CODE.SEASON_NOT_FOUND]: { title: '시즌을 찾을 수 없습니다' },
  [ERROR_CODE.SEASON_TICKER_NOT_FOUND]: { title: '이 시즌에 없는 종목입니다' },
  [ERROR_CODE.SEASON_ALREADY_JOINED]: { title: '이미 진행 중인 모의투자가 있습니다', hint: '이어서 하거나 초기화하고 다시 시작할 수 있습니다' },
  [ERROR_CODE.SEASON_NOT_RUNNING]: { title: '지금은 참가할 수 없는 시즌입니다' },
  [ERROR_CODE.SEASON_JOIN_NOT_SUPPORTED]: { title: '대회 참가는 아직 열리지 않았습니다' },
  [ERROR_CODE.SEASON_NOT_JOINED]: { title: '아직 참가하지 않은 시즌입니다', hint: '시즌 상세에서 먼저 참가해 주세요' },
  [ERROR_CODE.SEASON_INSUFFICIENT_QTY]: { title: '보유 수량이 부족합니다' },
  [ERROR_CODE.SEASON_PRICE_NOT_FOUND]: { title: '이 게임일의 가격이 아직 없습니다' },
  [ERROR_CODE.SEASON_ADVANCE_NOT_ALLOWED]: { title: '대회는 직접 진행할 수 없습니다', hint: '정해진 시각에 함께 넘어갑니다' },
  [ERROR_CODE.SEASON_ATTEMPT_ENDED]: { title: '이미 끝난 회차입니다', hint: '결과를 보거나 새로 시작할 수 있습니다' },
  [ERROR_CODE.SEASON_LAST_DAY]: { title: '마지막 게임일입니다', hint: '종료하고 결과를 확인해 주세요' },
  [ERROR_CODE.SEASON_NOT_LAST_DAY]: { title: '아직 마지막 게임일이 아닙니다' },
  [ERROR_CODE.SEASON_RESULT_NOT_FOUND]: { title: '아직 결과가 없습니다', hint: '마지막 게임일에 종료하면 만들어집니다' },
  [ERROR_CODE.SEASON_REVIEW_FAILED]: { title: 'AI 복기를 만들지 못했습니다', hint: '아직 종료되지 않았습니다. 잠시 후 다시 시도해 주세요', retryable: true },

  // 지갑 · 서명 (M-01) — 사용자가 할 일이 서로 달라 문구를 합치지 않는다
  [ERROR_CODE.INVALID_SIGNATURE]: { title: '서명이 올바르지 않습니다', hint: '지갑에서 다시 서명해 주세요', retryable: true },
  [ERROR_CODE.NONCE_NOT_FOUND]: { title: '인증 요청이 만료되었습니다', hint: '5분이 지났습니다. 처음부터 다시 진행해 주세요', retryable: true },
  [ERROR_CODE.SIGNER_MISMATCH]: { title: '서명한 지갑이 다릅니다', hint: '연동하려는 주소의 계정으로 바꾼 뒤 다시 서명해 주세요', retryable: true },
  [ERROR_CODE.WALLET_NOT_LINKED]: { title: '지갑을 먼저 연동해 주세요', hint: '지갑 · 토큰 화면에서 연동할 수 있습니다' },
  // 재시도로 풀리지 않는다 — 다른 지갑을 쓰라고 안내한다(설계 제약)
  [ERROR_CODE.WALLET_ALREADY_LINKED]: { title: '이미 연동된 지갑입니다', hint: '다른 계정에 연동된 주소이거나, 이 계정에 이미 지갑이 있습니다' },

  // 회원 · 인증
  [ERROR_CODE.DUPLICATE_NICKNAME]: { title: '이미 사용 중인 닉네임입니다' },
  [ERROR_CODE.USER_NOT_FOUND]: { title: '회원을 찾을 수 없습니다' },
  [ERROR_CODE.ACCOUNT_BANNED]: { title: '이용이 제한된 계정입니다', hint: '다시 로그인해도 풀리지 않습니다' },
  [ERROR_CODE.PROVIDER_NOT_SUPPORTED]: { title: '아직 지원하지 않는 로그인 방식입니다' },

  // 온체인 (ANT-CHAIN)
  [ERROR_CODE.ANCHOR_NOT_FOUND]: { title: '앵커 배치를 찾을 수 없습니다', hint: '아직 만들어지지 않았거나 지워진 배치입니다' },
  [ERROR_CODE.PREDICTION_FORBIDDEN]: { title: '아직 공개되지 않은 예측입니다', hint: '판정 전에는 작성자와 구독자만 근거를 볼 수 있습니다' },
  // 체인이 잠깐 끊긴 것이라 잠시 뒤 되살아난다
  [ERROR_CODE.CHAIN_UNAVAILABLE]: { title: '체인에 연결하지 못했습니다', hint: '잠시 후 다시 시도해 주세요', retryable: true },

  // 업로드
  [ERROR_CODE.FILE_TOO_LARGE]: { title: '파일이 너무 큽니다', hint: '더 작은 이미지를 올려 주세요' },
  [ERROR_CODE.IMAGE_TOO_LARGE]: { title: '이미지 크기가 너무 큽니다', hint: '가로·세로가 더 작은 이미지를 올려 주세요' },
  [ERROR_CODE.UNSUPPORTED_IMAGE_TYPE]: { title: '지원하지 않는 이미지 형식입니다' },
  [ERROR_CODE.INVALID_IMAGE_RATIO]: { title: '이미지 비율이 맞지 않습니다' },
  [ERROR_CODE.UPLOAD_FILE_NOT_FOUND]: { title: '올린 파일을 찾을 수 없습니다', hint: '다시 올려 주세요' },

  // 광고 · 비동기 작업
  [ERROR_CODE.AD_SLOT_SOLD_OUT]: { title: '해당 기간의 광고 자리가 찼습니다', hint: '다른 기간을 선택해 주세요' },
  [ERROR_CODE.OPERATION_NOT_FOUND]: { title: '처리 내역을 찾을 수 없습니다' },
  [ERROR_CODE.OPERATION_FORBIDDEN]: { title: '이 처리 내역을 볼 수 없습니다' },

  // 시세 · 리서치 · 관심 종목
  [ERROR_CODE.STOCK_NOT_FOUND]: { title: '종목을 찾을 수 없습니다' },
  [ERROR_CODE.BRIEFING_NOT_FOUND]: { title: '브리핑을 찾을 수 없습니다', hint: '지워졌거나 주소가 잘못된 링크입니다' },
  [ERROR_CODE.DUPLICATE_WATCHLIST_ITEM]: { title: '이미 관심 종목에 있습니다' },

  // 댓글 · 좋아요
  [ERROR_CODE.COMMENT_NOT_FOUND]: { title: '댓글을 찾을 수 없습니다', hint: '삭제되었을 수 있습니다' },
  [ERROR_CODE.DUPLICATE_LIKE]: { title: '이미 좋아요를 눌렀습니다' },

  // 첨부 대상
  [ERROR_CODE.POST_NOT_FOUND]: { title: '글을 찾을 수 없습니다', hint: '삭제되었거나 가려진 글입니다' },
  [ERROR_CODE.REPORT_NOT_FOUND]: { title: '리포트를 찾을 수 없습니다' },
  [ERROR_CODE.PREDICTION_NOT_FOUND]: { title: '예측을 찾을 수 없습니다' },

  // 프론트가 만든 code
  [CLIENT_ERROR_CODE.CLIENT_NOT_JSON]: { title: '서버에 연결하지 못했습니다', hint: '백엔드 연동 전이거나 응답이 올바르지 않습니다', retryable: true },
  [CLIENT_ERROR_CODE.CLIENT_WALLET_MISSING]: { title: '지갑을 찾을 수 없습니다', hint: '브라우저에 지갑 확장을 설치한 뒤 다시 시도해 주세요' },
  [CLIENT_ERROR_CODE.CLIENT_SIGN_REJECTED]: { title: '지갑에서 요청을 취소했습니다', hint: '연동하려면 지갑 창에서 승인해 주세요', retryable: true },
  [CLIENT_ERROR_CODE.CLIENT_WALLET_BUSY]: { title: '지갑이 응답을 기다리고 있습니다', hint: '열려 있는 지갑 창을 확인해 주세요', retryable: true },
  [CLIENT_ERROR_CODE.CLIENT_NO_ACCOUNT]: { title: '지갑 계정을 가져오지 못했습니다', hint: '지갑 잠금을 해제하고 계정을 선택해 주세요', retryable: true },
  /* D-03 은 브라우저가 체인 RPC 를 직접 읽는다. CHAIN_UNAVAILABLE 과 문구를 나눈 이유 —
     그건 서버가 못 붙은 것이고 이건 이 브라우저가 못 붙은 것이라, 사용자가 확인할 곳이 다르다. */
  [CLIENT_ERROR_CODE.CLIENT_CHAIN_UNREACHABLE]: { title: '체인에서 확인하지 못했습니다', hint: '네트워크 연결을 확인한 뒤 다시 시도해 주세요', retryable: true },
  // 배포 설정 문제라 사용자가 다시 눌러도 풀리지 않는다 — 재시도 버튼을 주지 않는다
  [CLIENT_ERROR_CODE.CLIENT_CHAIN_NOT_CONFIGURED]: { title: '체인 접속 주소가 설정되지 않았습니다', hint: '관리자에게 알려 주세요' },
  /* 업로드(M-07) — 전송이 끊긴 것과 서버가 규격 위반으로 거절한 것은 사용자가 할 일이 다르다.
     앞은 같은 파일로 다시, 뒤는 다른 파일로. 그래서 문구를 합치지 않는다. */
  [CLIENT_ERROR_CODE.CLIENT_UPLOAD_FAILED]: { title: '올리는 중에 연결이 끊겼습니다', hint: '같은 파일로 다시 시도할 수 있습니다', retryable: true },
  // 사용자가 스스로 멈춘 것이라 화면에 오류로 띄우지 않는다. 호출부가 이 code 를 보고 조용히 넘긴다
  [CLIENT_ERROR_CODE.CLIENT_UPLOAD_ABORTED]: { title: '업로드를 취소했습니다' },
}

/* code 를 못 찾으면 status 로 떨어진다. 명세에 없는 사유가 와도 화면이 비지 않게. */
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
   화면은 이걸 확인해 키를 발급하고 조용히 재요청한다(설계서 §6). */
export function isClientDefect(error: ApiError) {
  return error.code === ERROR_CODE.IDEMPOTENCY_KEY_REQUIRED
}
