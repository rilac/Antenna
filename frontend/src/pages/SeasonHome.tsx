/* G-01 모의투자 홈 · /sim
   담당 스토리 [ANT-FE-SEASON-HOME]
   설계서 docs/화면설계서.md §3 G-01 — "진행 중 시즌(이어하기) · 완료 기록 요약 · 시즌 목록"

   구조·클래스 이름(sh-*)은 프로토타입 screens/sim-index.html 을 따른다
   (routes.ts 의 bodyClass 'sim-home-page' 가 그 화면을 가리킨다).
   셸(사이드바·상단바)은 프로토타입 것을 쓰지 않고 현재 Layout 을 그대로 쓴다.

   ── 프로토타입에서 뺀 것 ────────────────────────────────
   1. 시즌을 화면에 드러내지 않는다. 프로토타입 히어로의 "시즌 7 · 2008 금융위기",
      게임일 옆 달력 날짜(2008.08.24), 인기 시즌 썸네일·시나리오 이름이 전부
      시대를 특정하는 단서다. 진행 중인 것은 "진행 중인 모의투자" 하나로 부르고
      회차 번호도 쓰지 않는다 — 여러 개면 시작한 순서로만 구분한다.
      애초에 /seasons · /seasons/me 응답에 이름 필드가 없어 그릴 값도 없다.
   2. KPI 4종(최근 수, 평균·최고 수익률, 승률)과 인기/추천 시즌은 §9.2 가
      G-01 에서 제외한 항목이다 — /seasons/history/me 는 목록만이라 집계 근거가 없다.
      그 자리는 설계서가 G-01 의 API 로 지정한 GET /seasons(시즌 목록)로 채운다.
   3. 보상 금액(+50 ANT · +100 ANT)을 지웠다. §1 규칙 3 — 금액 상수는 서버 상수이고
      API 에 노출되지 않으므로 보상 규모는 정적 문구로만 쓴다.
   4. "남은 기간 20일 14시간" 을 뺐다. 연습은 사용자가 직접 진행해 남은 시간이라는
      개념이 없고, 대회 시간표는 G-03 시즌 상세의 몫이다(§3 G-03).

   ── 서버에서 받는 것 ────────────────────────────────────
     GET /seasons/me?status=ONGOING → { items: [{ seasonId, mode, currentDay, lengthDays, progress }] }
     GET /seasons/me?status=DONE    → 같은 스키마
     GET /seasons                   → { items: [{ id, mode, lengthDays, initialCash, entryFee, status }] }
   셋 다 커서 페이징이 없는 목록이라(§1 규칙 6 의 예외) useCursorList 를 쓰지 않는다. */
import { Link } from 'react-router-dom'
import type { ApiError } from '../api/errors'
import {
  ant, getMyRuns, getOpenRuns, isJoinable, joinableFirst, MODE_LABEL, progressOf, recentFirst, won,
  type MyRun, type SeasonMode,
} from '../api/seasons'
import { useAsync } from '../api/useAsync'
import { useAuth } from '../auth/context'
import ErrorState from '../components/state/ErrorState'
import '../styles/screens/sim-home.css'

/* 홈은 요약이라 몇 줄만 보여준다. 전체는 G-09 기록에서 본다. */
const RECENT_ROWS = 4
const OPEN_ROWS = 3

/* 401 은 배너로 띄우지 않는다. 세션이 정말 끊겼으면 API 클라이언트가 로그아웃시켜
   로그인 화면으로 보내므로 여기까지 오지 않는다. 여기 남는 401 은 백엔드에 아직 그
   API 가 없다는 뜻인데(Spring Security 가 라우팅보다 먼저 걸러 없는 경로도 401 이
   온다), 그 사정을 "로그인이 필요합니다" 로 보여주면 로그인한 사람이 헷갈린다.
   그때는 오류가 아니라 빈 상태로 그린다 — 참가한 것이 없는 화면과 같은 모습이다. */
const shown = (error: ApiError | null) => (error && error.status !== 401 ? error : null)

const Ico = ({ size = 22, children }: { size?: number; children: React.ReactNode }) => (
  <svg width={size} height={size} viewBox="0 0 24 24" fill="none" stroke="currentColor"
       strokeWidth="1.8" strokeLinecap="round" strokeLinejoin="round" aria-hidden="true">
    {children}
  </svg>
)

