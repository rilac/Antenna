/* 종목 상세의 카드 껍데기와 상태 표시.

   화면을 한눈에 담으려면 카드가 전폭으로 쌓이면 안 된다. 그래서 카드는 12칸
   격자에서 제 폭(span)을 받고, 내용이 길면 카드 안에서 스크롤한다. 카드 높이를
   줄에 맞춰 고정하지 않으면 격자가 들쭉날쭉해져 "한눈" 이 깨진다.

   한편 B-03 은 "블록 단위로 로딩·실패를 독립 처리한다" 가 설계 제약이다(§4 B-03).
   보기 좋게 카드를 합치면서 이 제약을 깨지 않으려고 둘을 나눠 뒀다:

     Panel       카드 하나. 제목·폭·높이·스크롤을 맡는다. 상태를 모른다.
     BlockState  원천 하나의 로딩·실패·빈 상태. 카드를 그리지 않는다.

   그래서 원천이 여러 개인 카드는 Panel 하나 안에 BlockState 를 여러 개 둔다.
   /financials 가 죽어도 같은 카드의 기업 개요는 그대로 보인다.
   원천이 하나면 둘을 합친 Block 을 쓴다. */
import type { ReactNode } from 'react'
import type { ApiError } from '../../api/errors'
import ErrorState from '../state/ErrorState'

/** 12칸 격자에서 차지할 칸 수. 좁은 화면에서는 무시하고 전폭이 된다. */
type Span = 4 | 5 | 6 | 7 | 8 | 12

type PanelProps = {
  title: string
  /** 제목 오른쪽 보조 문구. 기준일·표본 수처럼 값의 출처를 밝히는 자리 */
  note?: ReactNode
  /** 제목 줄 오른쪽 끝의 조작부(기간 버튼 등) */
  actions?: ReactNode
  span?: Span
  /* 접기 — 부모가 상태를 들고 있다. 카드가 스스로 기억하면 종목을 옮길 때마다
     초기화되고, 어느 카드가 열려 있는지 부모가 알 수 없어 저장도 못 한다.
     open 을 주지 않으면 접히지 않는 카드다. */
  open?: boolean
  onToggle?: () => void
  children: ReactNode
}

/** 접기 삼각형. 열리면 아래를, 접히면 오른쪽을 가리킨다 */
const Chevron = () => (
  <svg className="sd-chev" width="15" height="15" viewBox="0 0 24 24" fill="none"
       stroke="currentColor" strokeWidth="2.6" strokeLinecap="round" strokeLinejoin="round"
       aria-hidden="true">
    <path d="m9 6 6 6-6 6" />
  </svg>
)

export function Panel({
  title, note, actions, span = 12, open, onToggle, children,
}: PanelProps) {
  const collapsible = open !== undefined && onToggle !== undefined
  const shown = collapsible ? open : true

  return (
    <section
      className="sd-block"
      data-span={span}
      data-collapsed={collapsible && !shown ? '' : undefined}
    >
      <header className="sd-block-head">
        {collapsible ? (
          /* 제목 줄 전체가 누르는 자리다. 삼각형만 누르게 하면 과녁이 너무 작다.
             actions 는 이 버튼 안에 넣지 않는다 — 버튼 안의 버튼이 된다. */
          <button type="button" className="sd-head-btn" aria-expanded={shown} onClick={onToggle}>
            <Chevron />
            <h2>{title}</h2>
            {note && <span className="sd-block-note">{note}</span>}
          </button>
        ) : (
          <>
            <h2>{title}</h2>
            {note && <span className="sd-block-note">{note}</span>}
          </>
        )}
        {/* 접혀 있으면 조작부도 숨긴다. 차트가 없는데 기간 버튼만 남으면 이상하다 */}
        {actions && shown && <div className="sd-block-actions">{actions}</div>}
      </header>
      {shown && <div className="sd-block-body">{children}</div>}
    </section>
  )
}

type StateProps = {
  loading: boolean
  error: ApiError | null
  onRetry?: () => void
  /** 로딩 자리 높이(px). 실제 내용 높이에 맞춰 주면 화면이 덜 튄다 */
  skeleton?: number
  /** 자료는 왔는데 보여 줄 게 없을 때의 문구 */
  empty?: string
  isEmpty?: boolean
  children: ReactNode
}

/** 실패한 원천은 자리를 지키고 그 안에서만 알린다 — 통째로 빠지면 "원래 없는 항목" 으로 오해한다. */
export function BlockState({
  loading, error, onRetry, skeleton = 110, empty, isEmpty, children,
}: StateProps) {
  if (loading) return <div className="sd-skel" style={{ height: skeleton }} aria-hidden="true" />
  if (error) return <ErrorState error={error} onRetry={onRetry} inline />
  if (isEmpty) return <p className="sd-block-empty">{empty ?? '아직 쌓인 자료가 없습니다'}</p>
  return <>{children}</>
}

/** 원천이 하나인 카드. Panel + BlockState 를 겹쳐 놓은 것뿐이다. */
export default function Block({
  title, note, actions, span, open, onToggle, ...state
}: PanelProps & Omit<StateProps, 'children'> & { children: ReactNode }) {
  const { children, ...rest } = state
  return (
    <Panel
      title={title} note={note} actions={actions} span={span}
      open={open} onToggle={onToggle}
    >
      <BlockState {...rest}>{children}</BlockState>
    </Panel>
  )
}
