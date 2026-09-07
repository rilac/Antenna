/* G-03 시즌 상세 · 참가 · /sim/seasons/:id
   담당 스토리 [ANT-FE-SEASON-JOIN]
   설계서 docs/화면설계서.md §3 G-03 · §4 G-03

   구조는 프로토타입 screens/sim-contest.html 의 ct-season 블록을 옮겼다.
   그 화면은 대회 허브(시즌 카드 + 리더보드 + 규칙)였고, 그중 시즌 카드와
   참가 버튼만 이 화면의 몫이다. 리더보드는 G-07, 규칙은 아래 요약으로 둔다.

   ── 응답 계약이 명세에 비어 있다 ────────────────────────────────
   API 명세 §모의투자 의 GET /seasons/{id} 는 "200 성공" 만 적혀 있고 필드가 없다
   (목록 GET /seasons 만 { id, mode, lengthDays, initialCash, entryFee, status } 로
   정의돼 있다). 그래서 아래 SeasonDetail 은 이 화면이 그리려면 반드시 필요한
   최소 필드를 적은 것이고, 백엔드와 맞출 때 이 타입이 기준이 된다.

   ── 시기를 보여주지 않는다 ──────────────────────────────────────
   연습은 "성격(급락 구간 · 횡보 구간 같은 것)과 섹터는 고르고, 시기는 서버가
   골라 숨긴다" 로 정했다. 연도를 알려주면 답을 알고 하는 복기가 되어 예측
   연습이 안 된다. 그래서 SeasonDetail 에 baseDate·연도 필드를 두지 않는다 —
   타입에 아예 없어야 실수로 그려지지 않는다.

   ── 참가는 여기서 한다 ──────────────────────────────────────────
   POST /seasons/{id}/join · PRACTICE·DEMO 는 즉시 201, COMPETITION 은 참가비
   소각 서명을 동반해 202 → M-02 폴링(명세 §1 규칙 4). 서명 흐름은 지갑 연동에
   걸려 있어 [ANT-FE-WALLET-LINK] 뒤에 붙는다 — 지금은 대회 버튼을 막고 이유를
   적어 둔다. 연습·시연은 바로 참가해서 G-04 로 넘긴다. */
import { useState } from 'react'
import { Link, useNavigate, useParams } from 'react-router-dom'
import { api } from '../api/client'
import { ApiError } from '../api/errors'
import { useApiQuery } from '../api/useApiQuery'
import ErrorState from '../components/state/ErrorState'
import EmptyState from '../components/state/EmptyState'
import '../styles/screens/sim-join.css'

type Mode = 'PRACTICE' | 'COMPETITION' | 'DEMO'
type Status = 'SCHEDULED' | 'RUNNING' | 'CLOSED'

/** 이 화면이 필요한 최소 필드. 명세에 응답이 비어 있어 여기서 제안한다. */
type SeasonDetail = {
  id: number
  mode: Mode
  status: Status
  /** 시즌의 성격. "급락 구간" 처럼 무슨 장이었는지만 알려준다 */
  title: string
  /** 한 줄 설명 */
  note?: string
  /** 섹터 힌트. 상위 분류로만 온다 — 좁게 주면 종목이 추정된다 */
  sector?: string
  /** 총 게임일 */
  lengthDays: number
  initialCash: number
  /** 시즌 종목 수. 실명이며 시총 상위 최대 200 이다(API 명세 v0.26) */
  tickerCount: number
  /** 대회만 값이 있다. 소각하는 ANT 토큰이다 */
  entryFee?: number
  /** 대회 시간표. 연습·시연은 없다 */
  opensAt?: string
  closesAt?: string
  dayIntervalMinutes?: number
  /** 이미 참가한 시즌이면 이어하기로 바뀐다 */
  joined?: boolean
  /** 참가했을 때의 개인 진행일 */
  currentDay?: number
}

const won = (n: number) => `${n.toLocaleString('ko-KR')}원`
const ant = (n: number) => `${n.toLocaleString('ko-KR')} ANT`

const MODE_LABEL: Record<Mode, string> = {
  PRACTICE: '연습',
  COMPETITION: '대회',
  DEMO: '시연',
}

const TONE: Record<Mode, string> = {
  PRACTICE: 'learn',
  COMPETITION: 'contest',
  DEMO: 'demo',
}

