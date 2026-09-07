/* 잘라 보여주되 전체 값을 복사할 수 있는 해시. D-01 설계 제약.

   머클루트·트랜잭션 해시는 66자라 그대로 두면 표를 밀어낸다. 그렇다고 잘라만 두면
   사용자가 체인 탐색기에 붙여 넣어 직접 확인할 길이 사라진다 — 이 서비스가 "검증할 수
   있다"고 주장하는 근거가 화면에서 끊긴다. 그래서 줄이되 원본을 복사하게 한다.

   title 에 전체 값을 넣어 마우스로도 확인할 수 있게 한다. */
import { useEffect, useState } from 'react'
import { shortHash } from '../api/anchors'
import '../styles/copy-hash.css'

export default function CopyHash({ value, head, tail }: { value: string; head?: number; tail?: number }) {
  const [copied, setCopied] = useState(false)

  // 복사됨 표시를 잠깐 뒤 되돌린다. 언마운트되면 타이머를 거둔다.
  useEffect(() => {
    if (!copied) return
    const timer = setTimeout(() => setCopied(false), 1400)
    return () => clearTimeout(timer)
  }, [copied])

  async function copy() {
    try {
      /* clipboard 는 보안 컨텍스트(https·localhost)에서만 있다.
         없으면 조용히 넘어간다 — title 로 전체 값을 볼 수는 있다. */
      await navigator.clipboard?.writeText(value)
      setCopied(true)
    } catch { /* 사용자가 권한을 막은 경우. 화면을 깨뜨리지 않는다 */ }
  }

  return (
    <button
      type="button" className={`copy-hash ${copied ? 'copied' : ''}`}
      onClick={copy} title={value}
      aria-label={`${value} 복사`}
    >
      <code>{shortHash(value, head, tail)}</code>
      <span className="copy-hash-icon" aria-hidden="true">
        {copied ? (
          <svg width="13" height="13" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2.2" strokeLinecap="round" strokeLinejoin="round">
            <path d="m5 12.5 4.5 4.5L19 7.5" />
          </svg>
        ) : (
          <svg width="13" height="13" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.8" strokeLinecap="round" strokeLinejoin="round">
            <rect x="9" y="9" width="11" height="11" rx="2" />
            <path d="M5 15V5a2 2 0 0 1 2-2h8" />
          </svg>
        )}
      </span>
      {/* 스크린리더에 결과를 알린다 */}
      <span className="sr-only" role="status">{copied ? '복사했습니다' : ''}</span>
    </button>
  )
}
