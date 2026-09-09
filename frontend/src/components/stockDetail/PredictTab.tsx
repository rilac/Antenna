/* B-03 두 번째 안쪽 페이지 — C-01 예측 등록.

   설계서는 C-01 을 /predict?code= 라는 별도 라우트로 두지만, 화면은 종목 상세 안에서
   탭으로 전환된다. 종목을 보다가 바로 예측하는 흐름이라 페이지를 갈아 끼우면
   방금 읽던 근거가 사라지기 때문이다. 라우트는 그대로 살아 있고(딥링크·뒤로가기),
   그쪽으로 들어와도 이 탭이 열린다.

   설계 제약(§4 C-01)
   - direction 은 UP/DOWN 둘뿐이고 horizon 은 5·10·20·60 고정이다. 임의 마감일이 없다.
   - targetPrice 는 등록 후 불변이다. 그래서 등록 전에 "수정·삭제 불가" 를 반드시 고지한다.
   - note 는 5000자 이하이며 구독자 전용이다. 커밋 문자열에는 noteHash 만 들어간다.
   - evidencePointIds 는 B-03 투자 포인트에서 인계받은 것만 쓴다. 리포트·재무지표는 근거가 못 된다.
   - 기준가는 배치 B2 가 다음 영업일 종가로 확정한다. 등록 직후 basePrice 가 비어 있고,
     빈 값을 0 으로 그리지 않는다.
   - 응답 네 갈래: 201 슬롯 내 · 202 슬롯 초과(소각) → M-02 · 401 서명 불일치 · 409 잔액 부족.

   커밋 규격이 정해진 뒤 바뀐 것 (ANT-PRED-02)
   - commitHash 를 **이 화면이 직접 계산한다.** 커밋 salt 가 없어지고(09-09) 유일한 난수인
     noteSalt 를 클라이언트가 만들게 되면서, 미리보기가 원장에 남을 값과 같은 해시를 보여 줄
     수 있게 됐다. 규격·이유는 api/predictions.ts "커밋 봉인" 절.
   - 서명 대상이 지갑 연동용 문자열이 아니라 예측 내용이다(결정 B3). 연동용에 서명하면
     서버가 SIGNER_MISMATCH 로 거절한다. */
import { useEffect, useMemo, useState } from 'react'
import { Link } from 'react-router-dom'
import { Panel } from './Block'
import PredictionList from './PredictionList'
import { useBlock } from '../../api/useBlock'
import WalletLinkModal from '../wallet/WalletLinkModal'
import CommitProgressModal, { type CommitPhase } from '../prediction/CommitProgressModal'
import ErrorState from '../state/ErrorState'
import { ApiError } from '../../api/errors'
import { POINT_KINDS, POINT_LABEL, getPoints } from '../../api/stockDetail'
import type { InvestPoint, PointKind, StockSummary } from '../../api/stockDetail'
import {
  DIRECTIONS, HORIZONS, HORIZON_LABEL, NOTE_MAX,
  commitHash, commitPayload, createPrediction, getSlots, newNoteSalt, noteHash,
  predictionSigningPayload, targetPriceInScale,
} from '../../api/predictions'
import type {
  CommitFields, CreateResult, Direction, Horizon, PredictionDraft,
} from '../../api/predictions'
import { requestNonce } from '../../api/wallet'
import type { PredictForm } from './predictForm'
import { connectAddress, hasWallet, personalSign } from '../../wallet/provider'
import { useAuth } from '../../auth/context'

const DIRECTION_LABEL: Record<Direction, string> = { UP: '오른다', DOWN: '내린다' }

/* 커밋 문자열과 서명 문자열이 같은 다섯 값을 쓴다. 한 자리에서 꺼내야 둘이 갈리지 않는다.
   draft 를 그대로 펼치지 않는 이유 — note·noteSalt·evidencePointIds 는 등록 본문에만
   들어가고 해시 대상에는 없다. 무엇이 봉인되는지 이 함수가 그대로 보여 준다. */
