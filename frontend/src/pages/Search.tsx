/* A-03 통합 검색 · /search?q=
   담당 스토리 [ANT-FE-SEARCH]

   셸 상단바가 입력과 이동만 맡고(`navigate('/search?q=…')`) 이 화면이 결과를 그린다.
   타입별 상위 N건만 오고 필터·정렬·페이징은 없다 — 그건 각 타입 전용 목록의
   책임이라 "더보기" 로 넘긴다(명세서 §검색).

   상단바 자동완성 드롭다운도 이 스토리 몫이지만 Layout.tsx 를 고쳐야 해서
   이번 커밋에는 넣지 않았다. 지금도 엔터로 이 화면까지는 온다. */
import { useCallback } from 'react'
import { Link, useSearchParams } from 'react-router-dom'
import { search } from '../api/insight'
import { useAsync } from '../api/useAsync'
import '../styles/search.css'

const won = (n: number) => n.toLocaleString('ko-KR')

export default function Search() {
  const [params] = useSearchParams()
  const q = (params.get('q') ?? '').trim()

  // q 가 바뀌면 다시 읽는다. 빈 검색어면 서버를 부르지 않는다.
  const load = useCallback(
    () => (q ? search(q, 5) : Promise.resolve({ stocks: [], channels: [], reports: [] })),
    [q],
  )
  const { data, loading, error, reload } = useAsync(load)

  const total = data ? data.stocks.length + data.channels.length + data.reports.length : 0

  if (!q) {
    return (
      <main className="main">
        <div className="main-inner">
          <div className="page-head">
            <h1>통합 검색</h1>
            <p>종목명 · 종목코드 · 예측가 닉네임 · 리포트 제목으로 찾습니다</p>
          </div>
          <p className="sr-empty">위쪽 검색창에 찾을 내용을 입력해 주세요.</p>
        </div>
      </main>
    )
  }

  return (
    <main className="main">
      <div className="main-inner">
        <div className="page-head">
          <h1>{'‘'}{q}{'’'} 검색 결과</h1>
          {!loading && !error && <p>{total > 0 ? `${total}건을 찾았습니다` : '일치하는 결과가 없습니다'}</p>}
        </div>

        {loading && <p className="sr-state">찾고 있습니다…</p>}
        {error && (
          <p className="sr-state err">
            검색에 실패했습니다.
            <button type="button" className="sr-retry" onClick={reload}>다시 시도</button>
          </p>
        )}

        {data && total === 0 && !loading && (
          <div className="sr-none">
            <p>종목명이나 종목코드로 다시 찾아보세요.</p>
            <p className="sr-hint">예) 삼성전자 · 005930 · 반도체</p>
          </div>
        )}

        {data && total > 0 && (
          <div className="sr-groups">
            {data.stocks.length > 0 && (
              <section className="card sr-card" aria-labelledby="sr-stocks">
                <header className="sr-head">
                  <h2 id="sr-stocks">종목<span className="sr-count num">{data.stocks.length}</span></h2>
                  <Link to="/stocks">종목 전체 보기 ›</Link>
                </header>
                <ul className="sr-list">
                  {data.stocks.map((s) => (
                    <li key={s.code}>
                      <Link to={`/stocks/${s.code}`}>
                        <span className="sr-main">
                          <b>{s.name}</b>
                          <small className="num">{s.code} · {s.sector}</small>
                        </span>
                        <span className="sr-side">
                          <b className="num">{won(s.prevClose)}원</b>
                          <small>전일 종가</small>
                        </span>
                        {s.watching && <span className="sr-flag" title="관심 종목">찜</span>}
                      </Link>
                    </li>
                  ))}
                </ul>
              </section>
            )}

            {data.channels.length > 0 && (
              <section className="card sr-card" aria-labelledby="sr-channels">
                <header className="sr-head">
                  <h2 id="sr-channels">예측가<span className="sr-count num">{data.channels.length}</span></h2>
                  <Link to="/rankings">랭킹 보기 ›</Link>
                </header>
                <ul className="sr-list">
                  {data.channels.map((c) => (
                    <li key={c.userId}>
                      <Link to={`/channels/${c.userId}`}>
                        <span className="sr-main"><b>{c.nickname}</b></span>
                        <span className="sr-side">
                          <b className="num">{c.hitRate.toFixed(1)}%</b>
                          <small>적중률</small>
                        </span>
                      </Link>
                    </li>
                  ))}
                </ul>
              </section>
            )}

            {data.reports.length > 0 && (
              <section className="card sr-card" aria-labelledby="sr-reports">
                <header className="sr-head">
                  <h2 id="sr-reports">리포트<span className="sr-count num">{data.reports.length}</span></h2>
                  <Link to="/reports">리포트 전체 보기 ›</Link>
                </header>
                <ul className="sr-list">
                  {data.reports.map((r) => (
                    <li key={r.id}>
                      <Link to={`/reports/${r.id}`}>
                        <span className="sr-main">
                          <b>{r.title}</b>
                          <small>{r.author}</small>
                        </span>
                      </Link>
                    </li>
                  ))}
                </ul>
              </section>
            )}
          </div>
        )}
      </div>
    </main>
  )
}
