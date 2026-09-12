/* 예측 등록 진행 모달 — C-01 [ANT-FE-PREDICT-NEW]
   설계서 docs/화면설계서.md §4 C-01 · §7

   왜 필요한가
   지갑에서 승인을 누른 뒤 서버 응답까지 빈 시간이 있다. 그 사이 화면이 그대로면
   승인이 먹혔는지 알 수 없고, 응답이 오는 순간 "등록했습니다" 가 튀어나온다.
   무엇이 어디까지 갔는지 단계로 보여준다.

   앵커는 GET /predictions/{id}/proof 로 물어본다(ANT-CHAIN-06). 배치가 나중에
   묶으므로 등록 직후에는 WAITING 이고, 폴링해서 PENDING → CONFIRMED 로 넘어가면
   그때 완료로 바꾼다. **없는 진행을 지어내지 않는다** — 서버가 말한 상태만 그린다.

   폴링은 배치 주기를 따라잡을 수 없다(분 단위일 수 있다). 그래서 기다리게 두지
   않고 닫기와 내 예측으로 가는 길을 처음부터 함께 연다. 창을 닫아도 등록은
   그대로 진행된다.

   201 과 202 는 두 번째 단계의 뜻이 다르다. 201 은 커밋 해시를 받은 것이고,
   202 는 슬롯을 넘겨 소각으로 접수된 것이라 아직 예측 id 가 없다. 문구를 하나로
   합치지 않는다 — 사용자가 받은 것이 서로 다르다. */
import { useEffect, useRef, useState } from 'react'
import { Link } from 'react-router-dom'
import { PROOF_STATUS_LABEL, fetchAnchorStatus, isAnchorSettling } from '../../api/proof'
import type { ProofAnchorStatus } from '../../api/proof'
import type { CreateResult } from '../../api/predictions'
import BlockchainScene from './BlockchainScene'
import '../../styles/screens/commit-progress.css'

/** signing 지갑 승인 대기 · committing 서버 응답 대기 · settled 응답 받음 */
export type CommitPhase = 'signing' | 'committing' | 'settled'

type Props = {
  phase: CommitPhase
  /** settled 일 때만 있다. 201 인지 202 인지로 문구가 갈린다 */
  result: CreateResult | null
  onClose: () => void
}

type StepState = 'done' | 'busy' | 'wait' | 'fail'

/** 봉인이 그려진 뒤 이 창이 스스로 비켜서기까지(ms).
 *
 *  BlockchainScene 이 onSealed 를 부르는 시점부터 장면의 마무리가 2.5초쯤
 *  더 이어진다 — 받침대가 완성 슬롯으로 바뀌어 무대 가운데로 옮겨 가는 데
 *  1.6초, 자리 잡는 순간의 반짝임이 0.8초. 3.2초면 마무리를 보자마자 창이
 *  닫혔다. 5초로 두면 끝난 그림을 잠깐 보고 넘어간다.
 *  장면의 시간을 바꾸면 이 값도 같이 봐야 한다. */
const CLOSE_MS = 5000

function Mark({ state }: { state: StepState }) {
  if (state === 'done') {
    return (
      <span className="cp-mark is-done" aria-hidden="true">
        <svg width="13" height="13" viewBox="0 0 24 24" fill="none" stroke="currentColor"
             strokeWidth="3.2" strokeLinecap="round" strokeLinejoin="round">
          <path d="M4 12.5 9.5 18 20 6.5" />
        </svg>
      </span>
    )
  }
  if (state === 'fail') {
    /* 실패를 완료로 그리지 않는다. 앵커가 실패하면 재시도는 서버 몫이고,
       사용자는 내 예측에서 상태를 확인한다 */
    return (
      <span className="cp-mark is-fail" aria-hidden="true">
        <svg width="12" height="12" viewBox="0 0 24 24" fill="none" stroke="currentColor"
             strokeWidth="3.2" strokeLinecap="round">
          <path d="M6 6l12 12M18 6L6 18" />
        </svg>
      </span>
    )
  }
  if (state === 'busy') return <span className="cp-mark is-busy" aria-hidden="true" />
  return <span className="cp-mark" aria-hidden="true" />
}

