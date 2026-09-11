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
     202(소각)는 predictionId 가 아직 없어 폴링할 대상이 없다 — 그쪽은 M-02 몫이다. */
  useEffect(() => {
    if (!settled || !predictionId) return
    let alive = true
    let timer: number | undefined

    const ask = async () => {
      try {
        const p = await fetchAnchorStatus(predictionId)
        if (!alive) return
        setPolled(p.anchorStatus)
        // 더 기다려도 안 바뀌는 상태면 멈춘다
        if (isAnchorSettling(p.anchorStatus)) timer = window.setTimeout(ask, 5000)
      } catch {
        /* 조회가 실패해도 등록 자체는 끝났다. 여기서 오류를 띄우면 성공한 일이
           실패한 것처럼 보인다 — 조용히 멈추고 상태는 내 예측에서 확인한다. */
      }
    }
    void ask()

    return () => { alive = false; if (timer) window.clearTimeout(timer) }
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
      /* 서버가 말한 상태를 그대로 쓴다. 확정·실패면 도는 표시를 멈춘다 */
      key: 'anchor',
      label: queued
        ? '블록 확정 대기'
        : anchor
          ? PROOF_STATUS_LABEL[anchor]
          : '머클 앵커 대기',
      state: !settled
        ? 'wait'
        : anchor === 'FAILED'
          ? 'fail'
          : anchor === 'CONFIRMED'
            ? 'done'
            : 'busy',
    },
  ]

  return (
    <div className="cp-backdrop">
      <div
        className="cp-modal" role="dialog" aria-modal="true" aria-labelledby="cp-title"
        aria-busy={!settled} tabIndex={-1} ref={dialogRef}
      >
        <div className="cp-head">
          <p className="cp-eyebrow">예측을 블록에 기록하고 있어요</p>
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

        <h2 id="cp-title" className="cp-title">예측을 블록에 담아 봉인하고 있어요</h2>

        {/* 장면은 phase 와 anchor 만 따라간다. 시간이 지났다고 다음 단계로
            넘어가지 않는다 — 서버가 말한 것만 그린다 */}
        <BlockchainScene phase={phase} anchor={anchor} />

        <ol className="cp-steps">
          {steps.map((s) => (
            <li key={s.key} className={`cp-step is-${s.state}`}>
              <Mark state={s.state} />
              <span>{s.label}</span>
            </li>
          ))}
        </ol>

        {/* 되돌릴 수 없다는 사실을 마지막으로 한 번 더 밝힌다(§4 C-01) */}
        <p className="cp-lock">
          <svg width="13" height="13" viewBox="0 0 24 24" fill="none" stroke="currentColor"
               strokeWidth="2" strokeLinecap="round" strokeLinejoin="round" aria-hidden="true">
            <rect x="4" y="10" width="16" height="11" rx="2" />
            <path d="M8 10V7a4 4 0 0 1 8 0v3" />
          </svg>
          앵커가 끝나면 이 예측은 누구도, 나조차도 수정할 수 없습니다.
        </p>

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
