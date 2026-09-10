/* M-02 온체인 처리 대기 — 기다림과 실패의 본문. [ANT-FE-OPERATION]
   설계서 §1-4 · §3 M · §7 OperationPoller

   ── 왜 모달이 아니라 본문인가 ────────────────────────────
   티켓은 "4개 흐름이 이 모달 하나를 공유한다" 고 적지만, 실제로 네 흐름 중 둘(E-02 구독 ·
   H-04 광고)은 **이미 자기 모달·자기 화면 안에서** 대기 상태로 넘어간다. 그 위에 모달을 또
   띄우면 창이 두 겹이 되고, 사용자는 뒤 창이 무엇인지 모른 채 앞 창을 닫는다.

   그래서 공유하는 것을 창이 아니라 **본문**으로 두었다. 티켓이 지키려는 것("폴링 코드
   1개 · 실패 스키마 1개")은 그대로다 — 다섯 갈래(대기 · 실패 · 지연 · 만료 · 권한없음)를
   여기 한 번만 적고, 창이 필요한 호출부는 OperationModal 로 감싼다.

   ── 성공은 여기서 그리지 않는다 ──────────────────────────
   성공 화면은 흐름마다 할 말이 다르다 — "채널을 30일간 이용합니다", "배너가 7일간
   노출됩니다" 처럼 이 컴포넌트가 모르는 값이 들어간다. 그래서 SUCCEEDED 면 null 을
   돌려주고 호출부가 자기 성공 화면을 그린다. 창이 필요한 쪽은 OperationModal 이 맡는다. */
import type { OperationKind, OperationState } from '../../api/operations'
import { errorText } from '../state/errorText'
import { OPERATION_COPY, REFUND_NOTE } from './copy'
import '../../styles/operation.css'

type Props = {
  /** useOperation(operationId) 의 반환값을 그대로 넘긴다 */
  op: OperationState & { recheck: () => void }
  kind: OperationKind
}

export default function OperationProgress({ op, kind }: Props) {
  const copy = OPERATION_COPY[kind]
  const status = op.operation?.status

  // 성공은 호출부의 몫이다(머리말)
  if (status === 'SUCCEEDED') return null

  /* 갈래의 순서가 곧 우선순위다. 403·404 는 "조회가 잠깐 안 됐다" 가 아니라 종착이라
     오류 문구보다 먼저 잡는다. */

  if (op.forbidden) {
    return (
      <div className="op" aria-live="polite">
        <p className="op-title">이 처리 내역을 볼 수 없습니다</p>
        {/* 재시도 버튼을 주지 않는다 — 다시 물어도 같은 답이고, 남의 작업이라 볼 방법이 없다 */}
        <p className="op-sub">다른 사람의 처리 내역입니다. 링크를 잘못 받았을 수 있습니다.</p>
      </div>
    )
  }

  if (op.expired) {
    /* 오류가 아니라 빈 상태다(티켓 제약). 빨간색도, 코드도, 재시도도 없다 —
       사용자가 할 일이 없고, 무엇보다 **작업 자체는 이미 끝났다.** */
    return (
      <div className="op" aria-live="polite">
        <p className="op-title">처리 내역이 남아 있지 않습니다</p>
        <p className="op-sub">끝난 지 하루가 지난 작업은 조회할 수 없습니다. 결과는 그대로 반영돼 있습니다.</p>
      </div>
    )
  }

  if (status === 'FAILED') {
    return (
      <div className="op" aria-live="polite">
        <p className="op-title is-failed">{copy.failed}</p>
        {/* 사유 문자열은 인덱서가 만든 것이라 옮기지 않는다 — 옮기면 검색이 안 된다 */}
        {op.operation?.error && <code className="op-code">{op.operation.error.code}</code>}
        <p className="op-sub">{`${REFUND_NOTE} 잠시 후 다시 시도해 주세요.`}</p>
      </div>
    )
  }

  if (op.timedOut) {
    /* 실패로 단정하지 않는다. 성공한 결제를 실패로 알리는 쪽이 훨씬 나쁘다 —
       되돌릴 방법이 없는 소각인데 사용자는 한 번 더 시도하게 된다. */
    return (
      <div className="op" aria-live="polite">
        <p className="op-title">확인이 늦어지고 있습니다</p>
        <p className="op-sub">취소된 것은 아닙니다. 체인이 붐비면 몇 분 더 걸릴 수 있습니다.</p>
        <button type="button" className="op-btn" onClick={op.recheck}>다시 확인</button>
      </div>
    )
  }

  return (
    <div className="op" aria-live="polite">
      <span className="op-spinner" aria-hidden="true" />
      <p className="op-title">{copy.waiting}</p>
      {/* PENDING 은 "블록 확정 대기" 다. 무엇을 기다리는지 한 줄 더 말한다(티켓 제약) */}
      <p className="op-sub">블록에 담기기를 기다리는 중입니다. 이 화면을 떠나도 계속됩니다.</p>

      {/* 체인 장애는 이 안에서만 말한다. 전역 배너로 번지게 하지 않는다(티켓 제약) */}
      {op.error && (
        <p className="op-error" role="alert">
          <b>{errorText(op.error).title}</b>
          <button type="button" className="op-btn" onClick={op.recheck}>다시 확인</button>
        </p>
      )}
    </div>
  )
}
