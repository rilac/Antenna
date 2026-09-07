/* B-04 관심 종목 · /watchlist
   담당 스토리 [ANT-FE-WATCHLIST]
   설계서 docs/화면설계서.md §3 B · §4 B-04 · §7 · §9.2

   GET /watchlist 에 처음부터 실제로 붙는다. 목업을 거치지 않았다 — 서버가
   이미 화면에 맞는 다섯 필드를 내리고 있어 흉내낼 이유가 없다.

   설계 제약
   - 응답은 다섯 필드뿐이다(stockCode · name · prevClose · changeRate · series).
     **그 밖의 값을 화면에서 만들어내지 않는다.**
   - **행별 예측 심리 배지를 두지 않는다(§9.2).** 이 응답에 집계가 없다.
     심리는 B-02 · B-03 에서만 보여준다.
   - **커서 페이징이 없다.** 담는 수가 화면 한 장 안이라 서버가 전량을 내린다.
     "더 보기" 를 두지 않고, 정렬 컨트롤도 두지 않는다 — 최근 담은 순 하나다.
   - 전일 종가다. "현재가" 라고 쓰지 않는다(§7 legal). 기준일이 응답에 없어
     행마다 날짜를 적을 수 없으므로 목록 위에 무슨 값인지 한 번 밝힌다.

   빼기는 낙관적으로 처리한다. 눌렀을 때 바로 사라지고 실패하면 되돌린다.
   B-02 의 별 토글과 같은 규칙이다(§4 B-02). */
import { useCallback, useMemo, useState } from 'react'
import { Link } from 'react-router-dom'
import Sparkline from '../components/Sparkline'
import EmptyState from '../components/state/EmptyState'
import ErrorState from '../components/state/ErrorState'
import { useAsync } from '../api/useAsync'
import { getWatchlist, removeWatch } from '../api/stocks'
import type { WatchlistItem } from '../api/stocks'
import '../styles/screens/watchlist.css'

const DASH = '—'
const won = (n: number) => `${n.toLocaleString('ko-KR')}원`
const signed = (n: number) => `${n > 0 ? '+' : ''}${n.toFixed(2)}%`

