/* 연습하기 · /sim/practice
   담당 스토리 [ANT-FE-SEASON-PRACTICE]

   화면설계서 §3 에 없는 화면이다. G-02 모드 선택에서 PRACTICE 를 고른 사람이 머무는
   허브라 화면 ID 는 G-02 를 그대로 쓴다 — A-01 의 OAuth 복귀 경로와 같은 처리다.

   구조·클래스 이름(pr-*)은 프로토타입 screens/sim-practice.html 을 따른다.
   셸(사이드바·상단바)은 프로토타입 것을 쓰지 않고 현재 Layout 을 그대로 쓴다.

   ── 프로토타입에서 뺀 것 ────────────────────────────────
   원본 디자인의 네 블록 중 상당수가 설계서 §9.1 "도메인을 두지 않는다" 에 걸린다.
   §9.2(화면에 두지 않는다)보다 강한 항목이라 목업으로도 만들지 않는다.

   1. 초보자용·상급자용 토글 → §9.1 "시즌 난이도 — 시즌 속성에 difficulty 없음".
   2. 오늘의 연습 코스 · 이번 주 목표 10회 · 연속 학습 7일
      → §9.1 "연습 코스 · 주간 목표 · 연속 학습 — PRACTICE 는 시즌 모드의 하나일 뿐".
      그 자리를 이어서 할 연습(진행 중 목록)이 받는다.
   3. AI 힌트 받기 / AI 복기 받기 버튼 → §9.1 "온디맨드 AI 힌트 · 복기 생성 —
      AI 생성은 전부 배치(B6)". 버튼 대신 "언제 생기는지" 를 알리고 기록으로 보낸다.
   4. 나의 연습 성과(적중률 62.4% · 평균 수익률 +4.21% · 최근 성장 추이)
      → 집계 엔드포인트가 없다. /seasons/history/me 는 목록만이다(§9.2 G-01 과 같은 사정).
      최근 완료한 연습 목록으로 대신한다.

   연습 주제 이름에서는 연도·사건을 뺀다. 시대를 특정할 수 있는 말은 모의투자
   전체에서 쓰지 않는다 — "반도체 실적 시즌" → "반도체 실적 구간".

   ── 서버에서 받는 것 ────────────────────────────────────
   api/seasons.ts 의 두 목록이다. 연습 주제는 seasons.title·note 로 오고(API 명세
   v0.24) 카드마다 그 시즌으로 들어간다 — 화면에 하드코딩하지 않는다(설계서 §4 G-02a).
   목록은 ?mode=PRACTICE 로 서버가 걸러 준다. 클라이언트에서 걸러도 응답에는
   실려 오므로, DEMO 가 관리자 전용인 정책은 서버에서 지켜야 한다. */
import { Link } from 'react-router-dom'
import type { ApiError } from '../api/errors'
import {
  getMyRuns, getPracticeRuns, isJoinable, joinableFirst, progressOf, recentFirst, won,
  type MyRun, type OpenRun,
} from '../api/seasons'
import { useAsync } from '../api/useAsync'
import ErrorState from '../components/state/ErrorState'
import '../styles/screens/sim-practice.css'

/* 401 을 그대로 보여준다.
   전에는 빈 상태로 뭉갰다 — 시즌 API 가 하나도 없던 때라 401 이 "그 API 가 아직 없다" 는
   뜻이었기 때문이다. 이제 /seasons 계열이 다 붙었으므로 401 은 <b>로그인이 안 됐다</b> 는
   뜻이고, 그걸 "연습이 없습니다" 로 그리면 서버가 거부한 것을 시즌이 없는 것으로 읽게 된다.
   errorText 가 UNAUTHENTICATED 를 "로그인이 필요합니다" 로 풀어 준다. */
const shown = (error: ApiError | null) => error

/* 카드가 세 칸이라 그 이상은 받지 않는다. 전체는 G-09 기록에서 본다. */
const GOING_CARDS = 3
const DONE_ROWS = 4

const Ico = ({ size = 24, children }: { size?: number; children: React.ReactNode }) => (
  <svg width={size} height={size} viewBox="0 0 24 24" fill="none" stroke="currentColor"
       strokeWidth="1.8" strokeLinecap="round" strokeLinejoin="round" aria-hidden="true">
    {children}
  </svg>
)

/* ── 연습 주제 아이콘 ───────────────────────────────────────
   주제 이름과 설명은 서버가 준다(seasons.title·note · API 명세 v0.24) — 화면에
   하드코딩하지 않는다(설계서 §4 G-02a). 여기 남은 것은 theme 별 도형뿐이다.

   그림으로 시대를 알려주지 않으려고 프로토타입의 시나리오 일러스트 대신
   도형만 쓴다. 모르는 theme 은 기본 도형으로 떨어진다. */