const CalendarIcon = () => (
  <Ico><rect x="3" y="5" width="18" height="16" rx="2.5" /><path d="M8 3v4M16 3v4M3 10h18" /></Ico>
)
const RulerIcon = () => <Ico><path d="M4 20V10M10 20V4M16 20v-7M22 20H2" /></Ico>
const FlagIcon = () => (
  <Ico><path d="M5 21V4M5 4h11l-2 3.5L16 11H5" /></Ico>
)
const PlayIcon = () => (
  <Ico size={24}><circle cx="12" cy="12" r="9" /><path d="m10 8.2 6 3.8-6 3.8z" fill="currentColor" /></Ico>
)
const HistoryIcon = () => (
  <Ico><path d="M3 12a9 9 0 1 0 2.6-6.4" /><path d="M3 4v5h5" /><path d="M12 8v4.5l3 1.8" /></Ico>
)
const GiftIcon = () => (
  <Ico size={21}>
    <rect x="3" y="8" width="18" height="13" rx="2" /><path d="M2 8h20v4H2zM12 8v13" />
    <path d="M12 8S9.5 3 7.5 4.2 10 8 12 8zM12 8s2.5-5 4.5-3.8S14 8 12 8z" />
  </Ico>
)

/* 모드 배지에 쓰는 도형. 모드마다 달라야 목록에서 구분이 되는데, 시나리오 썸네일은
   그림 자체가 시대를 알려주므로(프로토타입의 야경·유전 일러스트) 쓰지 않는다. */
const MODE_ICON: Record<SeasonMode, React.ReactNode> = {
  PRACTICE: <><path d="M22 9 12 4 2 9l10 5z" /><path d="M6 11.5V16c0 1.7 2.7 3 6 3s6-1.3 6-3v-4.5" /></>,
  COMPETITION: <><path d="M7 4h10v5a5 5 0 0 1-10 0z" /><path d="M7 6H4v1.5A3.5 3.5 0 0 0 7.5 11M17 6h3v1.5a3.5 3.5 0 0 1-3.5 3.5" /><path d="M10 19h4M12 14v5M8.5 21h7" /></>,
  DEMO: <><rect x="2.5" y="4" width="19" height="13" rx="2" /><path d="M8 21h8M12 17v4" /></>,
}

/* ── 히어로 ───────────────────────────────────────────────
   진행 중인 것이 있으면 이어하기, 없으면 시작을 권한다.
   여러 개 진행 중이면 가장 최근 것을 세우고 나머지는 아래 목록에서 본다. */
function Hero({ run }: { run: MyRun | null }) {
  const { user } = useAuth()
  const percent = run ? progressOf(run.currentDay, run.lengthDays) : 0
  const left = run ? Math.max(0, run.lengthDays - run.currentDay) : 0

  return (
    <section className="sh-hero" aria-labelledby="sh-hero-h">
      <div className="sh-hero-copy">
        <span className="sh-flag">{run ? '진행 중' : '모의투자'}</span>

        {/* 페이지 제목 자리다. 위에 따로 머리글을 두지 않으므로 h1 이다. */}
        <h1 className="sh-title" id="sh-hero-h">
          {run
            ? `${MODE_LABEL[run.mode]} 모의투자를 이어서`
            : `${user?.nickname ?? '안테나'}님, 모의투자를 시작해 보세요`}
        </h1>

        <p className="sh-lead">
          {run ? (
            <>
              지난 시장을 그대로 되짚으며
              <br />
              투자 전략을 다듬어 보세요.
            </>
          ) : (
            <>
              실제로 있었던 시장 흐름 위에서
              <br />
              부담 없이 투자 전략을 시험해 보세요.
            </>
          )}
        </p>

        {run && (
          <>
            <p className="sh-progress-label">진행률</p>
            <div className="sh-progress">
              <span
                className="sh-bar" role="progressbar" aria-label="진행률"
                aria-valuenow={percent} aria-valuemin={0} aria-valuemax={100}
              >
                <i style={{ width: `${percent}%` }} />
              </span>
              <b className="num">{percent}%</b>
            </div>

            {/* 게임일은 남기고 달력 날짜는 쓰지 않는다 — 날짜가 곧 시대 단서다. */}
            <div className="sh-stats">
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
            </div>
          </>
        )}
      </div>

      {/* 모의투자 캐릭터는 검정 개미다. 인사이트 쪽 캐릭터(antena-character-transparent)
          와 다른 이미지이며, 모의투자 화면에는 이쪽만 쓴다.
          더듬이가 이미지 맨 위에 붙어 있어 위가 잘리면 바로 티가 난다 —
          카드에 overflow:hidden 을 걸지 않고 높이도 카드 안에 들어가게 잡는다. */}
      <img className="sh-hero-art" src="/assets/character/black_ant/antena-character-black.png"
           alt="" aria-hidden="true" />

      <div className="sh-cta">
        {run ? (
          <Link className="sh-btn solid" to={`/sim/${run.seasonId}/play`}>
            <PlayIcon />
            이어서 하기
          </Link>
        ) : (
          <Link className="sh-btn solid" to="/sim/modes">
            <PlayIcon />
            모의투자 시작하기
          </Link>
        )}
        <Link className="sh-btn ghost" to="/sim/history">
          <HistoryIcon />
          지난 기록 보기
        </Link>
      </div>
    </section>
  )
}

