/* 화면 하나의 렌더 예외가 앱 전체를 흰 화면으로 만들지 않게 막는다.

   React 는 렌더 중 예외가 나면 트리를 통째로 버린다. 40개 화면을 여러 명이
   나눠 만드는 동안 누구든 undefined 를 읽는 실수를 하면 사이드바까지 사라져
   사용자가 다른 화면으로 갈 수도 없다. 여기서 잡아 그 화면만 오류로 바꾼다.

   훅으로는 만들 수 없다 — componentDidCatch 는 클래스 컴포넌트만 쓸 수 있다. */
import { Component, type ErrorInfo, type ReactNode } from 'react'

/* 라우트가 바뀌면 오류 상태가 풀려야 한다. componentDidUpdate 에서 setState 하는
   대신 호출부가 key 를 바꿔 리마운트시킨다 — 상태 갱신 없이 자연히 초기화된다. */
type Props = { children: ReactNode }

type State = { error: Error | null }

export default class ErrorBoundary extends Component<Props, State> {
  state: State = { error: null }

  static getDerivedStateFromError(error: Error): State {
    return { error }
  }

  componentDidCatch(error: Error, info: ErrorInfo) {
    // 사용자에게는 이름만 보인다. 원인 추적은 콘솔에 남긴다.
    console.error('[ErrorBoundary]', error, info.componentStack)
  }

  render() {
    const { error } = this.state
    if (!error) return this.props.children

    return (
      <main className="main">
        <div className="main-inner">
          <div className="page-head">
            <h1>화면을 표시할 수 없습니다</h1>
            <p>이 화면에서 문제가 발생했습니다. 다른 화면은 그대로 쓸 수 있습니다.</p>
          </div>
          <div className="state" role="alert">
            <p className="state-title">예상하지 못한 오류가 발생했습니다</p>
            <p className="state-hint">새로고침하거나 잠시 후 다시 열어 주세요</p>
            <button type="button" className="state-cta" onClick={() => this.setState({ error: null })}>
              다시 시도
            </button>
            {/* 서버 message 처럼 내부 사정을 그대로 노출하지 않는다. 이름만 남긴다 */}
            <p className="state-code">{error.name}</p>
          </div>
        </div>
      </main>
    )
  }
}
