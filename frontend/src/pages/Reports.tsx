/* F-01 리포트 피드 · /reports
   담당 스토리 [ANT-FE-REPORTS]
   설계서 docs/화면설계서.md §3 F · §4 F-01 · §9.2

   설계 제약
   - 화면 컨트롤은 scope(ALL/SUBSCRIBED) · sort(RECENT/POPULAR) 를 넘지 않는다.
   - locked 여도 제목·작성자·발행일·조회수는 그대로 보여준다. 잠긴 것을 숨기면
     구독 유인이 사라진다(설계서 §5) — 404 처럼 그리지 않는다.
   - 커서 페이징이다. 페이지 번호를 만들지 않는다.

   두지 않는 것: 내가 쓴 리포트 탭 · 적중률 정렬 · 검증/공개/최소 적중률 필터 ·
   찜 · 댓글 수 · 근거 검증 배지. scope 는 2종뿐이고 응답 통계는 viewCount 뿐이며
   북마크 도메인이 없다.

   섹터 칩을 두지 않는 이유
   설계서 §3 F-01 은 섹터 칩을 적어 두었지만 백엔드가 sector 를 400 으로 거절한다.
   출처 컬럼이 ERD 에 없어 필터를 구현할 수 없고(Jira S15P21A507-69 ⛔), 조용히
   무시하면 "칩을 눌렀고 걸러진 목록을 받았다"고 믿게 되기 때문이다. 칩을 그리면
   누를 때마다 400 이거나, 더 나쁘게는 안 걸러진 목록을 정답처럼 보여준다.
   설계서 §10 도 "scope 2종뿐"으로 적고 있어 문서 안에서 이미 어긋난 항목이다.
   reports.sector 가 신설되면 그때 칩을 붙인다. */
import { useState } from 'react'
import { Link } from 'react-router-dom'
import { useCursorList } from '../api/useCursorList'
import {
  REPORT_SCOPES, REPORT_SORTS, SCOPE_LABEL, SORT_LABEL, formatDate,
  type ReportFeedItem, type ReportScope, type ReportSort,
} from '../api/reports'
import EmptyState from '../components/state/EmptyState'
import ErrorState from '../components/state/ErrorState'
import '../styles/screens/reports.css'

export default function Reports() {
  const [scope, setScope] = useState<ReportScope>('ALL')
  const [sort, setSort] = useState<ReportSort>('RECENT')

  const feed = useCursorList<ReportFeedItem>('/reports', { scope, sort })

  return (
    <main className="main">
      <div className="main-inner">
        <div className="page-head rp-page-head">
          <div>
            <h1>리포트</h1>
            <p>예측가가 발행한 리서치를 모아 봅니다</p>
          </div>
          {/* F-03 진입점. 이게 없으면 /reports/new 에 갈 길이 없다 */}
          <Link className="rp-new" to="/reports/new">리포트 작성</Link>
        </div>

        <div className="rp-controls">
          {/* 탭 — 전체 / 구독 중 */}
          <div className="rp-tabs" role="tablist" aria-label="리포트 범위">
            {REPORT_SCOPES.map((s) => (
              <button
                key={s} type="button" role="tab"
                aria-selected={scope === s}
                className={scope === s ? 'on' : ''}
                onClick={() => setScope(s)}
              >
                {SCOPE_LABEL[s]}
              </button>
            ))}
          </div>

          <div className="rp-sorts" role="group" aria-label="정렬">
            {REPORT_SORTS.map((s) => (
              <button
                key={s} type="button"
                aria-pressed={sort === s}
                className={sort === s ? 'on' : ''}
                onClick={() => setSort(s)}
              >
                {SORT_LABEL[s]}
              </button>
            ))}
          </div>
        </div>

        {feed.error && <ErrorState error={feed.error} onRetry={feed.reload} />}

        {!feed.error && feed.items.length === 0 && !feed.loading && (
          <EmptyState
            title={scope === 'SUBSCRIBED' ? '구독 중인 채널의 리포트가 없습니다' : '아직 발행된 리포트가 없습니다'}
            hint={scope === 'SUBSCRIBED' ? '채널을 구독하면 여기에 모입니다' : '첫 리포트를 기다리고 있습니다'}
          />
        )}

        {feed.items.length > 0 && (
          <ul className="rp-list">
            {feed.items.map((r) => (
              <li key={r.id} className="rp-item">
                {/* 잠겼어도 상세로 보낸다 — F-02 가 200 + 미리보기로 응답한다(404 아님) */}
                <Link className="rp-link" to={`/reports/${r.id}`}>
                  <div className="rp-head">
                    <h2 className="rp-title">{r.title}</h2>
                    {r.locked && (
                      <span className="rp-lock">
                        <svg width="13" height="13" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
                          <rect x="4" y="10" width="16" height="10" rx="2" />
                          <path d="M8 10V7a4 4 0 0 1 8 0v3" />
                        </svg>
                        구독 전용
                      </span>
                    )}
                  </div>

                  <div className="rp-meta">
                    <span className="rp-author">{r.author.nickname}</span>
                    <span className="rp-dot" aria-hidden="true">·</span>
                    <span>{formatDate(r.publishedAt)}</span>
                    <span className="rp-dot" aria-hidden="true">·</span>
                    <span className="num">{`조회 ${r.viewCount.toLocaleString('ko-KR')}`}</span>
                  </div>
                </Link>
              </li>
            ))}
          </ul>
        )}

        {feed.loading && feed.items.length > 0 && (
          <p className="rp-more-hint">불러오는 중…</p>
        )}

        {feed.hasNext && !feed.loading && (
          <button type="button" className="rp-more" onClick={feed.loadMore}>
            더 보기
          </button>
        )}
      </div>
    </main>
  )
}
