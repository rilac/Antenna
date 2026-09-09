/* B-03 첫 번째 안쪽 페이지 — 종목을 파악하는 자리.

   배치 의도
   전폭 카드를 세로로 쌓으면 스크롤을 내려야 내용이 나온다. 그래서 12칸 격자에
   카드를 나란히 놓고, 관련 있는 원천을 한 카드로 묶어 줄 수를 줄였다.

     차트(5)          │ 예측 현황 · AI 브리핑(7)
     종목 정보(5)      │ 투자 포인트(7)             ← 개요 + 밸류에이션
     재무(6)          │ 같은 업종(6)
     공시 · 뉴스(12)

   카드 안에서 스크롤하지 않는다. 펼친 카드는 내용을 다 보여준다 — 높이를 묶어
   두면 펼쳤는데도 잘려 보이고 막대가 카드마다 생긴다. 한 줄의 바닥은 CSS 의
   stretch 가 맞춰 주고, 짧은 카드는 아래가 빈다.

   카드는 모두 접힌다. 처음부터 다 펼쳐 두면 필요 없는 정보까지 읽어야 하므로
   먼저 보는 넷만 펼치고 나머지는 접어 둔다(DEFAULT_OPEN). 어느 것을 접었는지는
   이 브라우저에 남아, 다른 종목으로 옮겨도 접어 둔 대로 열린다.

   카드를 합쳤어도 원천마다 BlockState 를 따로 쓴다. /valuation 이 죽어도 같은
   카드의 기업 개요는 그대로 보인다 — "블록 단위로 로딩·실패를 독립 처리" (§4 B-03).

   투자 포인트에서 고른 id 는 두 번째 안쪽 페이지(예측 등록)의 근거로 넘어간다.
   이 인계에는 API 가 없어 부모가 상태로 들고 있다 — 그래서 선택 상태를 props 로 받는다. */
import { useCallback, useMemo, useState } from 'react'
import { Link } from 'react-router-dom'
import CloseChart from '../CloseChart'
import Block, { BlockState, Panel } from './Block'
import { useBlock } from '../../api/useBlock'
import {
  POINT_KINDS, POINT_LABEL, SOURCE_LABEL,
  getDocuments, getFinancials, getPeers, getPoints,
  getPrices, getProfile, getValuation,
} from '../../api/stockDetail'
import type { StockSummary } from '../../api/stockDetail'

/* 차트 기간. 서버는 from·to 를 받으므로 여기서 from 을 만들어 넘긴다. */
const RANGES = [
  { key: '1M', label: '1개월', days: 30 },
  { key: '3M', label: '3개월', days: 92 },
  { key: '6M', label: '6개월', days: 183 },
  { key: '1Y', label: '1년', days: 365 },
] as const
type RangeKey = (typeof RANGES)[number]['key']

/* 이 수보다 예측이 적으면 비율을 흐리게 그린다. 5건은 한 명이 몇 번만 더 걸어도
   비율이 통째로 뒤집히는 구간이라, 또렷하게 보이면 없는 신호를 읽게 된다. */

/* 접었다 펼치는 칸들.

   열 개 블록을 한꺼번에 펼쳐 두면 필요 없는 정보까지 읽어야 한다. 그래서 처음에는
   종목을 파악하는 데 먼저 보는 넷만 펼치고, 더 파고들 때 보는 셋은 접어 둔다.
   접힌 칸은 useBlock 의 enabled 가 꺼져 요청도 나가지 않는다. */
const DEFAULT_OPEN = {
  chart: true,
  sentiment: true,
  profile: true,
  points: true,
  financials: false,
  peers: false,
  documents: false,
}
type SectionKey = keyof typeof DEFAULT_OPEN

/* 접힘 상태는 이 브라우저에만 남긴다. 종목을 옮겨도 "내가 접어 둔 대로" 가
   유지돼야 접는 의미가 있다. 저장이 막힌 환경(사생활 보호 모드 등)에서는
   조용히 기본값으로 돌아간다 — 화면이 멈추지 않는 게 우선이다. */
