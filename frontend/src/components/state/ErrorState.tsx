/* 오류 표현. 설계서 §6 — code 로 분기하고 서버 message 는 노출하지 않는다.

   403 은 여기로 오지 않는다. 잠금은 오류가 아니라 구독 유도라서 LockedCard 가 맡는다.
   404 도 "빈 상태" 로 그리는 편이 맞을 때가 많다 — EmptyState 를 쓴다. */
import type { ApiError } from '../../api/errors'
import { errorText } from './errorText'

type Props = {
  error: ApiError
  /** 다시 시도할 수 있으면 넘긴다. 재시도 가능한 오류에서만 버튼이 보인다 */
  onRetry?: () => void
  /** 화면 전체를 채울지, 카드 안에 들어갈지 */
  inline?: boolean
}

export default function ErrorState({ error, onRetry, inline }: Props) {
  const text = errorText(error)

  return (
    <div className={inline ? 'state state-inline' : 'state'} role="alert">
      <p className="state-title">{text.title}</p>
      {text.hint && <p className="state-hint">{text.hint}</p>}

      {onRetry && text.retryable && (
        <button type="button" className="state-cta" onClick={onRetry}>
          다시 시도
        </button>
      )}

      {/* 개발 중 원인 추적용. 사용자에게는 code 만 보이고 서버 message 는 나가지 않는다 */}
      <p className="state-code">{error.code}</p>
    </div>
  )
}