const THEME_ICON: Record<string, React.ReactNode> = {
  IT: <><rect x="7" y="7" width="10" height="10" rx="1.5" /><path d="M10 3v4M14 3v4M10 17v4M14 17v4M3 10h4M3 14h4M17 10h4M17 14h4" /></>,
  FINANCE: <><circle cx="7.5" cy="7.5" r="2.5" /><circle cx="16.5" cy="16.5" r="2.5" /><path d="M19 5 5 19" /></>,
  AUTO: <><path d="M4 15h16l-1.6-5.2A2 2 0 0 0 16.5 8h-9a2 2 0 0 0-1.9 1.8z" /><path d="M4 15v3h3M20 15v3h-3" /><circle cx="7.5" cy="18" r="1.6" /><circle cx="16.5" cy="18" r="1.6" /></>,
}

const DEFAULT_ICON = (
  <><path d="M3 20h18" /><path d="M6 20v-7M11 20v-11M16 20v-5" /></>
)

const themeIcon = (theme?: string) =>
  (theme && THEME_ICON[theme]) ?? DEFAULT_ICON

/* 카드 아이콘은 순서대로 색만 바꿔 쓴다. 그림으로 시대를 알려주지 않으려고
   프로토타입의 시나리오 일러스트 대신 도형만 남겼다. */
const CARD_TONES = ['v', 'g', 'b'] as const

/** 진행률 고리. r=17 이라 둘레는 2πr ≈ 106.8 이다. */
function Ring({ percent }: { percent: number }) {
  const C = 106.8
  return (
    <span className="pr-ring">
      <svg viewBox="0 0 42 42" width="78" height="78" aria-hidden="true">
        <circle cx="21" cy="21" r="17" fill="none" stroke="#e6f4ec" strokeWidth="5" />
        {/* 0% 일 때는 아예 그리지 않는다 — 길이가 0 이어도 둥근 끝(linecap)이
            점으로 찍혀 조금 진행한 것처럼 보인다. */}
        {percent > 0 && (
          <circle cx="21" cy="21" r="17" fill="none" stroke="#16a06a" strokeWidth="5"
                  strokeLinecap="round" strokeDasharray={`${(C * percent) / 100} ${C}`} />
        )}
      </svg>
      <span className="num">{percent}%</span>
    </span>
  )
}

/* ── 요약 스트립 ───────────────────────────────────────────
   프로토타입의 세 칸을 근거 있는 값으로 바꿔 채운다. 셋 다 목록에서 바로 나오는
   수라 별도 집계 API 가 필요 없다. */
function Summary({ going, done, open, openFailed }: {
  going: MyRun[]; done: MyRun[]; open: OpenRun[]; openFailed: boolean
}) {
  const lead = [...going].sort(recentFirst)[0]
  const percent = lead ? progressOf(lead.currentDay, lead.lengthDays) : 0

  return (
    <dl className="pr-card pr-stats">
      <div className="pr-stat">
        <Ring percent={percent} />
        <div>
          <dt>진행 중인 연습</dt>
          <small>
            {lead
              ? <>총 {lead.lengthDays}일 중<br />D+{lead.currentDay} 까지 진행</>
              : <>아직 진행 중인<br />연습이 없습니다</>}
          </small>
        </div>
      </div>

      <div className="pr-stat">
        <i className="pr-tone-v">
          <Ico size={32}>
            <rect x="3" y="5" width="18" height="16" rx="2.5" /><path d="M8 3v4M16 3v4M3 10h18" />
            <path d="m9 15 2 2 4-4" />
          </Ico>
        </i>
        <div>
          <dt>완료한 연습</dt>
          <dd className="brand num">{done.length}회</dd>
          <small>전체 누적</small>
        </div>
      </div>

      <div className="pr-stat">
        <i className="pr-tone-o">
          <Ico size={32}>
            <path d="M22 9 12 4 2 9l10 5z" /><path d="M6 11.5V16c0 1.7 2.7 3 6 3s6-1.3 6-3v-4.5" />
            <path d="M22 9v5" />
          </Ico>
        </i>
        <div>
          <dt>시작할 수 있는 연습</dt>
          {/* 목록을 못 받았으면 0개가 아니라 모르는 것이다 */}
          <dd className="hot num">{openFailed ? '—' : `${open.length}개`}</dd>
          <small>{openFailed ? '목록을 불러오지 못했습니다' : '지금 참가 가능'}</small>
        </div>
      </div>
    </dl>
  )
}

