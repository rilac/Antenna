/* G-02 모드 선택 · /sim/modes
   담당 스토리 [ANT-FE-SEASON-MODE]
   설계서 docs/화면설계서.md §3 G-02 — "연습 · 대회 2카드 + 모드별 차이 · 시연은
   관리자만 3번째 카드"

   구조·클래스 이름은 프로토타입 screens/sim-setup.html 을 그대로 따른다
   (routes.ts 의 bodyClass 'sim-setup-page' 가 그 화면을 가리킨다).
   셸(사이드바·상단바)은 프로토타입 것을 쓰지 않고 현재 Layout 을 그대로 쓴다.

   프로토타입에서 바꾼 것 두 가지 —
   1. CTA 목적지: 프로토타입은 모드별 화면으로 바로 갔지만, 확정된 흐름은
      G-02 → G-03 시즌 상세·참가(/sim/seasons/:id)다. 시즌 id 가 필요하므로
      GET /seasons 로 참가 가능한 시즌을 받아 그중 하나로 보낸다.
      단 연습은 허브 화면(/sim/practice)을 한 번 거친다 — 이어서 할 연습과
      지난 기록이 거기 있어, 참가 가능한 시즌이 없어도 갈 이유가 있다(HUB 참고).
   2. '추천' 배지를 대회 → 연습으로 옮겼다. 대회는 참가에 지갑 서명과 참가비
      소각이 걸려 있어 처음 오는 사람이 바로 갈 곳이 아니다. 되돌리려면
      MODES 의 recommended 를 COMPETITION 쪽으로 옮기면 된다.

   모드별 차이(traits·chips)는 서버 값이 아니라 제품 사실이라 정적 상수로 둔다.
   프로토타입 문구 중 스키마·명세에 근거가 없는 것은 빼거나 바꿨다 —
   "난이도 선택 가능"(seasons 에 난이도 컬럼이 없다) · "가이드 포함"(근거 없음).

   서버에서 받는 것은 참가 가능한 시즌 목록 하나뿐이다.
     GET /seasons → { items: [{ id, mode, lengthDays, initialCash, entryFee, status }] }
   커서 페이징이 없는 목록이라(§1 규칙 6 의 예외) useCursorList 를 쓰지 않는다.
   ?mode= 로 세 번 부르는 대신 한 번 받아 모드별로 나눈다.

   금액은 응답에 담겨 온 값만 쓴다 — §1 규칙 3(금액 상수는 서버 상수 · API 미노출).
   참가자 수는 넣지 않는다. /seasons 응답에 없다(설계서 §8). */
import { useEffect, useState } from 'react'
import { Link } from 'react-router-dom'
import { useAuth } from '../auth/context'
import { api } from '../api/client'
import { ApiError, CLIENT_ERROR_CODE } from '../api/errors'
import ErrorState from '../components/state/ErrorState'
import '../styles/screens/sim-setup.css'

type Mode = 'PRACTICE' | 'COMPETITION' | 'DEMO'
type Status = 'SCHEDULED' | 'RUNNING' | 'CLOSED'

type Season = {
  id: number
  mode: Mode
  /** 시즌의 성격. 시즌을 구분하는 유일한 값이다 — 기간·예수금은 시즌마다 같을 수 있다 */
  title?: string
  lengthDays: number
  initialCash: number
  /** 대회만 값이 있다. 연습·시연은 참가비가 없다 */
  entryFee?: number
  status: Status
}

type ModeSpec = {
  key: Mode
  /** 프로토타입의 모드별 색 클래스 */
  tone: 'learn' | 'contest' | 'demo'
  /** 관리자에게만 보인다 */
  adminOnly?: boolean
  title: string
  lead: React.ReactNode
  /** 카드 가운데 3칸. 이 모드를 고르면 무엇이 달라지는가 */
  facts: { label: React.ReactNode; icon: React.ReactNode }[]
  cta: string
  recommended?: boolean
  /** 참가에 지갑 서명이 필요한가 (설계서 §3 G-03 권한 등급) */
  needsSignature?: boolean
  icon: React.ReactNode
}

