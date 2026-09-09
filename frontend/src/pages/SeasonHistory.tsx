/* G-09 기록 · /sim/history
   담당 스토리 [ANT-FE-SEASON-HISTORY]
   설계서 docs/화면설계서.md §3 · §4 G-09 · API 명세 §모의투자

   ── 여기가 모의투자의 목록 자리다 ────────────────────────
   사이드바의 "투자 포트폴리오" 가 이 주소다. 시즌 번호가 필요한 화면(진행·결과·
   매매일지)은 사이드바에 못 걸리므로, 여기서 시즌을 고르고 그쪽으로 들어간다.

   ── 무엇으로 그리는가 ────────────────────────────────────
   GET /seasons/me 하나다. v0.39 에서 제목·종료 시각·최종 수익률이 붙어(대연님)
   목록에 필요한 값이 한 응답에 다 온다 — 시즌마다 상세를 다시 부르지 않는다.

   ── 배지는 뺐다 ──────────────────────────────────────────
   설계서 §G-09 는 "기록·배지" 지만 배지는 MVP 가 아니다(2026-09-09). GET /users/me/badges
   도 서버에 없다. 자리만 만들어 두면 빈 상자가 화면을 차지하기만 하므로 통째로 뺐다 —
   배지가 생기면 마친 시즌 아래에 카드 하나를 더하면 된다. */
import { useEffect, useMemo, useState, type ReactNode } from 'react'
import { Link } from 'react-router-dom'
import { ApiError } from '../api/errors'
import { rate, signOf } from '../api/seasonPlay'
import {
  getMyRuns, MODE_LABEL, progressOf, recentFirst, type MyRun,
} from '../api/seasons'
import ErrorState from '../components/state/ErrorState'
import '../styles/screens/sim-history.css'

type Sort = 'RECENT' | 'BEST' | 'WORST'

const SORTS: { key: Sort; label: string }[] = [
  { key: 'RECENT', label: '최근순' },
  { key: 'BEST', label: '수익률 높은 순' },
  { key: 'WORST', label: '수익률 낮은 순' },
]

/** 끝낸 날. 시즌의 시기가 아니라 내가 끝낸 실제 날짜라 시대 단서가 아니다 */
const endedLabel = (iso?: string | null) => {
  if (!iso) return null
  const d = new Date(iso)
  return Number.isNaN(d.getTime())
    ? null
    : `${d.getMonth() + 1}월 ${d.getDate()}일 마침`
}

const Ico = ({ size = 18, children }: { size?: number; children: ReactNode }) => (
  <svg width={size} height={size} viewBox="0 0 24 24" fill="none" stroke="currentColor"
       strokeWidth="1.7" strokeLinecap="round" strokeLinejoin="round" aria-hidden="true">
    {children}
  </svg>
)

function Stat({ tone, label, value, sub, icon }: {
  tone: string
  label: string
  value: string
  sub?: ReactNode
  icon: ReactNode
}) {
  return (
    <div className={`sh-stat t-${tone}`}>
      <div className="sh-stat-txt">
        <span className="sh-stat-label">{label}</span>
        <b className="sh-stat-val num">{value}</b>
        {sub && <span className="sh-stat-sub num">{sub}</span>}
      </div>
      <span className="sh-stat-ico" aria-hidden="true">{icon}</span>
    </div>
  )
}

/** 완료 회차 한 줄. 누르면 그 판의 결과로 간다 */
function DoneRow({ run }: { run: MyRun }) {
  const r = run.returnRate
  const ended = endedLabel(run.endedAt)
  return (
    <li>
      <Link className="sh-row" to={`/sim/${run.seasonId}/result`}>
        <span className="sh-mode">{MODE_LABEL[run.mode]}</span>
        <b>{run.title}</b>
        <span className="sh-when">{ended ?? `${run.lengthDays}게임일`}</span>
        {/* 결과가 없는 완료 회차도 있다 — 끝냈지만 성적표를 안 굳힌 판이다 */}
        <span className={`sh-rate num ${r == null ? 'flat' : signOf(r)}`}>
          {r == null ? '성적표 없음' : rate(r)}
        </span>
        <i aria-hidden="true">›</i>
      </Link>
    </li>
  )
}

/** 진행 중 회차 한 줄. 누르면 이어서 한다 */
function OngoingRow({ run }: { run: MyRun }) {
  const pct = progressOf(run.currentDay, run.lengthDays)
  return (
    <li>
      <Link className="sh-row sh-row-go" to={`/sim/${run.seasonId}/play`}>
        <span className="sh-mode">{MODE_LABEL[run.mode]}</span>
        <b>{run.title}</b>
        <span className="sh-when num">DAY {run.currentDay} / {run.lengthDays}</span>
        <span className="sh-bar" aria-hidden="true"><i style={{ width: `${pct}%` }} /></span>
        <i aria-hidden="true">›</i>
      </Link>
    </li>
  )
}

