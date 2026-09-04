/* ⚠ 임시 목데이터 — GET /rankings 가 열리면 이 파일을 통째로 지운다.

   지우는 절차
   1. rankings.ts 의 fetchRankings · fetchMyRank 에서 주석 처리된 api.get 을 살린다
   2. 이 파일과 rankings.ts 의 import 한 줄을 지운다
   화면 코드는 손대지 않아도 된다.

   값은 지어냈지만 형태는 지어내지 않았다 — 명세 §랭킹의 응답과 백엔드 Ranking
   엔티티의 컬럼(score · hitRate · avgError 는 nullable, doneCount 는 not null)을
   그대로 따른다. 그래야 실제 API 로 갈아끼울 때 화면이 그대로 동작한다.

   일부러 섞어 둔 것
   - 지표가 null 인 행: 판정 표본이 아직 없는 예측가. 화면이 0 이 아니라 "—" 로 그리는지 본다.
   - REAL 과 REPLAY 의 점수대를 다르게: 두 트랙을 같은 표에 섞으면 안 된다는 제약을 눈으로 확인한다.
   - tier: REPLAY 에만 채운다. REAL 에서 티어가 보이면 설계 제약 위반이다. */
import type { MyRank, RankingPage, RankingQuery, RankingRow, Track } from './rankings'

/** 목이라도 네트워크처럼 비동기여야 로딩 상태가 실제로 지나간다 */
const LATENCY_MS = 200

function delay<T>(value: T): Promise<T> {
  return new Promise((resolve) => setTimeout(() => resolve(value), LATENCY_MS))
}

const NICKNAMES = [
  '반도체훈련소', '중꺾마투자', '코스닥사냥꾼', '배당수집가', '차트도사',
  '가치투자김씨', '이차전지덕후', '외국인따라가기', '실적발표대기중', '역발상러',
  '바이오관찰자', '금융주매니아', '느긋한장기전', '아침형트레이더', '숫자만봅니다',
  '리스크관리자', '분할매수신봉자', '공시읽는사람', '테마주경계령', '조용한복리',
]

/* 결정적 난수. 새로고침마다 순위가 뒤집히면 화면을 검증할 수 없다.
   시드만 있으면 같은 입력에 같은 목록이 나온다. */
function seeded(seed: number) {
  let s = seed
  return () => {
    s = (s * 1103515245 + 12345) % 2147483648
    return s / 2147483648
  }
}

function trackSeed(track: Track, period?: string, sector?: string, seasonId?: number) {
  const key = `${track}|${period ?? ''}|${sector ?? ''}|${seasonId ?? ''}`
  let h = 7
  for (const ch of key) h = (h * 31 + ch.charCodeAt(0)) % 2147483647
  return h
}

const TOTAL = 48

function rows(query: RankingQuery): RankingRow[] {
  const rand = seeded(trackSeed(query.track, query.period, query.sector, query.seasonId))
  // 실전은 표본이 적어 점수대가 낮게, 리플레이는 회차가 많아 높게 잡는다
  const top = query.track === 'REAL' ? 82 : 94

  return Array.from({ length: TOTAL }, (_, i) => {
    const rank = i + 1
    // 상위일수록 높은 점수. 같은 순위에 같은 값이 나오도록 rank 로만 만든다
    const score = top - i * 1.4 - rand() * 0.8
    // 판정 표본이 없는 예측가를 하위권에 섞는다
    const noSample = rank > TOTAL - 5
    return {
      rank,
      userId: 100 + i,
      nickname: NICKNAMES[i % NICKNAMES.length] + (i >= NICKNAMES.length ? `${Math.floor(i / NICKNAMES.length) + 1}` : ''),
      score: noSample ? null : Number(score.toFixed(3)),
      hitRate: noSample ? null : Number((top - i * 1.1 - rand() * 2).toFixed(2)),
      avgError: noSample ? null : Number((0.8 + i * 0.05 + rand() * 0.3).toFixed(3)),
      doneCount: noSample ? 0 : Math.max(3, 140 - i * 3 - Math.floor(rand() * 8)),
    }
  })
}

export function fetchRankingsMock(query: RankingQuery): Promise<RankingPage> {
  const all = rows(query)
  const from = query.fromRank ?? 1
  const limit = query.limit ?? 20
  return delay({
    // 매 영업일 배치라 분 단위까지 고정된 값이 온다
    computedAt: '2026-09-02T06:10:00+09:00',
    items: all.slice(from - 1, from - 1 + limit),
  })
}

export function fetchMyRankMock(track: Track, seasonId?: number): Promise<MyRank> {
  const rand = seeded(trackSeed(track, undefined, undefined, seasonId))
  const rank = track === 'REAL' ? 37 : 12
  return delay({
    rank,
    percentile: Number((rank / TOTAL * 100).toFixed(1)),
    delta: track === 'REAL' ? -3 : 5,
    // 티어는 리플레이 전용이다. REAL 이면 서버도 null 을 준다.
    tier: track === 'REPLAY' ? (rand() > 0.5 ? 'PLATINUM' : 'GOLD') : null,
  })
}