const Ico = ({ children }: { children: React.ReactNode }) => (
  <svg width="22" height="22" viewBox="0 0 24 24" fill="none" stroke="currentColor"
       strokeWidth="1.7" strokeLinecap="round" strokeLinejoin="round" aria-hidden="true">
    {children}
  </svg>
)

/* 시각은 KST ISO-8601 로 온다(명세 §1 규칙 7). 시간표는 시:분만 쓴다. */
function hhmm(iso?: string) {
  if (!iso) return null
  const d = new Date(iso)
  return Number.isNaN(d.getTime())
    ? null
    : `${String(d.getHours()).padStart(2, '0')}:${String(d.getMinutes()).padStart(2, '0')}`
}

export default function SeasonJoin() {
  const { id } = useParams<{ id: string }>()
  const navigate = useNavigate()
  const season = useApiQuery<SeasonDetail>(`/seasons/${id}`)

  const [joining, setJoining] = useState(false)
  const [joinError, setJoinError] = useState<ApiError | null>(null)

  const s = season.data

  async function join() {
    if (!s || joining) return
    setJoining(true)
    setJoinError(null)
    try {
      /* 되돌릴 수 없는 POST 라 Idempotency-Key 를 붙인다(명세 §멱등성).
         같은 scope 로 재시도하면 같은 키가 나가 두 번 참가되지 않는다. */
      await api.post(`/seasons/${s.id}/join`, undefined, { idempotencyScope: `join:${s.id}` })
      navigate(`/sim/${s.id}/play`)
    } catch (e) {
      setJoinError(e instanceof ApiError ? e : null)
    } finally {
      setJoining(false)
    }
  }

  if (season.loading) {
    return (
      <main className="main">
        <div className="main-inner sim-join">
          <div className="placeholder tall">{'시즌을 불러오는 중…'}</div>
        </div>
      </main>
    )
  }

  if (season.error || !s) {
    return (
      <main className="main">
        <div className="main-inner sim-join">
          <nav className="sj-crumb" aria-label="위치">
            <Link to="/sim">모의투자 홈</Link>
            <i aria-hidden="true">›</i>
            <span>시즌 상세</span>
          </nav>
          {season.error ? (
            <ErrorState error={season.error} onRetry={season.reload} />
          ) : (
            <EmptyState
              title="시즌을 찾을 수 없습니다"
              hint="종료되었거나 삭제된 시즌일 수 있습니다"
              action={{ label: '모드 선택으로 가기', to: '/sim/modes' }}
            />
          )}
        </div>
      </main>
    )
  }

  const closed = s.status === 'CLOSED'
  const isCompetition = s.mode === 'COMPETITION'
  const opens = hhmm(s.opensAt)
  const closes = hhmm(s.closesAt)

  return (
    <main className="main">
      <div className={`main-inner sim-join ${TONE[s.mode]}`}>
        <nav className="sj-crumb" aria-label="위치">
          <Link to="/sim">모의투자 홈</Link>
          <i aria-hidden="true">›</i>
          <Link to={s.mode === 'PRACTICE' ? '/sim/practice' : '/sim/modes'}>
            {MODE_LABEL[s.mode]}
          </Link>
          <i aria-hidden="true">›</i>
          <span>시즌 상세</span>
        </nav>

        <header className="sj-head">
          <span className="sj-mode">{MODE_LABEL[s.mode]}</span>
          <h1>{s.title}</h1>
          {s.note && <p>{s.note}</p>}
        </header>

        {/* 시즌 카드 — 프로토타입 ct-season */}
        <section className="sj-card" aria-label="시즌 조건">
          <div className="sj-card-main">
            <dl className="sj-stats">
              <div>
                <Ico>
                  <rect x="3" y="4.5" width="18" height="16" rx="2" />
                  <path d="M8 3v3M16 3v3M3 10h18" />
                </Ico>
                <div>
                  <dt>기간</dt>
                  <dd><b className="num">{s.lengthDays}</b>게임일</dd>
                </div>
              </div>
              <div>
                <Ico>
                  <rect x="2.5" y="6" width="19" height="12" rx="2" />
                  <circle cx="12" cy="12" r="2.5" />
                </Ico>
                <div>
                  <dt>초기 예수금</dt>
                  <dd><b className="num">{won(s.initialCash)}</b></dd>
                </div>
              </div>
              <div>
                <Ico>
                  <path d="M3 20h18" /><path d="M6 20v-7M11 20v-11M16 20v-5" />
                </Ico>
                <div>
                  <dt>종목</dt>
                  <dd><b className="num">{s.tickerCount}</b>개</dd>
                </div>
              </div>
              {s.sector && (
                <div>
                  <Ico>
                    <path d="M4 20V9l8-5 8 5v11" />
                    <path d="M9 20v-6h6v6" />
                  </Ico>
                  <div>
                    <dt>섹터</dt>
                    <dd><b>{s.sector}</b></dd>
                  </div>
                </div>
              )}
            </dl>

            {/* 대회 시간표 — 명세 §2 모의투자, 연습·시연은 값이 없다 */}
            {isCompetition && (opens || closes || s.dayIntervalMinutes) && (
              <p className="sj-schedule">
                <Ico>
                  <circle cx="12" cy="12" r="9" /><path d="M12 7v5.5l3.5 2" />
                </Ico>
                {opens && closes && <span>{`${opens} ~ ${closes} 진행`}</span>}
                {s.dayIntervalMinutes && (
                  <span>{`${s.dayIntervalMinutes}분마다 1게임일`}</span>
                )}
              </p>
            )}
          </div>

          <div className="sj-cta-wrap">
            {closed ? (
              <>
                <span className="sj-cta is-off" aria-disabled="true">종료된 시즌</span>
                <p className="sj-cta-note">기록은 결과 화면에서 볼 수 있습니다</p>
              </>
            ) : s.joined ? (
              <>
                <Link className="sj-cta" to={`/sim/${s.id}/play`}>
                  이어서 하기<em aria-hidden="true">›</em>
                </Link>
                <p className="sj-cta-note">
                  {s.currentDay ? `${s.currentDay}게임일까지 진행했습니다` : '진행 중인 시즌입니다'}
                </p>
              </>
            ) : isCompetition ? (
              /* 참가비 소각 서명이 필요해 지갑 연동에 걸려 있다.
                 눌러도 서명할 곳이 없어 막아 두고 이유를 적는다. */
              <>
                <span className="sj-cta is-off" aria-disabled="true">대회 참가하기</span>
                <p className="sj-cta-note">
                  {s.entryFee
                    ? `참가비 ${ant(s.entryFee)} 소각 · 지갑 서명이 필요합니다`
                    : '지갑 서명이 필요합니다'}
                </p>
              </>
            ) : (
              <>
                <button className="sj-cta" type="button" onClick={join} disabled={joining}>
                  {joining ? '참가하는 중…' : '시작하기'}
                  {!joining && <em aria-hidden="true">›</em>}
                </button>
                <p className="sj-cta-note">참가비 없이 바로 시작합니다</p>
              </>
            )}
          </div>
        </section>

        {joinError && <ErrorState error={joinError} onRetry={join} inline />}

        {/* 진행 규칙 요약 — 명세 §2·설계서 §4 G-04 에서 확정된 것만 적는다 */}
        <section className="sj-rules" aria-labelledby="sj-rules-title">
          <h2 id="sj-rules-title">진행 방식</h2>
          <ul>
            <li>
              <b>시기는 공개되지 않습니다.</b> 어떤 장이었는지만 알려주고 실제 날짜는
              가립니다. 종목은 실명이며, 그 구간의 대형주가 전부 들어 있습니다.
            </li>
            <li>
              <b>주문은 그 게임일 종가로 한 번에 체결됩니다.</b> 수수료·슬리피지·예약
              주문이 없고, 수량과 방향만 정합니다.
            </li>
            <li>
              {isCompetition ? (
                <>
                  <b>게임일은 시간이 지나면 자동으로 넘어갑니다.</b> 모든 참가자가 같은
                  게임일을 같은 시각에 보며, 직접 넘길 수 없습니다.
                </>
              ) : (
                <>
                  <b>게임일은 직접 넘깁니다.</b> 중단해도 진행하던 게임일부터 이어서
                  할 수 있습니다.
                </>
              )}
            </li>
            <li>
              {isCompetition ? (
                <>성과가 집계되어 리더보드에 오릅니다.</>
              ) : (
                <>연습 성과는 <b>실전 랭킹과 신뢰도에 반영되지 않습니다.</b></>
              )}
            </li>
          </ul>
        </section>
      </div>
    </main>
  )
}
