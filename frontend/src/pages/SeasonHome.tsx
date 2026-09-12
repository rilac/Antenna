/* G-01 모의투자 홈 · /sim
   담당 스토리 [ANT-FE-SEASON-HOME]
   설계서 docs/화면설계서.md §3 G-01 — "진행 중 시즌(이어하기) · 완료 기록 요약 · 시즌 목록"

   화면은 2026-09-12 시안을 따른다. 히어로 오른쪽이 마스코트와 장식이 떠다니는
   무대(sh-stage)이고, 아래는 "최근 연습 기록 · 처음이라면 이렇게 시작해요" 두 칸이다.

   ── 시안에서 그리지 않은 것 ────────────────────────────
   1. 기록 줄의 날짜 범위("2025. 07. 01 - 2025. 08. 13"). 시즌이 언제의 장이었는지가
      곧 시대 단서다. 어느 응답에도 오지 않고, 와도 그리지 않는다.
      대신 endedAt(내가 그 회차를 끝낸 실제 시각)만 쓴다 — 시즌의 시기가 아니다.
   2. "시장 수익률 +3.1%". /seasons/me 응답에 벤치마크가 없다. 내 수익률(returnRate)만 쓴다.
   3. "연습 모의투자 #02" 의 회차 번호. 응답에 순번이 없다(recentFirst 도 seasonId 로
      대신한다). 자리에는 시즌 제목(title, v0.39)을 넣는다.
   4. 보상 카드. 시안이 그 자리를 "처음이라면 이렇게 시작해요" 로 바꿨다.
      금액을 쓰지 않는다는 §1 규칙 3 은 그대로다 — 보상 규모는 지갑(H-01)에서 본다.

   ── 서버에서 받는 것 ────────────────────────────────────
     GET /seasons/me?status=ONGOING → { items: [{ seasonId, mode, title, currentDay,
                                                  lengthDays, progress, endedAt, returnRate }] }
     GET /seasons/me?status=DONE    → 같은 스키마
     GET /seasons?mode=PRACTICE     → 히어로 칩의 시작 자산·진행 기간
   셋 다 커서 페이징이 없는 목록이라(§1 규칙 6 의 예외) useCursorList 를 쓰지 않는다. */
import { useState } from 'react'
import { Link } from 'react-router-dom'
import type { ApiError } from '../api/errors'
import {
  getMyRuns, getPracticeRuns, MODE_LABEL, progressOf, recentFirst, won,
  type MyRun, type OpenRun,
} from '../api/seasons'
import { useAsync } from '../api/useAsync'
import ErrorState from '../components/state/ErrorState'
import '../styles/screens/sim-home.css'

/* 홈은 요약이라 접힌 상태로는 두 줄만 보여준다. 더 있으면 토글로 편다.
   편 상태에도 상한을 두는 이유는 /seasons/me 가 커서 페이징이 없는 목록이어서다 —
   회차가 쌓이면 응답이 통째로 오고, 그대로 그리면 홈이 기록 화면이 된다.
   상한을 넘는 것은 지금처럼 "전체 보기"(G-09)가 맡는다. */
const COLLAPSED_ROWS = 2
const EXPANDED_ROWS = 6

/* 401 을 그대로 보여준다.
   전에는 빈 상태로 뭉갰다 — 이 화면이 부르는 /seasons·/seasons/me 가 없던 때라 401 이
   "그 API 가 아직 없다"(Spring Security 가 라우팅보다 먼저 걸러 없는 경로도 401 을 준다)
   는 뜻이었기 때문이다. 이제 둘 다 붙었으므로 401 은 로그인이 안 됐다는 뜻이고, 그걸
   빈 상태로 그리면 서버가 거부한 것을 참가한 것이 없는 것으로 읽게 된다.
   errorText 가 UNAUTHENTICATED 를 "로그인이 필요합니다" 로 풀어 준다. */
const shown = (error: ApiError | null) => error

/* 내가 끝낸 시각. 시즌의 시기가 아니라 내 기록이라 그려도 된다(api/seasons.ts 주석).
   값이 없거나 파싱이 안 되면 아무것도 그리지 않는다 — 빈 칸이 틀린 날짜보다 낫다. */