/* 시연(DEMO)은 관리자에게만 보인다. 일반 사용자는 연습·대회 두 장이다.
   서버가 DEMO 시즌을 내려줘도 카드가 없으면 화면에 나오지 않는다. */
const MODES: ModeSpec[] = [
  {
    key: 'PRACTICE',
    tone: 'learn',
    title: '연습하기',
    lead: (
      <>
        부담 없이 전략을 연습하고
        <br />
        AI 힌트를 받아보세요.
      </>
    ),
    recommended: true,
    /* 가운데 세 칸의 첫째·둘째는 고정 축이다 — 스토리 S15P21A507-172 가
       "사용자가 실제로 체감하는 차이는 진행을 내가 하는가와 참가비가 있는가" 라고
       못 박았다. 두 모드가 같은 자리에서 서로 반대 값을 보여야 비교가 된다. */
    facts: [
      {
        label: (
          <>
            원하는 때
            <br />
            직접 진행
          </>
        ),
        icon: (
          <>
            <circle cx="12" cy="12" r="9" />
            <path d="m10 8.5 6 3.5-6 3.5z" />
          </>
        ),
      },
      {
        label: (
          <>
            참가비
            <br />
            없음
          </>
        ),
        icon: (
          <>
            <circle cx="12" cy="12" r="9" />
            <path d="M8.5 15.5 15.5 8.5" />
          </>
        ),
      },
      {
        label: (
          <>
            랭킹
            <br />
            반영 없음
          </>
        ),
        icon: (
          <>
            <path d="M12 3 5 5.6v5.9c0 4.2 3 7.4 7 8.5 4-1.1 7-4.3 7-8.5V5.6z" />
            <path d="m9.5 9.5 5 5M14.5 9.5l-5 5" />
          </>
        ),
      },
    ],
    cta: '연습 참여하기',
    icon: (
      <>
        <path d="M22 9 12 4 2 9l10 5z" />
        <path d="M6 11.5V16c0 1.7 2.7 3 6 3s6-1.3 6-3v-4.5" />
        <path d="M22 9v5" />
      </>
    ),
  },
  {
    key: 'COMPETITION',
    tone: 'contest',
    title: '대회',
    lead: (
      <>
        동일한 조건에서 다른 참가자와
        <br />
        실력을 겨뤄보세요.
      </>
    ),
    needsSignature: true,
    /* 첫째·둘째 칸이 연습과 정반대 값이다(진행 주체 · 참가비).
       참가비는 비용이라 소각이라고 쓴다 — 상금처럼 읽히는 문구를 쓰지 않는다. */
    facts: [
      {
        label: (
          <>
            1시간마다
            <br />
            자동 진행
          </>
        ),
        icon: (
          <>
            <circle cx="12" cy="12" r="9" />
            <path d="M12 7v5.5l3.5 2" />
          </>
        ),
      },
      {
        label: (
          <>
            참가비
            <br />
            소각
          </>
        ),
        icon: (
          <>
            <path d="M12 21c3.9 0 6.5-2.4 6.5-5.6 0-4-3.4-6-4.7-9.4-1 1.9-1.6 3-3.3 4.6C8.3 12.6 5.5 12.4 5.5 15.4 5.5 18.6 8.1 21 12 21z" />
          </>
        ),
      },
      {
        label: (
          <>
            공통
            <br />
            시나리오
          </>
        ),
        icon: (
          <>
            <circle cx="9" cy="8" r="3" />
            <path d="M3 20v-1.5A4.5 4.5 0 0 1 7.5 14h3a4.5 4.5 0 0 1 4.5 4.5V20" />
            <circle cx="17.5" cy="9.5" r="2.5" />
            <path d="M21 20v-1a3.5 3.5 0 0 0-3.5-3.5" />
          </>
        ),
      },
    ],
    cta: '대회 참여하기',
    icon: (
      <>
        <path d="M7 4h10v5a5 5 0 0 1-10 0z" />
        <path d="M7 6H4v1.5A3.5 3.5 0 0 0 7.5 11M17 6h3v1.5a3.5 3.5 0 0 1-3.5 3.5" />
        <path d="M10 19h4M12 14v5M8.5 21h7" />
      </>
    ),
  },
  {
    key: 'DEMO',
    tone: 'demo',
    adminOnly: true,
    title: '시연',
    lead: (
      <>
        발표와 체험을 위해 빠르게
        <br />
        핵심 흐름을 살펴보세요.
      </>
    ),
    /* 앞의 두 칸은 연습·대회와 같은 축이다(진행 주체 · 참가비). */
    facts: [
      {
        label: (
          <>
            원하는 때
            <br />
            직접 진행
          </>
        ),
        icon: (
          <>
            <circle cx="12" cy="12" r="9" />
            <path d="m10 8.5 6 3.5-6 3.5z" />
          </>
        ),
      },
      {
        label: (
          <>
            참가비
            <br />
            없음
          </>
        ),
        icon: (
          <>
            <circle cx="12" cy="12" r="9" />
            <path d="M8.5 15.5 15.5 8.5" />
          </>
        ),
      },
      {
        label: (
          <>
            짧은
            <br />
            진행
          </>
        ),
        icon: (
          <>
            <circle cx="12" cy="13" r="8" />
            <path d="M12 9v4l2.5 1.5M9 2h6M19 6l1.5-1.5" />
          </>
        ),
      },
    ],
    cta: '시연 참여하기',
    icon: (
      <>
        <rect x="2.5" y="4" width="19" height="13" rx="2" />
        <path d="M8 21h8M12 17v4" />
        <path d="m6.5 13 3-3.5 2.5 2 4.5-4.5" />
      </>
    ),
  },
]