const OPEN_STORE_KEY = 'antena.b03.sections'

function readOpen(): Record<SectionKey, boolean> {
  try {
    const raw = localStorage.getItem(OPEN_STORE_KEY)
    if (!raw) return DEFAULT_OPEN
    return { ...DEFAULT_OPEN, ...(JSON.parse(raw) as Partial<Record<SectionKey, boolean>>) }
  } catch {
    return DEFAULT_OPEN
  }
}

const num = (n: number | null, unit = '', digits = 0) =>
  n === null
    ? '—'
    : `${n.toLocaleString('ko-KR', { minimumFractionDigits: digits, maximumFractionDigits: digits })}${unit}`

/* 서버는 금액을 **원 단위**로 준다(억원이 아니다). 원 그대로 찍으면 자릿수가
   열 자리를 넘어 표가 읽히지 않으므로 조·억으로 줄인다. */
const money = (n: number | null) => {
  if (n === null) return '—'
  const abs = Math.abs(n)
  if (abs >= 1e12) return `${(n / 1e12).toFixed(1)}조`
  if (abs >= 1e8) return `${Math.round(n / 1e8).toLocaleString('ko-KR')}억`
  return n.toLocaleString('ko-KR')
}

const day = (iso: string) => iso.slice(0, 10).replace(/-/g, '.').slice(2)

type Props = {
  code: string
  summary: StockSummary | null
  /** 예측 근거로 고른 포인트 id */
  picked: number[]
  onPick: (id: number) => void
  /** 예측 등록 탭으로 넘어간다 */
  onGoPredict: () => void
}