function endedOn(iso?: string | null) {
  if (!iso) return null
  const d = new Date(iso)
  if (Number.isNaN(d.getTime())) return null
  const pad = (n: number) => String(n).padStart(2, '0')
  return `${d.getFullYear()}. ${pad(d.getMonth() + 1)}. ${pad(d.getDate())} 완료`
}

/* 시안의 "1,000만원". 만 단위로 딱 떨어질 때만 이 표기를 쓰고 아니면 공용 won() 으로
   돌아간다 — 칩은 한눈에 읽히는 것이 목적이라 1,000만원이 10,000,000원보다 낫지만,
   단위가 안 맞는 값을 억지로 줄이면 반올림이 생겨 금액이 달라진다. */
const manwon = (n: number) =>
  n >= 10_000 && n % 10_000 === 0 ? `${(n / 10_000).toLocaleString('ko-KR')}만원` : won(n)

/**
 * 연습 목록이 한 값으로 모일 때만 그 값을 준다.
 *
 * 히어로 칩("시작 자산 1,000만원")은 특정 시즌이 아니라 "연습은 이런 것" 이라는 안내다.
 * 시즌마다 초기 자금이 다르면 아무 시즌의 값이나 대표로 세우는 셈이라 거짓이 된다.
 * 그럴 때는 undefined 를 줘서 칩을 통째로 감춘다 — §1 규칙 3 이 금지하는 건 금액 상수를
 * 화면에 박는 것이지, 응답에 실려 온 값을 그리는 것이 아니다.
 */
function agreed<K extends keyof OpenRun>(runs: OpenRun[] | undefined, key: K) {
  if (!runs?.length) return undefined
  const first = runs[0][key]
  return runs.every((r) => r[key] === first) ? first : undefined
}

const Ico = ({ size = 22, children }: { size?: number; children: React.ReactNode }) => (
  <svg width={size} height={size} viewBox="0 0 24 24" fill="none" stroke="currentColor"
       strokeWidth="1.8" strokeLinecap="round" strokeLinejoin="round" aria-hidden="true">
    {children}
  </svg>
)

const CalendarIcon = () => (
  <Ico><rect x="3" y="5" width="18" height="16" rx="2.5" /><path d="M8 3v4M16 3v4M3 10h18" /></Ico>
)
const CoinsIcon = () => (
  <Ico><ellipse cx="12" cy="6" rx="7.5" ry="3" /><path d="M4.5 6v5c0 1.7 3.4 3 7.5 3s7.5-1.3 7.5-3V6" />
    <path d="M4.5 11v5c0 1.7 3.4 3 7.5 3s7.5-1.3 7.5-3v-5" /></Ico>
)
const BarsIcon = () => <Ico><path d="M6 20v-6M12 20V7M18 20v-9" /></Ico>
const RulerIcon = () => <Ico><path d="M4 20V10M10 20V4M16 20v-7M22 20H2" /></Ico>
const FlagIcon = () => <Ico><path d="M5 21V4M5 4h11l-2 3.5L16 11H5" /></Ico>
const PlayIcon = () => (
  <Ico size={24}><circle cx="12" cy="12" r="9" /><path d="m10 8.2 6 3.8-6 3.8z" fill="currentColor" /></Ico>
)
const HistoryIcon = () => (
  <Ico><path d="M3 12a9 9 0 1 0 2.6-6.4" /><path d="M3 4v5h5" /><path d="M12 8v4.5l3 1.8" /></Ico>
)
const BookIcon = () => (
  <Ico><path d="M12 6.5C10.5 5 8.5 4.4 4 4.6v13C8.5 17.4 10.5 18 12 19.5" />
    <path d="M12 6.5C13.5 5 15.5 4.4 20 4.6v13c-4.5-.2-6.5.4-8 1.9z" /><path d="M12 6.5v13" /></Ico>
)
const NoteIcon = () => (
  <Ico><rect x="5" y="3" width="14" height="18" rx="2.5" /><path d="M9 8h6M9 12h6M9 16h3.5" /></Ico>
)
const BulbIcon = () => (
  <Ico size={20}><path d="M9 18h6M10 21h4" />
    <path d="M12 3a6 6 0 0 0-3.5 10.9c.6.4 1 1.1 1 1.8V16h5v-.3c0-.7.4-1.4 1-1.8A6 6 0 0 0 12 3z" /></Ico>
)
const ChevronIcon = () => <Ico size={18}><path d="m9 5 7 7-7 7" /></Ico>
const CaretIcon = () => <Ico size={16}><path d="m6 9 6 6 6-6" /></Ico>

