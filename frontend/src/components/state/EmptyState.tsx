/* 빈 상태. 설계서 §6 — 404 리소스 없음·작업 만료도 여기로 온다.

   "결과가 없다" 와 "아직 만들지 않았다" 는 다르다. 후자에는 만들러 가는 길을
   같이 준다(action). 전자는 조건을 바꾸라고 안내한다. */
import { Link } from 'react-router-dom'

type Props = {
  title: string
  hint?: string
  /** 비어 있을 때 할 일이 있으면 넘긴다. 예: 첫 예측 등록하기 */
  action?: { label: string; to: string }
}

export default function EmptyState({ title, hint, action }: Props) {
  return (
    <div className="state">
      <p className="state-title">{title}</p>
      {hint && <p className="state-hint">{hint}</p>}
      {action && <Link className="state-cta" to={action.to}>{action.label}</Link>}
    </div>
  )
}
