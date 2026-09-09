/* B-02 종목 탐색 · /stocks
   담당 스토리 [ANT-FE-STOCKS]

   컨트롤은 API 파라미터와 1:1 이다(§4 B-02) — sector · market · sentiment ·
   perMin/perMax · hasOpenPrediction · watchedOnly · sort(5종 고정).
   지금 서버에는 GET /stocks 최소판(cursor · size)만 있어 api/stocks.ts 의
   MOCK 으로 그린다. 백엔드에 요청할 목록은 그 파일 머리말에 적어 두었다.

   실전 시세는 전일 종가만이다(§7). "현재가" 라는 말을 쓰지 않고, 기준 영업일은
   행마다 반복하지 않고 머리글에 한 번 찍는다(응답 baseDate).

   행별 UP/DOWN 집계는 응답에 실려 온다 — 행마다 /sentiment 를 부르지 않는다.
   페이지 번호는 두지 않는다(커서 페이징이라 성립하지 않는다 · §9.2).
   행을 누르면 B-03 종목 상세로 간다. */
import { useCallback, useMemo, useState } from 'react'
import { Link } from 'react-router-dom'
import {
  addWatch, EMPTY_FILTER, fetchStocks, getSectorSummary, MARKET_LABEL, removeWatch,
  SORT_LABEL, STOCK_SORTS,
} from '../api/stocks'
import type {
  Market, SectorSummary, Sentiment, StockFilter, StockListItem, StockListMeta, StockSort,
} from '../api/stocks'
import { useAsync } from '../api/useAsync'
import { useCursorList } from '../api/useCursorList'
import EmptyState from '../components/state/EmptyState'
import ErrorState from '../components/state/ErrorState'
import '../styles/screens/stocks.css'

const won = (n: number) => n.toLocaleString('ko-KR')
/* 0 에는 부호를 붙이지 않고 색도 주지 않는다 — 근거는 Watchlist.tsx(B-04). */
const signed = (n: number) => `${n > 0 ? '+' : ''}${n.toFixed(2)}%`
const toneOf = (rate: number | null) =>
  rate === null || rate === 0 ? '' : rate > 0 ? 'up' : 'down'
const dotted = (iso: string) => iso.replaceAll('-', '.')
/** 값이 없는 칸. 0 으로 그리면 "PER 0 배" 로 읽힌다 */
const DASH = '—'

const MARKET_CHOICES: (Market | undefined)[] = [undefined, 'KOSPI', 'KOSDAQ']
const SENTIMENT_LABEL: Record<Sentiment, string> = { UP: '상승 우세', DOWN: '하락 우세' }

/* 종목 로고 자산이 없어 이름 첫 글자로 배지를 만든다. 색은 종목코드에서 뽑아
   항상 같은 색이 나오게 한다(무작위가 아니다).
   B-01 홈에도 같은 배지가 있다 — 세 번째 화면이 필요해지면 공용으로 뽑는다. */
function StockBadge({ code, name }: { code: string; name: string }) {
  let h = 0
  for (const ch of code) h = (h * 31 + ch.charCodeAt(0)) % 360
  return (
    <span className="st-logo" aria-hidden="true"
          style={{ background: `hsl(${h} 62% 92%)`, color: `hsl(${h} 54% 36%)` }}>
      {name.slice(0, 1)}
    </span>
  )
}

/* 예측 심리 막대. UP 과 DOWN 이 한 줄을 나눠 갖는다 — 두 개를 따로 그리면
   합이 100 이라는 게 보이지 않는다. 비율만으로 방향을 전달하지 않도록
   우세 쪽 라벨과 퍼센트를 함께 적는다. */
function SentimentBar({ upRatio, count }: { upRatio: number | null; count: number }) {
  if (upRatio === null || count === 0) {
    return <span className="st-senti-none">예측 없음</span>
  }
  const up = upRatio >= 50
  return (
    <span className="st-senti">
      <span className="st-senti-head">
        <b className={up ? 'up' : 'down'}>
          {up ? 'UP' : 'DOWN'} {Math.round(up ? upRatio : 100 - upRatio)}%
        </b>
        <em className="num">{count}건</em>
      </span>
      <span className="st-senti-track" aria-hidden="true">
        <i className="st-senti-up" style={{ width: `${upRatio}%` }} />
        <i className="st-senti-down" style={{ width: `${100 - upRatio}%` }} />
      </span>
    </span>
  )
}

