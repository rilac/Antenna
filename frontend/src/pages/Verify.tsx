/* D-03 3단계 검산 · /ledger/verify/:predictionId
   담당 스토리 [ANT-FE-VERIFY]
   설계서 docs/화면설계서.md §3 D · §4 D-03

   설계 제약
   - "전부 클라이언트 실행 — 서버가 '검증됨'이라 말해주면 증명이 아니다."
     그래서 화면 어디에도 서버가 내린 판정을 그대로 옮겨 적는 자리가 없다.
     ②의 판정은 브라우저가 접은 루트와 체인이 답한 루트를 여기서 직접 비교해 만든다.
   - 잠긴 단계는 실패가 아니라 아직이다 — 붉게 그리지 않는다.

   ①이 이제 만기 전에도 돈다 (ANT-PRED-02)
   설계서는 "만기 전에는 salt 가 null 이라 ①을 잠근다" 였는데, 09-09 결정으로 커밋 salt
   자체가 없어졌다. 커밋 문자열은 공개 필드 넷 + payload.noteHash 뿐이고 그 다섯은 커밋이
   생긴 순간부터 전원에게 온다. 그래서 ①은 만기·구독과 무관하게, 커밋만 있으면 계산된다.
   잠기는 경우는 하나뿐이다 — 아직 커밋 전이라 재료가 없을 때.

   남은 한 겹(①')은 일부러 사용자 손에 남긴다
   구독자·작성자는 리빌 뒤 salt 를 받아 keccak256(note ‖ salt) == noteHash 를 더 볼 수
   있는데, 근거 본문은 이 응답에 없다(GET /predictions/{id} 쪽이다). 우리가 본문을 대신
   불러다 대조해 "맞습니다" 라고 하면 ③과 똑같이 다시 우리를 믿는 일이 된다.

   ②를 자동으로 실행하는 이유
   버튼 뒤에 두면 대부분은 누르지 않고, 그러면 이 화면은 다시 "서버가 준 값을 보여주는
   화면" 이 된다. 검산이 이 화면의 본문이므로 들어오면 바로 돈다. */
import { useCallback, useEffect, useMemo, useRef, useState } from 'react'
import { Link, useNavigate, useParams } from 'react-router-dom'
import { formatConfirmedAt } from '../api/anchors'
import type { ApiError } from '../api/errors'
import {
  DIRECTION_LABEL, SETTLE_LABEL, commitPreimage, fetchProof, recomputeCommitHash,
  type Proof, type ProofAnchor,
} from '../api/proof'
import { useAsync } from '../api/useAsync'
import { foldRoot, readChain, type ChainRead } from '../chain/commitAnchor'
import AnchorBadge from '../components/AnchorBadge'
import CopyHash from '../components/CopyHash'
import ErrorState from '../components/state/ErrorState'
import '../styles/screens/verify.css'

/* ── 검산 결과 한 줄 ──────────────────────────────────────
   pass·fail 을 색으로만 나누지 않는다(설계서 §7 과 같은 규칙). 흑백으로 캡쳐해도,
   색각 이상이 있어도 읽혀야 한다. wait 는 "아직" 이지 실패가 아니라 중립색을 쓴다.

   manual 은 나머지와 성격이 다르다 — 기계가 판정을 못 해서가 아니라 하면 안 되는
   자리다(③). 우리가 원본을 대신 불러다 "맞습니다" 라고 하면 다시 우리를 믿는 것이 되므로
   대조는 사용자가 한다. 그래서 체크도 시계도 아닌 별도 표시를 쓴다. */
type Verdict = 'pass' | 'fail' | 'wait' | 'locked' | 'manual'

const VERDICT_ICON: Record<Verdict, React.ReactNode> = {
  pass: <path d="m5 12.5 4.5 4.5L19 7.5" />,
  fail: <><path d="M6 6l12 12" /><path d="M18 6 6 18" /></>,
  wait: <><circle cx="12" cy="12" r="9" /><path d="M12 7.5V12l3 1.8" /></>,
  locked: <><rect x="4.5" y="10.5" width="15" height="10" rx="2" /><path d="M8 10.5V7a4 4 0 0 1 8 0v3.5" /></>,
  // 바깥으로 나가는 화살표 — 확인할 곳이 이 화면 밖이라는 뜻
  manual: <><path d="M14 5h5v5" /><path d="M19 5l-8 8" /><path d="M18 14v4a2 2 0 0 1-2 2H6a2 2 0 0 1-2-2V8a2 2 0 0 1 2-2h4" /></>,
}

