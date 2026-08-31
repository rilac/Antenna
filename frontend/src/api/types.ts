/* 모든 화면이 공유하는 응답 계약. 설계서 §7 공통 컴포넌트 참고. */

/** 목록은 전부 커서 페이징이다. 페이지 번호는 쓰지 않는다. */
export type CursorList<T> = {
  items: T[]
  nextCursor: string | null
  hasNext: boolean
}

/** 서버 오류 본문. code 로 분기하고 message 를 그대로 화면에 노출하지 않는다. */
export type ApiErrorBody = {
  code: string
  message: string
  status: number
}

/* ── 타입으로 막는 두 가지 규칙 ──────────────────────────
   설계서 §7 의 PriceLabel · TickerLabel 제약을 구현이 아니라 타입으로 강제한다.
   나중에 고치려면 전 화면을 훑어야 하므로 지금 박아 둔다. */

/** 실전 시세는 전일 종가뿐이다. "현재가" 를 만들 수 없도록 기준일을 함께 강제한다. */
export type ClosePrice = {
  /** 전일 종가 */
  close: number
  /** 종가 기준 영업일 YYYY-MM-DD */
  asOf: string
}

/** 모의투자 종목은 CLOSED 전까지 실명이 없다. 실명 필드를 아예 받지 않는 타입. */
export type DisplayTicker = {
  tickerId: string
  displayName: string
}

/** 202 를 쓰는 4곳(C-01 · E-02 · G-03 · H-04)이 공유하는 작업 응답 */
export type OperationRef = {
  operationId: string
}