const Ico = ({ size = 24, children }: { size?: number; children: React.ReactNode }) => (
  <svg width={size} height={size} viewBox="0 0 24 24" fill="none" stroke="currentColor"
       strokeWidth="1.7" strokeLinecap="round" strokeLinejoin="round" aria-hidden="true">
    {children}
  </svg>
)

/* 두 금액의 단위가 다르다 — 섞으면 안 된다.
   initialCash 는 금융망 예수금이라 원화이고(명세 §2 모의투자 · §5.2),
   entryFee 는 참가 시 소각하는 ANT 토큰이다(설계서 §4 G-03 "참가비는 비용(소각)"). */
const won = (n: number) => `${n.toLocaleString('ko-KR')}원`
const ant = (n: number) => `${n.toLocaleString('ko-KR')} ANT`

/* 참가할 수 있는 시즌만 쓴다. CLOSED 는 G-09 기록에서 본다.
   진행 중인 것을 먼저 보여준다 — 바로 들어갈 수 있는 쪽이 우선이다. */
/* 연습은 허브 화면이 따로 있다 — 진행 중인 연습 이어하기·완료 기록·연습 주제를
   거기서 본다. 그래서 시즌 상세로 바로 보내지 않고 허브를 거친다.
   대회·시연은 허브가 없어 종전대로 G-03 시즌 상세로 곧장 간다. */
const HUB: Partial<Record<Mode, string>> = { PRACTICE: '/sim/practice' }

const RANK: Record<string, number> = { RUNNING: 0, SCHEDULED: 1 }
const joinable = (all: Season[], mode: Mode) =>
  all
    .filter((s) => s.mode === mode && s.status !== 'CLOSED')
    .sort((a, b) => (RANK[a.status] ?? 9) - (RANK[b.status] ?? 9) || a.id - b.id)

/* fetch 자체가 거부되면(오프라인·프록시 죽음) ApiError 가 아니라 TypeError 가 온다.
   화면은 ApiError 만 그릴 수 있으므로 계약 형태로 감싼다. */