const VERDICT_SR: Record<Verdict, string> = {
  pass: '통과', fail: '불일치', wait: '대기', locked: '잠김', manual: '직접 확인',
}

function CheckLine({ verdict, label, children }: {
  verdict: Verdict
  label: string
  children?: React.ReactNode
}) {
  return (
    <li className={`vf-check is-${verdict}`}>
      <span className="vf-check-mark" aria-hidden="true">
        <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2.2" strokeLinecap="round" strokeLinejoin="round">
          {VERDICT_ICON[verdict]}
        </svg>
      </span>
      <span className="sr-only">{`${VERDICT_SR[verdict]} — `}</span>
      <span className="vf-check-body">
        <b>{label}</b>
        {children && <span className="vf-check-detail">{children}</span>}
      </span>
    </li>
  )
}

function Step({ no, title, formula, verdict, children }: {
  no: number
  title: string
  formula?: string
  verdict: Verdict
  children: React.ReactNode
}) {
  return (
    <section className={`vf-step is-${verdict}`}>
      <header className="vf-step-head">
        <span className="vf-step-no num" aria-hidden="true">{no}</span>
        <div className="vf-step-title">
          <h2>{title}</h2>
          {formula && <code className="vf-formula">{formula}</code>}
        </div>
      </header>
      <div className="vf-step-body">{children}</div>
    </section>
  )
}

/* ── ① 커밋 해시 재계산 ──────────────────────────────────
   서버가 준 것은 재료(payload)와 주장(commitHash)이다. 둘을 잇는 계산은 여기서 한다.
   keccak256 이 ethers 동적 import 라 비동기이고, 그래서 상태로 들고 있는다. */
type Recompute = {
  /** 다시 조립한 커밋 문자열. 재료가 덜 오면 null — 그때는 계산 자체를 하지 않는다 */
  preimage: string | null
  /** 그 문자열의 keccak256. 계산 중이면 null */
  hash: string | null
}

function useCommitCheck(payload: Proof['payload'] | null): Recompute {
  /* 조립은 순수 계산이라 그리는 김에 한다. 상태로 두면 첫 그림에는 없다가 효과가
     한 번 더 그리게 만든다 — 화면에 남는 것은 같은데 렌더가 늘 뿐이다. */
  const preimage = useMemo(() => (payload ? commitPreimage(payload) : null), [payload])

  /* 해시만 비동기다(ethers 동적 import). 어느 문자열의 해시인지 함께 들고 있는 이유는
     PredictTab 미리보기와 같다 — 늦게 온 이전 계산이 지금 문자열의 값처럼 보이면 안 된다. */
  const [done, setDone] = useState<{ preimage: string; hash: string } | null>(null)
  useEffect(() => {
    if (!preimage) return
    let alive = true
    void recomputeCommitHash(preimage).then((hash) => { if (alive) setDone({ preimage, hash }) })
    return () => { alive = false }
  }, [preimage])

  return { preimage, hash: done?.preimage === preimage ? done.hash : null }
}

/* ── ② 브라우저 검산 ─────────────────────────────────────
   접기(로컬 계산)와 체인 조회(네트워크)를 나눠 담는다. 체인에 못 붙어도 접기 결과는
   보여줄 수 있고, 서버 응답이 스스로 앞뒤가 맞는지는 그것만으로 확인되기 때문이다. */
type Check = {
  /** 브라우저가 proof 를 접어 복원한 루트 */
  localRoot: string | null
  chain: ChainRead | null
  error: ApiError | null
  running: boolean
}

const CHECK_START: Check = { localRoot: null, chain: null, error: null, running: true }

