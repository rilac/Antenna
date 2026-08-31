/* B-01 인사이트 홈 · /
   담당 스토리 [ANT-FE-HOME]

   백엔드에 조회 API 가 아직 없어 api/insight.ts 의 목업으로 그린다.
   호출 경로·응답 타입은 명세서대로 맞춰 두었으므로, 백엔드가 붙으면
   insight.ts 의 MOCK 만 false 로 바꾸면 된다.

   실전 시세는 전일 종가만 제공된다(법적 제약 · 명세서 §7).
   그래서 이 화면 어디에도 "현재가" 라는 말을 쓰지 않는다. */
import { useCallback } from 'react'
import { Link } from 'react-router-dom'
import {
  getActiveAds, getBriefings, getMarketIndices, getTopPredictors, getWalletBalance, getWatchlist,
} from '../api/insight'
import { useAsync } from '../api/useAsync'
import Sparkline from '../components/Sparkline'
import '../styles/home.css'

const INDEX_LABEL: Record<string, string> = {
  KOSPI: '코스피', KOSDAQ: '코스닥', USDKRW: '환율 (USD/KRW)',
}

const won = (n: number) => n.toLocaleString('ko-KR')
const signed = (n: number) => `${n >= 0 ? '+' : ''}${n.toFixed(2)}%`

/** 섹션 안의 로딩·실패·빈 상태. A-04 [ANT-FE-ERROR] 가 공용 컴포넌트를 만들면 그걸로 교체한다. */
function State({ loading, error, empty, onRetry }: {
  loading: boolean; error: unknown; empty?: boolean; onRetry?: () => void
}) {
  if (loading) return <p className="hm-state">불러오는 중…</p>
  if (error) return (
    <p className="hm-state err">
      불러오지 못했습니다.
      {onRetry && <button type="button" className="hm-retry" onClick={onRetry}>다시 시도</button>}
    </p>
  )
  if (empty) return <p className="hm-state">아직 표시할 내용이 없습니다.</p>
  return null
}

function MarketOverview() {
  const load = useCallback(() => getMarketIndices(30), [])
  const { data, loading, error, reload } = useAsync(load)

  return (
    <section className="card hm-card" aria-labelledby="hm-market">
      <header className="hm-head">
        <h2 id="hm-market">시장 Overview</h2>
        <Link to="/stocks">종목 보기 ›</Link>
      </header>
      <State loading={loading} error={error} onRetry={reload} />
      {data && (
        <ul className="hm-indices">
          {data.items.map((it) => {
            const up = it.changeRate >= 0
            return (
              <li key={it.code}>
                <span className="hm-ix-name">{INDEX_LABEL[it.code] ?? it.code}</span>
                <div className="hm-ix-row">
                  <b className="num">{won(it.close)}</b>
                  <Sparkline series={it.series} up={up} />
                </div>
                <span className={`hm-delta num ${up ? 'up' : 'down'}`}>{signed(it.changeRate)}</span>
              </li>
            )
          })}
        </ul>
      )}
      <p className="hm-foot">최근 30일 종가 흐름 · 실전 시세는 전일 종가만 제공됩니다</p>
    </section>
  )
}

function MyAssets() {
  const load = useCallback(() => getWalletBalance(), [])
  const { data, loading, error, reload } = useAsync(load)

  return (
    <section className="card hm-card" aria-labelledby="hm-assets">
      <header className="hm-head">
        <h2 id="hm-assets">내 자산 현황</h2>
        <Link to="/me/wallet">지갑 ›</Link>
      </header>
      <State loading={loading} error={error} onRetry={reload} />
      {data && (
        <>
          <p className="hm-ant"><b className="num">{won(data.balance)}</b> <em>ANT</em></p>
          <dl className="hm-ant-grid">
            <div><dt>사용 (30일)</dt><dd className="num">{won(data.spent30d)} ANT</dd></div>
            <div><dt>획득 (30일)</dt><dd className="num">{won(data.earned30d)} ANT</dd></div>
            <div><dt>총 가치</dt><dd className="num">₩{won(data.valuationKrw)}</dd></div>
          </dl>
        </>
      )}
    </section>
  )
}