export default function CommitProgressModal({ phase, result, onClose }: Props) {
  const dialogRef = useRef<HTMLDivElement>(null)

  const settled = phase === 'settled'
  const queued = result?.kind === 'queued'
  const predictionId = result?.kind === 'created' ? result.data.id : null

  /* 앵커 상태. 서버가 말해 주기 전까지는 아무것도 주장하지 않는다.

     이 모달은 phase='signing' 에 뜨고 result 는 그 뒤에 온다 — 그래서 useState 의
     초기값으로는 201 의 anchorStatus 를 못 잡는다(마운트 때는 아직 null 이다).
     폴링이 한 바퀴 돌기 전까지는 201 이 준 값을 쓰도록 파생해서 읽는다. */
  const [polled, setPolled] = useState<ProofAnchorStatus | null>(null)
  const anchor = polled ?? (result?.kind === 'created' ? result.data.anchorStatus : null)

  /* 배치가 묶을 때까지 물어본다. 확정·실패로 끝나면 멈춘다.
     202(소각)는 predictionId 가 아직 없어 폴링할 대상이 없다 — 그쪽은 M-02 몫이다.

     **한 번 실패했다고 그만두지 않는다.** 예전에는 catch 가 비어 있어서 401 이나
     끊긴 연결 한 번에 폴링이 영영 죽었다. 그러면 앵커가 확정돼도 화면은 계속
     "머클 앵커 대기" 로 남는다 — 실제로 그 증상이 나왔다.

     대신 오래 기다릴수록 뜸하게 묻는다. 배치는 정해진 시각에만 묶는 일도 있어
     몇 분에서 그 이상 걸리는데, 그동안 5초마다 두드리면 서버만 축낸다. */
  useEffect(() => {
    if (!settled || !predictionId) return
    let alive = true
    let inflight = false
    let timer: number | undefined
    const startedAt = Date.now()
    let failures = 0

    const nextDelay = () => {
      const waited = Date.now() - startedAt
      const base = waited < 60_000 ? 5_000 : waited < 300_000 ? 15_000 : 30_000
      // 실패가 이어지면 더 물러선다. 다만 멈추지는 않는다
      return failures ? Math.min(base * 2 ** Math.min(failures, 3), 60_000) : base
    }

    const ask = async () => {
      if (!alive || inflight) return
      inflight = true
      try {
        const p = await fetchAnchorStatus(predictionId)
        if (!alive) return
        failures = 0
        setPolled(p.anchorStatus)
        // 더 기다려도 안 바뀌는 상태면 여기서 끝낸다
        if (!isAnchorSettling(p.anchorStatus)) return
      } catch {
        /* 조회가 실패해도 등록 자체는 끝났다. 여기서 오류를 띄우면 성공한 일이
           실패한 것처럼 보이므로 화면에는 아무 말도 하지 않는다 — 다시 물을 뿐이다. */
        failures += 1
      } finally {
        inflight = false
      }
      if (alive) timer = window.setTimeout(ask, nextDelay())
    }
    void ask()

    /* 탭을 덮어 두면 브라우저가 타이머를 늦춘다. 돌아왔을 때 묵은 상태를
       보여 주지 않도록 즉시 다시 묻는다. */
    const onShow = () => {
      if (!alive || document.visibilityState !== 'visible') return
      if (timer) window.clearTimeout(timer)
      void ask()
    }
    document.addEventListener('visibilitychange', onShow)

    return () => {
      alive = false
      if (timer) window.clearTimeout(timer)
      document.removeEventListener('visibilitychange', onShow)
    }
  }, [settled, predictionId])

  /* 아직 진행 중일 때는 닫지 않는다. 지갑 창이 떠 있거나 서버가 응답하는
     중인데 닫아 버리면, 등록이 됐는지 모른 채 화면만 사라진다. */
  useEffect(() => {
    if (!settled) return
    const onKey = (e: KeyboardEvent) => { if (e.key === 'Escape') onClose() }
    document.addEventListener('keydown', onKey)
    return () => document.removeEventListener('keydown', onKey)
  }, [settled, onClose])

  useEffect(() => { dialogRef.current?.focus() }, [])

  /* 봉인까지 보여 줬으면 이 창의 할 일은 끝났다. 남은 앵커는 배치가 묶는 일이라
     여기서 기다릴 수 없는데, 창이 계속 떠 있으면 아직 안 끝난 것처럼 보인다.
     뒤에는 이미 등록 완료 화면(커밋 해시·다음 걸음)이 그려져 있으므로,
     봉인을 잠깐 보여 준 뒤 비켜서서 그 화면으로 넘겨준다.

     다만 그 사이에 서버가 확정이나 실패를 알려 오면 물러나지 않는다 —
     사용자가 기다리지 않아도 될 뿐이지, 마침 도착한 결과를 가릴 이유는 없다. */
  const [sealed, setSealed] = useState(false)
  const decided = anchor === 'CONFIRMED' || anchor === 'FAILED'
  useEffect(() => {
    if (!sealed || decided) return
    const t = window.setTimeout(onClose, CLOSE_MS)
    return () => window.clearTimeout(t)
  }, [sealed, decided, onClose])

  const steps: { key: string; label: string; state: StepState }[] = [
    {
      key: 'sign',
      label: '지갑 서명',
      state: phase === 'signing' ? 'busy' : 'done',
    },
    {
      key: 'commit',
      label: queued ? '토큰 소각 접수' : '커밋 해시 생성',
      state: settled ? 'done' : phase === 'committing' ? 'busy' : 'wait',
    },
    {
      /* 서버가 말한 상태를 그대로 쓴다.

         **앵커를 도는 표시로 그리지 않는다.** 배치는 정해진 시각에 묶으므로
         지금 이 창 앞에서 기다릴 수 있는 일이 아니다. 도는 표시를 두면 아직
         끝나지 않은 일처럼 보여, 등록이 이미 확정됐는데도 사용자를 붙잡아 둔다.
         확정·실패가 오면 그때 결과로 바꾼다 — 창이 열려 있으면 바로 반영된다. */
      key: 'anchor',
      label: queued
        ? '블록 확정 대기'
        : anchor === 'CONFIRMED' || anchor === 'FAILED'
          ? PROOF_STATUS_LABEL[anchor]
          : '블록체인 앵커 — 배치가 묶습니다',
      state: !settled
        ? 'wait'
        : anchor === 'FAILED'
          ? 'fail'
          : anchor === 'CONFIRMED'
            ? 'done'
            : 'wait',
    },
  ]

  return (
    <div className="cp-backdrop">
      <div
        className="cp-modal" role="dialog" aria-modal="true" aria-labelledby="cp-title"
        aria-busy={!settled} tabIndex={-1} ref={dialogRef}
      >
        <div className="cp-head">
          <p className="cp-eyebrow">
            {!settled ? '예측을 블록에 기록하고 있어요'
              : queued ? '토큰 소각으로 접수되었어요'
              : '예측이 블록에 기록되었어요'}
          </p>
          {/* 진행 중에는 닫지 못한다 — 위 useEffect 와 같은 이유다 */}
          <button
            type="button" className="cp-x" aria-label="닫기"
            onClick={onClose} disabled={!settled}
          >
            <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor"
                 strokeWidth="1.8" strokeLinecap="round" aria-hidden="true">
              <path d="M6 6l12 12M18 6L6 18" />
            </svg>
          </button>
        </div>

        {/* 서버가 받아 준 순간 등록은 끝이고 되돌릴 수 없다. 앵커가 남았다고
            "진행 중" 으로 적으면, 이미 확정된 일을 미완으로 읽게 만든다. */}
        {/* 202 는 슬롯을 넘겨 소각으로 접수된 것이라 아직 봉인이 아니다.
            블록이 확정돼야 예측이 선다 — 둘을 같은 말로 덮으면 사용자가 받은 것이
            서로 다른데 같은 것으로 읽힌다. */}
        <h2 id="cp-title" className="cp-title">
          {!settled ? '예측을 블록에 담아 봉인하고 있어요'
            : queued ? '토큰 소각으로 접수했습니다'
            : '예측이 봉인되었습니다'}
        </h2>

        {/* 장면은 phase 와 anchor 만 따라간다. 시간이 지났다고 다음 단계로
            넘어가지 않는다 — 서버가 말한 것만 그린다 */}
        <BlockchainScene phase={phase} anchor={anchor} onSealed={() => setSealed(true)} />

        <ol className="cp-steps">
          {steps.map((s) => (
            <li key={s.key} className={`cp-step is-${s.state}`}>
              <Mark state={s.state} />
              <span>{s.label}</span>
            </li>
          ))}
        </ol>

        {/* 여기서 기다릴 일이 아니라는 것을 분명히 말한다. 이 문장이 없으면
            앵커 줄만 보고 창을 못 닫는다 — 배치는 몇 분에서 그 이상 걸린다. */}
        {settled && queued && (
          <p className="cp-note">
            블록이 확정되면 예측 목록에 나타납니다 — <b>창을 닫아도 그대로 진행되고</b>,
            진행 상황은 커밋 원장에서 확인할 수 있습니다.
          </p>
        )}
        {settled && !queued && anchor !== 'CONFIRMED' && anchor !== 'FAILED' && (
          <p className="cp-note">
            이 예측은 이제 누구도, 나조차도 바꿀 수 없습니다.
            앵커는 잠시 뒤 배치가 묶어요 — <b>창을 닫아도 그대로 진행되고</b>,
            결과는 내 예측 내역에서 확인할 수 있습니다.
          </p>
        )}

        {/* 되돌릴 수 없다는 사실을 한 번 더 밝힌다(§4 C-01).
            **아직 끝나지 않았을 때만** 보여 준다 — 끝난 뒤에는 경고가 아니라
            설명이 되고, 위 안내와 두 상자로 겹쳐 쌓여 창만 길어진다. */}
        {!settled && (
        <p className="cp-lock">
          <svg width="13" height="13" viewBox="0 0 24 24" fill="none" stroke="currentColor"
               strokeWidth="2" strokeLinecap="round" strokeLinejoin="round" aria-hidden="true">
            <rect x="4" y="10" width="16" height="11" rx="2" />
            <path d="M8 10V7a4 4 0 0 1 8 0v3" />
          </svg>
          앵커가 끝나면 이 예측은 누구도, 나조차도 수정할 수 없습니다.
        </p>
        )}

        {/* 앵커는 여기서 기다릴 수 없다. 응답을 받은 뒤에는 길을 열어 준다 */}
        {settled && (
          <div className="cp-actions">
            <button type="button" className="cp-btn ghost" onClick={onClose}>닫기</button>
            {queued ? (
              <Link className="cp-btn solid" to="/ledger" onClick={onClose}>커밋 원장으로</Link>
            ) : (
              <Link className="cp-btn solid" to="/me/predictions" onClick={onClose}>내 예측 내역으로</Link>
            )}
          </div>
        )}
      </div>
    </div>
  )
}