export default function InfoTab({ code, summary, picked, onPick, onGoPredict }: Props) {
  const [range, setRange] = useState<RangeKey>('3M')

  /* from 은 range 가 바뀔 때만 새로 만든다. 매 렌더 새 문자열이면
     useBlock 의 deps 가 계속 달라져 끝없이 다시 부른다. */
  const from = useMemo(() => {
    const days = RANGES.find((r) => r.key === range)?.days ?? 92
    const d = new Date()
    d.setDate(d.getDate() - days)
    return d.toISOString().slice(0, 10)
  }, [range])

  const [open, setOpen] = useState(readOpen)
  const toggle = useCallback((key: SectionKey) => {
    setOpen((prev) => {
      const next = { ...prev, [key]: !prev[key] }
      try { localStorage.setItem(OPEN_STORE_KEY, JSON.stringify(next)) } catch { /* 저장 불가 */ }
      return next
    })
  }, [])

  /* 접힌 칸은 부르지 않는다 — 펼치는 순간 그때 부른다 */
  const prices = useBlock(() => getPrices(code, from), [code, from], open.chart)
  const points = useBlock(() => getPoints(code), [code], open.points)
  const documents = useBlock(() => getDocuments(code), [code], open.documents)
  const profile = useBlock(() => getProfile(code), [code], open.profile)
  const financials = useBlock(() => getFinancials(code), [code], open.financials)
  const valuation = useBlock(() => getValuation(code), [code], open.profile)
  const peers = useBlock(() => getPeers(code), [code], open.peers)

  /* 서버가 열마다 배열을 따로 내려주므로 화면이 kind 로 다시 나누지 않는다.
     한 열이 비어도 나머지 두 열은 그대로 그린다. */
  const pointCount = POINT_KINDS.reduce((n, k) => n + (points.data?.[k].length ?? 0), 0)

  return (
    <div className="sd-grid">
      {/* ── 종가 차트 ─────────────────────────────────────── */}
      <Block
        title="종가 추이"
        note={summary?.asOf ? `${summary.asOf.replace(/-/g, '.')} 기준` : undefined}
        span={5}
        open={open.chart}
        onToggle={() => toggle('chart')}
        skeleton={286}
        loading={prices.loading}
        error={prices.error}
        onRetry={prices.retry}
        isEmpty={(prices.data?.items.length ?? 0) < 2}
        empty="이 구간에 쌓인 종가가 없습니다. 거래가 정지된 종목일 수 있습니다."
        actions={
          <div className="sd-range" role="group" aria-label="차트 기간">
            {RANGES.map((r) => (
              <button
                key={r.key}
                type="button"
                className={r.key === range ? 'is-on' : undefined}
                aria-pressed={r.key === range}
                onClick={() => setRange(r.key)}
              >
                {r.label}
              </button>
            ))}
          </div>
        }
      >
        <CloseChart series={prices.data?.items ?? []} label={summary?.name ?? ''} height={286} />
      </Block>

      {/* ── 예측 현황 ─────────────────────────────────────
          종목 AI 브리핑 목록이 있던 자리. 생성 배치를 2026-09-09 에 없앴다 — 같은 탭에
          숫자로 있는 등락률·재무를 문장으로 다시 쓴 것에 종목당 매일 1콜을 썼다.
          뉴스·공시 쪽은 아래 투자 포인트가 담는다 */}
      <Panel
        title="예측 현황"
        span={7}
        open={open.sentiment}
        onToggle={() => toggle('sentiment')}
      >
        {/* 예측 심리는 서버가 아직 없다(GET /stocks/{code}/sentiment).
            목업으로 그리지 않는다 — 옆의 재무가 실제 값이라 같은 화면에서 지어낸 비율이
            참으로 읽힌다. 자리를 비우지도 않는다. 왜 없는지 말한다. */}
        <p className="sd-block-empty">
          예측 심리 집계는 아직 제공되지 않습니다. 준비되면 이 자리에 상승·하락 비율이 표시됩니다.
        </p>
      </Panel>

      {/* ── 종목 정보(개요 · 밸류 · 재무) ────────────────────
          원천이 셋이다. 하나가 죽어도 나머지 둘은 그대로 보인다 */}
      <Panel
        title="종목 정보"
        span={5}
        open={open.profile}
        onToggle={() => toggle('profile')}
      >
        <BlockState
          loading={profile.loading}
          error={profile.error}
          onRetry={profile.retry}
          skeleton={120}
        >
          {/* 서버에 소개 문단과 임직원 수가 없다. listedAt 은 DART 기업개황에 상장일이
              없어 항상 null 이라 자리를 만들지 않는다 — 영영 "—" 일 칸은 두지 않는다 */}
          {profile.data && (
            <dl className="sd-facts">
              <div><dt>대표</dt><dd>{profile.data.ceo ?? '—'}</dd></div>
              <div><dt>설립</dt><dd className="num">{profile.data.establishedAt?.replace(/-/g, '.') ?? '—'}</dd></div>
              <div><dt>업종</dt><dd>{profile.data.industry ?? '—'}</dd></div>
              <div>
                <dt>홈페이지</dt>
                <dd>
                  {profile.data.homepage
                    ? <a href={profile.data.homepage} target="_blank" rel="noreferrer noopener">바로가기</a>
                    : '—'}
                </dd>
              </div>
              {profile.data.address && (
                <div className="is-wide"><dt>주소</dt><dd>{profile.data.address}</dd></div>
              )}
            </dl>
          )}
        </BlockState>

        <hr className="sd-rule" />

        {/* 업종 평균 PER/PBR · EPS · 배당수익률은 두지 않는다(§9.2) —
            /valuation 에 없고 /peers 는 나열이지 평균이 아니다 */}
        <h3 className="sd-sub">
          {'밸류에이션 '}
          {valuation.data?.basedOn.fiscal && (
            <span>{`${valuation.data.basedOn.fiscal} 연간${
              valuation.data.basedOn.fsDiv === 'OFS' ? ' 별도' : ' 연결'} 기준`}</span>
          )}
        </h3>
        <BlockState
          loading={valuation.loading}
          error={valuation.error}
          onRetry={valuation.retry}
          skeleton={54}
        >
          {valuation.data && (
            <>
              <dl className="sd-metrics">
                <div><dt>PER</dt><dd className="num">{num(valuation.data.per, '배', 1)}</dd></div>
                <div><dt>PBR</dt><dd className="num">{num(valuation.data.pbr, '배', 2)}</dd></div>
                <div><dt>ROE</dt><dd className="num">{num(valuation.data.roe, '%', 1)}</dd></div>
                <div><dt>부채비율</dt><dd className="num">{num(valuation.data.debtRatio, '%', 1)}</dd></div>
              </dl>
              {/* 값이 빈 이유를 서버가 문장으로 준다. 빈 칸만 남기면 고장으로 읽힌다 */}
              {valuation.data.basedOn.note && (
                <p className="sd-note-why">{valuation.data.basedOn.note}</p>
              )}
            </>
          )}
        </BlockState>

      </Panel>

      {/* ── 투자 포인트 ───────────────────────────────────── */}
      <Block
        title="투자 포인트"
        note="고른 항목이 예측의 근거로 넘어갑니다"
        span={7}
        open={open.points}
        onToggle={() => toggle('points')}
        skeleton={230}
        loading={points.loading}
        error={points.error}
        onRetry={points.retry}
        isEmpty={points.data !== null && pointCount === 0}
        empty="투자 포인트가 아직 없습니다. 공시·뉴스를 요약하는 기능이 준비되면 이 자리에 채워집니다."
      >
        <div className="sd-points">
          {POINT_KINDS.map((kind) => {
            const list = points.data?.[kind] ?? []
            return (
              <div key={kind} className={`sd-point-col is-${kind}`}>
                <h3>{POINT_LABEL[kind]}</h3>
                {list.length === 0 ? (
                  <p className="sd-point-none">없음</p>
                ) : (
                  <ul>
                    {list.map((p) => {
                      const on = picked.includes(p.id)
                      return (
                        <li key={p.id}>
                          {/* 체크박스로 두는 이유 — 여러 개를 고르는 조작이고,
                              키보드·보조기술이 선택 상태를 그대로 읽는다 */}
                          <label className={on ? 'sd-point is-on' : 'sd-point'}>
                            <input type="checkbox" checked={on} onChange={() => onPick(p.id)} />
                            {/* 제목이 따로 없다. 본문 한 덩어리가 포인트 전체다 */}
                            <span className="sd-point-body">{p.body}</span>
                          </label>
                          {/* 근거 문서가 있으면 원문으로 갈 길을 준다.
                              없는 포인트는 시세·재무 수치에서 나온 종합이라 링크가 없다 */}
                          {p.source && (
                            <span className="sd-point-src">{SOURCE_LABEL[p.source]}</span>
                          )}
                        </li>
                      )
                    })}
                  </ul>
                )}
              </div>
            )
          })}
        </div>

        <p className="sd-point-foot">
          <span className="num">{`${picked.length}개 선택됨`}</span>
          <button type="button" className="sd-cta" onClick={onGoPredict}>
            이 근거로 예측하기
          </button>
        </p>
      </Block>

      {/* ── 재무 · 경쟁사 ──────────────────────────────────
          표 둘은 줄 수가 적어 높이를 고정하지 않는다. 고정하면 두 종목뿐인
          업종에서 카드 아래가 통째로 빈다. */}
      <Block
        title="재무"
        note={financials.data?.items[0]?.fsDiv === 'OFS' ? '별도 기준' : '연결 기준'}
        span={6}
        open={open.financials}
        onToggle={() => toggle('financials')}
        skeleton={140}
        loading={financials.loading}
        error={financials.error}
        onRetry={financials.retry}
        isEmpty={financials.data?.items.length === 0}
        empty="이 종목의 재무는 아직 수집되지 않았습니다."
      >
        <div className="sd-table-wrap">
          <table className="sd-table">
            <thead>
              <tr>
                <th scope="col">기수</th>
                <th scope="col">매출액</th>
                <th scope="col">영업이익</th>
                <th scope="col">순이익</th>
              </tr>
            </thead>
            <tbody>
              {financials.data?.items.map((f) => (
                <tr key={`${f.year}-${f.quarter}`}>
                  {/* 4분기는 연간 보고서다. 그 밖은 분기를 밝혀야 반기·분기가 구분된다 */}
                  <th scope="row" className="num">
                    {f.quarter === 4 ? `${f.year}` : `${f.year} ${f.quarter}Q`}
                  </th>
                  <td className="num">{money(f.revenue)}</td>
                  <td className="num">{money(f.operatingProfit)}</td>
                  <td className="num">{money(f.netIncome)}</td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      </Block>

      <Block
        title="같은 업종"
        note={summary?.sector ?? undefined}
        span={6}
        open={open.peers}
        onToggle={() => toggle('peers')}
        skeleton={140}
        loading={peers.loading}
        error={peers.error}
        onRetry={peers.retry}
        isEmpty={peers.data?.items.length === 0}
        empty="비교할 같은 업종 종목이 없습니다."
      >
        <div className="sd-table-wrap">
          <table className="sd-table">
            <thead>
              {/* 시가총액은 응답에 있지만 그리지 않는다 — §9.2 의 "두지 않는 것" 이다 */}
              <tr>
                <th scope="col">종목</th>
                <th scope="col">전일 종가</th>
                <th scope="col">PER</th>
                <th scope="col">PBR</th>
              </tr>
            </thead>
            <tbody>
              {peers.data?.items.map((p) => (
                <tr key={p.code}>
                  <th scope="row">
                    <Link to={`/stocks/${p.code}`}>{p.name}</Link>
                  </th>
                  <td className="num">{p.prevClose === null ? '—' : `${p.prevClose.toLocaleString('ko-KR')}원`}</td>
                  <td className="num">{num(p.per, '', 1)}</td>
                  <td className="num">{num(p.pbr, '', 2)}</td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      </Block>

      {/* ── 공시·뉴스 ─────────────────────────────────────── */}
      <Block
        title="공시 · 뉴스"
        note="요약과 원문 링크만 제공합니다"
        span={12}
        open={open.documents}
        onToggle={() => toggle('documents')}
        skeleton={200}
        loading={documents.loading}
        error={documents.error}
        onRetry={documents.retry}
        isEmpty={documents.data?.items.length === 0}
      >
        {/* 원문 본문을 저장하지 않으므로 전문을 그리지 않는다(§4 B-03).
            제목을 눌러 원문으로 나간다 — 새 탭이라 이 화면의 작성 중 내용이 날아가지 않는다 */}
        <ul className="sd-docs">
          {documents.data?.items.map((d) => (
            <li key={d.id}>
              <span className={`sd-doc-kind is-${d.source.toLowerCase()}`}>
                {SOURCE_LABEL[d.source]}
              </span>
              <div className="sd-doc-body">
                <a href={d.originUrl} target="_blank" rel="noreferrer noopener">
                  {d.title}
                  <svg width="13" height="13" viewBox="0 0 24 24" fill="none" stroke="currentColor"
                       strokeWidth="2.4" strokeLinecap="round" strokeLinejoin="round" aria-hidden="true">
                    <path d="M13 5h6v6M19 5l-8 8M18 14v4a1 1 0 0 1-1 1H6a1 1 0 0 1-1-1V7a1 1 0 0 1 1-1h4" />
                  </svg>
                </a>
                {/* 공시는 발췌가 없어 제목만 보여준다 */}
                {d.snippet && <p>{d.snippet}</p>}
                <span className="sd-doc-meta num">{day(d.publishedAt)}</span>
              </div>
            </li>
          ))}
        </ul>
      </Block>
    </div>
  )
}
