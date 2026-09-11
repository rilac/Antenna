/* B-03 두 번째 안쪽 페이지 — C-01 예측 등록.

   설계서는 C-01 을 /predict?code= 라는 별도 라우트로 두지만, 화면은 종목 상세 안에서
   탭으로 전환된다. 종목을 보다가 바로 예측하는 흐름이라 페이지를 갈아 끼우면
   방금 읽던 근거가 사라지기 때문이다. 라우트는 그대로 살아 있고(딥링크·뒤로가기),
   그쪽으로 들어와도 이 탭이 열린다.

   설계 제약(§4 C-01)
   - direction 은 UP/DOWN 둘뿐이고 horizon 은 **7·14·30·90 캘린더일** 고정이다(결정 B5).
     임의 마감일이 없다. 화면설계서는 아직 5·10·20·60 거래일로 적혀 있는데, DB CHECK·ERD·
     API 명세 v0.38 이 모두 앞의 값이라 그쪽이 낡았다.
   - targetPrice 는 등록 후 불변이다. 그래서 등록 전에 "수정·삭제 불가" 를 반드시 고지한다.
   - note 는 **필수**이고 5000자 이하이며 구독자 전용이다(서버 @NotBlank).
     커밋 문자열에는 noteHash 만 들어간다.
   - evidencePointIds 는 B-03 투자 포인트에서 인계받은 것만 쓴다. 리포트·재무지표는 근거가 못 된다.
   - 기준가는 배치 B2 가 기준일 종가로 확정한다. 등록 직후 basePrice 가 비어 있고,
     빈 값을 0 으로 그리지 않는다.
   - 응답: 201 슬롯 내 · 401 서명 불일치 · 409 오늘 슬롯 소진.
     202 소각 경로는 소각할 토큰(ANT-CHAIN-03)이 아직 없어 서버에 없다(명세 v0.41).

   왼쪽 패널 (설계 변경 2026-09-10)
   - 개별 예측 목록이 아니라 **구간별 인원(호가창)** 이다. 누가 걸었는지는 이 화면에서
     보여주지 않고, 개인은 작성자 채널(E-02)에서만 본다. PredictionDepth 참고.

   커밋 규격이 정해진 뒤 바뀐 것 (ANT-PRED-02)
   - commitHash 를 **이 화면이 직접 계산한다.** 커밋 salt 가 없어지고(09-09) 유일한 난수인
     noteSalt 를 클라이언트가 만들게 되면서, 미리보기가 원장에 남을 값과 같은 해시를 보여 줄
     수 있게 됐다. 규격·이유는 api/predictions.ts "커밋 봉인" 절.
   - 서명 대상이 지갑 연동용 문자열이 아니라 예측 내용이다(결정 B3). 연동용에 서명하면
     서버가 SIGNER_MISMATCH 로 거절한다. */
import { useEffect, useMemo, useState } from 'react'
import { Link } from 'react-router-dom'
import { Panel } from './Block'
import PredictionDepth from './PredictionDepth'
import { useBlock } from '../../api/useBlock'
import WalletLinkModal from '../wallet/WalletLinkModal'
import CommitProgressModal, { type CommitPhase } from '../prediction/CommitProgressModal'
import SlideToSign from '../prediction/SlideToSign'
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

/* 등락률 고르개에 놓을 값. 왼쪽 분포표가 5% 폭이라 같은 눈금을 쓴다 — 두 곳의
   눈금이 다르면 "+10% 에 열네 명" 을 보고 +10% 를 골랐는데 다른 칸에 떨어진다. */
const PCT_CHOICES = [20, 15, 10, 5, -5, -10, -15, -20] as const

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

/* 기간 버튼에 붙일 만기일.

   horizon 은 **캘린더일**이다(결정 B5). 서버가 만기일 = 기준일 + horizon 일로 잡고,
   기준일은 등록 시점의 "다음 평일" 이다. 휴장일을 세지 않으므로 클라이언트가 서버와
   똑같이 계산할 수 있다 — 예전처럼 어긋나지 않는다. */
