/* M-10 AI 브리핑 상세 (모달 · 라우트 없음)
   담당 스토리 [ANT-FE-BRIEFING] · 설계서 §3 M · §4 M-10 · §7

   왜 모달이 따로 있는가
   목록(GET /briefings)은 헤드라인만 준다 — 서버 BriefingItemResponse 주석이
   "본문은 상세에서만" 이라고 못 박았다. 카드에서 본문을 펼칠 수가 없으니
   상세를 부르는 자리가 필요하고, 그 자리가 여기다.

   설계 제약
   - 라우트를 만들지 않는다. 여는 열쇠는 ?briefing={id} 쿼리 하나뿐이다
     (briefingParam.ts 에 이유를 적어 뒀다).
   - 뒤 화면의 스크롤과 상태가 유지돼야 한다. 오버레이라 뒤 화면이 살아 있다.
   - 배경 클릭으로 닫는다. M-09 온보딩·C-01 등록 모달은 닫지 못하게 막았는데,
     그건 놓치면 되돌아갈 길이 없거나 진행 중인 일이 있어서였다. 이건 읽기만 하는
     창이고 카드를 다시 누르면 그만이라 가두지 않는다.

   본문을 innerHTML 로 넣지 않는다
   body 는 LLM 이 만든 문자열이다. 서버가 마크다운도 HTML 도 아니라고 했으므로
   (엔티티 주석 "서술 문단만 담는다") 빈 줄로 문단만 갈라 텍스트로 그린다. */
import { useCallback, useEffect, useRef } from 'react'
import ErrorState from './state/ErrorState'
import SnapshotStamp from './SnapshotStamp'
import { getBriefing } from '../api/briefings'
import { useAsync } from '../api/useAsync'
import '../styles/screens/briefing-modal.css'

type Props = {
  id: number
  onClose: () => void
}

/** 빈 줄로 갈라 문단으로 만든다. 서버가 문단 사이를 \n\n 로 준다. */
const paragraphs = (body: string) =>
  body.split(/\n\s*\n/).map((p) => p.trim()).filter(Boolean)

export default function BriefingModal({ id, onClose }: Props) {
  const dialogRef = useRef<HTMLDivElement>(null)

  const load = useCallback(() => getBriefing(id), [id])
  const { data, loading, error, reload } = useAsync(load)

  useEffect(() => {
    const onKey = (e: KeyboardEvent) => { if (e.key === 'Escape') onClose() }
    document.addEventListener('keydown', onKey)
    return () => document.removeEventListener('keydown', onKey)
  }, [onClose])

  /* 초점을 창 안으로 들인다. 없으면 탭이 뒤 화면을 돌아다녀 어디를 누르는지 모른다 */
  useEffect(() => { dialogRef.current?.focus() }, [])

  /* 뒤 화면이 살아 있으므로 그쪽이 스크롤되지 않게 잠근다.
     닫을 때 원래 값을 되돌린다 — 다른 곳에서 이미 손대 두었을 수 있다. */
  useEffect(() => {
    const prev = document.body.style.overflow
    document.body.style.overflow = 'hidden'
    return () => { document.body.style.overflow = prev }
  }, [])

  const body = data?.body?.trim() ? paragraphs(data.body) : null

  return (
    /* 배경을 눌러 닫는다. 창 안쪽 클릭이 새어 나가지 않게 target 을 확인한다 */
    <div
      className="bf-backdrop"
      onClick={(e) => { if (e.target === e.currentTarget) onClose() }}
    >
      <div
        className="bf-modal" role="dialog" aria-modal="true" aria-labelledby="bf-title"
        aria-busy={loading} tabIndex={-1} ref={dialogRef}
      >
        <div className="bf-head">
          <p className="bf-eyebrow">
            <svg width="13" height="13" viewBox="0 0 24 24" fill="none" stroke="currentColor"
                 strokeWidth="1.9" strokeLinecap="round" strokeLinejoin="round" aria-hidden="true">
              <path d="M12 3v3M12 18v3M3 12h3M18 12h3M5.6 5.6l2.1 2.1M16.3 16.3l2.1 2.1M18.4 5.6l-2.1 2.1M7.7 16.3l-2.1 2.1" />
            </svg>
            AI 브리핑
          </p>
          <button type="button" className="bf-x" aria-label="닫기" onClick={onClose}>
            <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor"
                 strokeWidth="1.8" strokeLinecap="round" aria-hidden="true">
              <path d="M6 6l12 12M18 6L6 18" />
            </svg>
          </button>
        </div>

        <div className="bf-body">
          {loading && (
            <div className="bf-skeleton" aria-hidden="true">
              <span style={{ width: '82%' }} /><span style={{ width: '38%' }} />
              <span style={{ width: '100%' }} /><span style={{ width: '96%' }} />
              <span style={{ width: '64%' }} />
            </div>
          )}

          {error && <ErrorState error={error} onRetry={reload} inline />}

          {data && (
            <>
              <h2 id="bf-title" className="bf-title">{data.headline}</h2>
              {/* 배치 산출물이다. 기준 영업일을 병기해 실시간 값으로 읽히지 않게 한다(§7) */}
              <SnapshotStamp at={data.targetDate} label="브리핑" />

              {body ? (
                <div className="bf-text">
                  {body.map((p, i) => <p key={i}>{p}</p>)}
                </div>
              ) : (
                /* body 는 엔티티에서 nullable 이다. 빈 문단을 지어내지 않는다 */
                <p className="bf-nobody">본문이 아직 채워지지 않은 브리핑입니다.</p>
              )}

              {/* 무엇을 읽고 있는지 밝힌다. 브리핑은 지난 자료의 요약이고
                  종목 추천·미래 예측을 담지 않는다(AiBriefing 엔티티가 정한 규칙) */}
              <p className="bf-note">
                지난 자료를 정리한 AI 요약입니다. 종목 추천이나 시세 예측이 아닙니다.
              </p>
            </>
          )}
        </div>
      </div>
    </div>
  )
}
