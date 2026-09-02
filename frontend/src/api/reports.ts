/* 리포트 도메인. API 명세서 §리포트 · 설계서 §3 F.

   GET /reports?scope=&sort=&cursor=&size=  { items, nextCursor, hasNext }
   GET /reports/{reportId}                  전문 또는 3줄 미리보기 + locked
   POST /reports                            Idempotency-Key 필수
   GET /channels/{userId}/reports           채널별 목록 (E-02 · 최신순 고정) */

/** 글·리포트·예측 카드가 공유하는 작성자 표기. 서버 AuthorResponse 와 짝이다. */
export type Author = {
  userId: number
  nickname: string
}

/* 서버 ReportService.Scope · Sort 를 그대로 옮긴다.
   어휘에 없는 값을 보내면 400 INVALID_REQUEST 에 field 로 어느 쪽이 틀렸는지 온다
   — 스프링 자동 변환에 맡기면 500 이 나므로 백엔드가 직접 파싱한다. */
export const REPORT_SCOPES = ['ALL', 'SUBSCRIBED'] as const
export const REPORT_SORTS = ['RECENT', 'POPULAR'] as const

export type ReportScope = (typeof REPORT_SCOPES)[number]
export type ReportSort = (typeof REPORT_SORTS)[number]

export const SCOPE_LABEL: Record<ReportScope, string> = {
  ALL: '전체',
  SUBSCRIBED: '구독 중',
}

export const SORT_LABEL: Record<ReportSort, string> = {
  RECENT: '최신순',
  POPULAR: '인기순',
}

/**
 * 피드 한 줄. 서버 ReportFeedItemResponse 와 짝이다.
 *
 * 잠긴 리포트도 제목·작성자·발행일·조회수는 그대로 온다 — 숨기면 구독 유인이
 * 사라지기 때문이다(설계서 §5). locked 는 본문을 볼 수 있는지만 가른다.
 */
export type ReportFeedItem = {
  id: number
  title: string
  author: Author
  locked: boolean
  publishedAt: string
  viewCount: number
}

/**
 * 리포트 상세. 서버 ReportDetailResponse 와 짝이다.
 *
 * body 와 preview 는 둘 중 하나만 온다 — locked 가 그 둘을 가른다.
 * 서버가 잠긴 리포트에 body 를 아예 담지 않으므로, 전문을 받아 CSS 로 가리는
 * 방식이 애초에 불가능하다(설계 제약 그대로다).
 */
export type ReportDetail = {
  id: number
  title: string
  author: Author
  /** 작성자가 정한 공개 범위. locked 와 다른 값이다 — 아래 주석 참고 */
  visibility: boolean
  /** 이 열람자가 본문을 볼 수 없는 상태 */
  locked: boolean
  /** locked 면 null */
  body: string | null
  /** locked 일 때만 채워진다. 서버가 앞 3줄(최대 300자)로 잘라 준 값 */
  preview: string | null
  publishedAt: string
  /** 서버가 이번 열람을 반영해 내려준다. 본인 글은 증가하지 않는다 */
  viewCount: number
}

/** 발행일 표기. 목록에서는 시각까지 필요 없다. */
export function formatDate(iso: string) {
  const d = new Date(iso)
  if (Number.isNaN(d.getTime())) return iso
  return d.toLocaleDateString('ko-KR', { year: 'numeric', month: '2-digit', day: '2-digit' })
}
