/* 시즌 종목 고르기 (모달 · 라우트 없음) — G-04 가 쓴다.

   왜 모달인가. 시즌 종목이 200개다(구간 첫날 시총 상위). 목록을 화면에 늘 세워 두면
   차트가 그만큼 좁아지는데, 종목을 바꾸는 일은 자주 하는 동작이 아니다.

   ── 값에 없는 것 ──────────────────────────────────────────
   GET /seasons/{id}/tickers 는 {tickerId, displayName, sector} 만 준다. 그래서 이 목록에
   <b>현재가와 등락률을 그릴 수 없다</b> — 200종목의 가격을 받으려면 종목마다 /prices 를
   불러야 하고 그건 200번 요청이다. 이미 받아 둔 종목만 가격을 함께 보여준다.

   종목코드로 검색하지 않는다. 응답에 코드가 없다 — 이름으로만 찾는다.
   둘 다 서버가 채워 주면 풀린다(§메모: 목록 응답에 가격·코드 추가 요청).

   ── 높이를 고정한다 ──────────────────────────────────────
   목록 길이에 맞춰 늘었다 줄었다 하면 타자 한 글자마다 상자가 뛴다. "이니" 를 치는
   동안 결과가 200 → 3 → 0 으로 바뀌는데 그때마다 높이가 따라 변하면 읽을 수가 없다.
   그래서 높이를 먼저 정하고 목록만 안에서 구른다.

   ── 업종을 탭이 아니라 드롭다운으로 둔다 ──────────────────
   업종이 스무 개가 넘는다. 칩으로 늘어놓으면 가로 스크롤이 생기고, 스크롤바가 검색칸
   바로 아래에 걸려 지저분하다. 접어 두면 한 줄이면 되고 개수도 같이 보여줄 수 있다.

   모달 규칙은 M-01 지갑 연동(WalletLinkModal)과 같다 — Escape 로 닫고, 열리면 초점을
   안으로 들인다. 없으면 탭이 뒤 화면을 돌아다닌다. */
import { useCallback, useEffect, useMemo, useRef, useState } from 'react'
import type { Ticker } from '../../api/seasonPlay'
import '../../styles/screens/sim-play.css'

type Props = {
  tickers: Ticker[]
  /** 지금 보고 있는 종목 */
  selectedId: number | null
  /** 종목 id → 보유 수량. 보유한 종목을 맨 위로 올린다 */
  heldQty: Record<number, number>
  /** 이미 받아 둔 종목의 현재가. 없는 종목은 가격 칸을 비운다 */
  priceOf: (tickerId: number) => number | null
  onPick: (tickerId: number) => void
  onClose: () => void
}

const ALL = '전체'
const won = (n: number) => Math.round(n).toLocaleString('ko-KR')