/** 상단 섹터 요약 칩. 누르면 섹터 필터가 즉시 바뀐다. */
function SectorChips({ current, onPick }: {
  current: string | undefined
  onPick: (sector: string | undefined) => void
}) {
  const load = useCallback(() => getSectorSummary(), [])
  const { data, error } = useAsync(load)
  if (error || !data) return null

  return (
    <div className="st-sectors" role="group" aria-label="섹터별 보기">
      {data.items.map((s: SectorSummary) => {
        const on = (s.sector ?? undefined) === current
        return (
          <button key={s.sector ?? 'ALL'} type="button"
                  className={`st-sector-chip${on ? ' on' : ''}`}
                  aria-pressed={on}
                  onClick={() => onPick(s.sector ?? undefined)}>
            <span className="st-sector-name">{s.sector ?? '전체 종목'}</span>
            <span className="st-sector-meta">
              <em className="num">{s.count}개</em>
              <b className={`num ${toneOf(s.changeRate)}`}>{signed(s.changeRate)}</b>
            </span>
          </button>
        )
      })}
    </div>
  )
}

export default function Stocks() {
  /* 적용된 필터와 편집 중인 값을 나눈다 — 좌측 패널은 "필터 적용" 을 눌러야
     반영되고, 상단 섹터 칩은 즉시 반영된다(탐색용이라 한 번에 바뀌는 게 낫다). */
  const [filter, setFilter] = useState<StockFilter>(EMPTY_FILTER)
  const [draft, setDraft] = useState<StockFilter>(EMPTY_FILTER)

  /* 관심 토글은 낙관적으로 먼저 바꾼다(§4 B-02). 목록은 훅이 들고 있어 직접
     고칠 수 없으므로, 바뀐 종목만 여기 얹고 렌더에서 덮어쓴다. 실패하면 되돌린다.

     어느 필터의 결과에 얹은 보정인지 함께 들고 있다 — 필터가 바뀌면 목록이
     처음부터 다시 오므로 보정도 버려야 한다. effect 로 비우면 렌더가 한 번 더
     돌아서(useCursorList 주석의 규칙) 렌더 중에 판단한다. */
  const [fix, setFix] = useState<{ for: StockFilter; map: Record<string, boolean> }>(
    { for: filter, map: {} },
  )
  const watchFix = fix.for === filter ? fix.map : {}

  const perInvalid =
    draft.perMin != null && draft.perMax != null && draft.perMin > draft.perMax

  /* filter 가 바뀔 때만 새 조회 함수를 만든다. 매 렌더 새로 만들면 훅이
     끝없이 다시 읽는다. filter 는 setFilter 로만 바뀌므로 참조가 안정적이다. */
  const source = useMemo(() => fetchStocks(filter), [filter])
  const list = useCursorList<StockListItem, StockListMeta>(source)

  const baseDate = list.meta?.baseDate ?? null
  const isEmpty = !list.loading && !list.error && list.items.length === 0

  const pickSector = (sector: string | undefined) => {
    setFilter((f) => ({ ...f, sector }))
    setDraft((d) => ({ ...d, sector }))
  }

  /** 같은 필터에 얹혀 있던 보정만 이어받아 한 종목을 덮어쓴다 */
  const putFix = (code: string, on: boolean) =>
    setFix((s) => ({
      for: filter,
      map: { ...(s.for === filter ? s.map : {}), [code]: on },
    }))

  const toggleWatch = async (s: StockListItem) => {
    const now = watchFix[s.code] ?? s.watched
    putFix(s.code, !now)
    try {
      await (now ? removeWatch(s.code) : addWatch(s.code))
    } catch {
      /* 서버가 거절하면 되돌린다 — 눌린 채로 남으면 담긴 줄 알고 넘어간다 */
      putFix(s.code, now)
    }
  }

  return (
    <main className="main">
      <div className="main-inner">
        <header className="st-hero">
          <div>
            <p className="st-eyebrow">MARKET EXPLORER</p>
            <h1>어떤 섹터가 움직이고 있나요?</h1>
            <p className="st-hero-sub">시장 지표와 검증 가능한 예측 흐름을 함께 비교해 보세요.</p>
          </div>
          {baseDate && (
            <p className="st-asof">
              <i aria-hidden="true" />
              직전 영업일 종가 · <b className="num">{dotted(baseDate)}</b>
            </p>
          )}
        </header>

        <SectorChips current={filter.sector} onPick={pickSector} />

        <div className="st-body">
          {/* ── 좌측 상세 필터 ───────────────────────────── */}
          <form className="card st-filter"
                onSubmit={(e) => { e.preventDefault(); if (!perInvalid) setFilter(draft) }}>
            <h2>상세 필터</h2>

            <fieldset className="st-field">
              <legend>시장</legend>
              <div className="st-chips">
                {MARKET_CHOICES.map((m) => (
                  <button key={m ?? 'ALL'} type="button"
                          className={`st-chip${draft.market === m ? ' on' : ''}`}
                          aria-pressed={draft.market === m}
                          onClick={() => setDraft((d) => ({ ...d, market: m }))}>
                    {m ? MARKET_LABEL[m] : '전체'}
                  </button>
                ))}
              </div>
            </fieldset>

            <fieldset className="st-field">
              <legend>예측 방향</legend>
              <div className="st-chips">
                <button type="button" className={`st-chip${!draft.sentiment ? ' on' : ''}`}
                        aria-pressed={!draft.sentiment}
                        onClick={() => setDraft((d) => ({ ...d, sentiment: undefined }))}>
                  전체
                </button>
                {(Object.keys(SENTIMENT_LABEL) as Sentiment[]).map((s) => (
                  <button key={s} type="button"
                          className={`st-chip${draft.sentiment === s ? ' on' : ''}`}
                          aria-pressed={draft.sentiment === s}
                          onClick={() => setDraft((d) => ({ ...d, sentiment: s }))}>
                    {SENTIMENT_LABEL[s]}
                  </button>
                ))}
              </div>
            </fieldset>

            <fieldset className="st-field">
              <legend>PER 범위</legend>
              <div className="st-range">
                <input type="number" inputMode="decimal" min="0" step="0.1" placeholder="최소"
                       aria-label="PER 최소" aria-invalid={perInvalid || undefined}
                       value={draft.perMin ?? ''}
                       onChange={(e) => setDraft((d) => ({
                         ...d, perMin: e.target.value === '' ? undefined : Number(e.target.value),
                       }))} />
                <span aria-hidden="true">–</span>
                <input type="number" inputMode="decimal" min="0" step="0.1" placeholder="최대"
                       aria-label="PER 최대" aria-invalid={perInvalid || undefined}
                       value={draft.perMax ?? ''}
                       onChange={(e) => setDraft((d) => ({
                         ...d, perMax: e.target.value === '' ? undefined : Number(e.target.value),
                       }))} />
              </div>
              {/* 서버는 역전을 400 으로 거절한다 — 보내기 전에 여기서 막는다 */}
              {perInvalid && (
                <p className="st-field-err" role="alert">최소가 최대보다 큽니다</p>
              )}
            </fieldset>

            <fieldset className="st-field">
              <legend>추가 조건</legend>
              <label className="st-check">
                <input type="checkbox" checked={draft.hasOpenPrediction ?? false}
                       onChange={(e) => setDraft((d) => ({
                         ...d, hasOpenPrediction: e.target.checked || undefined,
                       }))} />
                내가 진행 중인 예측 있음
              </label>
              <label className="st-check">
                <input type="checkbox" checked={draft.watchedOnly ?? false}
                       onChange={(e) => setDraft((d) => ({
                         ...d, watchedOnly: e.target.checked || undefined,
                       }))} />
                관심 종목만 보기
              </label>
            </fieldset>

            <button type="submit" className="st-apply" disabled={perInvalid}>필터 적용</button>
            <button type="button" className="st-reset"
                    onClick={() => { setDraft(EMPTY_FILTER); setFilter(EMPTY_FILTER) }}>
              필터 초기화
            </button>
          </form>

          {/* ── 우측 목록 ────────────────────────────────── */}
          <section className="card st-panel" aria-labelledby="st-list-h">
            <header className="st-panel-head">
              <h2 id="st-list-h">종목 리스트</h2>
              {list.items.length > 0 && (
                <span className="st-count num">{list.items.length}개 종목</span>
              )}
              <label className="st-sort">
                <span className="st-sr">정렬</span>
                <select value={filter.sort}
                        onChange={(e) => {
                          const sort = e.target.value as StockSort
                          setFilter((f) => ({ ...f, sort }))
                          setDraft((d) => ({ ...d, sort }))
                        }}>
                  {STOCK_SORTS.map((s) => (
                    <option key={s} value={s}>{SORT_LABEL[s]}</option>
                  ))}
                </select>
              </label>
            </header>

            {list.error && <ErrorState error={list.error} onRetry={list.reload} inline />}

            {isEmpty && (
              <EmptyState
                title="조건에 맞는 종목이 없습니다"
                hint="필터를 줄이거나 초기화해 보세요."
              />
            )}

            {list.items.length > 0 && (
              <>
                {/* 표가 아니라 목록이다 — 행 전체가 상세로 가는 링크라야 하고
                    tr 은 링크로 감쌀 수 없다. 관심 버튼은 링크 밖 형제로 둔다. */}
                <div className="st-cols" aria-hidden="true">
                  <span>종목</span>
                  <span className="r">전일 종가</span>
                  <span className="r">등락률</span>
                  <span className="r">PER</span>
                  <span className="r">PBR</span>
                  {/* 관심 버튼 칸은 머리글을 두지 않는다 — .st-cols 의
                      padding-right 가 그 자리를 비운다 */}
                  <span>예측 현황</span>
                </div>
                <ul className="st-list">
                  {list.items.map((s) => {
                    const on = watchFix[s.code] ?? s.watched
                    return (
                      <li key={s.code} className="st-item">
                        <Link className="st-link" to={`/stocks/${s.code}`}>
                          <span className="st-id">
                            <StockBadge code={s.code} name={s.name} />
                            <span className="st-id-text">
                              <b className="st-name">{s.name}</b>
                              <small className="st-sub">
                                <span className="num">{s.code}</span>
                                {s.market && <> · {MARKET_LABEL[s.market]}</>}
                                {s.sector && <> · {s.sector}</>}
                              </small>
                            </span>
                          </span>
                          <span className="st-close num">
                            {s.prevClose === null ? DASH : won(s.prevClose)}
                          </span>
                          <span className={`st-rate num ${
                            toneOf(s.changeRate)}`}>
                            {s.changeRate === null ? DASH : signed(s.changeRate)}
                          </span>
                          <span className="st-per num">
                            {s.per === null ? DASH : `${s.per.toFixed(1)}x`}
                          </span>
                          <span className="st-per num">
                            {s.pbr === null ? DASH : `${s.pbr.toFixed(2)}x`}
                          </span>
                          <SentimentBar upRatio={s.upRatio} count={s.predictionCount} />
                        </Link>
                        <button type="button" className={`st-star${on ? ' on' : ''}`}
                                aria-pressed={on}
                                aria-label={`${s.name} 관심 ${on ? '해제' : '추가'}`}
                                onClick={() => void toggleWatch(s)}>
                          <svg width="17" height="17" viewBox="0 0 24 24" aria-hidden="true">
                            <path d="M12 3.6l2.6 5.3 5.8.8-4.2 4.1 1 5.8-5.2-2.8-5.2 2.8 1-5.8-4.2-4.1 5.8-.8z"
                                  fill={on ? 'currentColor' : 'none'}
                                  stroke="currentColor" strokeWidth="1.6" />
                          </svg>
                        </button>
                      </li>
                    )
                  })}
                </ul>
              </>
            )}

            {list.loading && <p className="st-more-hint">불러오는 중…</p>}

            {list.hasNext && !list.loading && (
              <button type="button" className="st-more" onClick={list.loadMore}>더 보기</button>
            )}
            {!list.hasNext && list.items.length > 0 && (
              <p className="st-foot">마지막 종목까지 다 보셨습니다</p>
            )}
          </section>
        </div>
      </div>
    </main>
  )
}