/* ── 히어로 장식 ───────────────────────────────────────────
   무대 안에서 각자 다른 주기로 떠다니는 오브젝트다. 위치·크기·흔들림의 폭은
   전부 CSS 가 정한다(.d1~.d7) — 좌표를 마크업과 CSS 두 곳에 나눠 두면 시안과
   맞출 때마다 두 파일을 오간다.

   일곱 장 모두 뜻이 없는 장식이라 alt 를 비우고 레이어째 aria-hidden 으로 덮는다.
   BUY · SELL 은 글래스 버튼 그림이지만 여기서는 장식이다 — 누를 수 있는 것으로 보이지
   않게 작게(--art 의 29%) 쓰고, 레이어 전체가 pointer-events 를 받지 않는다.
   실제 매매는 G-04 시즌 진행 화면에서 한다. */
const DRIFT = ['bubble', 'buy', 'card-market', 'script', 'arrow', 'card-practice', 'sell']

/* ── 히어로 ───────────────────────────────────────────────
   진행 중인 것이 있으면 이어하기, 없으면 시작을 권한다.
   여러 개 진행 중이면 가장 최근 것을 세우고 나머지는 아래 목록에서 본다. */
function Hero({ run, practice }: { run: MyRun | null; practice: OpenRun[] | undefined }) {
  const percent = run ? progressOf(run.currentDay, run.lengthDays) : 0
  const left = run ? Math.max(0, run.lengthDays - run.currentDay) : 0

  const cash = agreed(practice, 'initialCash')
  const days = agreed(practice, 'lengthDays')

  return (
    <section className="sh-hero" aria-labelledby="sh-hero-h">
      <div className="sh-hero-copy">
        <span className="sh-flag">{run ? '진행 중' : '모의투자'}</span>

        {/* 페이지 제목 자리다. 위에 따로 머리글을 두지 않으므로 h1 이다. */}
        <h1 className="sh-title" id="sh-hero-h">
          {run ? (
            <>
              {MODE_LABEL[run.mode]} 모의투자를
              <br />
              <em>이어서</em> 해보세요
            </>
          ) : (
            <>
              실제 시장 흐름으로
              <br />
              <em>투자 연습</em>을 시작해보세요
            </>
          )}
        </h1>

        <p className="sh-lead">
          {run ? (
            <>
              지난 시장을 그대로 되짚으며,
              <br />
              투자 전략을 다듬을 수 있어요.
            </>
          ) : (
            <>
              과거의 시장을 다시 경험하며,
              <br />
              부담 없이 투자 전략을 연습할 수 있어요.
            </>
          )}
        </p>

        {run && (
          <div className="sh-progress">
            <span
              className="sh-bar" role="progressbar" aria-label="진행률"
              aria-valuenow={percent} aria-valuemin={0} aria-valuemax={100}
            >
              <i style={{ width: `${percent}%` }} />
            </span>
            <b className="num">{percent}%</b>
          </div>
        )}

        {/* 진행 중이면 내 회차 상태, 아니면 연습이 어떤 것인지를 알린다.
            게임일은 남기고 달력 날짜는 쓰지 않는다 — 날짜가 곧 시대 단서다. */}
        <div className="sh-stats">
          {run ? (
            <>
              <dl className="sh-stat">
                <CalendarIcon />
                <div><dt>현재 게임일</dt><dd className="num">D+{run.currentDay}</dd></div>
              </dl>
              <dl className="sh-stat">
                <FlagIcon />
                <div><dt>남은 게임일</dt><dd className="num">{left}일</dd></div>
              </dl>
              <dl className="sh-stat">
                <RulerIcon />
                <div><dt>총 기간</dt><dd className="num">{run.lengthDays}일</dd></div>
              </dl>
            </>
          ) : (
            <>
              {cash !== undefined && (
                <dl className="sh-stat">
                  <CoinsIcon />
                  <div><dt>시작 자산</dt><dd className="num">{manwon(cash)}</dd></div>
                </dl>
              )}
              {days !== undefined && (
                <dl className="sh-stat">
                  <CalendarIcon />
                  <div><dt>진행 기간</dt><dd className="num">{days}일</dd></div>
                </dl>
              )}
              <dl className="sh-stat">
                <BarsIcon />
                <div><dt>투자 방식</dt><dd>자유 투자</dd></div>
              </dl>
            </>
          )}
        </div>

        {run ? (
          <Link className="sh-btn solid" to={`/sim/${run.seasonId}/play`}>
            <PlayIcon />
            이어서 하기
            <ChevronIcon />
          </Link>
        ) : (
          <Link className="sh-btn solid" to="/sim/modes">
            <PlayIcon />
            연습 모의투자 시작하기
            <ChevronIcon />
          </Link>
        )}
      </div>

      {/* 마스코트와 장식이 함께 떠 있는 무대. 장식이 가장자리에서 잘려야 "안에서
          떠다니는" 것으로 읽혀 여기에만 overflow:hidden 을 건다 — 카드(.sh-hero)에
          걸면 마스코트 더듬이가 잘린다. 무대 높이는 마스코트가 들어가게 잡는다.

          모의투자 캐릭터는 검정 개미다. 인사이트 쪽 흰 개미와 다른 이미지이며,
          모의투자 화면에는 이쪽만 쓴다. */}
      <div className="sh-stage" aria-hidden="true">
        {DRIFT.map((name, i) => (
          <img key={name} className={`sh-drift d${i + 1}`} src={`/assets/pageset/${name}.webp`} alt="" />
        ))}
        <img className="sh-hero-art" src="/assets/pageset/ant-chart.webp" alt="" />
      </div>
    </section>
  )
}