export default function Watchlist() {
  /* 모듈 스코프 함수를 그대로 넘긴다 — 매 렌더 새로 만들면 계속 다시 읽는다 */
  const q = useAsync(getWatchlist)

  /* 빼는 중인 종목. 응답을 기다리지 않고 목록에서 먼저 감춘다.
     목록은 훅이 들고 있어 직접 못 고치므로, 감출 코드만 따로 들고
     렌더에서 걸러낸다(B-02 관심 토글과 같은 방식). */
  const [removing, setRemoving] = useState<string[]>([])

  const drop = useCallback(async (item: WatchlistItem) => {
    setRemoving((prev) => [...prev, item.stockCode])
    try {
      await removeWatch(item.stockCode)
    } catch {
      /* 실패하면 되돌린다. 사라진 채 두면 새로고침할 때까지 빠진 것처럼
         보이는데 서버에는 그대로 남아 있다. */
      setRemoving((prev) => prev.filter((c) => c !== item.stockCode))
    }
  }, [])

  const items = useMemo(
    () => (q.data?.items ?? []).filter((s) => !removing.includes(s.stockCode)),
    [q.data, removing],
  )

  /* 담은 게 없는 것과 다 빼버린 것을 같은 화면으로 그린다 — 둘 다
     "이제 담을 차례" 가 할 일이다 */
  const isEmpty = !q.loading && !q.error && items.length === 0

  return (
    <main className="main">
      <div className="main-inner">
        <header className="wl-head">
          <div>
            <h1>관심 종목</h1>
            <p>담아 둔 종목의 전일 종가와 최근 30영업일 흐름입니다. 최근 담은 순입니다.</p>
          </div>
          {items.length > 0 && (
            <p className="wl-count num">
              <b>{items.length}</b>
              <span>종목</span>
            </p>
          )}
        </header>

        {q.error ? (
          <ErrorState error={q.error} onRetry={q.reload} />
        ) : q.loading ? (
          <div className="wl-skel" aria-hidden="true">
            {Array.from({ length: 4 }, (_, i) => <span key={i} />)}
          </div>
        ) : isEmpty ? (
          <EmptyState
            title="담아 둔 종목이 없습니다"
            hint="종목 탐색이나 종목 상세에서 별을 누르면 이곳에 모입니다."
            action={{ label: '종목 탐색으로', to: '/stocks' }}
          />
        ) : (
          <ul className="wl-list">
            {items.map((s) => {
              /* 0% 는 상승도 하락도 아니다. 0 을 상승으로 넣으면 보합인 날과,
                 수집이 하루치뿐이라 서버가 0 으로 내리는 동안(응답 규약:
                 "점이 둘 미만이면 0")까지 전부 빨강으로 칠해져 "다 올랐다" 로
                 읽힌다. 등락률이 없으면(null) 색도 없다. */
              const tone = s.changeRate === null || s.changeRate === 0
                ? '' : s.changeRate > 0 ? ' up' : ' down'
              /* 미니차트 색은 **옆의 등락률을 따른다.** Sparkline 이 그 전제로
                 만들어졌다("옆에 등락률 숫자가 같이 있어 방향을 색으로만
                 전달하지 않는다").

                 선의 양 끝으로 색을 정하면 안 된다. series 는 30영업일이고
                 changeRate 는 전일 대비라, 30일은 올랐지만 어제는 빠진 종목에서
                 선은 빨강 · 숫자는 파랑이 되어 한 줄이 서로를 부정한다.

                 등락률에 부호가 없을 때(0 · null)만 남은 신호인 선의 양 끝을
                 쓴다. 그때 숫자는 회색이라 색이 무엇도 주장하지 않는다. */
              const rising = tone === ' up' ? true
                : tone === ' down' ? false
                : s.series.length < 2 || s.series[s.series.length - 1] >= s.series[0]
              return (
                <li key={s.stockCode} className="wl-row">
                  <Link className="wl-stock" to={`/stocks/${s.stockCode}`}>
                    <b>{s.name}</b>
                    <span className="num">{s.stockCode}</span>
                  </Link>

                  {/* series 가 한 점뿐이면 Sparkline 이 빈 자리를 남긴다.
                      수집 배치가 하루치만 채운 동안에도 줄이 흐트러지지 않는다 */}
                  <span className="wl-spark">
                    <Sparkline series={s.series} up={rising} />
                  </span>

                  <span className="wl-close num">
                    {s.prevClose === null ? DASH : won(s.prevClose)}
                  </span>

                  <span className={`wl-rate num${tone}`}>
                    {s.changeRate === null ? DASH : signed(s.changeRate)}
                  </span>

                  {/* 관심 종목을 보는 이유가 대개 "이제 걸어 볼까" 라서
                      상세를 거치지 않고 예측 탭으로 가는 길을 함께 둔다 */}
                  <Link className="wl-predict" to={`/stocks/${s.stockCode}?tab=predict`}>
                    예측
                  </Link>

                  <button
                    type="button"
                    className="wl-drop"
                    aria-label={`${s.name} 관심 해제`}
                    onClick={() => void drop(s)}
                  >
                    <svg width="16" height="16" viewBox="0 0 24 24" aria-hidden="true">
                      <path d="M12 3.6l2.6 5.3 5.8.8-4.2 4.1 1 5.8-5.2-2.8-5.2 2.8 1-5.8-4.2-4.1 5.8-.8z"
                            fill="currentColor" stroke="currentColor" strokeWidth="1.6" />
                    </svg>
                  </button>
                </li>
              )
            })}
          </ul>
        )}
      </div>
    </main>
  )
}
