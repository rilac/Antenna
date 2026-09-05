/* B-03 종목 상세 · /stocks/:code
   담당 스토리 [ANT-FE-STOCK-DETAIL]
   설계서 docs/화면설계서.md §3 B · §4 B-03 · §9.2

   화면 구조
   요약 헤더는 스크롤에 붙어 남고, 그 아래가 안쪽 페이지 둘로 갈린다.
     · 종목 정보 — 차트 · 심리 · 브리핑 · 포인트 · 공시/뉴스 · 개요 · 재무 · 밸류 · 경쟁사
     · 예측하기 — C-01 예측 등록
   종목을 보다가 바로 예측하는 흐름이라 페이지를 갈아 끼우지 않는다. 방금 읽던
   근거와 차트가 그대로 남아 있어야 목표가를 정할 수 있다.

   탭은 ?tab= 로 주소에 남긴다. 뒤로가기가 탭 사이를 오가고, 링크로 특정 탭을
   바로 열 수 있다. 상태로만 들고 있으면 새로고침에 첫 탭으로 돌아간다.

   투자 포인트 선택은 두 탭이 함께 쓰므로 여기(부모)에 둔다. 이 인계에는 API 가
   없다 — 클라이언트 상태로 넘기는 게 설계다(§4 B-03). */
import { useCallback, useMemo, useState } from 'react'
import { Link, useParams, useSearchParams } from 'react-router-dom'
import InfoTab from '../components/stockDetail/InfoTab'
import PredictTab from '../components/stockDetail/PredictTab'
import { useBlock } from '../components/stockDetail/useBlock'
import ErrorState from '../components/state/ErrorState'
import { addWatch, removeWatch, MARKET_LABEL } from '../api/stocks'
import { getSummary } from '../api/stockDetail'
import '../styles/screens/stock-detail.css'

const TABS = [
  { key: 'info', label: '종목 정보' },
  { key: 'predict', label: '예측하기' },
] as const
type TabKey = (typeof TABS)[number]['key']

