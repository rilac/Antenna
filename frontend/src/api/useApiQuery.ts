/* 단건 GET 훅. 목록은 useCursorList 가 맡고, 이건 그 외 조회에 쓴다.

   상태는 전부 await 뒤에서 건드린다 — effect 안에서 동기 setState 를 하면
   렌더가 한 번 더 돈다. loading 을 세우는 건 사용자 조작(reload)에서만 한다. */
import { useCallback, useEffect, useState } from 'react'
import { api } from './client'
import type { ApiError } from './errors'

type Query = Record<string, string | number | boolean | undefined>

type State<T> = {
  data: T | null
  loading: boolean
  error: ApiError | null
}

export function useApiQuery<T>(path: string, query?: Query) {
  const [state, setState] = useState<State<T>>({ data: null, loading: true, error: null })
  // 쿼리 객체는 매 렌더 새로 만들어지므로 문자열로 굳혀 의존성에 넣는다
  const queryKey = JSON.stringify(query ?? {})

  const load = useCallback(async () => {
    // 바로 위에서 stringify 한 값이라 파싱이 실패할 수 없다.
    // try 밖에 두어 동기 예외가 setState 로 이어지지 않게 한다.
    const parsed = JSON.parse(queryKey) as Query
    try {
      const data = await api.get<T>(path, { query: parsed })
      setState({ data, loading: false, error: null })
    } catch (e) {
      setState({ data: null, loading: false, error: e as ApiError })
    }
  }, [path, queryKey])

  // oxlint-disable-next-line react/set-state-in-effect -- 데이터 페칭이라 상태 갱신이 목적이다. setState 는 모두 await 뒤에 있다.
  useEffect(() => { void load() }, [load])

  return {
    ...state,
    reload: () => {
      setState((s) => ({ ...s, loading: true, error: null }))
      void load()
    },
  }
}
