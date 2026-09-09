/* E-01 예측가 랭킹 — 트랙마다 화면이 하나씩이다
     /rankings      주가 예측 랭킹 (REAL) · 인사이트 모드
     /sim/rankings  모의투자 랭킹 (REPLAY) · 모의 투자 모드
   담당 스토리 [ANT-FE-RANKING]
   설계서 docs/화면설계서.md §3 E · §4 E-01 · §7 · §9.2 · §10

   **설계서와 다르다.** §4 E-01 은 한 화면에 트랙 탭(REAL/REPLAY)을 두는 것으로
   적고 있는데, 화면을 둘로 갈랐다(2026-09-09 결정, 김경민 님과 합의).

   이유는 셸이다. 사이드바가 인사이트·모의 투자 두 모드로 갈리고 라우트마다
   mode 가 붙는데, 한 화면이 두 모드에 속할 수 없다. 탭 하나로 두면 모의 투자
   바에서 "모의투자 랭킹" 을 눌렀을 때 인사이트 모드로 넘어간다 — 실제로 그랬다.
   트랙을 라우트가 정하므로 탭은 없앴다.

   설계 제약
   - 트랙 분리는 타협하지 않는다. REAL 과 REPLAY 지표를 같은 표에 섞지 않는다.
     이제 화면부터 갈렸으니 섞일 자리가 없다.
   - tier 는 REPLAY 전용이다. 실전 화면에 노출하지 않는다.
   - 배치 B3 스냅샷이라 computedAt 표시가 필수다(SnapshotStamp). 실시간처럼 보이면 안 된다.
   - score 산식은 미확정이다. 값만 표시하고 산식 설명을 쓰지 않는다.
   - "더 보기"는 커서가 아니라 fromRank 오프셋이다 — 배치 스냅샷이라 중간 삽입이 없어
     오프셋이 안전하다(명세 §1 페이징 예외). 그래서 useCursorList 를 쓰지 않는다.

   두지 않는 것: 실전 화면의 리플레이 티어 표시.

   섹터 칩은 서버 어휘를 그대로 쓴다(ANT-FE-RANKING-LIVE)
   예전에는 api/rankings.ts 가 테마 6개를 상수로 들고 있었는데, 서버 필터 키는
   stocks.sector 의 KRX 업종명이라 1:1 이 아니었다 — 전기·전자 안에 반도체가,
   화학 안에 2차전지가 들어 있다. 그래서 종목 탐색(B-02)과 같은 GET /stocks/sectors 를
   재사용한다. 어휘가 늘어도 이 화면은 고칠 것이 없다. */
import { useCallback, useEffect, useState } from 'react'
import { Link } from 'react-router-dom'
import type { ApiError } from '../api/errors'
import {
  PERIODS, PERIOD_LABEL, TIER_LABEL,
  fetchMyRank, fetchRankings, formatComputedAt, metric,
  type MyRank, type Period, type RankingRow, type Sector, type Track,
} from '../api/rankings'
import { getSectorSummary } from '../api/stocks'
import { useAsync } from '../api/useAsync'
import EmptyState from '../components/state/EmptyState'
import ErrorState from '../components/state/ErrorState'
import SnapshotStamp from '../components/SnapshotStamp'
import '../styles/screens/ranking.css'

const PAGE = 20