function Briefings() {
  const load = useCallback(() => getBriefings('MARKET'), [])
  const { data, loading, error, reload } = useAsync(load)

  return (
    <section className="card hm-card" aria-labelledby="hm-brief">
      <header className="hm-head">
        <h2 id="hm-brief">오늘의 AI 브리핑</h2>
        {data?.items[0] && <span className="hm-date num">{data.items[0].targetDate}</span>}
      </header>
      <State loading={loading} error={error} empty={data?.items.length === 0} onRetry={reload} />
      {data && data.items.length > 0 && (
        <ul className="hm-brief">
          {data.items.map((b) => (
            <li key={b.id}><span className="hm-brief-mark" aria-hidden="true" />{b.headline}</li>
          ))}
        </ul>
      )}
      {/* 상세 본문은 모달 M-10 [ANT-FE-BRIEFING] 의 몫이라 아직 열리지 않는다 */}
      <p className="hm-foot">상세 보기는 M-10 브리핑 모달에서 이어집니다</p>
    </section>
  )
}

function Watchlist() {
  const load = useCallback(() => getWatchlist(), [])
  const { data, loading, error, reload } = useAsync(load)

  return (
    <section className="card hm-card hm-wide" aria-labelledby="hm-watch">
      <header className="hm-head">
        <h2 id="hm-watch">관심 종목</h2>
        <Link to="/watchlist">더보기 ›</Link>
      </header>
      <State loading={loading} error={error} empty={data?.items.length === 0} onRetry={reload} />
      {data && data.items.length > 0 && (
        <div className="hm-table-wrap">
          <table className="hm-table">
            <thead>
              <tr>
                <th scope="col">종목명</th>
                <th scope="col" className="r">전일 종가</th>
                <th scope="col" className="r">전일 대비</th>
                <th scope="col" className="c">최근 30일</th>
              </tr>
            </thead>
            <tbody>
              {data.items.map((s) => {
                const up = s.changeRate >= 0
                return (
                  <tr key={s.stockCode}>
                    <th scope="row">
                      <Link to={`/stocks/${s.stockCode}`}>
                        {s.name} <small className="num">{s.stockCode}</small>
                      </Link>
                    </th>
                    <td className="r num">{won(s.prevClose)}원</td>
                    <td className={`r num ${up ? 'up' : 'down'}`}>{signed(s.changeRate)}</td>
                    <td className="c"><Sparkline series={s.series} up={up} /></td>
                  </tr>
                )
              })}
            </tbody>
          </table>
        </div>
      )}
    </section>
  )
}

function TopPredictors() {
  const load = useCallback(() => getTopPredictors(5), [])
  const { data, loading, error, reload } = useAsync(load)

  return (
    <section className="card hm-card" aria-labelledby="hm-top">
      <header className="hm-head">
        <h2 id="hm-top">주목할 예측가</h2>
        <Link to="/rankings">랭킹 ›</Link>
      </header>
      <State loading={loading} error={error} empty={data?.items.length === 0} onRetry={reload} />
      {data && data.items.length > 0 && (
        <>
          <ol className="hm-top">
            {data.items.map((p) => (
              <li key={p.userId}>
                <span className="hm-rank num">{p.rank}</span>
                <Link className="hm-top-name" to={`/channels/${p.userId}`}>{p.nickname}</Link>
                <span className="hm-top-stat num">적중 {p.hitRate.toFixed(1)}%</span>
                <span className="hm-top-sub num">{p.doneCount}건</span>
              </li>
            ))}
          </ol>
          <p className="hm-foot">매 영업일 배치 집계 · 기준 {data.computedAt.slice(0, 10)}</p>
        </>
      )}
    </section>
  )
}

function Sponsored() {
  const load = useCallback(() => getActiveAds(), [])
  const { data, loading, error } = useAsync(load)
  const ad = data?.items[0]

  return (
    <section className="card hm-card hm-ad" aria-labelledby="hm-ad">
      <header className="hm-head">
        <h2 id="hm-ad">스폰서드</h2>
        <span className="hm-badge">광고</span>
      </header>
      <State loading={loading} error={error} empty={data?.items.length === 0} />
      {ad && (
        <Link className="hm-ad-body" to={ad.linkUrl}>
          <img src={ad.imageUrl} alt="" aria-hidden="true" />
          <span>내 예측을 더 많은 사람에게<br /><em>ANT로 홍보하고 성과를 키우세요</em></span>
        </Link>
      )}
    </section>
  )
}

export default function Home() {
  return (
    <main className="main">
      <div className="main-inner">
        <div className="page-head">
          <h1>인사이트 홈</h1>
          <p>근거를 보고 예측하는 사람들의 오늘</p>
        </div>

        <div className="hm-grid">
          <MarketOverview />
          <MyAssets />
          <Watchlist />
          {/* 예측가 카드가 브리핑보다 길어 왼쪽이 비므로 스폰서드를 같은 칸에 쌓는다 */}
          <div className="hm-col">
            <Briefings />
            <Sponsored />
          </div>
          <TopPredictors />
        </div>
      </div>
    </main>
  )
}
