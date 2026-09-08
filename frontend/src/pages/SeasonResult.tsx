/* G-08 결과 · AI 복기 · /sim/:id/result
   담당 스토리 [ANT-FE-SEASON-RESULT]
   설계서 docs/화면설계서.md §3 · §4 G-08.

   POST /finish 가 만든 season_results 를 GET /seasons/{id}/result/me 로 읽고,
   마지막 날 포트폴리오는 GET /seasons/{id}/me 로 읽는다(설계서 G-08 의 두 API).
   AI 복기는 아직 생성되지 않는다(ANT-SEASON-09) — null 이면 "준비 중" 으로 그린다. */
import { Link, useParams } from 'react-router-dom'
import { getMyStatus, getResult, rate, signOf, type MyStatus, type SeasonResultData } from '../api/seasonPlay'
import { useApiQuery } from '../api/useApiQuery'
import { useAsync } from '../api/useAsync'
import { useCallback } from 'react'
import ErrorState from '../components/state/ErrorState'
import '../styles/screens/sim-play.css'

type SeasonHead = { id: number; title: string; lengthDays: number; initialCash: number }

const won = (n: number) => `${Math.round(n).toLocaleString('ko-KR')}원`
const num = (n: number | null, unit = '') => (n == null ? '—' : `${n.toLocaleString('ko-KR')}${unit}`)

export default function SeasonResult() {
  const { id } = useParams<{ id: string }>()
  const seasonId = Number(id)
  const season = useApiQuery<SeasonHead>(`/seasons/${id}`)
  const loadResult = useCallback(() => getResult(seasonId), [seasonId])
  const loadMe = useCallback(() => getMyStatus(seasonId), [seasonId])
  const result = useAsync<SeasonResultData>(loadResult)
  const me = useAsync<MyStatus>(loadMe)

  if (season.loading || result.loading) {
    return (
      <main className="main">
        <div className="main-inner sim-play">
          <div className="placeholder tall">{'결과를 불러오는 중…'}</div>
        </div>
      </main>
    )
  }

  if (season.error || result.error || !season.data || !result.data) {
    return (
      <main className="main">
        <div className="main-inner sim-play">
          {result.error
            ? <ErrorState error={result.error} onRetry={result.reload} />
            : season.error
              ? <ErrorState error={season.error} onRetry={season.reload} />
              : <div className="placeholder tall">{'아직 결과가 없습니다'}</div>}
          <p className="sp-note"><Link to={`/sim/seasons/${seasonId}`}>시즌으로 돌아가기 ›</Link></p>
        </div>
      </main>
    )
  }

  const s = season.data
  const r = result.data
  const positions = me.data?.positions ?? []
  const pnl = r.finalAsset - s.initialCash

  return (
    <main className="main">
      <div className="main-inner sim-play">
        <nav className="sp-crumb" aria-label="위치">
          <Link to="/sim">모의투자 홈</Link>
          <i aria-hidden="true">›</i>
          <Link to="/sim/practice">연습</Link>
          <i aria-hidden="true">›</i>
          <span>결과</span>
        </nav>

        <header className="sp-head">
          <div className="sp-title">
            <h1>{s.title}</h1>
            <p>연습 종료 · {s.lengthDays}게임일 완주</p>
          </div>
        </header>

        <section className="sp-stats" aria-label="최종 성과">
          <div className="sp-stat t-asset">
            <div className="sp-stat-txt">
              <span className="sp-stat-label">최종 자산</span>
              <b className="sp-stat-val num">{won(r.finalAsset)}</b>
              <span className="sp-stat-sub num">시작 {won(s.initialCash)}</span>
            </div>
          </div>
          <div className={`sp-stat t-${signOf(pnl)}`}>
            <div className="sp-stat-txt">
              <span className="sp-stat-label">수익률</span>
              <b className="sp-stat-val num">{rate(r.returnRate)}</b>
              <span className="sp-stat-sub num">
                {r.benchmarkReturn == null ? '벤치마크 —' : `시장(등가중) ${rate(r.benchmarkReturn)}`}
              </span>
            </div>
          </div>
          <div className="sp-stat t-stock">
            <div className="sp-stat-txt">
              <span className="sp-stat-label">최대 낙폭</span>
              <b className="sp-stat-val num">{r.maxDrawdown == null ? '—' : `-${r.maxDrawdown.toFixed(2)}%`}</b>
              <span className="sp-stat-sub num">고점 대비</span>
            </div>
          </div>
          <div className="sp-stat t-cash">
            <div className="sp-stat-txt">
              <span className="sp-stat-label">매매</span>
              <b className="sp-stat-val num">승률 {r.winRate == null ? '—' : `${r.winRate.toFixed(0)}%`}</b>
              <span className="sp-stat-sub num">
                손익비 {num(r.profitFactor)} · 평균 보유 {num(r.avgHoldingDays, '일')}
              </span>
            </div>
          </div>
        </section>

        <div className="sp-body">
          <section className="sp-card" aria-label="마지막 날 포트폴리오">
            <h2>마지막 날 포트폴리오</h2>
            {me.error && <ErrorState error={me.error} onRetry={me.reload} inline />}
            {positions.length === 0 ? (
              <p className="sp-empty">
                보유 종목 없이 마쳤습니다.
                <small>현금 {won(r.finalAsset)}</small>
              </p>
            ) : (
              <ul className="sp-positions">
                {positions.map((p) => (
                  <li key={p.tickerId} className="num">
                    <b>{p.displayName}</b>
                    <span>{p.qty.toLocaleString('ko-KR')}주 · 평균 {won(p.avgPrice)}</span>
                    <span className={signOf(p.pnl)}>{p.pnl > 0 ? '+' : ''}{won(p.pnl)}</span>
                  </li>
                ))}
              </ul>
            )}
          </section>

          <section className="sp-card" aria-label="AI 복기">
            <h2>AI 복기</h2>
            {r.review ? (
              <p className="sp-review">{r.review}</p>
            ) : (
              <p className="sp-soon">
                체결 내역을 바탕으로 잘한 판단과 아쉬운 판단을 짚어 줍니다.
                <small>생성 준비 중 · ANT-SEASON-09</small>
              </p>
            )}
          </section>
        </div>

        <p className="sp-note">
          <Link to={`/sim/${seasonId}/trades`}>매매일지 보기 ›</Link>
          {'  ·  '}
          <Link to={`/sim/seasons/${seasonId}`}>같은 주제 다시 하기 ›</Link>
          {'  ·  '}
          <Link to="/sim/practice">연습 홈 ›</Link>
        </p>
      </div>
    </main>
  )
}