function useChainCheck(anchor: ProofAnchor | null, commitHash: string | null) {
  const [state, setState] = useState<Check>(CHECK_START)
  const [attempt, setAttempt] = useState(0)
  /* 늦게 온 이전 조회가 최신 결과를 덮지 않게 세대를 센다 — useAsync 와 같은 장치다 */
  const gen = useRef(0)

  useEffect(() => {
    if (!anchor || !commitHash) return
    const mine = ++gen.current
    void (async () => {
      try {
        // 접기가 먼저다. 체인 왕복을 기다리지 않고 ②-1 부터 화면에 뜬다.
        const localRoot = await foldRoot(commitHash, anchor.merkleProof)
        if (mine === gen.current) setState((s) => ({ ...s, localRoot }))

        // 서버가 준 루트가 아니라 내가 접은 루트로 묻는다 — 체인에 박혀 있으면 서버 값을 믿을 필요가 없다(v3, ANT-CHAIN-13)
        const chain = await readChain(anchor.contractAddress, localRoot, commitHash, anchor.merkleProof)
        if (mine === gen.current) setState((s) => ({ ...s, chain, running: false }))
      } catch (e) {
        if (mine === gen.current) setState((s) => ({ ...s, error: e as ApiError, running: false }))
      }
    })()
  }, [anchor, commitHash, attempt])

  return {
    ...state,
    retry: () => {
      setState(CHECK_START)
      setAttempt((n) => n + 1)
    },
  }
}

/** 해시 비교는 대소문자를 가리지 않는다 — 같은 값이 0xAB 와 0xab 로 올 수 있다. */
function sameHash(a: string | null | undefined, b: string | null | undefined) {
  return !!a && !!b && a.toLowerCase() === b.toLowerCase()
}

function won(value: number | null) {
  return value === null ? '—' : `${value.toLocaleString('ko-KR')}원`
}

