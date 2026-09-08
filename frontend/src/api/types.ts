/* 모든 화면이 공유하는 응답 계약. 설계서 §7 공통 컴포넌트 참고. */

/**
 * 목록은 전부 커서 페이징이다. 페이지 번호는 쓰지 않는다.
 *
 * nextCursor 는 숫자로도 온다 — 서버가 목록마다 다른 타입을 쓴다.
 * 문자열: ReportFeedResponse. 숫자: AnchorListResponse · PostListResponse ·
 * ChannelReportListResponse (정렬 키가 id 라 그 값을 그대로 커서로 쓴다).
 * 어느 쪽이든 쿼리 파라미터로 되돌려 보내기만 하므로 프론트는 값을 해석하지 않는다.
 */
export type CursorList<T> = {
  items: T[]
  nextCursor: string | number | null
  hasNext: boolean
}

/* 서버 오류 본문. API 명세서 §오류 계약 — { code, message, field? } 세 필드로 고정한다.

   HTTP 상태 코드는 본문에 넣지 않는다. 응답 상태와 중복이고, 두 값이 어긋나면
   어느 쪽이 참인지 프론트가 판단할 수 없기 때문이다. 상태는 res.status 에서만 읽는다. */
export type ApiErrorBody = {
  code: string
  message: string
  /** 입력값 오류일 때만 채워진다. 여러 필드가 틀려도 첫 위반 한 곳만 온다 */
  field?: string
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

/**
 * 모의투자 종목. 화면은 {@code displayName} 하나만 그린다.
 *
 * <p>연습은 이 값이 실제 종목명("삼성전자")이고 대회는 가명("A사")이다(2026-09-07 결정).
 * 종목코드는 어느 쪽도 응답에 오지 않으므로 여기에 자리를 두지 않는다 — 응답 계약을
 * 모드마다 갈라 두면 화면이 두 모양을 다뤄야 한다.
 */
export type DisplayTicker = {
  tickerId: string
  displayName: string
}

/** 202 를 쓰는 4곳(C-01 · E-02 · G-03 · H-04)이 공유하는 작업 응답 */
export type OperationRef = {
  operationId: string
}
