/* C-02 내 예측 · /me/predictions
   담당 스토리 [ANT-FE-PREDICT-LIST]
   설계서 docs/화면설계서.md §3 C · §4 C-02 · §7

   설계 제약
   - 응답 필드는 명세가 못 박은 아홉 개(id · stockCode · direction · targetPrice ·
     horizon · status · dday · errorRate · settleDate)에 stockName 하나를 더한
     것뿐이다. **그 밖의 값을 화면에서 만들어내지 않는다.**
     stockName 은 팀에서 추가하기로 정한 필드이고 서버에 아직 없다 — 그래서
     비었을 때 종목코드로 대체한다(api/predictions.ts 머리말).
   - **수정·삭제 버튼을 두지 않는다.** 해당 API 가 없고, 예측은 등록 후 불변이다.
   - 상태 필터는 서버가 거른다. 커서 페이징이라 클라이언트에서 거르면 페이지마다
     줄 수가 들쭉날쭉해진다.
   - 앵커 상태는 AnchorBadge 를 쓰라고 되어 있지만 이 응답에 앵커 값이 없다.
     컴포넌트도 아직 없어 D-01 에서 함께 만든다 — 값이 오지 않는 자리를 미리
     비워 두지 않는다.

   대기 건과 판정 건은 읽는 법이 다르다. 대기는 "언제 끝나나"(D-day)이고 판정은
   "얼마나 맞혔나"(오차)다. 그래서 한 줄 안에서 그 칸만 갈아 끼운다 — 목록을
   둘로 쪼개지 않는 이유는 여기가 내 기록을 통째로 훑는 자리이기 때문이다. */
import { useMemo, useState } from 'react'
import { Link } from 'react-router-dom'
import { useCursorList } from '../api/useCursorList'
import { MY_FILTERS, MY_FILTER_LABEL, fetchMyPredictions, phaseOf } from '../api/predictions'
import type { MyFilter, MyPrediction, MyPredictionMeta } from '../api/predictions'
import PredictionStatus from '../components/prediction/PredictionStatus'
import EmptyState from '../components/state/EmptyState'
import ErrorState from '../components/state/ErrorState'
import '../styles/screens/predict-list.css'

const won = (n: number) => `${n.toLocaleString('ko-KR')}원`
const day = (iso: string) => iso.replace(/-/g, '.').slice(2)

