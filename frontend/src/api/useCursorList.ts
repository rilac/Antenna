/* 모든 목록 화면이 쓰는 커서 페이징 훅.
   설계서 §7 — { items, nextCursor, hasNext } 계약 하나로 통일한다. */
import { useCallback, useEffect, useRef, useState } from 'react'
import { api } from './client'
import type { ApiError } from './errors'
import type { CursorList } from './types'

type State<T> = {
  items: T[]
  hasNext: boolean
  loading: boolean
  error: ApiError | null
}

export function useCursorList<T>(path: string, query?: Record<string, string | number | boolean | undefined>) {
  const [state, setState] = useState<State<T>>({ items: [], hasNext: false, loading: true, error: null })
  const cursor = useRef<string | null>(null)
  // 쿼리 객체는 매 렌더 새로 만들어지므로 문자열로 굳혀 의존성에 넣는다
  const queryKey = JSON.stringify(query ?? {})

  /* 상태는 전부 await 뒤에서 건드린다.
     effect 안에서 동기로 setState 하면 렌더가 한 번 더 도는 걸 피하기 위해서다.
     loading 을 세우는 건 사용자 조작(loadMore·reload)에서만 한다. */
  const load = useCallback(async (reset: boolean) => {
    try {
      const parsed = JSON.parse(queryKey) as Record<string, string | number | boolean | undefined>
      const page = await api.get<CursorList<T>>(path, {
        query: { ...parsed, cursor: reset ? undefined : cursor.current ?? undefined },
      })
      cursor.current = page.nextCursor
      setState((s) => ({
        items: reset ? page.items : [...s.items, ...page.items],
        hasNext: page.hasNext,
        loading: false,
        error: null,
      }))
    } catch (e) {
      setState((s) => ({ ...s, loading: false, error: e as ApiError }))
    }
  }, [path, queryKey])

  useEffect(() => {
    cursor.current = null
    void load(true)
  }, [load])

  return {
    ...state,
    /** 다음 페이지를 이어 붙인다 */
    loadMore: () => {
      if (!state.hasNext || state.loading) return
      setState((s) => ({ ...s, loading: true }))
      void load(false)
    },
    /** 처음부터 다시 읽는다 */
    reload: () => {
      cursor.current = null
      setState((s) => ({ ...s, loading: true, error: null }))
      void load(true)
    },
  }
}
