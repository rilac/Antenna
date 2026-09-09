/* 랭킹 도메인. API 명세서 §랭킹 · 설계서 §3 E · §9.2.

   GET /rankings?track=&period=&sector=&seasonId=&limit=&fromRank=
     → { computedAt, items: [{ rank, userId, nickname, score, hitRate, avgError, doneCount }] }
   GET /rankings/me?track=&seasonId=
     → { rank, percentile, delta, tier }

   백엔드 3건(ANT-RANK-01 배치 · 02 목록 · 03 내 순위)이 모두 dev 에 있어 목업을 걷어냈다
   (ANT-FE-RANKING-LIVE).

   **판정이 쌓이기 전에는 빈 목록이 정상이다.** 배치 B3 가 predictions 의 HIT/MISS 로
   스냅샷을 만들므로 판정이 0건이면 서버가 computedAt: null · items: [] 를 준다.
   404 가 아니다 — 화면은 이걸 오류가 아니라 빈 상태로 그린다. */
import { api } from './client'

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

/**
 * 섹터 필터 값. **목록을 여기서 만들지 않는다.**
 *
 * 서버는 stocks.sector 의 KRX 업종명(전기·전자 · 화학 등)을 필터 키로 쓰고, 그 어휘는
 * GET /stocks/sectors 가 준다. 예전에는 여기 테마 6개(반도체 · 2차전지 · 자동차 …)를
 * 상수로 두었는데 서버 어휘와 1:1 이 아니었다 — 전기·전자 안에 반도체가, 화학 안에
 * 2차전지가 들어 있다. 그대로 두고 실 API 로 넘기면 섹터 필터가 항상 빈 목록을 낸다.
 *
 * 종목 탐색(B-02)이 이미 같은 함수로 칩을 그리므로, 재사용하면 앱 안에서 섹터 목록이
 * 한 벌로 통일된다. 어휘가 늘어도 화면을 고칠 필요가 없다.
 */
export type Sector = string

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
  /** 배치가 아직 안 돌았으면 null 이다(서버 RankingListResponse.empty). 화면은 이때 도장을 숨긴다 */
  computedAt: string | null
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

/* ── 조회 ─────────────────────────────────────────────── */

export function fetchRankings(query: RankingQuery): Promise<RankingPage> {
  return api.get<RankingPage>('/rankings', { query })
}

/**
 * 내 순위. **랭킹에 없으면 204 라 undefined 가 온다** — 서버가 404 를 쓰지 않는 이유는
 * "없는 리소스" 가 아니라 "아직 순위가 안 잡힌 정상 상태"(스냅샷 전이거나 판정 3건 미만,
 * 명세 v0.35)라서다. client.ts 가 204 를 undefined 로 바꿔 주므로 반환 타입에 그대로
 * 드러낸다 — MyRank 로 적어 두면 호출부가 없는 값을 있다고 믿는다.
 */
export function fetchMyRank(track: Track, seasonId?: number): Promise<MyRank | undefined> {
  return api.get<MyRank | undefined>('/rankings/me', { query: { track, seasonId } })
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
