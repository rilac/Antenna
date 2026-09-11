/* C-03 예측 상세 · /predictions/:id
   담당 스토리 [ANT-FE-PREDICT-DETAIL]
   설계서 docs/화면설계서.md §3 C · §4 C-03 · §5 · §7

   이 화면의 일은 "조작하지 않았다" 를 보여주는 것이다. 목표·기준가·진행률로
   무엇을 걸었는지 밝히고, 커밋 해시·앵커·서명 주소로 그것이 나중에 바뀌지
   않았음을 드러내고, 검산(D-03)으로 넘겨 직접 확인하게 한다.

   설계 제약
   - 잠금이 두 겹이다(§5). ① 미판정 + 작성자·구독자 아님 → 예측 내용 잠금
     ② 미구독 → 근거 본문 잠금. ②는 **만기 리빌 후에도 풀리지 않는다** —
     payload 에 noteHash 만 들어가므로 애초에 공개 대상이 아니다.
   - **커밋 해시 · 앵커 · 서명 주소는 언제나 공개다.** 잠금 대상이 아니고,
     잠긴 화면에서도 그린다 — 이 셋이 이 화면의 존재 이유다.
   - 404 로 감추지 않는다. 존재 자체는 공개이며 잠금 카드와 구독 CTA 로 그린다.
   - 잠금 표현은 SubscriptionGate 를 쓴다. 화면마다 게이팅 분기를 새로 짜지 않는다.
   - 수정·삭제가 없다(§4 C-02 와 같은 이유). 되돌릴 수 없는 기록이다.

   진행률은 기준가·목표가·전일 종가로 계산한다(targetProgress). 실전 시세는
   전일 종가뿐이라 "현재가" 를 만들지 않고 기준일을 함께 적는다(§7 legal). */
import { Link, useLocation, useNavigate, useParams } from 'react-router-dom'
import AnchorBadge from '../components/AnchorBadge'
import PredictionStatus from '../components/prediction/PredictionStatus'
import ErrorState from '../components/state/ErrorState'
import SubscriptionGate from '../components/state/SubscriptionGate'
import { isSubscriptionGated } from '../api/errors'
import { PROOF_STATUS_LABEL, fetchAnchorStatus } from '../api/proof'
import { useBlock } from '../api/useBlock'
import { getPredictionDetail, targetProgress, phaseOf, HORIZON_LABEL } from '../api/predictions'
import { getProfile } from '../api/stockDetail'
import '../styles/screens/predict-detail.css'

const won = (n: number) => `${n.toLocaleString('ko-KR')}원`
const dot = (iso: string) => iso.slice(0, 10).replace(/-/g, '.')

/** 뒤로 표시. 버튼일 때와 링크일 때가 같은 모양이어야 해서 한 군데 둔다 */
function BackArrow() {
  return (
    <svg width="15" height="15" viewBox="0 0 24 24" fill="none" stroke="currentColor"
         strokeWidth="2.6" strokeLinecap="round" strokeLinejoin="round" aria-hidden="true">
      <path d="M15 5 8 12l7 7" />
    </svg>
  )
}

/** 목표까지의 진행 막대. 100 을 넘기면(목표 통과) 막대를 가득 채우고 값으로 알린다 */
function Progress({ value }: { value: number }) {
  const clamped = Math.max(0, Math.min(100, value))
  const passed = value >= 100
  return (
    <div className="pd-progress">
      <div
        className={passed ? 'pd-bar is-passed' : 'pd-bar'}
        role="img"
        aria-label={`목표까지 ${value}퍼센트 진행`}
      >
        <i style={{ width: `${clamped}%` }} />
      </div>
      <p className="pd-bar-val num">
        <b>{`${value}%`}</b>
        <span>{passed ? '목표를 지났습니다' : '목표까지'}</span>
      </p>
    </div>
  )
}

