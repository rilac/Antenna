/* 배치 산출물의 기준 시각. 설계서 §7 — E-01 · B-01 · B-03 이 함께 쓴다.

   숫자만 보이면 실시간 값으로 읽힌다. 랭킹은 매 영업일 배치(B3)가 만든 스냅샷이고
   요청할 때 다시 계산하지 않는다. 그 사실을 화면에 남기는 것이 이 컴포넌트의 일이다.
   같은 이유로 H-01 지갑 잔액도 대사 시각을 병기한다. */

export default function SnapshotStamp({ at, label = '산출' }: { at: string; label?: string }) {
  return (
    <p className="snapshot-stamp">
      <svg width="13" height="13" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.9" strokeLinecap="round" strokeLinejoin="round" aria-hidden="true">
        <circle cx="12" cy="12" r="9" /><path d="M12 7.5V12l3 1.8" />
      </svg>
      {`${label} ${at} 기준`}
    </p>
  )
}