/* ── ① 이어서 할 연습 ──────────────────────────────────────
   프로토타입 "오늘의 연습 코스" 자리. 코스 도메인이 없으므로(§9.1) 실제로 이어서
   할 수 있는 것, 즉 진행 중인 연습을 카드로 세운다. */
function Going({ runs, loading, failure, onRetry, startAt }: {
  runs: MyRun[]; loading: boolean; failure: ApiError | null
  onRetry: () => void; startAt: string
}) {
  const cards = [...runs].sort(recentFirst).slice(0, GOING_CARDS)

  return (
    <section className="pr-card pr-panel">
      <div className="pr-panel-head">
        <span className="pr-num num">1</span>
        <h2>이어서 할 연습</h2>
        <Link className="pr-more" to="/sim/history">전체 보기 ›</Link>
      </div>

      {loading && <p className="pr-state">불러오는 중…</p>}
      {failure && <ErrorState error={failure} onRetry={onRetry} inline />}
      {!loading && !failure && cards.length === 0 && (
        <p className="pr-empty">
          진행 중인 연습이 없습니다.
          <Link to={startAt}>연습 시작하기 ›</Link>
        </p>
      )}

      {cards.length > 0 && (
        <div className="pr-courses">
          {cards.map((r, i) => {
            const percent = progressOf(r.currentDay, r.lengthDays)
            return (
              <article className="pr-course" key={r.seasonId}>
                <i className={`t-${CARD_TONES[i % CARD_TONES.length]}`}>
                  <Ico size={26}><path d="M3 16l5-6 4 4 3-4 6 7" /></Ico>
                </i>
                <b>연습 모의투자</b>
                {/* 달력 날짜는 쓰지 않는다 — 날짜가 곧 시대 단서다 */}
                <p className="num">D+{r.currentDay} / 총 {r.lengthDays}일</p>
                <span className="pr-bar" role="progressbar" aria-label="진행률"
                      aria-valuenow={percent} aria-valuemin={0} aria-valuemax={100}>
                  <i style={{ width: `${percent}%` }} />
                </span>
                <Link className="pr-start" to={`/sim/${r.seasonId}/play`}>
                  이어서 하기 <em aria-hidden="true">›</em>
                </Link>
              </article>
            )
          })}
        </div>
      )}
    </section>
  )
}

/* ── ③ 최근 완료한 연습 ────────────────────────────────────
   프로토타입 "나의 연습 성과" 자리. 적중률·평균 수익률·성장 추이는 집계 API 가
   없어(§9.2 G-01 과 같은 사정) 목록으로 대신한다. 수익률은 /seasons/me 응답에 없다. */
function Done({ runs, loading, failure, onRetry }: {
  runs: MyRun[]; loading: boolean; failure: ApiError | null; onRetry: () => void
}) {
  const rows = [...runs].sort(recentFirst).slice(0, DONE_ROWS)

  return (
    <section className="pr-card pr-panel">
      <div className="pr-panel-head">
        <span className="pr-num num">3</span>
        <h2>최근 완료한 연습</h2>
        <Link className="pr-more" to="/sim/history">기록 ›</Link>
      </div>

      {loading && <p className="pr-state">불러오는 중…</p>}
      {failure && <ErrorState error={failure} onRetry={onRetry} inline />}
      {!loading && !failure && rows.length === 0 && (
        <p className="pr-empty">아직 완료한 연습이 없습니다.</p>
      )}

      {rows.length > 0 && (
        <ul className="pr-dones">
          {rows.map((r) => (
            <li className="pr-done" key={r.seasonId}>
              <i>
                <Ico size={20}><circle cx="12" cy="12" r="9" /><path d="m8.5 12.5 2.5 2.5 5-5" /></Ico>
              </i>
              <div>
                <b>연습 모의투자</b>
                <small className="num">{r.lengthDays}일 완주</small>
              </div>
              <Link className="pr-done-go" to={`/sim/${r.seasonId}/result`}>결과 보기</Link>
            </li>
          ))}
        </ul>
      )}
    </section>
  )
}