function asApiError(e: unknown): ApiError {
  if (e instanceof ApiError) return e
  return new ApiError({ code: CLIENT_ERROR_CODE.UNKNOWN, message: String(e) }, 0)
}

function seasonMeta(s: Season) {
  const parts = [`${s.lengthDays}게임일`, `예수금 ${won(s.initialCash)}`]
  if (s.entryFee) parts.push(`참가비 ${ant(s.entryFee)}`)
  return parts.join(' · ')
}

export default function SeasonMode() {
  const { user } = useAuth()
  /* 시연은 관리자에게만 보인다. 카드 수가 2 ↔ 3 으로 바뀌므로 열 수도 함께 간다. */
  const isAdmin = user?.role === 'ADMIN'
  const modes = MODES.filter((m) => !m.adminOnly || isAdmin)

  const [seasons, setSeasons] = useState<Season[] | null>(null)
  const [error, setError] = useState<ApiError | null>(null)
  /* 다시 시도 트리거. 이펙트 안에서 동기 setState 를 하지 않으려고 키를 쓴다 */
  const [attempt, setAttempt] = useState(0)

  useEffect(() => {
    let alive = true
    api
      .get<{ items: Season[] }>('/seasons')
      .then((res) => {
        if (!alive) return
        setSeasons(res.items)
        setError(null)
      })
      .catch((e: unknown) => {
        if (alive) setError(asApiError(e))
      })
    return () => {
      alive = false
    }
  }, [attempt])

  function reload() {
    setSeasons(null)
    setError(null)
    setAttempt((n) => n + 1)
  }

  return (
    <main className="main">
      <div className="main-inner sim-setup">
        {/* 모의투자 캐릭터는 검정 개미다. 인사이트 쪽 캐릭터(antena-character-transparent)
            와 다른 이미지이며, 모의투자 화면에는 이쪽만 쓴다. */}
        <figure className="ss-mascot">
          <img src="/assets/character/black_ant/antena-character-black.png" alt="" aria-hidden="true" />
        </figure>

        <nav className="ss-crumb" aria-label="위치">
          <Link to="/sim">모의투자 홈</Link>
          <i aria-hidden="true">›</i>
          <span>모드 선택</span>
        </nav>

        {/* 가이드 바를 따로 두지 않는다. "처음 써보면 연습하기" 는 카드의 추천 배지와
            설명이 이미 하는 말이고, 그 한 섹션이 140px 을 먹어 카드가 화면 밖으로
            밀렸다. 같은 정보를 이 한 줄로 대신한다. */}
        <header className="ss-head">
          <h1>어떤 모드로 시작할까요?</h1>
          <p>처음이면 연습하기, 순위를 겨루고 싶으면 대회를 고르세요.</p>
        </header>

        {/* 시즌 목록만 실패한 것이므로 화면 전체를 오류로 덮지 않는다 —
            모드별 차이는 서버와 무관하게 읽을 수 있어야 한다.

            401 도 그대로 띄운다. 전에는 뭉갰다 — /seasons 가 없던 때라 401 이 "그 API 가
            아직 없다"(없는 경로도 401 이 온다) 는 뜻이었기 때문이다. 이제 붙었으므로
            401 은 로그인이 안 됐다는 뜻이고, 그걸 감추면 카드마다 "참가 가능한 시즌이
            없습니다" 가 떠서 로그인 문제를 시즌 문제로 읽게 된다. */}
        {error && <ErrorState error={error} onRetry={reload} inline />}

        <section className={`ss-modes n-${modes.length}`} aria-label="모드">
          {modes.map((m) => {
            const open = seasons ? joinable(seasons, m.key) : []
            const first = open[0]
            const rest = open.slice(1)
            /* 허브가 있으면 시즌 유무와 무관하게 그리로 간다. 없으면 종전대로
               참가 가능한 시즌 하나를 골라 G-03 으로 보낸다. */
            const target = HUB[m.key] ?? (first ? `/sim/seasons/${first.id}` : null)

            return (
              <article className={`ss-mode ${m.tone}${m.recommended ? ' pick' : ''}`} key={m.key}>
                {m.recommended && (
                  <span className="ss-badge">
                    <svg width="14" height="14" viewBox="0 0 24 24" fill="currentColor" aria-hidden="true">
                      <path d="m12 3 2.7 5.6 6.1.9-4.4 4.3 1 6.2-5.4-2.9-5.4 2.9 1-6.2L3.2 9.5l6.1-.9z" />
                    </svg>
                    추천
                  </span>
                )}

                {m.needsSignature && <span className="ss-sign">지갑 서명 필요</span>}
                {m.adminOnly && <span className="ss-sign">관리자 전용</span>}

                <span className="ss-mode-icon">
                  <Ico size={44}>{m.icon}</Ico>
                </span>

                <h3>{m.title}</h3>
                <p>{m.lead}</p>

                <div className="ss-facts">
                  {m.facts.map((f, i) => (
                    <div className="ss-fact" key={i}>
                      <Ico>{f.icon}</Ico>
                      <span>{f.label}</span>
                    </div>
                  ))}
                </div>

                {/* 참가는 G-03 시즌 상세에서 한다. 여기서는 진입만 한다.
                    허브가 있는 모드(연습)는 참가 가능한 시즌이 없어도 눌러야 한다 —
                    이어서 할 연습과 지난 기록이 그 화면에 있기 때문이다. */}
                {target ? (
                  <>
                    {/* 화살표를 붙이지 않는다. 붙이면 "글자 + 화살표" 덩어리가 가운데라
                        글자 자체는 왼쪽으로 밀린다 — 버튼 문구가 이미 "참여하기" 라
                        무엇을 하는 버튼인지 화살표 없이도 읽힌다. */}
                    <Link className="ss-cta" to={target}>
                      <b>{m.cta}</b>
                    </Link>
                    <p className="ss-cta-meta">
                      {HUB[m.key]
                        ? open.length > 0
                          ? `고를 수 있는 연습 주제 ${open.length}개`
                          : '연습 화면에서 이어하기와 지난 기록을 봅니다'
                        : first && seasonMeta(first)}
                    </p>
                  </>
                ) : (
                  <>
                    <span className="ss-cta is-off" aria-disabled="true">
                      <b>{m.cta}</b>
                    </span>
                    <p className="ss-cta-meta">
                      {seasons === null && !error && '시즌을 불러오는 중…'}
                      {error && '지금은 시즌을 불러올 수 없습니다'}
                      {seasons !== null && !error && '지금 참가할 수 있는 시즌이 없습니다'}
                    </p>
                  </>
                )}

                {/* 허브가 있는 모드(연습)에서는 시즌을 나열하지 않는다.
                    CTA 가 허브로 가고 그 화면이 "연습 주제" 카드로 시즌을 다 보여준다 —
                    여기서 또 세우면 같은 목록이 두 번이고, 게다가 이 목록에는 제목이
                    없어서 "진행 중 · 60게임일 · 예수금 3,000만원" 세 줄이 똑같아 보였다.

                    허브가 없는 모드(대회·시연)는 CTA 가 시즌 하나로 가므로 나머지를
                    여기서 보여줘야 한다. 그때는 제목으로 구분한다. */}
                {!HUB[m.key] && rest.length > 0 && (
                  <ul className="ss-others">
                    {rest.map((s) => (
                      <li key={s.id}>
                        <Link to={`/sim/seasons/${s.id}`}>
                          <b>{s.title ?? (s.status === 'RUNNING' ? '진행 중' : '시작 전')}</b>
                          <span>{seasonMeta(s)}</span>
                        </Link>
                      </li>
                    ))}
                  </ul>
                )}
              </article>
            )
          })}
        </section>

      </div>
    </main>
  )
}
