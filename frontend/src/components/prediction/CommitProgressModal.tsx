/* 예측 등록 진행 모달 — C-01 [ANT-FE-PREDICT-NEW]
   설계서 docs/화면설계서.md §4 C-01 · §7

   왜 필요한가
   지갑에서 승인을 누른 뒤 서버 응답까지 빈 시간이 있다. 그 사이 화면이 그대로면
   승인이 먹혔는지 알 수 없고, 응답이 오는 순간 "등록했습니다" 가 튀어나온다.
   무엇이 어디까지 갔는지 단계로 보여준다.

   **없는 진행을 만들지 않는다.**
   앵커(머클 루트 확정)는 배치가 나중에 묶는다. 여기서 확인할 방법이 없으므로
   마지막 단계는 끝내 "대기" 로 남는다 — 도는 표시를 완료로 바꾸지 않는다.
   그래서 기다리게 두지 않고 닫기와 내 예측으로 가는 길을 함께 연다.

   201 과 202 는 두 번째 단계의 뜻이 다르다. 201 은 커밋 해시를 받은 것이고,
   202 는 슬롯을 넘겨 소각으로 접수된 것이라 아직 예측 id 가 없다. 문구를 하나로
   합치지 않는다 — 사용자가 받은 것이 서로 다르다. */
import { useEffect, useRef } from 'react'
import { Link } from 'react-router-dom'
import type { CreateResult } from '../../api/predictions'
import '../../styles/screens/commit-progress.css'

/** signing 지갑 승인 대기 · committing 서버 응답 대기 · settled 응답 받음 */
export type CommitPhase = 'signing' | 'committing' | 'settled'

type Props = {
  phase: CommitPhase
  /** settled 일 때만 있다. 201 인지 202 인지로 문구가 갈린다 */
  result: CreateResult | null
  onClose: () => void
}

type StepState = 'done' | 'busy' | 'wait'

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
  if (state === 'busy') return <span className="cp-mark is-busy" aria-hidden="true" />
  return <span className="cp-mark" aria-hidden="true" />
}

export default function CommitProgressModal({ phase, result, onClose }: Props) {
  const dialogRef = useRef<HTMLDivElement>(null)

  const settled = phase === 'settled'
  const queued = result?.kind === 'queued'

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
      /* 배치가 묶을 때까지 확인할 방법이 없다. 완료로 바뀌지 않는 단계다 */
      key: 'anchor',
      label: queued ? '블록 확정 대기' : '머클 앵커 대기',
      state: settled ? 'busy' : 'wait',
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

        <h2 id="cp-title" className="cp-title">개미들이 예측을 물고 블록으로 들어갑니다</h2>

        {/* 개미가 블록으로 줄지어 들어간다. 장식이라 읽어 줄 필요가 없다 */}
        <div className="cp-scene" aria-hidden="true">
          <div className="cp-ants">
            {[0, 1, 2, 3].map((i) => (
              <img
                key={i}
                className="cp-ant"
                style={{ animationDelay: `${i * 0.32}s` }}
                src="/assets/character/black_ant/antena-character-black-run.png"
                alt=""
              />
            ))}
          </div>
          <div className="cp-block">
            <svg width="52" height="58" viewBox="0 0 52 58" fill="none" aria-hidden="true">
              <path d="M26 2 50 15v28L26 56 2 43V15z" stroke="currentColor" strokeWidth="2"
                    strokeLinejoin="round" />
              <path d="M2 15l24 13 24-13M26 28v28" stroke="currentColor" strokeWidth="1.4"
                    strokeLinejoin="round" opacity=".55" />
            </svg>
          </div>
        </div>

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