export default function SeasonPractice() {
  const mine = useAsync(getMyRuns)
  const openList = useAsync(getPracticeRuns)

  /* 이 화면은 연습만 다룬다. 대회·시연은 G-02 모드 선택에서 고른다. */
  const going = (mine.data?.ongoing ?? []).filter((r) => r.mode === 'PRACTICE')
  const done = (mine.data?.done ?? []).filter((r) => r.mode === 'PRACTICE')
  /* 모드는 서버가 걸러 준다(?mode=PRACTICE) — DEMO 가 관리자 전용이라
     응답 자체에 실려 오지 않아야 한다. 여기서는 참가 가능 여부만 본다. */
  const open = (openList.data?.items ?? []).filter(isJoinable).sort(joinableFirst)

  /* 바로 들어갈 수 있는 연습이 있으면 그 상세로, 없으면 모드 선택으로 보낸다.
     주제로 시즌을 고르는 길은 아직 서버에 없어 주제 카드도 같은 곳을 가리킨다. */
  const first = open[0]
  const startAt = first ? `/sim/seasons/${first.id}` : '/sim/modes'
  const topicsFailure = shown(openList.error)

  return (
    <main className="main">
      <div className="main-inner practice">
        <header className="pr-head">
          <div>
            <h1>연습하기</h1>
            <p>부담 없이 전략을 연습하고 투자 흐름을 익혀보세요.</p>
          </div>
        </header>

        <Summary going={going} done={done} open={open} openFailed={topicsFailure !== null} />

        <div className="pr-body">
          <div className="pr-col">
            <Going runs={going} loading={mine.loading} failure={shown(mine.error)}
                   onRetry={mine.reload} startAt={startAt} />
            <Done runs={done} loading={mine.loading} failure={shown(mine.error)}
                  onRetry={mine.reload} />
          </div>

          <div className="pr-col">
            <section className="pr-card pr-panel">
              <div className="pr-panel-head">
                <span className="pr-num num">2</span>
                <h2>연습 주제</h2>
              </div>

              {/* 실패와 없음을 구분한다. 실패인데 "없습니다" 를 그리면 서버가 죽은 것을
                  시즌이 없는 것으로 읽게 된다 — ①③ 패널과 같은 규칙을 쓴다. */}
              {openList.loading && <p className="pr-state">불러오는 중…</p>}
              {topicsFailure && (
                <ErrorState error={topicsFailure} onRetry={openList.reload} inline />
              )}

              {!openList.loading && !topicsFailure && open.length > 0 && (
                <ul className="pr-scenarios">
                  {open.map((s, i) => (
                    <li key={s.id}>
                      {/* 카드마다 그 시즌으로 간다 — 셋이 같은 곳을 가리키지 않는다 */}
                      <Link className="pr-scenario" to={`/sim/seasons/${s.id}`}>
                        <i className={`t-${CARD_TONES[i % CARD_TONES.length]}`}>
                          <Ico size={26}>{themeIcon(s.theme)}</Ico>
                        </i>
                        <div>
                          <b>{s.title}</b>
                          <small>{s.note ?? `${s.lengthDays}게임일 · 예수금 ${won(s.initialCash)}`}</small>
                        </div>
                        <em aria-hidden="true">›</em>
                      </Link>
                    </li>
                  ))}
                </ul>
              )}

              {!openList.loading && !topicsFailure && open.length === 0 && (
                <p className="pr-note">지금 참가할 수 있는 연습이 없습니다.</p>
              )}

              {/* 시기를 숨기는 것이 이 게임의 규칙이라는 걸 여기서 한 번 알린다 */}
              <p className="pr-note">주제는 어떤 장이었는지만 알려줍니다. 실제 시기와 종목명은 가려집니다.</p>
            </section>

            <section className="pr-ai">
              <div className="pr-panel-head">
                <span className="pr-num num">4</span>
                <h2>AI 가이드</h2>
              </div>
              <div className="pr-ai-inner">
                <b>연습이 끝나면 AI가 복기해 드려요</b>
                {/* 온디맨드 생성 버튼을 두지 않는다 — AI 산출물은 전부 배치다(§9.1) */}
                <p>
                  판단 근거와 놓친 신호를 정리한 복기 리포트가
                  <br />
                  연습 종료 후 자동으로 만들어집니다.
                </p>
                <Link className="pr-hint" to="/sim/history">지난 연습 복기 보기</Link>
              </div>
              {/* 모의투자 캐릭터는 검정 개미다. 더듬이가 이미지 맨 위에 붙어 있어
                  위가 잘리면 바로 티가 난다 — 아래쪽에 붙여 놓는다. */}
              <figure className="pr-ai-art">
                <img src="/assets/character/black_ant/antena-character-black.png" alt="" aria-hidden="true" />
              </figure>
            </section>

            <div className="pr-cta">
              <Link to={startAt}>
                {first ? '연습 시작하기' : '모드 선택으로 이동'} <em aria-hidden="true">›</em>
              </Link>
            </div>
          </div>
        </div>
      </div>
    </main>
  )
}