export default function PredictList() {
  const [filter, setFilter] = useState<MyFilter>('ALL')

  /* 필터가 바뀌면 새 fetcher 가 만들어져 훅이 처음부터 다시 읽는다 */
  const list = useCursorList<MyPrediction, MyPredictionMeta>(fetchMyPredictions(filter))

  /* 건수·적중률은 목록 전체 값이라 서버가 준다. 불러온 페이지로 세면
     "더 보기" 를 누를 때마다, 필터를 바꿀 때마다 숫자가 흔들린다. */
  const count = useMemo<Record<MyFilter, number | null>>(() => ({
    ALL: list.meta?.total ?? null,
    PENDING: list.meta?.pendingCount ?? null,
    JUDGED: list.meta?.judgedCount ?? null,
  }), [list.meta])

  const hitRate = list.meta?.hitRate ?? null
  const isEmpty = !list.loading && !list.error && list.items.length === 0

  return (
    <main className="main">
      <div className="main-inner">
        <header className="mp-head">
          <div>
            <h1>내 예측</h1>
            <p>등록한 예측과 판정 결과를 모아 봅니다. 등록한 예측은 수정하거나 삭제할 수 없습니다.</p>
          </div>
          {/* 적중률은 판정이 끝난 건에 대한 값이다. 판정 건이 없으면 아예 두지 않는다 —
              0% 로 그리면 다 틀린 것처럼 보인다 */}
          {hitRate !== null && (
            <p className="mp-hit">
              <b className="num">{`${hitRate}%`}</b>
              <span>{`판정 ${count.JUDGED}건 적중률`}</span>
            </p>
          )}
        </header>

        {/* 상태 거르개. 알약 모양으로 두고 건수를 함께 보여준다 */}
        <div className="mp-filters" role="group" aria-label="상태 필터">
          {MY_FILTERS.map((f) => (
            <button
              key={f}
              type="button"
              className={f === filter ? 'is-on' : undefined}
              aria-pressed={f === filter}
              onClick={() => setFilter(f)}
            >
              {MY_FILTER_LABEL[f]}
              {count[f] !== null && <span className="mp-n num">{count[f]}</span>}
            </button>
          ))}
        </div>

        {list.error ? (
          <ErrorState error={list.error} onRetry={list.reload} />
        ) : list.loading && list.items.length === 0 ? (
          <div className="mp-skel" aria-hidden="true">
            {Array.from({ length: 5 }, (_, i) => <span key={i} />)}
          </div>
        ) : isEmpty ? (
          <EmptyState
            title={filter === 'JUDGED' ? '판정이 끝난 예측이 없습니다' : '아직 등록한 예측이 없습니다'}
            hint={filter === 'JUDGED'
              ? '만기가 지나면 결과와 오차가 이곳에 쌓입니다.'
              : '종목을 고르고 방향과 목표가를 정해 첫 예측을 남겨 보세요.'}
            action={{ label: '종목 탐색으로', to: '/stocks' }}
          />
        ) : (
          <>
            <ul className="mp-list">
              {list.items.map((p) => (
                <li key={p.id} className="mp-row">
                  {/* 종목명을 앞세우고 코드를 아래에 둔다. 이름으로 알아보고
                      코드로 정확히 짚는다. 이름이 아직 안 오면 코드만 남는다 */}
                  <Link className="mp-stock" to={`/stocks/${p.stockCode}`}>
                    <b>{p.stockName ?? p.stockCode}</b>
                    {p.stockName && <span className="num">{p.stockCode}</span>}
                  </Link>

                  <p className="mp-call num">
                    <b className={p.direction === 'UP' ? 'up' : 'down'}>
                      {p.direction === 'UP' ? '상승' : '하락'}
                    </b>
                    <span className="mp-target">{won(p.targetPrice)}</span>
                  </p>

                  {/* 대기 건은 남은 일수, 판정 건은 오차. 서로 배타적인 값이라
                      같은 칸을 나눠 쓴다 */}
                  <p className="mp-result num">
                    {phaseOf(p.status) === 'JUDGED' ? (
                      p.errorRate === null ? (
                        <span className="mp-none">—</span>
                      ) : (
                        <span className={Math.abs(p.errorRate) <= 3 ? 'mp-err is-near' : 'mp-err'}>
                          {`오차 ${p.errorRate > 0 ? '+' : ''}${p.errorRate}%`}
                        </span>
                      )
                    ) : p.dday === null ? (
                      /* 기준가 대기(BASE)는 기준일이 아직 없어 남은 일수를 셀 수 없다.
                         0 으로 그리면 "오늘 만기" 로 읽힌다 */
                      <span className="mp-none">기준가 확정 후</span>
                    ) : (
                      <span className="mp-dday">{p.dday === 0 ? '오늘 만기' : `D-${p.dday}`}</span>
                    )}
                  </p>

                  <p className="mp-when num">
                    <span>{`${p.horizon}거래일`}</span>
                    <span className="mp-date">{`${day(p.settleDate)} 만기`}</span>
                  </p>

                  <PredictionStatus status={p.status} />

                  {/* 수정·삭제는 두지 않는다(§4 C-02). 상세로 가는 길만 준다 */}
                  <Link
                    className="mp-more"
                    to={`/predictions/${p.id}`}
                    aria-label={`${p.stockName ?? p.stockCode} 예측 상세`}
                  >
                    <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor"
                         strokeWidth="2.4" strokeLinecap="round" strokeLinejoin="round" aria-hidden="true">
                      <path d="m9 6 6 6-6 6" />
                    </svg>
                  </Link>
                </li>
              ))}
            </ul>

            {list.hasNext && (
              <button type="button" className="mp-load" disabled={list.loading} onClick={list.loadMore}>
                {list.loading ? '불러오는 중…' : '더 보기'}
              </button>
            )}
          </>
        )}
      </div>
    </main>
  )
}