/* ── 최근 연습 기록 ────────────────────────────────────────
   설계서 §3 G-01 의 "완료 기록 요약". 목록에서 온 것만 쓴다 —
   벤치마크(시장 수익률)와 날짜 범위는 응답에 없어 그리지 않는다. */
function RecentRuns({ ongoing, done, loading, error, onRetry }: {
  ongoing: MyRun[]; done: MyRun[]; loading: boolean
  error: ApiError | null
  onRetry: () => void
}) {
  const [open, setOpen] = useState(false)

  /* 진행 중인 것이 먼저다 — 바로 들어갈 수 있는 것이 위에 있어야 한다. */
  const all = [
    ...[...ongoing].sort(recentFirst).map((r) => ({ run: r, live: true })),
    ...[...done].sort(recentFirst).map((r) => ({ run: r, live: false })),
  ]
  const rows = all.slice(0, open ? EXPANDED_ROWS : COLLAPSED_ROWS)
  /* 펼쳤을 때 실제로 더 보이는 줄 수. 상한에 걸리면 그만큼만 약속한다 */
  const more = Math.min(all.length, EXPANDED_ROWS) - COLLAPSED_ROWS
  const failure = shown(error)

  return (
    <article className="sh-panel">
      <div className="sh-panel-head">
        <h2><HistoryIcon />최근 연습 기록</h2>
        <Link to="/sim/history">전체 보기 ›</Link>
      </div>

      {loading && <p className="sh-state">불러오는 중…</p>}
      {failure && <ErrorState error={failure} onRetry={onRetry} inline />}
      {!loading && !failure && rows.length === 0 && (
        <p className="sh-state">아직 참가한 모의투자가 없습니다.</p>
      )}

      {rows.length > 0 && (
        <ul className="sh-runs" id="sh-recent-runs">
          {rows.map(({ run, live }) => {
            const when = live ? null : endedOn(run.endedAt)
            /* 0 도 그려야 해서 null·undefined 만 걸러 낸다 */
            const rate = run.returnRate
            return (
              <li className="sh-run" key={run.seasonId}>
                <span className={`sh-state-tag ${live ? 'live' : 'done'}`}>
                  {live ? '진행중' : '완료'}
                </span>

                <div className="sh-run-main">
                  <b>{run.title || `${MODE_LABEL[run.mode]} 모의투자`}</b>
                  <small className="num">
                    {live ? `D+${run.currentDay} / ${run.lengthDays}일` : `${run.lengthDays}일 완주`}
                    {when && <><span className="sh-dot" />{when}</>}
                  </small>
                </div>

                {!live && rate != null && (
                  <dl className="sh-rate">
                    <dt>내 수익률</dt>
                    <dd className={`num ${rate >= 0 ? 'up' : 'down'}`}>
                      {rate >= 0 ? '+' : '−'}{Math.abs(rate).toFixed(1)}%
                    </dd>
                  </dl>
                )}

                {live ? (
                  <Link className="sh-replay" to={`/sim/${run.seasonId}/play`}>이어서 하기</Link>
                ) : (
                  <Link className="sh-replay" to={`/sim/${run.seasonId}/result`}>결과 보기</Link>
                )}
              </li>
            )
          })}
        </ul>
      )}

      {/* 접힌 줄이 있을 때만 나온다. margin-top:auto 로 카드 바닥에 붙어(sim-home.css)
          옆 칸 팁 상자와 같은 높이에 선다. */}
      {more > 0 && (
        <button
          type="button" className="sh-more" onClick={() => setOpen((v) => !v)}
          aria-expanded={open} aria-controls="sh-recent-runs"
        >
          {open ? '접기' : `${more}개 더 보기`}
          <CaretIcon />
        </button>
      )}
    </article>
  )
}

