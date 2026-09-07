/* 구독 게이팅. 설계서 §7 — C-03 · E-02 · F-02 가 함께 쓴다.
   "403 또는 locked 를 받아 구독 CTA 로 전환한다" 가 이 컴포넌트의 일이다.

   왜 화면마다 짜지 않는가
   게이팅 판단이 두 갈래로 온다. 목록 응답은 `locked: true` 로 오고, 단건 조회는
   403 으로 온다(§5). 화면마다 이 분기를 새로 쓰면 한쪽을 빼먹기 쉽고, 무엇보다
   **404 로 그리는 실수**가 난다 — 없는 것처럼 보이면 구독 유인이 사라지므로
   존재 자체는 반드시 알려야 한다(§5).

   무엇을 잠그지 않는가
   잠기는 것은 내용이고, 커밋 해시 · 앵커 · 서명 주소는 언제나 공개다(§4 C-03).
   그래서 이 컴포넌트는 화면 전체를 감싸는 자리가 아니라 잠기는 블록만 감싼다.

   403 이 아닌 오류는 삼키지 않는다 — 그건 호출부가 ErrorState 로 다룰 일이다.
   여기서 함께 처리하면 서버가 죽은 것과 구독이 없는 것이 같은 화면으로 보인다.

   잠금 모양은 LockedCard 하나에만 둔다. 이 컴포넌트는 "언제 잠글지" 만 정한다. */
import type { ReactNode } from 'react'
import { isSubscriptionGated, type ApiError } from '../../api/errors'
import LockedCard from './LockedCard'

type Props = {
  /** 목록 응답이 주는 locked 플래그 */
  locked?: boolean
  /** 단건 조회가 주는 오류. 403 이면 잠금으로 본다 */
  error?: ApiError | null
  /** 무엇이 잠겼는지. 예: '예측 근거' */
  label: string
  /** 구독하면 풀리는 채널. 없으면 CTA 를 숨긴다 */
  channelId?: string | null
  /** 서버가 내려준 미리보기(있으면 잠금 위에 흐리게 보여준다) */
  preview?: string | null
  children: ReactNode
}

export default function SubscriptionGate({
  locked, error, label, channelId, preview, children,
}: Props) {
  if (!locked && !isSubscriptionGated(error)) return <>{children}</>

  return (
    <LockedCard
      label={label}
      channelId={channelId ?? undefined}
      preview={preview ?? undefined}
    />
  )
}