/** 트랙만 다르고 나머지는 같다. 두 라우트가 이 하나를 나눠 쓴다. */
function RankingBoard({ track }: { track: Track }) {
  const [period, setPeriod] = useState<Period>('ALL')
  const [sector, setSector] = useState<Sector | null>(null)

  const [rows, setRows] = useState<RankingRow[]>([])
  const [computedAt, setComputedAt] = useState<string | null>(null)
  const [me, setMe] = useState<MyRank | null>(null)
  const [loading, setLoading] = useState(true)
  /** 마지막 응답이 꽉 찼으면 더 있을 수 있다. 응답에 hasNext 가 없어 이렇게 가른다 */
  const [more, setMore] = useState(false)
  /* 목업일 때는 실패할 일이 없어 오류 자리가 없었다. 실 API 로 넘기면서 둔다 —
     없으면 401·500 에 화면이 조용히 빈 채로 남고, 사용자는 순위가 없는 줄 안다. */
  const [error, setError] = useState<ApiError | null>(null)
  /* 다시 시도. 필터를 바꾸지 않고 같은 조건으로 다시 읽어야 하는데, changeSector 는
     값이 같으면 조기 반환하므로 쓸 수 없다. 이 값을 올려 effect 를 다시 돌린다. */
  const [attempt, setAttempt] = useState(0)

  /* 섹터 칩은 종목 탐색(B-02)과 같은 어휘를 쓴다. 실패하면 칩만 빠지고 목록은 살아 있다
     — 섹터는 거들 뿐이라 이것 때문에 화면 전체를 오류로 덮지 않는다(B-02 와 같은 판단). */
  const loadSectors = useCallback(() => getSectorSummary(), [])
  const sectorList = useAsync(loadSectors)
  /* sector 가 null 인 행은 "전체" 요약이라 칩으로 만들지 않는다 — 전체 버튼이 따로 있다 */
  const sectors = (sectorList.data?.items ?? [])
    .map((s) => s.sector)
    .filter((s): s is string => s !== null)

  /* 필터가 바뀌면 목록을 처음부터 다시 읽는다. 이어 붙이지 않고 통째로 갈아친다.
     track 은 라우트가 정하는 값이라 이 화면 안에서 바뀌지 않는다. */
  /* loading 은 이 effect 가 아니라 아래 changePeriod·changeSector 에서 세운다.
     effect 안에서 동기 setState 를 하면 렌더가 한 번 더 돈다 — 첫 진입은 useState(true) 가 덮는다. */
  useEffect(() => {
    let cancelled = false

    const query = track === 'REAL'
      ? { track, period, sector: sector ?? undefined, limit: PAGE }
      // 리플레이는 기간·섹터가 없다. 시즌 선택은 G-01 이 시즌을 만든 뒤 붙인다.
      : { track, limit: PAGE }

    Promise.all([fetchRankings(query), fetchMyRank(track)])
      .then(([page, mine]) => {
        if (cancelled) return
        setError(null)
        setRows(page.items)
        setComputedAt(page.computedAt)
        // 랭킹에 없으면 204 라 undefined 다. 내 순위 카드를 그리지 않는다
        setMe(mine ?? null)
        setMore(page.items.length === PAGE)
      })
      .catch((e: unknown) => {
        if (cancelled) return
        /* 목록을 비운다. 옛 필터 결과가 오류 배너와 함께 남아 있으면
           "이 조건의 결과" 로 읽힌다. */
        setRows([])
        setMe(null)
        setMore(false)
        setError(e as ApiError)
      })
      .finally(() => { if (!cancelled) setLoading(false) })

    return () => { cancelled = true }
  }, [track, period, sector, attempt])

  /* 필터를 바꾸는 순간이 곧 다시 읽기 시작하는 순간이다. 여기서 로딩을 세운다. */
  function changePeriod(next: Period) {
    if (next === period) return
    setLoading(true)
    setPeriod(next)
  }

  function changeSector(next: Sector | null) {
    if (next === sector) return
    setLoading(true)
    setSector(next)
  }

  /* 순위 직행. 커서가 아니라 다음 순위부터 오프셋으로 가져온다. */
  function loadMore() {
    if (loading || !more) return
    setLoading(true)
    const next = rows.length + 1
    const query = track === 'REAL'
      ? { track, period, sector: sector ?? undefined, limit: PAGE, fromRank: next }
      : { track, limit: PAGE, fromRank: next }

    fetchRankings(query)
      .then((page) => {
        setRows((prev) => [...prev, ...page.items])
        setMore(page.items.length === PAGE)
      })
      .catch((e: unknown) => {
        /* 이미 읽은 줄은 그대로 둔다 — 다음 장을 못 가져온 것뿐이라 앞 순위를 지우면
           사용자가 보던 것을 잃는다. "더 보기" 만 닫고 오류를 알린다. */
        setMore(false)
        setError(e as ApiError)
      })
      .finally(() => setLoading(false))
  }

  return (
    <main className="main">
      <div className="main-inner">
        <div className="page-head">
          <h1>{track === 'REAL' ? '주가 예측 랭킹' : '모의투자 랭킹'}</h1>
          <p>
            {track === 'REAL'
              ? '판정이 끝난 예측만 집계합니다'
              : '리플레이 시즌 성적만 집계합니다. 실전 신뢰도에는 반영되지 않습니다.'}
          </p>
        </div>

        {/* 섹터는 REAL 에만 있다(명세 §랭킹).
            기간 스위치는 표 바로 위 툴바로 옮겼다 — 목록에 바로 걸리는 조건이라
            표에 붙어 있는 편이 무엇을 거른 결과인지 읽기 쉽다. */}
        {track === 'REAL' && (
          <div className="rk-filters">
            <div className="rk-filter-row">
              <span className="rk-filter-label" id="rk-sector-label">섹터</span>
              <div className="rk-sectors" role="group" aria-labelledby="rk-sector-label">
                <button
                  type="button" aria-pressed={sector === null}
                  className={sector === null ? 'on' : ''}
                  onClick={() => changeSector(null)}
                >전체</button>
                {sectors.map((s) => (
                  <button
                    key={s} type="button"
                    aria-pressed={sector === s}
                    className={sector === s ? 'on' : ''}
                    onClick={() => changeSector(s)}
                  >{s}</button>
                ))}
              </div>
            </div>
          </div>
        )}

        {/* 내 순위 — 트랙마다 다르다. 티어는 REPLAY 에서만 붙는다 */}
        {me && (
          <section className="rk-me">
            <div className="rk-me-rank">
              <span className="rk-me-label">내 순위</span>
              <p><b className="num">{me.rank}</b><small>위</small></p>
            </div>

            <div className="rk-me-stats">
              <span className="num">{`상위 ${me.percentile}%`}</span>
              {me.delta !== 0 && (
                <span className={`rk-delta ${me.delta > 0 ? 'up' : 'down'} num`}>
                  {`${me.delta > 0 ? '▲' : '▼'} ${Math.abs(me.delta)}`}
                </span>
              )}
              {/* 실전에서는 서버가 null 을 준다 — 여기서 걸러도 트랙 조건을 한 번 더 둔다 */}
              {track === 'REPLAY' && me.tier && (
                <span className="rk-tier">{TIER_LABEL[me.tier]}</span>
              )}
            </div>
          </section>
        )}

        {/* 표 툴바 — 왼쪽은 이 목록이 언제 만들어졌는지, 오른쪽은 무엇으로 걸렀는지 */}
        <div className="rk-tablebar">
          {computedAt ? <SnapshotStamp at={formatComputedAt(computedAt)} /> : <span />}

          {/* 두 값 중 하나를 고르는 배타 선택이라 세그먼트 컨트롤로 둔다.
              고른 값과 고르지 않은 값이 나란히 보여, 지금 무엇으로 집계 중인지와
              무엇으로 바꿀 수 있는지를 한 번에 읽는다. 기간은 REAL 에만 있다. */}
          {track === 'REAL' && (
            <div className="rk-period-seg" role="group" aria-label="집계 기간">
              {PERIODS.map((p) => (
                <button
                  key={p} type="button"
                  aria-pressed={period === p}
                  className={period === p ? 'on' : ''}
                  onClick={() => changePeriod(p)}
                >{PERIOD_LABEL[p]}</button>
              ))}
            </div>
          )}
        </div>

        {/* 오류가 먼저다. 빈 목록과 못 불러온 것은 다르다 — 오류를 빈 상태로 그리면
            "아직 랭킹이 없구나" 로 읽혀 다시 시도할 생각을 못 한다. */}
        {error && (
          <ErrorState
            error={error}
            onRetry={() => { setLoading(true); setAttempt((n) => n + 1) }}
          />
        )}

        {!error && !loading && rows.length === 0 && (
          <EmptyState
            title="집계된 랭킹이 없습니다"
            hint="판정이 끝난 예측이 쌓이면 다음 배치에 반영됩니다"
          />
        )}

        {rows.length > 0 && (
          <div className="rk-table-wrap">
            <table className="rk-table">
              <thead>
                <tr>
                  <th scope="col" className="rk-col-rank">순위</th>
                  <th scope="col">예측가</th>
                  {/* score 산식은 미확정이라 머리글에 설명을 달지 않는다 */}
                  <th scope="col" className="rk-col-num">점수</th>
                  <th scope="col" className="rk-col-num">적중률</th>
                  <th scope="col" className="rk-col-num">평균 오차</th>
                  <th scope="col" className="rk-col-num">판정</th>
                </tr>
              </thead>
              <tbody>
                {rows.map((r) => (
                  <tr key={r.userId}>
                    <td className="rk-col-rank num">{r.rank}</td>
                    <td>
                      {/* 예측가 이름은 채널 프로필(E-02)로 가는 문이다 */}
                      <Link className="rk-name" to={`/channels/${r.userId}`}>{r.nickname}</Link>
                    </td>
                    <td className="rk-col-num num">{metric(r.score, 1)}</td>
                    <td className="rk-col-num num">{metric(r.hitRate, 1, '%')}</td>
                    <td className="rk-col-num num">{metric(r.avgError, 2)}</td>
                    <td className="rk-col-num num">{r.doneCount.toLocaleString('ko-KR')}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}

        {loading && <p className="rk-loading">불러오는 중…</p>}

        {more && !loading && (
          <button type="button" className="rk-more" onClick={loadMore}>더 보기</button>
        )}
      </div>
    </main>
  )
}

/* 라우트가 트랙을 정한다. routes.ts 의 element 는 props 를 받지 않으므로
   화면마다 얇은 컴포넌트를 하나씩 둔다. */
export default function Ranking() {
  return <RankingBoard track="REAL" />
}

export function SimRanking() {
  return <RankingBoard track="REPLAY" />
}
