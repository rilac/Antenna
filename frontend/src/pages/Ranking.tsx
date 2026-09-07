/* E-01 예측가 랭킹 · /rankings
   담당 스토리 [ANT-FE-RANKING]
   설계서 docs/화면설계서.md §3 E · §4 E-01 · §7 · §9.2 · §10

   설계 제약
   - 트랙 분리는 타협하지 않는다. REAL 과 REPLAY 지표를 같은 표에 섞지 않는다.
     탭을 바꾸면 목록·내 순위·필터가 통째로 갈린다.
   - tier 는 REPLAY 전용이다. 실전 화면에 노출하지 않는다.
   - 배치 B3 스냅샷이라 computedAt 표시가 필수다(SnapshotStamp). 실시간처럼 보이면 안 된다.
   - score 산식은 미확정이다. 값만 표시하고 산식 설명을 쓰지 않는다.
   - "더 보기"는 커서가 아니라 fromRank 오프셋이다 — 배치 스냅샷이라 중간 삽입이 없어
     오프셋이 안전하다(명세 §1 페이징 예외). 그래서 useCursorList 를 쓰지 않는다.

   두지 않는 것: 실전 화면의 리플레이 티어 표시.

   ⚠ GET /rankings 가 아직 없어 api/rankings.mock.ts 가 응답을 대신한다.
     화면 코드는 실제 응답 형태를 그대로 다루므로, API 가 열리면 rankings.ts 의
     두 함수 본문만 바꾸면 된다. 이 파일은 손대지 않는다. */
import { useEffect, useState } from 'react'
import { Link } from 'react-router-dom'
import {
  PERIODS, PERIOD_LABEL, SECTORS, TIER_LABEL, TRACKS, TRACK_LABEL,
  fetchMyRank, fetchRankings, formatComputedAt, metric,
  type MyRank, type Period, type RankingRow, type Sector, type Track,
} from '../api/rankings'
import EmptyState from '../components/state/EmptyState'
import SnapshotStamp from '../components/SnapshotStamp'
import '../styles/screens/ranking.css'

const PAGE = 20

export default function Ranking() {
  const [track, setTrack] = useState<Track>('REAL')
  const [period, setPeriod] = useState<Period>('ALL')
  const [sector, setSector] = useState<Sector | null>(null)

  const [rows, setRows] = useState<RankingRow[]>([])
  const [computedAt, setComputedAt] = useState<string | null>(null)
  const [me, setMe] = useState<MyRank | null>(null)
  const [loading, setLoading] = useState(true)
  /** 마지막 응답이 꽉 찼으면 더 있을 수 있다. 응답에 hasNext 가 없어 이렇게 가른다 */
  const [more, setMore] = useState(false)

  /* 트랙·필터가 바뀌면 목록을 처음부터 다시 읽는다.
     REAL 과 REPLAY 를 섞지 않기 위해 이어 붙이지 않고 통째로 갈아친다. */
  /* loading 은 이 effect 가 아니라 아래 changeTrack·changePeriod·changeSector 에서 세운다.
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
        setRows(page.items)
        setComputedAt(page.computedAt)
        setMe(mine)
        setMore(page.items.length === PAGE)
      })
      .finally(() => { if (!cancelled) setLoading(false) })

    return () => { cancelled = true }
  }, [track, period, sector])

  /* 필터를 바꾸는 순간이 곧 다시 읽기 시작하는 순간이다. 여기서 로딩을 세운다. */
  function changeTrack(next: Track) {
    if (next === track) return
    setLoading(true)
    // 트랙마다 필터 구성이 달라 기간·섹터를 기본값으로 되돌린다
    setPeriod('ALL')
    setSector(null)
    setTrack(next)
  }

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
      .finally(() => setLoading(false))
  }

  return (
    <main className="main">
      <div className="main-inner">
        <div className="page-head">
          <h1>예측가 랭킹</h1>
          <p>판정이 끝난 예측만 집계합니다</p>
        </div>

        {/* 트랙 탭 — 두 트랙은 집계가 완전히 분리된다 */}
        <div className="rk-tracks" role="tablist" aria-label="랭킹 트랙">
          {TRACKS.map((t) => (
            <button
              key={t} type="button" role="tab"
              aria-selected={track === t}
              className={track === t ? 'on' : ''}
              onClick={() => changeTrack(t)}
            >
              {TRACK_LABEL[t]}
            </button>
          ))}
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
                {SECTORS.map((s) => (
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

        {!loading && rows.length === 0 && (
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