export default function StockDetail() {
  const { code = '' } = useParams<{ code: string }>()
  const [params, setParams] = useSearchParams()

  const tab: TabKey = params.get('tab') === 'predict' ? 'predict' : 'info'
  const goTab = useCallback((next: TabKey) => {
    const p = new URLSearchParams(params)
    if (next === 'info') p.delete('tab')
    else p.set('tab', next)
    /* 탭 전환은 히스토리를 쌓지 않는다. 탭을 다섯 번 오간 뒤 뒤로가기를
       다섯 번 눌러야 목록으로 돌아가면 답답하다. */
    setParams(p, { replace: true })
  }, [params, setParams])

  const summary = useBlock(() => getSummary(code), [code])
  const s = summary.data

  /* 근거 포인트 선택 — 두 탭이 공유한다 */
  const [picked, setPicked] = useState<number[]>([])
  const onPick = useCallback((id: number) => {
    setPicked((prev) => (prev.includes(id) ? prev.filter((x) => x !== id) : [...prev, id]))
  }, [])

  /* 관심 토글은 낙관적으로 먼저 바꾸고 실패하면 되돌린다(§4 B-02 와 같은 규칙) */
  const [watchOverride, setWatchOverride] = useState<boolean | null>(null)
  const watched = watchOverride ?? s?.watched ?? false

  const toggleWatch = useCallback(() => {
    const next = !watched
    setWatchOverride(next)
    const call = next ? addWatch(code) : removeWatch(code)
    call.catch(() => setWatchOverride(!next))
  }, [watched, code])

  const change = useMemo(() => {
    if (!s || s.changeRate === null) return null
    return { up: s.changeRate >= 0, text: `${s.changeRate > 0 ? '+' : ''}${s.changeRate.toFixed(2)}%` }
  }, [s])

  return (
    <main className="main">
      <div className="main-inner">
        {summary.error ? (
          <ErrorState error={summary.error} onRetry={summary.retry} />
        ) : (
          <>
            {/* ── 요약 헤더 ───────────────────────────────────
                스크롤을 내려도 어느 종목을 보는지, 종가가 얼마인지 남아 있어야
                아래쪽 재무·경쟁사를 읽을 때 기준을 잃지 않는다. */}
            <header className="sd-head">
              <div className="sd-head-top">
                <Link className="sd-back" to="/stocks">
                  <svg width="15" height="15" viewBox="0 0 24 24" fill="none" stroke="currentColor"
                       strokeWidth="2.6" strokeLinecap="round" strokeLinejoin="round" aria-hidden="true">
                    <path d="M15 5 8 12l7 7" />
                  </svg>
                  종목 탐색
                </Link>
              </div>

              <div className="sd-ident">
                {summary.loading ? (
                  <span className="sd-skel sd-skel-title" aria-hidden="true" />
                ) : (
                  <>
                    <h1>{s?.name}</h1>
                    <span className="sd-code num">{code}</span>
                    {s?.market && <span className="sd-chip">{MARKET_LABEL[s.market]}</span>}
                    {s?.sector && <span className="sd-chip is-soft">{s.sector}</span>}

                    <button
                      type="button"
                      className={watched ? 'sd-watch is-on' : 'sd-watch'}
                      aria-pressed={watched}
                      onClick={toggleWatch}
                    >
                      <svg width="16" height="16" viewBox="0 0 24 24"
                           fill={watched ? 'currentColor' : 'none'} stroke="currentColor"
                           strokeWidth="2" strokeLinejoin="round" aria-hidden="true">
                        <path d="m12 3.6 2.6 5.3 5.9.9-4.3 4.1 1 5.8-5.2-2.7-5.2 2.7 1-5.8L3.5 9.8l5.9-.9z" />
                      </svg>
                      {watched ? '관심 종목' : '관심 담기'}
                    </button>
                  </>
                )}
              </div>

              {/* 전일 종가만 쓴다. "현재가" 라는 말을 만들지 않는다(§7 legal) */}
              <div className="sd-price">
                {summary.loading ? (
                  <span className="sd-skel sd-skel-price" aria-hidden="true" />
                ) : s?.prevClose === null ? (
                  <p className="sd-halted">이 종목은 해당 영업일에 거래가 없었습니다</p>
                ) : (
                  <>
                    <b className="num">{s?.prevClose?.toLocaleString('ko-KR')}<span>원</span></b>
                    {change && (
                      <span className={`sd-change num ${change.up ? 'up' : 'down'}`}>{change.text}</span>
                    )}
                    <span className="sd-asof num">{s?.asOf?.replace(/-/g, '.')} 종가</span>
                  </>
                )}
              </div>

              {/* ── 안쪽 페이지 전환 ────────────────────────── */}
              <div className="sd-tabs" role="tablist" aria-label="종목 상세 보기">
                {TABS.map((t) => (
                  <button
                    key={t.key}
                    type="button"
                    role="tab"
                    id={`sd-tab-${t.key}`}
                    aria-selected={tab === t.key}
                    aria-controls={`sd-panel-${t.key}`}
                    className={tab === t.key ? 'is-on' : undefined}
                    onClick={() => goTab(t.key)}
                  >
                    {t.label}
                    {t.key === 'predict' && s && s.predictionCount > 0 && (
                      <span className="sd-tab-count num">{s.predictionCount}</span>
                    )}
                  </button>
                ))}
              </div>
            </header>

            <div
              role="tabpanel"
              id={`sd-panel-${tab}`}
              aria-labelledby={`sd-tab-${tab}`}
              tabIndex={-1}
            >
              {tab === 'info' ? (
                <InfoTab
                  code={code}
                  summary={s}
                  picked={picked}
                  onPick={onPick}
                  onGoPredict={() => goTab('predict')}
                />
              ) : (
                <PredictTab
                  code={code}
                  summary={s}
                  picked={picked}
                  onPick={onPick}
                  onGoInfo={() => goTab('info')}
                />
              )}
            </div>
          </>
        )}
      </div>
    </main>
  )
}