export default function TickerPickerModal({
  tickers,
  selectedId,
  heldQty,
  priceOf,
  onPick,
  onClose,
}: Props) {
  const [query, setQuery] = useState('')
  const [sector, setSector] = useState(ALL)
  const dialogRef = useRef<HTMLDivElement>(null)
  const searchRef = useRef<HTMLInputElement>(null)

  const close = useCallback(() => onClose(), [onClose])

  useEffect(() => {
    const onKey = (e: KeyboardEvent) => { if (e.key === 'Escape') close() }
    document.addEventListener('keydown', onKey)
    return () => document.removeEventListener('keydown', onKey)
  }, [close])

  /* 열리면 검색칸에 초점을 준다 — 200개 중에서 찾는 화면이라 타이핑이 첫 동작이다 */
  useEffect(() => { searchRef.current?.focus() }, [])

  /* 섹터 탭. 서버가 종목마다 sector 를 주므로 그걸 모아 만든다 —
     화면에 업종 목록을 하드코딩하지 않는다. 종목 수가 많은 업종을 앞에 둔다. */
  const sectors = useMemo(() => {
    const count = new Map<string, number>()
    tickers.forEach((t) => {
      if (t.sector) count.set(t.sector, (count.get(t.sector) ?? 0) + 1)
    })
    return [
      { name: ALL, count: tickers.length },
      ...[...count.entries()]
        .sort((a, b) => b[1] - a[1])
        .map(([name, c]) => ({ name, count: c })),
    ]
  }, [tickers])

  const rows = useMemo(() => {
    const q = query.trim().toLowerCase()
    return tickers
      .filter((t) => sector === ALL || t.sector === sector)
      .filter((t) => q === '' || t.displayName.toLowerCase().includes(q))
      /* 보유 종목을 맨 위로. 200개 중에서 내 것을 찾는 게 제일 잦은 동작이다.
         그 안에서는 서버가 준 순서(시총순)를 흐트러뜨리지 않는다. */
      .sort((a, b) => (heldQty[b.tickerId] ?? 0 ? 1 : 0) - (heldQty[a.tickerId] ?? 0 ? 1 : 0))
  }, [tickers, sector, query, heldQty])

  const heldCount = Object.values(heldQty).filter((q) => q > 0).length

  return (
    <div className="tp-backdrop" onClick={close}>
      <div
        ref={dialogRef}
        className="tp-modal"
        role="dialog"
        aria-modal="true"
        aria-labelledby="tp-title"
        tabIndex={-1}
        onClick={(e) => e.stopPropagation()}
      >
        <div className="tp-head">
          <h2 id="tp-title">종목 고르기</h2>
          <button type="button" className="tp-x" onClick={close} aria-label="닫기">×</button>
        </div>

        <div className="tp-tools">
          <label className="tp-search">
            <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor"
                 strokeWidth="2" strokeLinecap="round" aria-hidden="true">
              <circle cx="11" cy="11" r="7" /><path d="m20 20-4.3-4.3" />
            </svg>
            {/* 종목코드로는 못 찾는다 — 목록 응답에 코드가 없다 */}
            <input
              ref={searchRef}
              type="search"
              value={query}
              onChange={(e) => setQuery(e.target.value)}
              placeholder="종목명으로 검색 (예: 삼성전자)"
              aria-label="종목명 검색"
            />
          </label>

          <div className="tp-filter">
            <label className="tp-pick">
              <span>업종</span>
              <select value={sector} onChange={(e) => setSector(e.target.value)}>
                {sectors.map((x) => (
                  <option key={x.name} value={x.name}>
                    {x.name} ({x.count})
                  </option>
                ))}
              </select>
              <i aria-hidden="true">⌄</i>
            </label>
            <p className="tp-count num">
              {rows.length}종목
              {heldCount > 0 && <span> · 보유 {heldCount}종목 위</span>}
            </p>
          </div>
        </div>

        {/* 목록이 이 칸 안에서만 구른다. 비어도 칸 높이가 그대로라 상자가 안 뛴다 */}
        <ul className="tp-list">
          {rows.length === 0 && (
            <li className="tp-empty">
              {query.trim() ? `"${query.trim()}" 에 맞는 종목이 없습니다` : '종목이 없습니다'}
            </li>
          )}
          {rows.map((t) => {
            const qty = heldQty[t.tickerId] ?? 0
            const price = priceOf(t.tickerId)
            return (
              <li key={t.tickerId}>
                <button
                  type="button"
                  className={t.tickerId === selectedId ? 'tp-row on' : 'tp-row'}
                  onClick={() => { onPick(t.tickerId); close() }}
                >
                  <b>{t.displayName}</b>
                  {t.sector && <span className="tp-sector">{t.sector}</span>}
                  {qty > 0 && <span className="tp-held num">{qty.toLocaleString('ko-KR')}주</span>}
                  {/* 이미 받아 둔 종목만 가격이 있다. 없으면 칸을 비운다 */}
                  <span className="tp-price num">{price === null ? '' : `${won(price)}원`}</span>
                </button>
              </li>
            )
          })}
        </ul>
      </div>
    </div>
  )
}
