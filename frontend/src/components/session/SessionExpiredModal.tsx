/* M-08 세션 만료 (모달 · 라우트 없음). [ANT-FE-SESSION]
   호출 위치: 전역 401 — AuthProvider 가 하나만 띄운다
   설계서 §3 M · §6

   ── 이 창이 뜨는 조건은 하나뿐이다 ─────────────────────────
   api/client.ts 가 401 을 받고 **재발급까지 실패했을 때만** 부른다. 재발급이 성공하면
   사용자는 아무것도 못 본 채 원래 하려던 요청이 이어진다(설계 제약: "조용히 재발급을
   시도한 뒤 실패했을 때만 사용자에게 재로그인을 요구한다").

   그래서 이 창에는 **다시 시도 버튼이 없다.** 여기까지 왔다는 것은 refresh 쿠키가 죽었다는
   뜻이고, 같은 쿠키로 한 번 더 두드려 봐야 결과가 같다. 오히려 서버의 재사용 탐지에 걸려
   남은 세션까지 통째로 날릴 수 있다(티켓: "실패한 refresh를 반복하면 사용자를 완전히
   로그아웃시킨다").

   ── 두지 않는 것 ─────────────────────────────────────────
   자동 로그아웃 카운트다운 · 세션 연장 버튼(티켓). 남은 시간을 세어 보여 주면 사용자는
   그 숫자를 관리해야 할 일로 받아들이는데, 실제로 할 수 있는 일은 다시 로그인뿐이다.

   ── 닫기를 남겨 둔 이유 ──────────────────────────────────
   글을 쓰다가 세션이 끊기는 경우가 있다. 창을 강제로 붙잡아 두면 사용자는 방금 쓴 글을
   **읽지도 복사하지도 못한 채** 로그인으로 끌려간다. 닫기를 주되, 닫아도 저장은 안 된다는
   것을 분명히 적는다. 닫는다고 세션이 살아나지는 않으므로 다음 요청은 또 막힌다. */
import { useCallback, useEffect, useRef } from 'react'
import '../../styles/session-expired.css'

type Props = {
  /** 로그인 화면으로 보낸다. 돌아올 경로를 기억하는 것은 호출부(AuthProvider)의 몫이다 */
  onRelogin: () => void
  onDismiss: () => void
}

export default function SessionExpiredModal({ onRelogin, onDismiss }: Props) {
  const dialogRef = useRef<HTMLDivElement>(null)

  const dismiss = useCallback(() => { onDismiss() }, [onDismiss])

  useEffect(() => {
    const onKey = (e: KeyboardEvent) => { if (e.key === 'Escape') dismiss() }
    document.addEventListener('keydown', onKey)
    return () => document.removeEventListener('keydown', onKey)
  }, [dismiss])

  /* 초점을 창 안으로 들인다. 이 창은 화면 아무 곳에서나 갑자기 뜨므로, 초점이 뒤에 남아
     있으면 키보드 사용자는 창이 떴다는 사실조차 모른다. */
  useEffect(() => { dialogRef.current?.focus() }, [])

  return (
    <div className="se-backdrop" onClick={dismiss} role="presentation">
      <div
        className="se-modal" role="alertdialog" aria-modal="true"
        aria-labelledby="se-title" aria-describedby="se-desc"
        tabIndex={-1} ref={dialogRef} onClick={(e) => e.stopPropagation()}
      >
        <div className="se-body">
          <h2 id="se-title">로그인이 풀렸습니다</h2>
          <p id="se-desc" className="se-sub">
            로그인 상태를 되살리려 했지만 실패했습니다. 다시 로그인하면 보던 화면으로 돌아옵니다.
          </p>
          {/* 닫아도 되살아나지 않는다는 것을 적어 둔다 — 안 적으면 닫고 계속 쓰다가
              저장 단계에서 또 막히고, 그때는 쓴 것을 잃는다 */}
          <p className="se-note">
            <b>지금 화면의 내용은 저장되지 않습니다.</b> 쓰던 글이 있으면 닫고 복사해 두세요.
          </p>
        </div>

        <div className="se-actions">
          <button type="button" className="se-btn" onClick={dismiss}>닫기</button>
          <button type="button" className="se-btn solid" onClick={onRelogin}>다시 로그인</button>
        </div>
      </div>
    </div>
  )
}
