/* 한 번만 읽는 조회용 훅.

   목록은 useCursorList 가 맡는다. 이건 커서 페이징이 없는 단건·고정 목록
   (시장 Overview · 브리핑 · 관심 종목 · 랭킹 위젯 · 지갑 잔액)에 쓴다.

   useCursorList 와 같은 규칙을 지킨다 — effect 안에서 동기로 setState 하지 않는다.
   렌더가 한 번 더 도는 걸 피하려는 것이다. 그래서 load 가 바뀌었을 때의 초기화는
   effect 가 아니라 렌더 중에 처리한다(React 의 "prop 변화에 맞춰 state 조정" 패턴).
   loading 을 세우는 건 사용자 조작(reload)에서만 한다. */
import { useCallback, useEffect, useRef, useState } from 'react'
import type { ApiError } from './errors'

type State<T> = { data: T | null; loading: boolean; error: ApiError | null }

const PENDING = { data: null, loading: true, error: null } as const

/**
 * @param load 데이터를 읽는 함수. 매 렌더 새로 만들어지면 계속 다시 읽으므로
 *             useCallback 으로 감싸거나 모듈 스코프 함수를 넘긴다.
 */
export function useAsync<T>(load: () => Promise<T>) {
  const [state, setState] = useState<State<T>>(PENDING)

  /* load 가 바뀌면(예: 검색어 변경) 이전 결과를 그대로 보여주지 않고 로딩으로 되돌린다.
     렌더 중 setState 는 이 비교 패턴에서만 안전하다 — 다음 렌더에서 조건이 거짓이 된다. */
  const [seen, setSeen] = useState(() => load)
  if (seen !== load) {
    setSeen(() => load)
    setState(PENDING)
  }

  /* 응답이 늦게 온 이전 요청이 최신 결과를 덮지 않게 세대를 센다.
     effect 의 cleanup 만으로는 reload 로 겹친 요청을 못 막는다. */
  const gen = useRef(0)

  const run = useCallback(() => {
    const mine = ++gen.current
    load()
      .then((data) => { if (mine === gen.current) setState({ data, loading: false, error: null }) })
      .catch((e: unknown) => { if (mine === gen.current) setState({ data: null, loading: false, error: e as ApiError }) })
  }, [load])

  useEffect(() => { run() }, [run])

  return {
    ...state,
    /** 사용자가 다시 시도할 때. 여기서만 loading 을 직접 세운다. */
    reload: () => {
      setState(PENDING)
      run()
    },
  }
}