export default function Verify() {
  const { predictionId } = useParams<{ predictionId: string }>()
  const navigate = useNavigate()

  const load = useCallback(() => fetchProof(predictionId ?? ''), [predictionId])
  const { data, loading, error, reload } = useAsync<Proof>(load)

  const check = useChainCheck(data?.anchor ?? null, data?.commitHash ?? null)
  const commit = useCommitCheck(data?.payload ?? null)

  /* 커밋 원장에서도, 예측 상세에서도 들어온다 — 어느 쪽으로 돌려보낼지 화면이 정할 수 없다.
     직접 주소로 들어와 돌아갈 곳이 없을 때만 커밋 원장으로 보낸다. */
  const canGoBack = window.history.length > 1

  const anchor = data?.anchor ?? null
  const settle = data?.settle ?? null

  return (
    <main className="main">
      <div className="main-inner vf">
        {canGoBack ? (
          <button type="button" className="vf-back" onClick={() => navigate(-1)}>
            <svg width="15" height="15" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round"><path d="M15 18l-6-6 6-6" /></svg>
            뒤로
          </button>
        ) : (
          <Link className="vf-back" to="/ledger">
            <svg width="15" height="15" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round"><path d="M15 18l-6-6 6-6" /></svg>
            커밋 원장
          </Link>
        )}

        <div className="page-head">
          <h1>3단계 검산</h1>
          {/* 이 화면이 무엇을 주장하는지 먼저 밝힌다. 아래 판정들은 우리가 내린 것이 아니다 */}
          <p>
            아래 계산은 전부 이 브라우저에서 실행됩니다.
            서버가 <b>&ldquo;검증됨&rdquo;</b>이라고 답해 준 값은 하나도 쓰지 않습니다.
          </p>
        </div>

        {error && <ErrorState error={error} onRetry={reload} />}
        {loading && !data && <p className="vf-loading">검산 재료를 불러오는 중…</p>}

        {data && (
          <>
            {/* ── 무엇을 검산하는가 ───────────────────────── */}
            <section className="vf-subject">
              <div className="vf-subject-main">
                <span className="vf-subject-code num">{data.payload.stockCode ?? '—'}</span>
                <span className={`vf-dir is-${data.payload.direction.toLowerCase()}`}>
                  {DIRECTION_LABEL[data.payload.direction]}
                </span>
                <span className="vf-subject-target num">{won(data.payload.targetPrice)}</span>
                <span className="vf-subject-h">{`${data.payload.horizon}영업일`}</span>
              </div>
              <p className="vf-subject-sub">
                {`예측 #${data.predictionId} · 등록 ${formatConfirmedAt(data.payload.createdAt)}`}
                {data.signerAddress && ` · 서명 ${data.signerAddress.slice(0, 8)}…${data.signerAddress.slice(-6)}`}
              </p>
            </section>

            {/* ── ① 커밋 해시 대조 ────────────────────────── */}
            <Step
              no={1}
              title="커밋 해시 대조"
              formula="keccak256(커밋 문자열) == commitHash"
              verdict={step1Verdict(data, commit)}
            >
              <p className="vf-lead">
                등록 당시의 예측 내용을 정해진 순서로 이어 붙인 것이 커밋 문자열이고,
                그것을 해시한 값이 커밋 해시다. 브라우저가 아래 문자열을 다시 해시해
                서버가 말한 커밋 해시와 맞춰 본다 — 같으면 등록 뒤 내용이 바뀌지 않았다는 뜻이다.
              </p>

              <ul className="vf-checks">
                {!data.commitHash ? (
                  <CheckLine verdict="locked" label="아직 커밋 전입니다">
                    등록이 원장에 봉인되면 이 자리에서 바로 계산해 보여 드립니다.
                  </CheckLine>
                ) : !commit.preimage ? (
                  /* 재료가 덜 왔다. 빈칸을 채워 해시하면 반드시 불일치가 나오고,
                     그 붉은 표시는 "네 예측이 조작됐다" 로 읽힌다 — 계산하지 않는다. */
                  <CheckLine verdict="locked" label="커밋 문자열을 다시 만들 재료가 오지 않았습니다">
                    종목코드와 근거 해시가 있어야 등록 당시의 문자열을 복원할 수 있습니다.
                  </CheckLine>
                ) : !commit.hash ? (
                  <CheckLine verdict="wait" label="브라우저에서 다시 해시하는 중…" />
                ) : (
                  <>
                    <CheckLine
                      verdict={sameHash(commit.hash, data.commitHash) ? 'pass' : 'fail'}
                      label={sameHash(commit.hash, data.commitHash)
                        ? '브라우저가 계산한 해시가 서버가 말한 커밋 해시와 같습니다'
                        : '브라우저가 계산한 해시가 서버가 말한 커밋 해시와 다릅니다'}
                    >
                      <CopyHash value={commit.hash} head={14} tail={10} />
                    </CheckLine>

                    {/* 한 겹 더는 사용자 몫이다(③과 같은 이유) — 근거 본문이 이 응답에 없다 */}
                    {data.salt && (
                      <CheckLine verdict="manual" label="근거 본문 대조는 직접 하실 수 있습니다">
                        아래 근거 salt 를 자기 근거 본문 뒤에 그대로 붙여 keccak256 하면
                        위 문자열의 <code>noteHash</code> 가 나와야 합니다.
                      </CheckLine>
                    )}
                  </>
                )}
              </ul>

              {/* 재계산에 쓴 문자열을 그대로 편다. 이걸 감추면 "우리가 계산해 봤더니
                  맞더라" 가 되어 이 화면이 없애려던 것으로 되돌아간다. */}
              {commit.preimage && (
                <pre className="vf-preimage">{commit.preimage}</pre>
              )}

              <dl className="vf-fields">
                <div>
                  <dt>커밋 해시</dt>
                  <dd>{data.commitHash ? <CopyHash value={data.commitHash} head={14} tail={10} /> : <span className="vf-none">아직 커밋 전</span>}</dd>
                </div>
                <div>
                  <dt>근거 salt</dt>
                  <dd>{data.salt
                    ? <CopyHash value={data.salt} head={12} tail={8} />
                    : <span className="vf-none">리빌 전이거나 구독자가 아닙니다</span>}</dd>
                </div>
                {data.revealedAt && (
                  <div>
                    <dt>근거 공개</dt>
                    <dd>{formatConfirmedAt(data.revealedAt)}</dd>
                  </div>
                )}
              </dl>
            </Step>

            {/* ── ② 배치 포함 증명 — 이 화면에서 실제로 도는 단계 ── */}
            <Step
              no={2}
              title="배치 포함 증명"
              formula="anchoredAt(fold(proof)) > 0"
              verdict={step2Verdict(data, check)}
            >
              <p className="vf-lead">
                커밋은 하루치를 묶어 머클 트리로 접은 뒤 루트만 체인에 올립니다.
                형제 해시 목록(proof)을 순서대로 접어 그 루트가 나오면, 이 커밋이 그날 배치에
                들어 있었다는 뜻입니다.
              </p>

              {!anchor ? (
                <ul className="vf-checks">
                  <CheckLine verdict="locked" label="아직 앵커 배치에 담기지 않았습니다">
                    앵커 배치는 하루에 한 번 돕니다. 오늘 등록한 예측은 다음 배치에 담깁니다.
                  </CheckLine>
                </ul>
              ) : (
                <>
                  <ul className="vf-checks">
                    {/* ②-1 로컬 계산 */}
                    {check.localRoot ? (
                      <CheckLine verdict="pass" label={`형제 해시 ${anchor.merkleProof.length}칸을 브라우저에서 접었습니다`}>
                        <CopyHash value={check.localRoot} head={14} tail={10} />
                      </CheckLine>
                    ) : (
                      <CheckLine verdict="wait" label="proof 를 접는 중…" />
                    )}

                    {/* ②-2 서버 응답이 스스로 앞뒤가 맞는가. 체인 없이도 확인된다 */}
                    {check.localRoot && (
                      <CheckLine
                        verdict={sameHash(check.localRoot, anchor.merkleRoot) ? 'pass' : 'fail'}
                        label={sameHash(check.localRoot, anchor.merkleRoot)
                          ? '서버가 말한 머클루트와 같습니다'
                          : '서버가 말한 머클루트와 다릅니다'}
                      >
                        {!sameHash(check.localRoot, anchor.merkleRoot) && (
                          <>서버 값 <CopyHash value={anchor.merkleRoot} head={14} tail={10} /></>
                        )}
                      </CheckLine>
                    )}

                    {/* ②-3 여기서부터가 서버를 신뢰 대상에서 빼는 부분이다 */}
                    {check.error ? (
                      <li className="vf-check-error">
                        <ErrorState error={check.error} onRetry={check.retry} inline />
                      </li>
                    ) : check.chain ? (
                      <>
                        <CheckLine
                          verdict={chainVerdict(check.chain, anchor)}
                          label={chainLabel(check.chain, anchor)}
                        >
                          {check.chain.anchoredBlock > 0 && `블록 #${check.chain.anchoredBlock}`}
                        </CheckLine>

                        {/* ②-4 접기를 내 코드가 아니라 컨트랙트가 한 판정. 내 JS 가 틀려도 이 줄은 옳다.
                            루트가 아직 없으면 isIncluded 는 무조건 false 라 이 줄이 말해 주는 게 없다
                            — 위 줄과 같은 사실을 붉게 한 번 더 적으면 없는 문제를 만든다. */}
                        {check.chain.anchoredBlock > 0 && (
                          <CheckLine
                            verdict={check.chain.included ? 'pass' : 'fail'}
                            label={check.chain.included
                              ? '컨트랙트 isIncluded 가 직접 접어 본 결과도 포함으로 나왔습니다'
                              : '컨트랙트 isIncluded 는 포함이 아니라고 답했습니다'}
                          />
                        )}

                        {/* ②-5 다른 장부를 본 것은 아닌지 */}
                        {check.chain.chainId !== anchor.chainId && (
                          <CheckLine verdict="fail" label="읽은 체인이 서버가 말한 체인과 다릅니다">
                            {`읽은 체인 ${check.chain.chainId} · 서버가 말한 체인 ${anchor.chainId}`}
                          </CheckLine>
                        )}
                      </>
                    ) : (
                      <CheckLine verdict="wait" label="체인에 직접 물어보는 중…">
                        {`${anchor.contractAddress.slice(0, 10)}… 의 anchoredAt(루트)`}
                      </CheckLine>
                    )}
                  </ul>

                  {/* 불일치를 봤을 때 무엇을 해야 하는지. 붉은 표시만 남기고 끝내지 않는다 */}
                  {step2Verdict(data, check) === 'fail' && (
                    <p className="vf-warn" role="alert">
                      검산이 맞아떨어지지 않았습니다. 이 커밋의 증명 자료가 어긋났다는
                      뜻이므로, 위 해시들을 복사해 문의해 주세요. 값을 그대로 보여 드리는
                      이유는 저희가 고쳐서 감출 수 없게 하기 위해서입니다.
                    </p>
                  )}

                  <dl className="vf-fields">
                    <div>
                      <dt>배치</dt>
                      <dd>
                        <Link className="vf-link" to={`/ledger/anchors/${anchor.batchId}`}>
                          {`#${anchor.batchId}`}
                        </Link>
                        <AnchorBadge status={anchor.status} />
                      </dd>
                    </div>
                    <div>
                      <dt>리프 자리</dt>
                      <dd className="num">{`${anchor.leafIndex + 1} / ${anchor.leafCount}`}</dd>
                    </div>
                    {anchor.txHash && (
                      <div>
                        <dt>트랜잭션</dt>
                        <dd><CopyHash value={anchor.txHash} head={14} tail={10} /></dd>
                      </div>
                    )}
                    {anchor.blockNumber !== null && (
                      <div>
                        <dt>블록</dt>
                        <dd className="num">{anchor.blockNumber.toLocaleString('ko-KR')}</dd>
                      </div>
                    )}
                    {anchor.confirmedAt && (
                      <div>
                        <dt>확정</dt>
                        <dd>{formatConfirmedAt(anchor.confirmedAt)}</dd>
                      </div>
                    )}
                    <div>
                      <dt>장부</dt>
                      <dd>
                        <CopyHash value={anchor.contractAddress} head={10} tail={8} />
                        <span className="vf-chain num">{`chainId ${anchor.chainId}`}</span>
                      </dd>
                    </div>
                  </dl>
                </>
              )}
            </Step>

            {/* ── ③ 판정 종가 대조 ────────────────────────── */}
            <Step
              no={3}
              title="판정 종가 대조"
              formula="settlePrice == 공공데이터 종가"
              verdict={settle ? 'manual' : 'locked'}
            >
              <p className="vf-lead">
                적중·빗나감을 가른 종가가 실제 시장 종가와 같은지는 원천에서 직접 확인합니다.
                우리가 대신 불러다 보여 주면 다시 우리를 믿어야 하므로, 원본 주소만 드립니다.
              </p>

              {!settle ? (
                <ul className="vf-checks">
                  <CheckLine verdict="locked" label="아직 판정 전입니다">
                    만기가 지나 판정이 끝나면 이 자리에 기준가와 판정 종가가 나옵니다.
                  </CheckLine>
                </ul>
              ) : (
                <>
                  <ul className="vf-checks">
                    <CheckLine verdict="manual" label="이 대조는 직접 하셔야 합니다">
                      아래 숫자는 우리가 기록한 값입니다. 같은 날짜·종목의 종가를
                      원천에서 직접 열어 맞춰 보세요.
                    </CheckLine>
                  </ul>

                  <div className="vf-settle">
                    <span className={`vf-settle-badge is-${settle.status.toLowerCase()}`}>
                      {SETTLE_LABEL[settle.status]}
                    </span>
                    <dl className="vf-settle-nums">
                      <div><dt>기준가</dt><dd className="num">{won(settle.basePrice)}</dd></div>
                      <div><dt>판정 종가</dt><dd className="num">{won(settle.settlePrice)}</dd></div>
                      <div><dt>목표가</dt><dd className="num">{won(data.payload.targetPrice)}</dd></div>
                      <div><dt>오차율</dt><dd className="num">{settle.errorRate === null ? '—' : `${settle.errorRate}%`}</dd></div>
                      <div><dt>판정일</dt><dd className="num">{settle.settleDate}</dd></div>
                    </dl>
                  </div>

                  {settle.sourceUrl ? (
                    <>
                      <a className="vf-source" href={settle.sourceUrl} target="_blank" rel="noreferrer noopener">
                        공공데이터포털 원본 열기
                        <svg width="13" height="13" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round" aria-hidden="true">
                          <path d="M14 5h5v5" /><path d="M19 5l-8 8" /><path d="M18 14v4a2 2 0 0 1-2 2H6a2 2 0 0 1-2-2V8a2 2 0 0 1 2-2h4" />
                        </svg>
                      </a>
                      {/* 열어 보면 인증키 오류가 나므로 미리 말한다 — 고장으로 오해하지 않게 */}
                      <p className="vf-hint">
                        주소에 인증키가 없습니다. 공공데이터포털에서 발급받은 본인 키를{' '}
                        <code>&serviceKey=…</code> 로 붙여 열면 같은 날짜·종목의 종가를 볼 수 있습니다.
                      </p>
                    </>
                  ) : (
                    <p className="vf-hint">원본 조회 주소가 아직 없습니다.</p>
                  )}
                </>
              )}
            </Step>
          </>
        )}
      </div>
    </main>
  )
}

