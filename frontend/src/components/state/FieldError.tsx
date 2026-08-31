/* 400 검증 실패는 화면 전체 오류가 아니라 입력 필드 옆에 붙는다(설계서 §6).

   예: perMin > perMax · horizon 범위 · 자기 신고 · 업로드 규격.
   입력값이 원인이므로 사용자가 고칠 자리 바로 옆에 있어야 한다. */

export default function FieldError({ children, id }: { children?: React.ReactNode; id?: string }) {
  if (!children) return null
  return (
    <p className="field-error" id={id} role="alert">
      <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" aria-hidden="true">
        <circle cx="12" cy="12" r="9" /><path d="M12 7.5v5" /><path d="M12 16.2v.3" />
      </svg>
      {children}
    </p>
  )
}
