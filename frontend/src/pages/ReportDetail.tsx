/* F-02 리포트 상세 · /reports/:id
   담당 스토리 [ANT-FE-REPORT-DETAIL]
   설계서 docs/화면설계서.md §3 F · §4 F-02 · §5 · §7

   설계 제약
   - visibility=false 이고 미구독이면 200 + { preview, locked } 가 온다. 404 가 아니다
     — 없는 것처럼 보이면 구독 유인이 사라진다.
   - 서버가 잘라서 내려준 만큼만 그린다. 전문을 받아 CSS 로 가리지 않는다.
     서버가 잠긴 리포트에 body 를 아예 담지 않으므로 이 화면은 가릴 전문을 갖지 못한다.
   - 3줄 미리보기 + 페이드 + 구독 CTA 가 잠금 표현이다. 설계서 §7 의 SubscriptionGate 는
     [ANT-FE-ERROR] 가 만든 LockedCard 다 — preview·channelId 를 받아 그 셋을 그린다.
   - 구독 CTA 는 채널 프로필(E-02)로 보낸다. 결제 흐름(M-03 → 서명 → 202 → M-02)은
     그쪽 스토리 몫이라 여기서 만들지 않는다.

   채널 식별자에 대하여
   채널은 회원 1인당 하나다 — GET /channels/{userId}/reports 가 userId 를 쓴다.
   그래서 CTA 목적지는 작성자의 userId 다. */
import { Link, useParams } from 'react-router-dom'
import { useApiQuery } from '../api/useApiQuery'
import { formatDate, type ReportDetail as Report } from '../api/reports'
import ErrorState from '../components/state/ErrorState'
import LockedCard from '../components/state/LockedCard'
import '../styles/screens/report-detail.css'

export default function ReportDetail() {
  const { id } = useParams<{ id: string }>()
  const report = useApiQuery<Report>(`/reports/${id}`)

  return (
    <main className="main">
      <div className="main-inner">
        <Link className="rd-back" to="/reports">
          <svg width="15" height="15" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
            <path d="M15 18l-6-6 6-6" />
          </svg>
          리포트 목록
        </Link>

        {/* 없는 리포트는 404 REPORT_NOT_FOUND 다. 잠금(200)과 다른 경로로 온다 */}
        {report.error && <ErrorState error={report.error} onRetry={report.reload} />}

        {!report.error && !report.data && (
          <p className="rd-loading">불러오는 중…</p>
        )}

        {report.data && (
          <article className="rd">
            <header className="rd-head">
              <h1 className="rd-title">{report.data.title}</h1>

              <div className="rd-meta">
                {/* 작성자 이름이 곧 채널 진입점이다 */}
                <Link className="rd-author" to={`/channels/${report.data.author.userId}`}>
                  {report.data.author.nickname}
                </Link>
                <span className="rd-dot" aria-hidden="true">·</span>
                <span>{formatDate(report.data.publishedAt)}</span>
                <span className="rd-dot" aria-hidden="true">·</span>
                <span className="num">{`조회 ${report.data.viewCount.toLocaleString('ko-KR')}`}</span>

                {/* 구독자 전용으로 발행된 글임을 알린다. 잠금 여부와 별개로 늘 붙는다
                    — 본인이나 구독자가 열어도 이 리포트가 유료라는 사실은 같다 */}
                {!report.data.visibility && (
                  <span className="rd-private">구독자 공개</span>
                )}
              </div>
            </header>

            {report.data.locked ? (
              /* title 을 직접 준다 — 공용 기본 문구는 조사를 "은(는)" 으로 흘려
                 "리포트 본문은(는) …" 이 된다. 이 화면은 label 이 고정이라 정확히 쓸 수 있다. */
              <LockedCard
                label="리포트 본문"
                title="리포트 본문은 구독자에게만 공개됩니다"
                preview={report.data.preview ?? undefined}
                channelId={String(report.data.author.userId)}
              />
            ) : (
              /* 작성자가 넣은 줄바꿈을 살린다. 서버는 본문을 그대로 보관한다 */
              <div className="rd-body">{report.data.body}</div>
            )}
          </article>
        )}
      </div>
    </main>
  )
}