const commitFields = (d: PredictionDraft, hash: string): CommitFields => ({
  stockCode: d.stockCode,
  direction: d.direction,
  targetPrice: d.targetPrice,
  horizon: d.horizon,
  noteHash: hash,
})

/* 기간 버튼에 붙일 만기일. horizon 은 거래일 수라 주말을 건너뛰며 센다.
   휴장일은 클라이언트가 알 수 없어 하루 이틀 어긋날 수 있다 — 그래서 버튼 아래에
   "서버가 확정한다" 를 함께 적어 둔다. 기준가도 다음 영업일 종가라 하루 뒤부터 센다. */
function dueLabel(horizon: Horizon) {
  const d = new Date()
  let left = horizon + 1
  while (left > 0) {
    d.setDate(d.getDate() + 1)
    const day = d.getDay()
    if (day !== 0 && day !== 6) left -= 1
  }
  return `${d.getMonth() + 1}.${d.getDate()}`
}

type Props = {
  code: string
  summary: StockSummary | null
  /** InfoTab 에서 고른 근거 포인트. 이 인계에는 API 가 없다 */
  picked: number[]
  onPick: (id: number) => void
  onGoInfo: () => void
  form: PredictForm
  onForm: (next: (prev: PredictForm) => PredictForm) => void
}

