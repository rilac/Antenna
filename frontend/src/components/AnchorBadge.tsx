/* 앵커 상태 배지. 설계서 §7 — C-01 · C-02 · D-01 · M-02 가 함께 쓴다.
   구독 PENDING 도 같은 컴포넌트다(설계서 §7 표).

   세 상태의 뜻이 서로 달라 색만으로 구분하지 않는다 — 아이콘과 글자를 함께 둔다.
   색각 이상이 있어도, 흑백으로 캡쳐해도 읽혀야 한다.

   PENDING 은 "아직"이지 "실패"가 아니다. 배치가 하루에 한 번 도는 구조라
   대기가 정상 상태이며, 사용자가 고장으로 오해하지 않아야 한다. */
import { STATUS_LABEL, type AnchorStatus } from '../api/anchors'
import '../styles/anchor-badge.css'

const ICON: Record<AnchorStatus, React.ReactNode> = {
  // 시계 — 기다리는 중
  PENDING: <><circle cx="12" cy="12" r="9" /><path d="M12 7.5V12l3 1.8" /></>,
  // 체크 — 체인에 올라 확정됨
  CONFIRMED: <><circle cx="12" cy="12" r="9" /><path d="m8.5 12.2 2.4 2.4 4.6-4.9" /></>,
  // 느낌표 — 전송이 실패해 다시 시도해야 함
  FAILED: <><circle cx="12" cy="12" r="9" /><path d="M12 7.6v5" /><path d="M12 15.9v.3" /></>,
}

export default function AnchorBadge({ status, label }: { status: AnchorStatus; label?: string }) {
  return (
    <span className={`anchor-badge is-${status.toLowerCase()}`}>
      <svg width="13" height="13" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round" aria-hidden="true">
        {ICON[status]}
      </svg>
      {label ?? STATUS_LABEL[status]}
    </span>
  )
}