export default function SeasonHistory() {
  const [ongoing, setOngoing] = useState<MyRun[] | null>(null)
  const [done, setDone] = useState<MyRun[]>([])
  const [loadError, setLoadError] = useState<ApiError | null>(null)
  const [sort, setSort] = useState<Sort>('RECENT')

  useEffect(() => {
    let alive = true
    getMyRuns()
      .then((r) => {
        if (!alive) return
        setOngoing(r.ongoing)
        setDone(r.done)
        setLoadError(null)
      })
      .catch((e) => { if (alive) setLoadError(e instanceof ApiError ? e : null) })
    return () => { alive = false }
  }, [])

  /* 성적표가 없는 회차는 늘 뒤로 보낸다. 수익률로 세우는 자리에 값이 없는 줄이
     섞이면 어디까지가 정렬된 것인지 알 수 없다. */
  const sortedDone = useMemo(() => {
    const rows = [...done]
    if (sort === 'RECENT') return rows.sort(recentFirst)
    const dir = sort === 'BEST' ? -1 : 1
    return rows.sort((a, b) => {
      if (a.returnRate == null) return 1
      if (b.returnRate == null) return -1
      return (a.returnRate - b.returnRate) * dir
    })
  }, [done, sort])

  const scored = done.filter((r) => r.returnRate != null)
  const avg = scored.length
    ? scored.reduce((a, r) => a + (r.returnRate ?? 0), 0) / scored.length
    : null
  const best = scored.length ? Math.max(...scored.map((r) => r.returnRate ?? 0)) : null

  if (ongoing === null) {
    return (
      <main className="main">
        <div className="main-inner sim-history">
          {loadError
            ? <ErrorState error={loadError} onRetry={() => setLoadError(null)} />
            : <div className="placeholder tall">{'기록을 불러오는 중…'}</div>}
        </div>
      </main>
    )
  }

  return (
    <main className="main">
      <div className="main-inner sim-history">
        <nav className="sh-crumb" aria-label="위치">
          <Link to="/sim">모의투자 홈</Link>
          <i aria-hidden="true">›</i>
          <span>기록</span>
        </nav>

        <header className="sh-head">
          <div>
            <h1>기록</h1>
            <p>내가 참가한 시즌과 성적입니다</p>
          </div>
          <Link className="sh-go" to="/sim/modes">새로 시작하기</Link>
        </header>

        {/* ── 요약 4칸 ─────────────────────────────────── */}
        <section className="sh-stats" aria-label="기록 요약">
          <Stat
            tone="done"
            label="마친 시즌"
            value={`${done.length}개`}
            sub={<span className="sh-dim">{scored.length}개 성적표 있음</span>}
            icon={<Ico><path d="M5 13l4 4L19 7" /></Ico>}
          />
          <Stat
            tone="go"
            label="진행 중"
            value={`${ongoing.length}개`}
            sub={<span className="sh-dim">{ongoing.length > 0 ? '이어서 할 수 있습니다' : '없습니다'}</span>}
            icon={<Ico><path d="m10 8.5 6 3.5-6 3.5z" /><circle cx="12" cy="12" r="9" /></Ico>}
          />
          <Stat
            tone={avg == null ? 'flat' : avg > 0 ? 'up' : avg < 0 ? 'down' : 'flat'}
            label="평균 수익률"
            value={avg == null ? '—' : rate(avg)}
            sub={<span className="sh-dim">{scored.length === 0 ? '성적표가 없습니다' : `${scored.length}개 평균`}</span>}
            icon={<Ico><path d="M4 16.5 9 11l3.5 3.5L20 7" /><path d="M15.5 7H20v4.5" /></Ico>}
          />
          <Stat
            tone={best == null ? 'flat' : best > 0 ? 'up' : 'down'}
            label="가장 좋았던 판"
            value={best == null ? '—' : rate(best)}
            sub={<span className="sh-dim">최고 수익률</span>}
            icon={<Ico><path d="M7 4h10v5a5 5 0 0 1-10 0z" /><path d="M10 19h4M12 14v5M8.5 21h7" /></Ico>}
          />
        </section>

        {/* ── 진행 중 ──────────────────────────────────── */}
        {ongoing.length > 0 && (
          <section className="sh-card">
            <h2>
              <span className="sh-h-ico t-go" aria-hidden="true">
                <Ico size={15}><path d="m10 8.5 6 3.5-6 3.5z" /><circle cx="12" cy="12" r="9" /></Ico>
              </span>
              이어서 할 연습
              <em>{ongoing.length}개</em>
            </h2>
            <ul className="sh-list">
              {[...ongoing].sort(recentFirst).map((r) => (
                <OngoingRow key={r.seasonId} run={r} />
              ))}
            </ul>
          </section>
        )}

        {/* ── 마친 시즌 ────────────────────────────────── */}
        <section className="sh-card">
          <h2>
            <span className="sh-h-ico t-done" aria-hidden="true">
              <Ico size={15}><path d="M5 13l4 4L19 7" /></Ico>
            </span>
            마친 시즌
            <em>{done.length}개</em>
          </h2>

          {done.length > 1 && (
            <div className="sh-sorts" role="group" aria-label="정렬">
              {SORTS.map((x) => (
                <button
                  key={x.key}
                  type="button"
                  aria-pressed={sort === x.key}
                  className={sort === x.key ? 'on' : undefined}
                  onClick={() => setSort(x.key)}
                >
                  {x.label}
                </button>
              ))}
            </div>
          )}

          {done.length === 0 ? (
            <p className="sh-empty">
              아직 끝까지 마친 시즌이 없습니다.
              <small>마지막 게임일까지 가면 성적표와 함께 여기에 쌓입니다.</small>
              <Link className="sh-go" to="/sim/practice">연습 고르러 가기</Link>
            </p>
          ) : (
            <ul className="sh-list">
              {sortedDone.map((r) => <DoneRow key={r.seasonId} run={r} />)}
            </ul>
          )}
        </section>

        {loadError && <ErrorState error={loadError} onRetry={() => setLoadError(null)} inline />}
      </div>
    </main>
  )
}