/* ── 단계 헤더에 붙일 종합 판정 ────────────────────────────
   한 줄이라도 어긋나면 fail 이다 — 통과한 줄이 더 많다고 통과가 아니다. */

/** ①은 재료가 없으면 잠김, 있으면 계산이 끝나는 대로 통과/불일치다. 중간은 없다. */
function step1Verdict(data: Proof, commit: Recompute): Verdict {
  if (!data.commitHash || !commit.preimage) return 'locked'
  if (!commit.hash) return 'wait'
  return sameHash(commit.hash, data.commitHash) ? 'pass' : 'fail'
}
function step2Verdict(data: Proof, check: Check): Verdict {
  if (!data.anchor) return 'locked'
  if (!check.localRoot) return 'wait'
  /* 접기와 서버 루트의 비교가 먼저다. 체인에 못 붙었다고 이 결과까지 "대기" 로 덮으면
     체인이 죽어 있는 동안 서버 자료의 어긋남이 화면에서 사라진다. */
  if (!sameHash(check.localRoot, data.anchor.merkleRoot)) return 'fail'
  if (check.error || check.running || !check.chain) return 'wait'
  const onChain = chainVerdict(check.chain, data.anchor)
  if (onChain !== 'pass') return onChain
  const agreed = check.chain.included && check.chain.chainId === data.anchor.chainId
  return agreed ? 'pass' : 'fail'
}