/* ── 최근 모의투자 ─────────────────────────────────────────
   설계서 §3 G-01 의 "완료 기록 요약". 목록에서 온 것만 쓴다 —
   평균·최고 수익률·승률 같은 집계는 §9.2 가 뺀 항목이다.
   수익률을 줄마다 붙이지 않는 이유도 같다. /seasons/me 응답에 없다. */
function RecentRuns({ ongoing, done, loading, error, onRetry }: {
  ongoing: MyRun[]; done: MyRun[]; loading: boolean
  error: ApiError | null
  onRetry: () => void
}) {
  const rows = [
    ...[...ongoing].sort(recentFirst).map((r) => ({ run: r, live: true })),
    ...[...done].sort(recentFirst).map((r) => ({ run: r, live: false })),
  ].slice(0, RECENT_ROWS)
  const failure = shown(error)

  return (
    <article className="sh-panel">
      <div className="sh-panel-head">
        <h2>최근 모의투자</h2>
        <Link to="/sim/history">전체 보기 ›</Link>
      </div>

      {loading && <p className="sh-state">불러오는 중…</p>}
      {failure && <ErrorState error={failure} onRetry={onRetry} inline />}
      {!loading && !failure && rows.length === 0 && (
        <p className="sh-state">아직 참가한 모의투자가 없습니다.</p>
      )}

      {rows.length > 0 && (
        <ul className="sh-runs">
          {rows.map(({ run, live }) => (
            <li className="sh-run" key={run.seasonId}>
              <span className={`sh-state-tag ${live ? 'live' : 'done'}`}>
                {live ? '진행중' : '완료'}
              </span>
              <div className="sh-run-main">
                <b>{MODE_LABEL[run.mode]} 모의투자</b>
                <small className="num">
                  {live ? `D+${run.currentDay} / ${run.lengthDays}일` : `${run.lengthDays}일 완주`}
                </small>
              </div>
              {live ? (
                <Link className="sh-replay" to={`/sim/${run.seasonId}/play`}>이어서 하기</Link>
              ) : (
                <Link className="sh-replay" to={`/sim/${run.seasonId}/result`}>결과 보기</Link>
              )}
            </li>
          ))}
        </ul>
      )}

      <Link className="sh-panel-foot" to="/sim/history">내 기록 더보기 ›</Link>
    </article>
  )
}

/* ── 참가 가능한 모의투자 ───────────────────────────────────
   프로토타입의 "인기 시즌 / 추천"이 있던 자리다. 인기·추천은 집계 API 가 없어
   §9.2 가 뺐고, 시나리오 이름·썸네일은 시대를 알려주므로 쓰지 않는다.
   대신 설계서가 G-01 의 API 로 지정한 GET /seasons 를 그대로 보여준다. */