/* ── 처음이라면 이렇게 시작해요 ────────────────────────────
   전부 정적이다. 서버에서 오는 값이 없으므로 실패할 것도 없다. */
const STEPS: { title: string; note: React.ReactNode; icon: React.ReactNode }[] = [
  { title: '연습 시작', note: <>버튼을 눌러<br />모의투자를 시작하세요.</>, icon: <PlayIcon /> },
  { title: '매매 기록', note: <>관심 종목을 사고팔며<br />나의 판단을 기록하세요.</>, icon: <NoteIcon /> },
  { title: '결과 비교', note: <>연습이 끝나면<br />결과를 돌아보세요.</>, icon: <BarsIcon /> },
]

function HowTo() {
  return (
    <article className="sh-panel">
      <div className="sh-panel-head">
        <h2><BookIcon />처음이라면 이렇게 시작해요</h2>
      </div>

      <ol className="sh-steps">
        {STEPS.map((s, i) => (
          <li className="sh-step" key={s.title}>
            <span className="sh-step-no">{i + 1}</span>
            <span className="sh-step-ico">{s.icon}</span>
            <b>{s.title}</b>
            <em>{s.note}</em>
          </li>
        ))}
      </ol>

      <p className="sh-tip">
        <BulbIcon />
        <span>
          <b>실제 과거 데이터로 연습하고, 내 판단을 기록하며, 결과를 돌아보세요.</b>
          연습이 쌓일수록 더 나은 투자로 가까워질 거예요.
        </span>
      </p>
    </article>
  )
}

export default function SeasonHome() {
  const { data, loading, error, reload } = useAsync(getMyRuns)
  /* 히어로 칩의 근거. 실패해도 칩만 빠지므로 오류를 따로 그리지 않는다 —
     이 화면의 본문은 내 기록이지 참가 가능한 목록이 아니다. */
  const { data: open } = useAsync(getPracticeRuns)

  const ongoing = data?.ongoing ?? []
  const done = data?.done ?? []
  /* 여러 개가 진행 중이면 가장 최근 것을 히어로에 세운다. 나머지는 아래 목록에 남는다. */
  const lead = [...ongoing].sort(recentFirst)[0] ?? null

  return (
    <main className="main">
      <div className="main-inner sim-home">
        {/* 목록이 실패해도 화면 전체를 오류로 덮지 않는다 — 실패한 칸만 안내를
            띄우고 나머지는 그대로 읽힌다. 판단은 shown() 이 한다. */}
        <Hero run={lead} practice={open?.items} />

        <section className="sh-panels" aria-label="내 모의투자 요약">
          <RecentRuns ongoing={ongoing} done={done} loading={loading} error={error} onRetry={reload} />
          <HowTo />
        </section>
      </div>
    </main>
  )
}