/**
 * 체인이 답한 블록 번호를 어떻게 읽을 것인가.
 *
 * 내가 접은 루트로 물었으므로(v3 — 장부 칸의 키가 루트, ANT-CHAIN-13) 0 이 아니면 그 루트가 체인에 박혀 있다는 뜻이고
 * 그걸로 통과다 — 비교할 "체인의 루트" 를 따로 읽을 필요가 없다.
 * 0 이면 그 루트는 체인에 없다. 아직 앵커 전이면 "없다" 이지 "틀리다" 가 아니지만, 서버가 CONFIRMED 라고 말해 놓고
 * 체인에 없다면(서버의 proof 가 앵커된 트리와 다르다는 뜻이기도 하다) 그건 진짜 어긋남이다.
 * 두 경우를 같은 회색으로 덮으면 후자를 놓친다.
 */
function chainVerdict(chain: ChainRead, anchor: ProofAnchor): Verdict {
  if (chain.anchoredBlock === 0) return anchor.status === 'CONFIRMED' ? 'fail' : 'wait'
  return 'pass'
}

function chainLabel(chain: ChainRead, anchor: ProofAnchor) {
  if (chain.anchoredBlock === 0) {
    return anchor.status === 'CONFIRMED'
      ? `서버는 확정이라는데 체인에는 이 루트가 없습니다 (배치 #${anchor.batchId})`
      : `아직 체인에 오르지 않았습니다 (배치 #${anchor.batchId})`
  }
  return `체인이 이 루트를 기억하고 있습니다 (chainId ${chain.chainId})`
}