function OpenRuns() {
  const { user } = useAuth()
  const isAdmin = user?.role === 'ADMIN'
  const { data, loading, error, reload } = useAsync(getOpenRuns)

  /* 시연은 관리자에게만 보인다 — 일반 사용자 노출 대상이 아니다. */
  const rows = (data?.items ?? [])
    .filter(isJoinable)
    .filter((s) => s.mode !== 'DEMO' || isAdmin)
    .sort(joinableFirst)
    .slice(0, OPEN_ROWS)
  const failure = shown(error)

  return (
    <article className="sh-panel">
      <div className="sh-panel-head">
        <h2>참가 가능한 모의투자</h2>
        <Link to="/sim/modes">모드 선택 ›</Link>
      </div>

      {loading && <p className="sh-state">불러오는 중…</p>}
      {failure && <ErrorState error={failure} onRetry={reload} inline />}
      {!loading && !failure && rows.length === 0 && (
        <p className="sh-state">지금 참가할 수 있는 모의투자가 없습니다.</p>
      )}

      {rows.length > 0 && (
        <ul className="sh-opens">
          {rows.map((s) => (
            <li className="sh-open" key={s.id}>
              <span className={`sh-open-ic m-${s.mode.toLowerCase()}`}>
                <Ico size={22}>{MODE_ICON[s.mode]}</Ico>
              </span>
              <div className="sh-open-main">
                <p className="sh-open-name">
                  <b>{MODE_LABEL[s.mode]} 모의투자</b>
                  <span className={`sh-state-tag ${s.status === 'RUNNING' ? 'live' : 'soon'}`}>
                    {s.status === 'RUNNING' ? '진행 중' : '시작 전'}
                  </span>
                </p>
                {/* 두 금액의 단위가 다르다 — 예수금은 금융망 원화(§5.2),
                    참가비는 참가 시 소각하는 ANT 다(§4 G-03). 섞으면 안 된다. */}
                <p className="sh-open-meta num">
                  {`${s.lengthDays}게임일 · 예수금 ${won(s.initialCash)}`}
                  {s.entryFee ? ` · 참가비 ${ant(s.entryFee)}` : ''}
                </p>
              </div>
              <Link className="sh-pick" to={`/sim/seasons/${s.id}`}>참가</Link>
            </li>
          ))}
        </ul>
      )}

      <Link className="sh-panel-foot" to="/sim/modes">모드부터 고르기 ›</Link>
    </article>
  )
}

/* ── 보상 ─────────────────────────────────────────────────
   §1 규칙 3 — 금액 상수(참가·보너스·보상표)는 서버 상수이고 API 에 노출되지 않는다.
   그래서 프로토타입의 "+50 ANT · +100 ANT" 를 지우고 정적 문구만 남긴다.
   실제로 얼마를 받았는지는 지갑 원장(H-01)에서 본다. */
const REWARDS: { tone: string; title: string; note: string; icon: React.ReactNode }[] = [
  {
    tone: 'a', title: '참가 보상', note: '참가하면 ANT 지급',
    icon: <><path d="M12 3 5 6.5v6c0 4.2 2.9 7.5 7 8.5 4.1-1 7-4.3 7-8.5v-6z" /><path d="m9.2 12 2 2 3.6-3.8" /></>,
  },
  {
    tone: 'b', title: '성과 보상', note: '상위 성과에 추가 지급',
    icon: <><path d="M3 17l6-6 4 4 8-8" /><path d="M15 7h6v6" /></>,
  },
  {
    tone: 'c', title: '달성 배지', note: '조건을 채우면 배지 지급',
    icon: <><circle cx="12" cy="9" r="5.5" /><path d="m8.5 13.5-1 7.5 4.5-2.5 4.5 2.5-1-7.5" /></>,
  },
]

function Rewards() {
  return (
    <article className="sh-panel">
      <div className="sh-panel-head">
        <h2><GiftIcon />보상</h2>
        <Link to="/me/wallet">지갑 ›</Link>
      </div>

      <ul className="sh-rewards">
        {REWARDS.map((r) => (
          <li className="sh-reward" key={r.title}>
            <span className={`sh-hex t-${r.tone}`}><Ico size={20}>{r.icon}</Ico></span>
            <b>{r.title}</b>
            <em>{r.note}</em>
          </li>
        ))}
      </ul>

      {/* 규모를 적지 않는 이유를 화면에서도 한 줄로 밝힌다 */}
      <p className="sh-note">지급량은 참가·정산 시점의 서버 기준을 따릅니다.</p>
      <Link className="sh-panel-foot" to="/me/wallet">내 보상 내역 보기 ›</Link>
    </article>
  )
}

export default function SeasonHome() {
  const { data, loading, error, reload } = useAsync(getMyRuns)

  const ongoing = data?.ongoing ?? []
  const done = data?.done ?? []
  /* 여러 개가 진행 중이면 가장 최근 것을 히어로에 세운다. 나머지는 아래 목록에 남는다. */
  const lead = [...ongoing].sort(recentFirst)[0] ?? null

  return (
    <main className="main">
      <div className="main-inner sim-home">
        {/* 목록이 실패해도 화면 전체를 오류로 덮지 않는다 — 실패한 칸만 안내를
            띄우고 나머지는 그대로 읽힌다. 판단은 shown() 이 한다. */}
        <Hero run={lead} />

        <section className="sh-panels" aria-label="내 모의투자 요약">
          <RecentRuns ongoing={ongoing} done={done} loading={loading} error={error} onRetry={reload} />
          <OpenRuns />
          <Rewards />
        </section>
      </div>
    </main>
  )
}
