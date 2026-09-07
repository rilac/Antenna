/* 로딩·실패를 따로 들고 있는 조회 훅. 목록이 아닌 단발 조회에 쓴다.

   useApiQuery 는 경로 문자열만 받아 목업 게이트(api/*.ts 의 MOCK)를 지나칠 수
   없다. 그래서 호출 함수를 그대로 받는 이 훅을 둔다 — B-03 블록들과 C-03 이 쓴다.

   B-03 은 블록마다 원천이 다르고 "블록 단위로 로딩·실패를 독립 처리한다" 가
   설계 제약이다(§4 B-03). 한 번의 Promise.all 로 묶으면 /financials 하나가
   느리거나 죽을 때 차트까지 같이 빈다 — 그래서 블록마다 이 훅을 하나씩 쓴다.

   useCursorList 와 역할이 다르다. 저쪽은 커서 페이징 목록이고, 이쪽은
   페이징이 없는 단발 조회다. 그래서 합치지 않았다. */
import { useCallback, useEffect, useRef, useState } from 'react'
import { ApiError, toApiError } from './errors'

type State<T> = {
  data: T | null
  loading: boolean
  error: ApiError | null
}

/* 던져진 값이 무엇이든 ApiError 로 만든다. client 를 거친 실패는 이미 ApiError 지만,
   목업은 { status, code } 를 얹은 Error 로 거절한다. 백엔드가 붙으면 앞 갈래만 남는다. */
function asApiError(e: unknown): ApiError {
  if (e instanceof ApiError) return e
  if (e && typeof e === 'object' && 'code' in e) {
    return toApiError(Number((e as { status?: number }).status ?? 0), e)
  }
  return toApiError(0, null)
}

/**
 * @param load 매번 새 Promise 를 만드는 함수. deps 가 바뀌면 다시 부른다.
 * @param deps load 가 의존하는 값들. 보통 종목코드다.
 * @param enabled false 면 부르지 않는다. 접힌 카드가 쓰는 스위치다 — 펼칠 때
 *   비로소 부르므로, 접어 둔 블록 때문에 요청이 나가지 않는다. 처음부터 꺼져
 *   있으면 상태가 loading 에 머무는데, 접힌 동안에는 본문을 그리지 않으니
 *   보이지 않고 펼치는 순간 이 훅이 실제로 부른다.
 */
export function useBlock<T>(
  load: () => Promise<T>,
  deps: unknown[],
  enabled = true,
): State<T> & { retry: () => void } {
  const [state, setState] = useState<State<T>>({ data: null, loading: true, error: null })
  const [nonce, setNonce] = useState(0)

  /* load 는 렌더마다 새 함수라 deps 에 넣으면 무한 루프가 된다.
     ref 로 최신 것만 들고 있고, 다시 부를 시점은 deps 와 nonce 가 정한다. */
  const loadRef = useRef(load)
  loadRef.current = load

  const retry = useCallback(() => setNonce((n) => n + 1), [])

  useEffect(() => {
    if (!enabled) return
    let alive = true
    setState({ data: null, loading: true, error: null })

    loadRef.current()
      .then((data) => {
        /* 종목을 빠르게 바꾸면 이전 응답이 늦게 도착한다. 그때 화면을
           덮어쓰지 않도록 버린다 — 안 그러면 A 종목 화면에 B 값이 뜬다. */
        if (alive) setState({ data, loading: false, error: null })
      })
      .catch((e: unknown) => {
        if (alive) setState({ data: null, loading: false, error: asApiError(e) })
      })

    return () => { alive = false }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [...deps, nonce, enabled])

  return { ...state, retry }
}