export default function PredictTab({ code, summary, picked, onPick, onGoInfo, form, onForm }: Props) {
  const { user, setWalletLinked } = useAuth()

  const slots = useBlock(() => getSlots(), [])
  const points = useBlock(() => getPoints(code), [code])

  /* 부모가 들고 있는 값을 그대로 쓴다. 호출부는 지역 state 때와 똑같이
     setDirection(d) 처럼 값만 넘기면 된다. */
  const { direction, target, horizon, note } = form
  const setTarget = (v: string) => onForm((f) => ({ ...f, target: v }))
  const setHorizon = (v: Horizon) => onForm((f) => ({ ...f, horizon: v }))
  const setNote = (v: string) => onForm((f) => ({ ...f, note: v }))
  /* 사람이 직접 고른 방향. 이때부터 목표가가 방향을 바꾸지 않는다 */
  const setDirection = (v: Direction) =>
    onForm((f) => ({ ...f, direction: v, directionTouched: true }))

  const [linking, setLinking] = useState(false)
  const [submitting, setSubmitting] = useState(false)
  const [failure, setFailure] = useState<ApiError | null>(null)
  const [result, setResult] = useState<CreateResult | null>(null)
  /* 지갑 승인 뒤 서버 응답까지 빈 시간이 있다. 그 사이 화면이 그대로면 승인이
     먹혔는지 알 수 없고, 응답이 오는 순간 완료 화면이 튀어나온다.
     null 이면 모달을 띄우지 않는다 — 아직 서명 흐름에 들어가지 않은 상태다. */
  const [phase, setPhase] = useState<CommitPhase | null>(null)
  /** 지갑 창을 닫거나 거부했을 때처럼 서버까지 못 간 실패 */
  const [localMsg, setLocalMsg] = useState<string | null>(null)

  const targetPrice = Number(target.replace(/[^0-9.]/g, ''))
  const priceEntered = target !== '' && Number.isFinite(targetPrice) && targetPrice > 0
  /* 소수 셋째 자리 이하는 서버가 반올림하지 않고 400 으로 거절한다(numeric(14,2)).
     여기서 조용히 잘라 서명하면 서명한 값과 서버가 해시한 값이 달라진다. */
  const priceTooPrecise = priceEntered && !targetPriceInScale(targetPrice)
  const validTarget = priceEntered && !priceTooPrecise

  /* 목표가가 전일 종가 대비 몇 %인지. 방향과 어긋나면 미리 알려 준다 —
     "오른다" 를 고르고 종가보다 낮은 목표가를 넣는 실수가 잦다.

     자릿수가 넘쳐 등록이 막힌 값(priceTooPrecise)에도 비율은 보여 준다 — 비율이
     깜빡이며 사라지면 자릿수 문제인지 입력이 지워진 건지 알 수 없다. */
  const gap = useMemo(() => {
    if (!priceEntered || !summary?.prevClose) return null
    return ((targetPrice - summary.prevClose) / summary.prevClose) * 100
  }, [priceEntered, targetPrice, summary?.prevClose])

  /* 방향을 고르지 않고 목표가부터 적는 사람이 있다. 전일 종가보다 높으면 상승,
     낮으면 하락으로 미리 세워 준다.

     직접 고르기 전까지는 계속 따라간다 — 한 번만 정하면 "9" 까지 친 순간의
     값으로 하락이 박히고, "90000" 을 마저 쳐도 그대로 남는다.
     같은 값(gap 0)이면 방향을 정할 수 없으므로 비운다.
     directionTouched 가 서면 이 효과는 더 이상 손대지 않는다. */
  useEffect(() => {
    if (form.directionTouched) return
    const next: Direction | null = gap === null || gap === 0 ? null : gap > 0 ? 'UP' : 'DOWN'
    onForm((f) => (f.direction === next ? f : { ...f, direction: next }))
  }, [form.directionTouched, gap, onForm])

  const directionMismatch =
    direction !== null && gap !== null &&
    ((direction === 'UP' && gap < 0) || (direction === 'DOWN' && gap > 0))

  const draft: PredictionDraft | null = useMemo(() => {
    if (!direction || !horizon || !validTarget) return null
    return {
      stockCode: code, direction, targetPrice, horizon,
      note, noteSalt: form.noteSalt, evidencePointIds: picked,
    }
  }, [code, direction, horizon, validTarget, targetPrice, note, form.noteSalt, picked])

  /* 미리보기 세 값(noteHash → 커밋 문자열 → commitHash)은 비동기라 상태로 들고 있는다.
     ethers 를 동적 import 하기 때문인데, 그 대신 이 화면에 들어오기 전에는 받지 않는다.

     무엇으로 만든 값인지 함께 들고 있는 이유 — 입력을 계속 고치면 계산이 끝나기 전에
     다음 글자가 들어온다. 값만 저장하면 그 사이에 옛 입력의 해시가 지금 입력의 것처럼
     보이고, 그건 "무엇에 서명하는지" 를 틀리게 보여 주는 것이다. 지금 입력과 짝이 맞을
     때만 값으로 인정한다. */
  /* 여섯 값을 JSON 으로 잇는다 — 구분자를 문자 하나로 두면 근거 본문에 그 문자가
     들어갔을 때 서로 다른 입력이 같은 열쇠가 된다. */
  const previewKey = draft && JSON.stringify(
    [draft.stockCode, draft.direction, draft.targetPrice, draft.horizon, draft.note, draft.noteSalt],
  )

  const [preview, setPreview] = useState<
    { key: string; noteHash: string; payload: string; commitHash: string } | null
  >(null)

  useEffect(() => {
    if (!draft || !previewKey) return
    let alive = true
    void (async () => {
      const nh = await noteHash(draft.note, draft.noteSalt)
      const payload = commitPayload(commitFields(draft, nh))
      const ch = await commitHash(payload)
      if (alive) setPreview({ key: previewKey, noteHash: nh, payload, commitHash: ch })
    })()
    return () => { alive = false }
  }, [draft, previewKey])

  const shown = preview?.key === previewKey ? preview : null

  /* 서버가 열 셋으로 나눠 주므로 한 자루에 담아 id 로 찾는다.
     어느 열에서 왔는지는 배지로 보여야 해서 kind 를 함께 기억한다. */
  const pointById = useMemo(() => {
    const map = new Map<number, InvestPoint & { kind: PointKind }>()
    for (const kind of POINT_KINDS) {
      for (const p of points.data?.[kind] ?? []) map.set(p.id, { ...p, kind })
    }
    return map
  }, [points.data])

  const remaining = slots.data?.remaining ?? 0
  const overSlot = slots.data ? remaining <= 0 : false

  async function submit() {
    if (!draft) return
    setFailure(null)
    setLocalMsg(null)

    /* 지갑이 준비되지 않았으면 M-01 을 띄운다. 모달이 성공을 올려 주면 이 함수를
       다시 부른다 — 사용자가 같은 버튼을 두 번 누르지 않게 한다.

       미설치와 미연동을 여기서 가르지 않는다. **M-01 이 이미 네 갈래(미설치 ·
       서명 거부 · 주소 불일치 · 이미 연동됨)를 각각 다른 안내로 다룬다.**
       여기서 미설치만 따로 걸러 문구를 쓰면 같은 안내가 두 곳에 생기고, 그때는
       모달을 아예 열지 않아 설치 안내로 이어지지도 않는다.
       티켓도 "지갑이 연동되지 않은 상태로 진입하면 M-01 을 먼저 띄운다" 다. */
    if (!hasWallet() || !user?.walletLinked) {
      setLinking(true)
      return
    }

    setSubmitting(true)
    setPhase('signing')
    try {
      /* 미리보기 값을 쓰지 않고 여기서 다시 계산한다. 미리보기는 화면에 보이는 것이고,
         서명에 들어갈 값은 지금 이 draft 에서 나와야 한다 — 계산이 끝나기 전에 눌렀을
         때 옛 입력에 서명하는 길을 아예 만들지 않는다. */
      const hash = await noteHash(draft.note, draft.noteSalt)

      const address = await connectAddress()
      /* scope 는 아직 PREDICTION_BURN 이다. 규격상 PREDICTION 이 되지만(결정 B4) 서버
         SignatureScope 에 그 값이 없어 지금 바꾸면 nonce 발급이 400 이다 — PRED-01 과 함께 바꾼다.
         서명 문자열 자체는 scope 를 쓰지 않으므로 지금도 규격대로다. */
      const nonce = await requestNonce('PREDICTION_BURN')
      const payload = predictionSigningPayload(commitFields(draft, hash), nonce)
      const signature = await personalSign(payload, address)
      // 지갑 승인이 끝났다. 여기서부터가 사용자가 기다리는 구간이다
      setPhase('committing')
      setResult(await createPrediction(draft, signature))
      setPhase('settled')
      /* 다음 예측은 새 난수로 봉인한다. 같은 noteSalt 를 두 번 쓰면 같은 근거가 같은
         noteHash 로 남아, 원장만 보고도 "같은 말을 두 번 했다" 를 알 수 있다. */
      onForm((f) => ({ ...f, noteSalt: newNoteSalt() }))
    } catch (e) {
      /* 실패하면 모달을 걷는다. 오류는 폼 쪽에서 보여 준다 — 진행 모달에
         오류까지 담으면 "무엇이 어디까지 갔나" 와 "무엇이 잘못됐나" 가 한 창에
         섞인다. */
      setPhase(null)
      if (e instanceof ApiError) setFailure(e)
      else if (e && typeof e === 'object' && 'code' in e) {
        setFailure(new ApiError({
          code: String((e as { code: unknown }).code), message: '',
        }, Number((e as { status?: number }).status ?? 0)))
      } else setLocalMsg(e instanceof Error ? e.message : '서명을 마치지 못했습니다.')
    } finally {
      setSubmitting(false)
    }
  }

  /* 진행 모달. 완료 화면과 입력 화면 두 갈래가 같은 것을 띄우므로 한 번만
     만들어 둔다. 닫으면 뒤에 있는 완료 화면이 그대로 남는다 — 커밋 해시와
     다음 걸음이 거기 적혀 있어 잃을 것이 없다. */
  const progress = phase && (
    <CommitProgressModal phase={phase} result={result} onClose={() => setPhase(null)} />
  )

  /* ── 등록 완료 ────────────────────────────────────────── */
  if (result) {
    return (
      <div className="sd-grid">
        {progress}
        <section className="sd-block sd-done" data-span="12">
          <span className="sd-done-mark" aria-hidden="true">
            <svg width="30" height="30" viewBox="0 0 24 24" fill="none" stroke="currentColor"
                 strokeWidth="2.6" strokeLinecap="round" strokeLinejoin="round">
              <path d="M4 12.5 9.5 18 20 6.5" />
            </svg>
          </span>

          {result.kind === 'created' ? (
            <>
              <h2>예측을 등록했습니다</h2>
              {/* 기준가는 다음 영업일 종가로 배치가 채운다. 아직 없는 값을
                  0 으로 그리지 않고, 언제 정해지는지 말로 알린다 */}
              <p className="sd-done-sub">
                기준가는 다음 영업일 종가로 확정됩니다. 그때까지 상태는 <b>BASE</b> 입니다.
              </p>
              <dl className="sd-commit">
                <div><dt>커밋 해시</dt><dd className="num">{result.data.commitHash}</dd></div>
              </dl>
              <p className="sd-done-cta">
                <Link className="sd-cta" to="/me/predictions">내 예측에서 보기</Link>
              </p>
            </>
          ) : (
            <>
              {/* 슬롯을 넘겨 소각으로 넘어갔다. 온체인 확정 전이라 예측 id 가 아직 없다 */}
              <h2>블록 확정을 기다리는 중입니다</h2>
              <p className="sd-done-sub">
                이번 주 슬롯을 넘겨 토큰 소각으로 등록됩니다. 블록이 확정되면 예측 목록에 나타납니다.
              </p>
              <dl className="sd-commit">
                <div><dt>작업 번호</dt><dd className="num">{result.data.operationId}</dd></div>
              </dl>
              <p className="sd-done-cta">
                <Link className="sd-cta" to="/ledger">커밋 원장에서 확인</Link>
              </p>
            </>
          )}
        </section>
      </div>
    )
  }

  /* ── 입력 ──────────────────────────────────────────────
     왼쪽은 남의 예측, 오른쪽은 내 등록. 종목을 볼 때 "다들 어떻게 봤나" 를
     먼저 확인하고 내 판단을 적는 흐름이라 둘을 나란히 둔다. */
  return (
    <div className="sd-grid">
      <PredictionList code={code} span={6} />

      <Panel
        title="예측 등록하기"
        note={slots.data ? `이번 주 ${remaining}/${slots.data.weeklyLimit}회` : undefined}
        span={6}
      >
        {overSlot && (
          <p className="sd-slot-warn">
            이번 주 슬롯을 모두 썼습니다. 지금 등록하면 <b>토큰이 소각</b>되며, 블록이 확정될 때까지 기다립니다.
          </p>
        )}

        <ol className="pf-steps">
          {/* ① 방향 — 아이콘과 한 줄 설명을 붙여 무엇을 고르는지 눈으로 읽게 한다 */}
          <li className="pf-step">
            <h3><i className="pf-no">1</i>예측 방향</h3>
            <div className="pf-dirs">
              {DIRECTIONS.map((d) => (
                <button
                  key={d}
                  type="button"
                  className={`pf-dir is-${d.toLowerCase()} ${direction === d ? 'is-on' : ''}`}
                  aria-pressed={direction === d}
                  onClick={() => setDirection(d)}
                >
                  <span className="pf-dir-ic" aria-hidden="true">
                    <svg width="19" height="19" viewBox="0 0 24 24" fill="none" stroke="currentColor"
                         strokeWidth="2.6" strokeLinecap="round" strokeLinejoin="round">
                      {d === 'UP'
                        ? <><path d="M6 17 12 8l6 9" /><path d="M12 8v9" /></>
                        : <><path d="M6 8l6 9 6-9" /><path d="M12 17V8" /></>}
                    </svg>
                  </span>
                  <span className="pf-dir-txt">
                    <b>{d === 'UP' ? '상승' : '하락'}</b>
                    <span>{d === 'UP' ? '종가가 오를 것으로 본다' : '종가가 내릴 것으로 본다'}</span>
                  </span>
                </button>
              ))}
            </div>
          </li>

          {/* ② 목표가 */}
          <li className="pf-step">
            <h3><i className="pf-no">2</i>목표가</h3>
            <div className="sd-target">
              <input
                type="text"
                inputMode="numeric"
                className="num"
                value={target}
                placeholder={summary?.prevClose ? String(summary.prevClose) : '0'}
                aria-label="목표가"
                aria-describedby="pf-target-help"
                onChange={(e) => setTarget(e.target.value)}
              />
              <span className="sd-unit">원</span>
            </div>
            {/* 실전 시세는 전일 종가뿐이다 — "현재가" 라는 말을 만들지 않는다(§7 legal) */}
            <p id="pf-target-help" className="sd-help">
              {summary?.prevClose
                ? <>전일 종가 <b className="num">{summary.prevClose.toLocaleString('ko-KR')}원</b>
                  {gap !== null && (
                    <> · 목표가는 <b className={`num ${gap >= 0 ? 'up' : 'down'}`}>
                      {`${gap > 0 ? '+' : ''}${gap.toFixed(2)}%`}</b></>
                  )}</>
                : '이 종목은 전일 종가가 없어 대비 비율을 계산할 수 없습니다.'}
            </p>
            {/* 자릿수는 방향 어긋남보다 먼저 알린다 — 이건 고치지 않으면 등록 자체가 막힌다 */}
            {priceTooPrecise && (
              <p className="sd-warn">
                목표가는 <b>소수 둘째 자리까지</b> 입력할 수 있습니다. 봉인되는 값이라 서버가
                임의로 반올림하지 않습니다.
              </p>
            )}
            {directionMismatch && (
              <p className="sd-warn">
                {`방향은 "${DIRECTION_LABEL[direction]}" 인데 목표가가 반대쪽입니다. 다시 확인해 주세요.`}
              </p>
            )}
          </li>

          {/* ③ 기간 — 5·10·20·60 4지 고정이다. 임의 마감일(직접 입력)을 두지 않는다(§4 C-01).
              버튼에 만기일을 함께 적어 "20거래일" 이 언제까지인지 세지 않아도 되게 한다. */}
          <li className="pf-step">
            <h3><i className="pf-no">3</i>예측 기간</h3>
            <div className="pf-horizons">
              {HORIZONS.map((h) => (
                <button
                  key={h}
                  type="button"
                  className={`pf-hz ${horizon === h ? 'is-on' : ''}`}
                  aria-pressed={horizon === h}
                  onClick={() => setHorizon(h)}
                >
                  <b>{HORIZON_LABEL[h]}</b>
                  <span className="num">{`~ ${dueLabel(h)}`}</span>
                </button>
              ))}
            </div>
            <p className="sd-help">만기는 휴장일에 따라 하루 이틀 달라질 수 있어, 서버가 확정합니다.</p>
          </li>

          {/* ④ 근거 포인트 — B-03 종목 정보에서 인계받는다 */}
          <li className="pf-step">
            <h3><i className="pf-no">4</i>근거 포인트 <span className="pf-opt">선택</span></h3>
            {points.error ? (
              <ErrorState error={points.error} onRetry={points.retry} inline />
            ) : picked.length === 0 ? (
              <p className="pf-none">
                고른 근거가 없습니다.{' '}
                <button type="button" className="sd-link" onClick={onGoInfo}>종목 정보에서 고르기</button>
              </p>
            ) : (
              <ul className="sd-picked">
                {picked.map((id) => {
                  const p = pointById.get(id)
                  /* 포인트 목록이 아직 안 왔거나 종목이 바뀌어 사라진 id 면 이름을 모른다.
                     그때도 줄은 남겨야 사용자가 빼기라도 할 수 있다. */
                  const label = p?.body ?? `포인트 ${id}`
                  return (
                    <li key={id}>
                      {p && (
                        <span className={`sd-pt-side is-${p.kind}`}>{POINT_LABEL[p.kind]}</span>
                      )}
                      <b>{label}</b>
                      <button type="button" className="sd-pick-off" onClick={() => onPick(id)}
                              aria-label={`${label} 근거에서 빼기`}>
                        ×
                      </button>
                    </li>
                  )
                })}
              </ul>
            )}
          </li>

          {/* ⑤ 내 판단 — 평문이다. payload 에는 noteHash 만 들어가므로 서식을 넣지 않는다 */}
          <li className="pf-step">
            <h3><i className="pf-no">5</i>내 판단 <span className="pf-opt">선택 · 구독자 전용</span></h3>
            <textarea
              className="sd-note"
              value={note}
              maxLength={NOTE_MAX}
              rows={5}
              placeholder="왜 이렇게 보는지 적어 두면, 만기 뒤에 스스로 판단을 되짚을 수 있습니다."
              onChange={(e) => setNote(e.target.value)}
            />
            <p className="sd-help sd-note-count num">{`${note.length} / ${NOTE_MAX}`}</p>
          </li>
        </ol>

        {/* 커밋 미리보기 + 서명 */}
        <div className="pf-sign">
          {draft ? (
            <>
              {/* 커밋 문자열을 그대로 펼쳐 보여 준다(SignConfirm, §7). 커밋 salt 가 없어져
                  (09-09) 이 브라우저가 원장에 남을 commitHash 를 직접 계산할 수 있다 —
                  값이 서로 다르면 등록 전에 알아챌 수 있어야 하므로 둘 다 적는다. */}
              <p className="sd-help">
                아래 내용에 서명합니다. 커밋 해시는 이 브라우저가 계산한 값이며, 등록 뒤 원장에 남는 값과 같습니다.
              </p>
              <pre className="sd-payload num">{shown ? shown.payload : '커밋 문자열을 만드는 중…'}</pre>
              <dl className="sd-commit">
                <div>
                  <dt>근거 본문 해시</dt>
                  <dd className="num">{shown?.noteHash ?? '계산 중…'}</dd>
                </div>
                <div>
                  <dt>커밋 해시</dt>
                  <dd className="num">{shown?.commitHash ?? '계산 중…'}</dd>
                </div>
              </dl>

              {/* 되돌릴 수 없는 동작이라 반드시 고지한다 */}
              <p className="sd-irreversible">
                등록한 예측은 <b>수정하거나 삭제할 수 없습니다.</b> 목표가와 기간을 다시 확인해 주세요.
              </p>

              <button type="button" className="sd-submit" disabled={submitting} onClick={submit}>
                {submitting
                  ? '서명을 기다리는 중…'
                  : overSlot ? '토큰을 소각하고 등록' : '서명하고 등록'}
              </button>

              {/* 누른 뒤에 생기는 것이라 버튼 **아래** 에 둔다. 위에 두면 눌렀을
                  때 시선이 버튼에 있어 문구가 나타난 줄 모르고, 스크롤 위치에
                  따라 화면 밖에 있기도 하다 — 실제로 "눌러도 아무 일이 없다" 는
                  보고가 그래서 나왔다.
                  role=alert 로 읽어 주는 순서도 맞춘다. */}
              {(failure || localMsg) && (
                <div className="sd-submit-msg" role="alert">
                  {failure && <ErrorState error={failure} inline />}
                  {localMsg && <p className="sd-warn">{localMsg}</p>}
                </div>
              )}
            </>
          ) : (
            <p className="pf-none">방향 · 목표가 · 기간을 모두 고르면 서명 단계로 넘어갑니다.</p>
          )}
        </div>
      </Panel>

      {progress}

      {/* 지갑이 없으면 먼저 연동하고, 끝나면 하던 등록을 이어 간다 */}
      {linking && (
        <WalletLinkModal
          onClose={() => setLinking(false)}
          onLinked={() => {
            setWalletLinked(true)
            setLinking(false)
            void submit()
          }}
        />
      )}
    </div>
  )
}
