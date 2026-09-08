/* 403 잠금 카드. 설계서 §5 게이팅 규칙.

   핵심은 "없는 것처럼 보이지 않는다" 는 것이다. 404 로 그리면 구독 유인이
   사라진다 — 잠긴 내용이 존재한다는 사실 자체는 알려야 한다. */
import { Link } from 'react-router-dom'

type Props = {
  /** 무엇이 잠겼는지. 예: '예측 근거', '리포트 본문' */
  label: string
  /**
   * 기본 문구를 통째로 갈아끼운다.
   * 구독으로 풀리지 않는 잠금(관리자 전용 등)에 쓴다.
   */
  title?: string
  /** 구독하면 풀리는 채널. 없으면 CTA 를 숨긴다 */
  channelId?: string
  /** 서버가 내려준 미리보기. 리포트는 3줄까지 온다(§5) */
  preview?: string
  children?: React.ReactNode
}

/**
 * 조사를 label 의 받침에 맞춘다. "목표가와 근거은(는)" 처럼 읽히지 않게 하는 것뿐이다.
 *
 * 한글 음절은 유니코드에서 (초성×21 + 중성)×28 + 종성 으로 배열돼 있어, 28로 나눈
 * 나머지가 0이면 받침이 없다. 그래서 표를 두지 않고 계산으로 가른다.
 * 한글이 아닌 글자로 끝나면(영문·숫자) 판정할 수 없으므로 기존 "은(는)" 을 그대로 둔다.
 */
function topicParticle(word: string) {
  const last = word.trim().charCodeAt(word.trim().length - 1)
  if (Number.isNaN(last) || last < 0xac00 || last > 0xd7a3) return '은(는)'
  return (last - 0xac00) % 28 === 0 ? '는' : '은'
}

export default function LockedCard({ label, title, channelId, preview, children }: Props) {
  return (
    <div className="state-locked">
      {preview && (
        <div className="state-locked-preview">
          <p>{preview}</p>
        </div>
      )}

      <div className="state-locked-body">
        <i className="state-locked-mark" aria-hidden="true">
          <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.8" strokeLinecap="round" strokeLinejoin="round">
            <rect x="4" y="10" width="16" height="10" rx="2" />
            <path d="M8 10V7a4 4 0 0 1 8 0v3" />
          </svg>
        </i>
        <p className="state-locked-title">{title ?? `${label}${topicParticle(label)} 구독자에게만 공개됩니다`}</p>
        {children}
        {channelId && (
          <Link className="state-cta" to={`/channels/${channelId}`}>
            채널 구독하고 보기
          </Link>
        )}
      </div>
    </div>
  )
}
