/* M-02 온체인 처리 대기 (모달 · 라우트 없음). [ANT-FE-OPERATION]
   호출 위치: C-01 슬롯 초과 · G-03 대회 참가
   설계서 §1-4 · §3 M · §7 OperationPoller

   대기·실패 갈래는 OperationProgress 가 그린다. 이 파일이 더하는 것은 창(배경·닫기)과
   **성공 뒤 결과로 보내는 일** 둘이다.

   ── E-02 · H-04 는 이 창을 쓰지 않는다 ────────────────────
   그 둘은 이미 자기 모달·자기 화면 안에서 대기로 넘어가므로 OperationProgress 만 끼운다.
   창을 두 겹으로 띄우면 사용자가 뒤 창을 모른 채 앞 창을 닫는다(OperationProgress 머리말).

   ── 닫기는 취소가 아니다 ─────────────────────────────────
   티켓이 "모달을 닫아도 작업은 계속 진행된다. 닫기가 취소가 아니라는 점이 분명해야 한다"
   고 적었다. 그래서 ① 언제든 닫히고 ② 대기 중에는 그 사실을 바닥에 적어 둔다.
   업로드 모달(M-07)이 전송 중 닫기를 막는 것과 반대인데, 이유가 정확히 반대다 —
   그쪽은 닫아도 요청이 살아 있는 것이 사고였고, 여기서는 그게 정상이다. */
import { useCallback, useEffect, useRef } from 'react'
import { Link } from 'react-router-dom'
import { useOperation, type OperationKind } from '../../api/operations'
import OperationProgress from './OperationProgress'
import { OPERATION_COPY, resourcePath } from './copy'
import '../../styles/operation.css'

type Props = {
  /** 202 응답의 operationId. null 이면 폴링하지 않는다 */
  operationId: string | null
  kind: OperationKind
  onClose: () => void
  /**
   * 성공 뒤 갈 곳. 기본값은 응답의 resource 로 정한다(copy.ts resourcePath).
   *
   * SEASON_JOIN 은 반드시 넘겨야 한다 — resource.id 가 참가 id 라 시즌 id 를 아는 쪽은
   * 호출부뿐이다.
   */
  to?: string
}

export default function OperationModal({ operationId, kind, onClose, to }: Props) {
  const op = useOperation(operationId)
  const dialogRef = useRef<HTMLDivElement>(null)

  const copy = OPERATION_COPY[kind]
  const succeeded = op.operation?.status === 'SUCCEEDED'
  const target = to ?? resourcePath(op.operation?.resource)

  const close = useCallback(() => { onClose() }, [onClose])

  useEffect(() => {
    const onKey = (e: KeyboardEvent) => { if (e.key === 'Escape') close() }
    document.addEventListener('keydown', onKey)
    return () => document.removeEventListener('keydown', onKey)
  }, [close])

  // 모달이 열리면 초점을 안으로 들인다. 없으면 탭이 뒤 화면을 돌아다닌다.
  useEffect(() => { dialogRef.current?.focus() }, [])

  return (
    <div className="op-backdrop" onClick={close} role="presentation">
      <div
        className="op-modal" role="dialog" aria-modal="true" aria-labelledby="op-title"
        tabIndex={-1} ref={dialogRef} onClick={(e) => e.stopPropagation()}
      >
        <div className="op-head">
          {/* 머리말은 창의 이름이고 할 말은 본문이 한다 — 둘 다 copy.done 을 쓰면
              같은 문장이 두 번 보인다 */}
          <h2 id="op-title">{succeeded ? '처리 완료' : '처리 중'}</h2>
          <button type="button" className="op-x" aria-label="닫기" onClick={close}>
            <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round">
              <path d="M6 6l12 12" /><path d="M18 6 6 18" />
            </svg>
          </button>
        </div>

        <div className="op-body">
          {succeeded ? (
            <div className="op" aria-live="polite">
              <span className="op-check" aria-hidden="true">
                <svg width="24" height="24" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2.2" strokeLinecap="round" strokeLinejoin="round">
                  <path d="m5 12.5 4.5 4.5L19 7.5" />
                </svg>
              </span>
              <p className="op-title">{copy.done}</p>
            </div>
          ) : (
            <OperationProgress op={op} kind={kind} />
          )}
        </div>

        <div className="op-actions">
          {/* 성공했는데 갈 곳을 못 정했으면 이동 버튼을 만들지 않는다 — 없는 주소로 보내는
              것보다 낫다. 그 경우 호출부가 to 를 넘기지 않은 것이다(copy.ts resourcePath) */}
          {succeeded && target
            ? <Link className="op-btn solid" to={target} onClick={close}>{copy.go}</Link>
            : <button type="button" className="op-btn" onClick={close}>닫기</button>}
        </div>

        {/* 닫아도 계속된다는 사실은 기다리는 동안에만 말한다. 끝난 뒤엔 할 말이 아니다 */}
        {op.polling && (
          <p className="op-foot">창을 닫아도 처리는 계속됩니다. 취소되지 않습니다.</p>
        )}
      </div>
    </div>
  )
}
