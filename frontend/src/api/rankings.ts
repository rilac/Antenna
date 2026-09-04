/* 랭킹 도메인. API 명세서 §랭킹 · 설계서 §3 E · §9.2.

   GET /rankings?track=&period=&sector=&seasonId=&limit=&fromRank=
     → { computedAt, items: [{ rank, userId, nickname, score, hitRate, avgError, doneCount }] }
   GET /rankings/me?track=&seasonId=
     → { rank, percentile, delta, tier }

   ⚠ 백엔드 미구현 — 지금은 rankings.mock.ts 가 응답을 대신한다.
     Ranking 엔티티와 RankingRepository 는 이미 있으나 컨트롤러가 없다.
     아래 두 함수의 본문만 api.get 으로 바꾸고 mock 파일을 지우면 끝나도록 짜 두었다. */
import { fetchRankingsMock, fetchMyRankMock } from './rankings.mock'

/** 실전과 리플레이는 랭킹이 분리된다. 리플레이 실적은 실전 신뢰도에 반영하지 않는다. */
export const TRACKS = ['REAL', 'REPLAY'] as const
export type Track = (typeof TRACKS)[number]

export const TRACK_LABEL: Record<Track, string> = {
  REAL: '실전',
  REPLAY: '모의투자',
}

/** REAL 트랙에만 붙는 기간 필터. 명세상 두 값뿐이다. */
export const PERIODS = ['ALL', 'D30'] as const
export type Period = (typeof PERIODS)[number]

export const PERIOD_LABEL: Record<Period, string> = {
  ALL: '전체',
  D30: '최근 30일',
}

/** 종목 탐색(B-02)과 같은 섹터 어휘를 쓴다. */
export const SECTORS = ['반도체', '2차전지', '자동차', '인터넷', '바이오', '금융'] as const
export type Sector = (typeof SECTORS)[number]

/**
 * 랭킹 한 줄. 서버 Ranking 엔티티의 공개 필드와 짝이다.
 *
 * score 산식은 미확정이다(명세 §랭킹). 값만 보여주고 산식을 화면에 설명하지 않는다.
 * 아직 판정 표본이 없으면 지표가 null 로 온다 — 엔티티에서 nullable 인 셋이 그렇다.
 */
export type RankingRow = {
  rank: number
  userId: number
  nickname: string
  score: number | null
  /** 적중률 % */
  hitRate: number | null
  /** 평균 오차 */
  avgError: number | null
  /** 판정 완료 건수 */
  doneCount: number
}

/**
 * 배치 스냅샷이라 computedAt 이 함께 온다.
 *
 * 이 값을 화면에서 빼면 실시간 순위로 읽힌다 — 매 영업일 배치(B3)가 만든 값이고
 * 요청할 때 다시 계산하지 않는다. 설계서 §7 의 SnapshotStamp 가 이 자리다.
 */
export type RankingPage = {
  computedAt: string
  items: RankingRow[]
}

/** 리플레이 전용 티어. 실전 화면에 노출하지 않는다(설계 제약). */
export const TIERS = ['DIAMOND', 'PLATINUM', 'GOLD'] as const
export type Tier = (typeof TIERS)[number]

export const TIER_LABEL: Record<Tier, string> = {
  DIAMOND: '다이아',
  PLATINUM: '플래티넘',
  GOLD: '골드',
}

export type MyRank = {
  rank: number
  /** 상위 백분위 */
  percentile: number
  /** 직전 스냅샷 대비 순위 변동. 양수가 상승이다 */
  delta: number
  /** REPLAY 에서만 채워진다 */
  tier: Tier | null
}

export type RankingQuery = {
  track: Track
  period?: Period
  sector?: Sector
  seasonId?: number
  limit?: number
  /**
   * 순위 직행 오프셋. 배치 스냅샷이라 중간 삽입이 없어 오프셋이 안전하다
   * — 명세 §1 의 커서 페이징 예외다.
   */
  fromRank?: number
}

/* ── 조회 ────────────────────────────────────────────────
   백엔드가 열리면 아래 두 함수 본문을 api.get 으로 바꾸고
   rankings.mock.ts 를 지운다. 호출부는 손대지 않아도 된다. */

export function fetchRankings(query: RankingQuery): Promise<RankingPage> {
  // return api.get<RankingPage>('/rankings', { query })
  return fetchRankingsMock(query)
}

export function fetchMyRank(track: Track, seasonId?: number): Promise<MyRank> {
  // return api.get<MyRank>('/rankings/me', { query: { track, seasonId } })
  return fetchMyRankMock(track, seasonId)
}

/** 배치 산출 시각. 실시간이 아니라는 것을 드러내야 하므로 분까지 적는다. */
export function formatComputedAt(iso: string) {
  const d = new Date(iso)
  if (Number.isNaN(d.getTime())) return iso
  return d.toLocaleString('ko-KR', {
    year: 'numeric', month: '2-digit', day: '2-digit',
    hour: '2-digit', minute: '2-digit',
  })
}

/** 표본이 없어 null 로 온 지표는 0 이 아니라 "—" 다. 0.0% 로 그리면 오해한다. */
export function metric(value: number | null, digits: number, suffix = '') {
  return value === null ? '—' : `${value.toFixed(digits)}${suffix}`
}