function dueLabel(horizon: Horizon) {
  const d = new Date()
  // 기준일 = 다음 평일. 토·일에 등록하면 월요일이 기준일이다
  do { d.setDate(d.getDate() + 1) } while (d.getDay() === 0 || d.getDay() === 6)
  d.setDate(d.getDate() + horizon)
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

  /* 근거(note)도 필수다 — 서버가 @NotBlank 로 받아 비우면 400 이다(명세: note *).
     커밋의 noteHash 가 봉인의 핵심이라, 근거 없는 예측은 규격상 만들 수 없다. */
  const draft: PredictionDraft | null = useMemo(() => {
    if (!direction || !horizon || !validTarget || !note.trim()) return null
    return {
      stockCode: code, direction, targetPrice, horizon,
      note, noteSalt: form.noteSalt, evidencePointIds: picked,
    }
  }, [code, direction, horizon, validTarget, targetPrice, note, form.noteSalt, picked])

  /* 못 채운 항목을 발판 위에 나열하던 줄은 뺐다(2026-09-10). 단계마다 "필수" 를
     붙였으므로 같은 말이 두 곳에 있었고, 발판 바로 위에서 문구가 나타났다 사라지며
     그때마다 발판이 아래위로 움직였다. */

  /* 등락률 고르개. 목표가를 직접 치는 사람도 있어 기본은 닫혀 있다 */
  const [pctOpen, setPctOpen] = useState(false)
  const horizonIdx = horizon === null ? 0 : HORIZONS.indexOf(horizon)

  /* 고른 비율로 목표가를 채운다. 원 단위로 반올림한다 — 소수 셋째 자리가 남으면
     서버가 400 으로 거절하고(numeric(14,2)), 호가 단위가 아닌 값도 사람이 읽기 어렵다. */
  const applyPct = (pct: number) => {
    const bases = summary?.prevClose
    if (!bases) return
    setTarget(String(Math.round(bases * (1 + pct / 100))))
    /* 비율로 골랐으면 방향도 그 비율이 정한다. 직접 고른 방향이 있어도 덮는다 —
       +5% 를 누른 순간 사용자의 뜻은 상승이다. */
    onForm((f) => ({ ...f, direction: pct > 0 ? 'UP' : 'DOWN', directionTouched: true }))
    setPctOpen(false)
  }

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
      /* scope 는 PREDICTION 이다(결정 B4). 이 값이 서버 nonce 칸 sig:nonce:{userId}:prediction
         을 정하고, PredictionCreateRequest.scope() 도 같은 값을 돌려준다. 소각(PREDICTION_BURN)
         은 슬롯을 넘겼을 때 M-02 가 쓰는 다른 칸이라 여기서 쓰면 서버가 못 찾는다. */
      const nonce = await requestNonce('PREDICTION')
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
      <PredictionDepth
        code={code}
        base={{ basePrice: summary?.prevClose ?? null, asOf: summary?.asOf ?? null }}
        span={6}
      />

      <Panel
        title="예측 등록하기"
        note={slots.data ? `오늘 ${remaining}/${slots.data.freeLimit}회` : undefined}
        span={6}
      >
        {overSlot && (
          /* 소각 경로(202)는 아직 서버에 없다 — 슬롯을 다 쓰면 409 로 끝난다(명세 v0.41).
             "지금 등록하면 소각된다" 고 적으면 누를 수 있는 것처럼 보이는데, 실제로는
             막힌다. 소각이 붙으면 그때 문구를 되돌린다. */
          <p className="sd-slot-warn">
            오늘 무료 슬롯을 모두 썼습니다. <b>내일 다시 채워집니다.</b>
          </p>
        )}

        <ol className="pf-steps">
          {/* ① 목표가 — 방향과 한 걸음으로 합쳤다(2026-09-10).
              방향은 목표가가 전일 종가의 어느 쪽인지로 정해진다. 따로 묻던 시절에는
              "오른다" 를 고르고 낮은 목표가를 넣는 모순이 잦았다. 버튼은 남겨 둔다 —
              값을 넣기 전에 어느 쪽을 보는지 먼저 정하는 사람이 있다. */}
          <li className="pf-step">
            <h3><i className="pf-no">1</i>목표가 <span className="pf-req">필수</span></h3>
            <div className="pf-price-row">
              <div className="sd-target">
                {/* 등락률로 목표가를 채운다. 전일 종가가 없으면(거래정지) 계산할 수
                    없으므로 잠근다 — 누르면 아무 일도 안 나는 버튼을 두지 않는다. */}
                <button
                  type="button"
                  className={`pf-pct-btn${pctOpen ? ' is-open' : ''}`}
                  disabled={!summary?.prevClose}
                  aria-expanded={pctOpen}
                  aria-haspopup="true"
                  onClick={() => setPctOpen((v) => !v)}
                >
                  <b className={gap === null ? '' : `num ${gap >= 0 ? 'up' : 'down'}`}>
                    {gap === null ? '등락률(%)' : `${gap > 0 ? '+' : ''}${gap.toFixed(2)}%`}
                  </b>
                  <svg width="11" height="11" viewBox="0 0 24 24" fill="none" stroke="currentColor"
                       strokeWidth="3" strokeLinecap="round" strokeLinejoin="round" aria-hidden="true">
                    <path d="M6 9l6 6 6-6" />
                  </svg>
                </button>

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

                {pctOpen && (
                  <div className="pf-pct-menu" role="menu" aria-label="등락률로 목표가 채우기">
                    {PCT_CHOICES.map((p) => (
                      <button
                        key={p}
                        type="button"
                        role="menuitem"
                        className={`pf-pct-item is-${p > 0 ? 'up' : 'down'}`}
                        onClick={() => applyPct(p)}
                      >
                        {`${p > 0 ? '+' : ''}${p}%`}
                      </button>
                    ))}
                  </div>
                )}
              </div>

              {/* 방향은 가격 **오른쪽**이다. 목표가를 넣으면 이쪽이 저절로 정해지므로,
                  읽는 순서(값 → 결과)와 놓인 순서가 같아야 한다. */}
              <div className="pf-dirs">
                {DIRECTIONS.map((d) => (
                  <button
                    key={d}
                    type="button"
                    className={`pf-dir is-${d.toLowerCase()} ${direction === d ? 'is-on' : ''}`}
                    aria-pressed={direction === d}
                    onClick={() => setDirection(d)}
                  >
                    {d === 'UP' ? '상승' : '하락'}
                  </button>
                ))}
              </div>
            </div>
            {/* 실전 시세는 전일 종가뿐이다 — "현재가" 라는 말을 만들지 않는다(§7 legal) */}
            {/* 비율은 등락률 버튼이 이미 보여 준다(`+10.00%`). 여기에 또 적으면 같은
                값이 한 줄 안에 두 번 나온다. 이 줄이 할 일은 0% 가 무엇인지 밝히는 것뿐. */}
            <p id="pf-target-help" className="sd-help">
              {summary?.prevClose
                ? <>전일 종가 기준 <b className="num">{summary.prevClose.toLocaleString('ko-KR')}원</b></>
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

          {/* ② 기간 — 네 값 고정이다. 임의 마감일(직접 입력)을 두지 않는다(§4 C-01).
              수직선(number line)으로 바꿨다(2026-09-10). 값이 네 개뿐이라 range 입력을
              **눈금 번호**로 쓴다 — 1·7·30·90 을 값 그대로 쓰면 90 쪽이 화면을 다
              먹어 앞쪽 셋이 붙어 버린다.
              native range 를 쓰는 이유는 접근성이다. 직접 만든 끌개는 키보드·스크린리더가
              못 쓰는데, range 는 화살표키·Home/End 가 그냥 된다. */}
          <li className="pf-step">
            <h3><i className="pf-no">2</i>예측 기간 <span className="pf-req">필수</span></h3>
            <div className={`pf-line${horizon === null ? ' is-unset' : ''}`}>
              <input
                type="range"
                className="pf-line-input"
                min={0}
                max={HORIZONS.length - 1}
                step={1}
                value={horizonIdx}
                aria-label="예측 기간"
                aria-valuetext={horizon === null ? '고르지 않음' : `${HORIZON_LABEL[horizon]} · ${dueLabel(horizon)} 만기`}
                onChange={(e) => setHorizon(HORIZONS[Number(e.target.value)])}
              />
              <div className="pf-line-ticks" aria-hidden="true">
                {HORIZONS.map((h, i) => (
                  <span
                    key={h}
                    className={`pf-tick${horizon === h ? ' is-on' : ''}`}
                    style={{ left: `${(i / (HORIZONS.length - 1)) * 100}%` }}
                  >
                    <i />
                    <b>{HORIZON_LABEL[h]}</b>
                  </span>
                ))}
              </div>
            </div>
            <p className="sd-help">
              {horizon === null
                ? '손잡이를 옮겨 기간을 고르세요.'
                : <>만기 <b className="num">{dueLabel(horizon)}</b> · 기준일(다음 평일)부터 캘린더일로 셉니다. 기준가는 기준일 종가로 확정됩니다.</>}
            </p>
          </li>

          {/* ③ 근거 포인트 — B-03 종목 정보에서 인계받는다. 근거 본문과 같이
              구독자 전용이다(§5 개정 2026-09-10). */}
          <li className="pf-step">
            <h3><i className="pf-no">3</i>근거 포인트 <span className="pf-opt">선택 · 구독자 전용</span></h3>
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

          {/* ④ 내 판단 — 평문이다. payload 에는 noteHash 만 들어가므로 서식을 넣지 않는다.

              **선택으로 바꾸지 않았다.** 서버 PredictionCreateRequest 가 @NotBlank 로
              받고(명세도 note *), 비우고 부르면 400 "근거를 입력해주세요." 가 온다 —
              실제로 눌러 확인했다. 서버가 풀어 주면 그때 pf-opt 로 바꾼다. */}
          <li className="pf-step">
            <h3><i className="pf-no">4</i>내 판단 <span className="pf-req">필수 · 구독자 전용</span></h3>
            <textarea
              className="sd-note"
              value={note}
              maxLength={NOTE_MAX}
              rows={3}
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
                등록한 예측은 수정하거나 삭제할 수 없습니다. 목표가와 기간을 다시 확인해 주세요.
              </p>

            </>
          ) : null}

          {/* 발판은 **늘 보인다**(2026-09-10). 조건이 안 찼을 때 아예 숨기면 다 채운
              뒤에야 발판이 나타나 화면이 아래로 밀리고, 그전까지는 이 카드가 어떻게
              끝나는지 알 수 없다. 못 누르는 상태로 자리를 지키게 한다. */}
          <SlideToSign
            onConfirm={submit}
            disabled={!draft}
            busy={submitting}
            label={overSlot ? '슬롯을 다 썼습니다' : '밀어서 등록하기'}
            busyLabel="서명중입니다…"
          />

          {/* 누른 뒤에 생기는 것이라 발판 **아래** 에 둔다. 위에 두면 눌렀을 때
              시선이 발판에 있어 문구가 나타난 줄 모르고, 스크롤 위치에 따라 화면
              밖에 있기도 하다 — 실제로 "눌러도 아무 일이 없다" 는 보고가 그래서
              나왔다. role=alert 로 읽어 주는 순서도 맞춘다. */}
          {(failure || localMsg) && (
            <div className="sd-submit-msg" role="alert">
              {failure && <ErrorState error={failure} inline />}
              {localMsg && <p className="sd-warn">{localMsg}</p>}
            </div>
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
