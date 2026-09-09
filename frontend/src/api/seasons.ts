/* 모의투자(G) 조회 API.

   G-01 홈과 연습하기가 같은 두 목록을 쓴다. 화면마다 타입을 다시 적으면 명세가
   바뀔 때 한쪽만 고쳐질 수 있어 여기로 모은다.

   타입은 API 명세서의 응답 스키마를 그대로 옮겼다. 담기지 않은 값은 만들지 않는다 —
   시나리오 이름·수익률·시각 필드는 어느 응답에도 없다.

   담당 백엔드 티켓: 아직 없다. 지금은 /seasons 계열이 하나도 구현돼 있지 않아
   전부 401 로 돌아온다(Spring Security 가 라우팅보다 먼저 걸러 없는 경로도 401 이다). */
import { api } from './client'

export type SeasonMode = 'PRACTICE' | 'COMPETITION' | 'DEMO'
export type SeasonStatus = 'SCHEDULED' | 'RUNNING' | 'CLOSED'

/* 모드는 시즌 정체가 아니라 진행 방식이라 화면에 써도 된다 — 시대 단서가 아니다. */
export const MODE_LABEL: Record<SeasonMode, string> = {
  PRACTICE: '연습',
  COMPETITION: '대회',
  DEMO: '시연',
}

/* ── 내 참가 목록 · GET /seasons/me ───────────────────────── */

/** v0.39 — 시즌 제목·종료 시각·최종 수익률이 함께 온다. 시기(연도)는 여전히 없다. */
export type MyRun = {
  seasonId: number
  mode: SeasonMode
  /** 시즌 제목("급락과 반등"). 카드 제목이다 */
  title: string
  currentDay: number
  lengthDays: number
  /** 명세에 단위(0~1 인지 0~100 인지)가 없다 — 화면은 progressOf() 를 쓴다 */
  progress: number
  /** 내가 끝낸 실제 시각. 진행 중이면 null. 시즌의 시기가 아니라 시대 단서가 아니다 */
  endedAt?: string | null
  /** 최종 수익률(%). 끝난 회차만 */
  returnRate?: number | null
}

/** 진행 중과 완료를 한 번에 받는다. status 는 둘 중 하나만 받는 파라미터라 두 번 부른다. */
export function getMyRuns() {
  return Promise.all([
    api.get<{ items: MyRun[] }>('/seasons/me', { query: { status: 'ONGOING' } }),
    api.get<{ items: MyRun[] }>('/seasons/me', { query: { status: 'DONE' } }),
  ]).then(([ongoing, done]) => ({ ongoing: ongoing.items, done: done.items }))
}

/* ── 참가 가능한 목록 · GET /seasons ──────────────────────── */

export type OpenRun = {
  id: number
  mode: SeasonMode
  status: SeasonStatus
  /**
   * 시즌의 <b>성격</b>. "급락 구간"·"실적 발표 구간" 처럼 무슨 장이었는지만 온다.
   * 연습 주제 카드가 이 값으로 그려진다 — 화면에 하드코딩하지 않는다(설계서 §4 G-02a).
   */
  title: string
  /** 한 줄 설명 */
  note?: string
  /** 섹터·테마 키. 카드 아이콘을 고르는 데 쓴다 */
  theme?: string
  /** 참가자에게 보이는 섹터 힌트. 상위 분류로만 온다 */
  sector?: string
  lengthDays: number
  initialCash: number
  /** 시즌 종목 수 — 실명이며 시총 상위 최대 200(API 명세 v0.30) */
  tickerCount?: number
  /** 대회만 값이 있다. 연습·시연은 참가비가 없다 */
  entryFee?: number
}

/* 시기는 어떤 응답에도 오지 않는다 — baseDate·연도 필드를 타입에 두지 않는 것이
   그 규칙을 지키는 방법이다. 타입에 없으면 실수로 그릴 수 없다(API 명세 v0.24). */

/**
 * 커서 페이징이 없는 목록이라(§1 규칙 6 의 예외) useCursorList 를 쓰지 않는다.
 *
 * mode 를 넘기면 서버가 걸러 준다. 연습 화면이 클라이언트에서 filter 하던 것을
 * 서버 필터로 바꾼 이유는 DEMO 가 관리자 전용이기 때문이다 — 화면에서 걸러도
 * 응답에는 실려 오므로 개발자도구로 다 보인다. 정책은 서버에서 지켜야 한다.
 */
export function getOpenRuns(mode?: SeasonMode) {
  return api.get<{ items: OpenRun[] }>('/seasons', { query: { mode } })
}

/** 연습 목록. 설계서 §3 G-02a 가 요구하는 GET /seasons?mode=PRACTICE 다. */
export const getPracticeRuns = () => getOpenRuns('PRACTICE')

/* ── 화면이 함께 쓰는 계산 ────────────────────────────────── */

/**
 * 진행률(%). 응답의 `progress` 를 쓰지 않는 이유는 명세에 단위가 적혀 있지 않아서다.
 * 두 값에서 직접 구하면 화면에 함께 적는 "D+n / 총 n일" 과 어긋날 수 없다.
 */
export const progressOf = (currentDay: number, lengthDays: number) =>
  lengthDays > 0 ? Math.max(0, Math.min(100, Math.round((currentDay / lengthDays) * 100))) : 0

/** 시작 순서를 드러내는 값이 응답에 없어 seasonId 로 대신한다. 큰 쪽이 나중이다. */
export const recentFirst = (a: MyRun, b: MyRun) => b.seasonId - a.seasonId

/* 참가할 수 있는 것만 쓴다. CLOSED 는 G-09 기록에서 본다.
   진행 중인 쪽을 먼저 — 바로 들어갈 수 있는 것이 우선이다. */
const RANK: Record<string, number> = { RUNNING: 0, SCHEDULED: 1 }
export const joinableFirst = (a: OpenRun, b: OpenRun) =>
  (RANK[a.status] ?? 9) - (RANK[b.status] ?? 9) || a.id - b.id
export const isJoinable = (s: OpenRun) => s.status !== 'CLOSED'

/* 두 금액의 단위가 다르다 — 예수금은 금융망 원화(명세 §5.2), 참가비는 참가 시
   소각하는 ANT 다(설계서 §4 G-03). 섞으면 안 된다. */
export const won = (n: number) => `${n.toLocaleString('ko-KR')}원`
export const ant = (n: number) => `${n.toLocaleString('ko-KR')} ANT`
