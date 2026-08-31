/* 체인 장애 배너.

   설계서 §4 M-02 — 체인 장애는 M-02 온체인 처리 대기 모달 안에서만 표현한다.
   전역 배너로 띄우면 시세 조회처럼 체인과 무관한 화면까지 장애로 보인다.
   그래서 이 컴포넌트는 M-02 밖에서 쓰지 않는다. */

export default function ChainFailureBanner({ onRetry }: { onRetry?: () => void }) {
  return (
    <div className="state-banner" role="status">
      <i className="state-banner-mark" aria-hidden="true">
        <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.8" strokeLinecap="round" strokeLinejoin="round">
          <path d="M10 13a5 5 0 0 0 7 0l3-3a5 5 0 0 0-7-7l-1.5 1.5" />
          <path d="M14 11a5 5 0 0 0-7 0l-3 3a5 5 0 0 0 7 7l1.5-1.5" />
          <path d="m3 3 18 18" />
        </svg>
      </i>
      <div>
        <p className="state-banner-title">블록체인 네트워크가 불안정합니다</p>
        <p className="state-banner-hint">처리는 계속 시도되며, 완료되면 알려드립니다</p>
      </div>
      {onRetry && (
        <button type="button" className="state-cta" onClick={onRetry}>
          상태 새로고침
        </button>
      )}
    </div>
  )
}