export default function PredictDetail() {
  const { id = '' } = useParams<{ id: string }>()
  const q = useBlock(() => getPredictionDetail(id), [id])
  const d = q.data

  /* ── 뒤로 가기 ────────────────────────────────────────
     이 화면은 내 예측 · 종목 상세 · 랭킹 · 채널 · 피드 여러 곳에서 열린다.
     보던 자리로 돌려보내는 것이 맞다 — 어디서 왔든 그게 사용자가 기대하는 곳이다.

     다만 -1 이 늘 되는 것은 아니다. 공유 링크로 이 화면이 **첫 화면**이면
     뒤에 아무것도 없어서 -1 은 이 앱 밖으로 나가거나 아무 일도 하지 않는다.
     react-router 는 그 첫 항목의 location.key 를 'default' 로 준다. 그때만
     종목의 예측 목록으로 보낸다 — 누구의 예측이든 항상 맞는 자리다. */
  const navigate = useNavigate()
  const cameFromApp = useLocation().key !== 'default'

  /* 앵커 상태만은 실제 서버에서 읽는다. GET /predictions/{id}/proof 는 이미 열려
     있고(ANT-CHAIN-06), anchorStatus 하나로 WAITING·PENDING·CONFIRMED·FAILED 를
     내려 준다 — 상세 본문이 아직 목업이어도 이 값은 참이다.

     숫자 id 일 때만 묻는다. 서버 예측 id 는 long 이라 목업 픽스처의 'p2' 같은
     id 로 부르면 400 이 난다. GET /predictions/{id} 가 붙어 상세가 실제
     데이터가 되면 이 갈래는 사라진다. */
  const isServerId = /^\d+$/.test(id)
  const proof = useBlock(() => fetchAnchorStatus(id), [id], isServerId)

  /* 종목명이 비어 오면 종목 개요에서 가져온다.

     제목에 코드만 뜨면("000080") 무슨 종목인지 알 수 없다. 서버가 붙으면
     stockName 이 실려 오므로 이 호출은 일어나지 않는다 — 이름이 없을 때만 부른다.
     지금은 목업이 이름을 모른다(예측 목록 응답에 종목명이 없어서다). 나중에도
     종목이 지워진 건은 이름이 비는데, 그때도 코드보다는 이 값이 낫다. */
  const code = q.data?.stockCode ?? null
  const needName = q.data != null && q.data.stockName === null && code !== null
  const profile = useBlock(() => getProfile(code as string), [code], needName)

  /* 403 은 잠금이라 SubscriptionGate 가 맡는다. 그 밖의 오류만 ErrorState 로 —
     둘을 합치면 서버가 죽은 것과 구독이 없는 것이 같은 화면으로 보인다. */
  if (q.error && !isSubscriptionGated(q.error)) {
    return (
      <main className="main">
        <div className="main-inner"><ErrorState error={q.error} onRetry={q.retry} /></div>
      </main>
    )
  }

  /* 403 이면 본문이 없다. 그때도 없는 것처럼 감추지 않고 잠금과 CTA 를 그린다.
     다만 어느 채널을 구독해야 하는지는 응답이 없어 알 수 없다 — 서버가 200 +
     locked 로 내려 주면 채널까지 안내할 수 있다(api/predictions.ts 요청 목록). */
  if (!d) {
    return (
      <main className="main">
        <div className="main-inner">
          {isSubscriptionGated(q.error) ? (
            <SubscriptionGate locked label="이 예측">
              {null}
            </SubscriptionGate>
          ) : (
            <div className="pd-skel" aria-hidden="true">
              <span /><span /><span />
            </div>
          )}
        </div>
      </main>
    )
  }

  /* 이름 → 개요에서 받은 이름 → 코드 순. 코드는 항상 온다 */
  const stock = d.stockName ?? profile.data?.corpName ?? d.stockCode

  /* 진행률은 미판정 건에서만 뜻이 있다. 판정이 끝나면 답은 오차율이고, 진행 막대는
     같은 이야기를 한 번 더 하면서 "아직 가는 중" 처럼 읽히게 만든다.
     같은 이유로 기준일 문구도 뺀다 — 만기 종가와 같은 값이라 중복이다. */
  const pending = phaseOf(d.status) === 'PENDING'
  const progress = pending ? targetProgress(d) : null

  return (
    <main className="main">
      <div className="main-inner">
        <header className="pd-head">
          {/* 판단 근거는 위 cameFromApp 주석에 있다. 앱 안에서 왔으면 보던 자리로,
              링크로 바로 들어왔으면 그 종목의 예측 목록으로 보낸다.
              돌아갈 곳을 모르므로 문구는 "돌아가기" 로 둔다 — 어디로 가는지 모르면서
              "종목 예측" 이라고 적으면 틀린 말이 된다. */}
          {cameFromApp ? (
            <button type="button" className="pd-back" onClick={() => navigate(-1)}>
              <BackArrow />
              돌아가기
            </button>
          ) : (
            <Link className="pd-back" to={`/stocks/${d.stockCode}?tab=predict`}>
              <BackArrow />
              {`${stock} 예측`}
            </Link>
          )}

          <div className="pd-title">
            <h1>
              {/* 종목 상세가 아니라 **그 종목의 예측 목록**으로 간다 — 예측을 보던
                  사람이 종목명을 누르는 것은 "이 종목에 누가 뭘 걸었나" 를 보려는
                  것이다. 종목 개요는 거기서 탭 하나 옮기면 된다. */}
              <Link to={`/stocks/${d.stockCode}?tab=predict`}>{stock}</Link>
            </h1>
            {/* 제목이 코드로 떨어졌으면 코드를 한 번 더 그리지 않는다 */}
            {stock !== d.stockCode && <span className="pd-code num">{d.stockCode}</span>}
            <PredictionStatus status={d.status} />
          </div>

          <p className="pd-by">
            <Link to={`/channels/${d.author.userId}`}>{d.author.nickname}</Link>
            <span className="num">{`${dot(d.createdAt)} 등록 · ${HORIZON_LABEL[d.horizon]}`}</span>
          </p>
        </header>

        <div className="pd-grid">
          {/* ── 예측 내용 · 진행률 ─────────────────────────
              **잠기지 않는다**(2026-09-08 결정). 방향·목표가·기준가·만기·진행률은
              누구에게나 보인다 — 예측가를 고를 근거가 되어야 하기 때문이다.
              구독으로 사는 것은 판단의 이유(아래 근거 본문)뿐이다. */}
          <section className="pd-card is-main">
            <h2>예측 내용</h2>
            <dl className="pd-figures">
              <div>
                <dt>방향</dt>
                <dd className={d.direction === 'UP' ? 'up' : 'down'}>
                  {d.direction === 'UP' ? '상승' : '하락'}
                </dd>
              </div>
              <div>
                <dt>목표가</dt>
                <dd className="num">{d.targetPrice === null ? '—' : won(d.targetPrice)}</dd>
              </div>
              <div>
                <dt>기준가</dt>
                {/* 배치 B2 가 다음 영업일 종가로 확정한다. 빈 값을 0 으로 그리지 않는다 */}
                <dd className="num">
                  {d.basePrice === null
                    ? <span className="pd-pending">다음 영업일 종가로 확정</span>
                    : won(d.basePrice)}
                </dd>
              </div>
              <div>
                <dt>{d.settlePrice === null ? '만기' : '만기 종가'}</dt>
                <dd className="num">
                  {d.settlePrice === null ? dot(d.settleDate) : won(d.settlePrice)}
                </dd>
              </div>
            </dl>

            {progress !== null && <Progress value={progress} />}

            {/* 전일 종가로 진행률을 그렸다는 사실을 밝힌다 — "현재가" 가 아니다 */}
            {pending && d.lastClose && (
              <p className="pd-basis num">
                {`${dot(d.lastClose.asOf)} 종가 ${won(d.lastClose.close)} 기준`}
              </p>
            )}

            {d.errorRate !== null && (
              <p className="pd-error num">
                <span>목표가 대비 오차</span>
                <b className={Math.abs(d.errorRate) <= 3 ? 'is-near' : undefined}>
                  {`${d.errorRate > 0 ? '+' : ''}${d.errorRate}%`}
                </b>
              </p>
            )}

            {d.dday !== null && (
              <p className="pd-dday num">{d.dday === 0 ? '오늘 만기' : `만기까지 D-${d.dday}`}</p>
            )}
          </section>

          {/* ── 조작 불가 근거 ─────────────────────────────
              커밋 해시·앵커·서명 주소는 언제나 공개다(§4 C-03). 잠긴 예측에서도
              그린다 — 내용을 못 봐도 "바뀌지 않았다" 는 확인할 수 있어야 한다. */}
          <section className="pd-card">
            <h2>조작 불가 근거</h2>
            <p className="pd-hint">
              등록 시점에 남긴 값입니다. 커밋 해시는 서버 salt 와 결합해 만들어지며, 앵커가
              확정되면 블록체인에 올라가 뒤에서 바꿀 수 없습니다.
            </p>

            <dl className="pd-proof">
              <div>
                <dt>커밋 해시</dt>
                <dd className="num">{d.commitHash}</dd>
              </div>
              <div>
                <dt>서명 주소</dt>
                <dd className="num">{d.signerAddress}</dd>
              </div>
              <div>
                <dt>앵커</dt>
                <dd>
                  {/* 실제 증명이 있으면 그것으로 그린다. 목업 상세로 들어온
                      경우(숫자가 아닌 id)에만 픽스처 값으로 물러난다. */}
                  {proof.data ? (
                    proof.data.anchor === null ? (
                      /* 아직 배치에 안 들어갔다. 서버가 anchor 를 null 로 두고
                         상태값 하나로 대기를 말한다 — 없는 배치 번호를 만들지 않는다 */
                      <span className="pd-pending">
                        {PROOF_STATUS_LABEL[proof.data.anchorStatus]}
                      </span>
                    ) : (
                      <span className="pd-anchor">
                        <AnchorBadge status={proof.data.anchor.status} />
                        <Link className="pd-anchor-link" to={`/ledger/anchors/${proof.data.anchor.batchId}`}>
                          {`배치 #${proof.data.anchor.batchId}`}
                        </Link>
                        {proof.data.anchor.blockNumber !== null && (
                          <span className="num">
                            {`블록 ${proof.data.anchor.blockNumber.toLocaleString('ko-KR')}`}
                          </span>
                        )}
                      </span>
                    )
                  ) : d.anchor === null ? (
                    <span className="pd-pending">아직 배치에 묶이지 않았습니다</span>
                  ) : (
                    <span className="pd-anchor">
                      <AnchorBadge status={d.anchor.status} />
                      <Link className="pd-anchor-link" to={`/ledger/anchors/${d.anchor.id}`}>
                        {`배치 #${d.anchor.id}`}
                      </Link>
                      {d.anchor.blockNumber !== null && (
                        <span className="num">{`블록 ${d.anchor.blockNumber.toLocaleString('ko-KR')}`}</span>
                      )}
                    </span>
                  )}
                </dd>
              </div>
            </dl>

            {/* 검산 진입점. 이 화면의 신뢰가 거기서 완성된다(티켓) */}
            <Link className="pd-verify" to={`/ledger/verify/${d.id}`}>
              3단계로 직접 검산하기
              <svg width="15" height="15" viewBox="0 0 24 24" fill="none" stroke="currentColor"
                   strokeWidth="2.4" strokeLinecap="round" strokeLinejoin="round" aria-hidden="true">
                <path d="m9 6 6 6-6 6" />
              </svg>
            </Link>
          </section>

          {/* ── 근거 포인트 ────────────────────────────────
              C-01 에서 인계받아 커밋에 묶인 값이다. 등록 후 바뀌지 않는다 */}
          {d.evidencePoints.length > 0 && (
            <section className="pd-card">
              <h2>고른 근거</h2>
              <ul className="pd-points">
                {d.evidencePoints.map((p) => <li key={p.id}>{p.body}</li>)}
              </ul>
            </section>
          )}

          {/* ── 근거 본문 ──────────────────────────────────
              항상 구독자 전용이다. 만기가 지나도 풀리지 않는다 */}
          <section className="pd-card">
            <h2>
              {'작성자의 판단 '}
              <span>구독자 전용</span>
            </h2>
            <SubscriptionGate
              locked={d.noteLocked}
              label="근거 본문"
              channelId={d.channelId}
            >
              {d.note === null
                ? <p className="pd-hint">작성자가 판단을 남기지 않았습니다.</p>
                : <p className="pd-note">{d.note}</p>}
            </SubscriptionGate>
          </section>
        </div>
      </div>
    </main>
  )
}
