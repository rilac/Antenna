/* 포트폴리오 도넛. G-04 아래쪽 "내 포트폴리오 요약" 카드가 쓴다.

   ── 왜 현금까지 한 조각으로 넣는가 ──────────────────────
   이 화면에서 보고 싶은 건 "내 돈이 지금 어디에 있나" 다. 주식만 그리면 3,000만원
   중 300만원만 넣은 사람과 다 넣은 사람이 똑같은 원으로 보인다. 현금을 조각으로
   넣어야 비중이 뜻을 갖는다.

   ── 조각 수를 자른다 ────────────────────────────────────
   종목이 200개라 다 보유하면 원이 실오라기 200개가 된다. 상위 넷만 남기고 나머지는
   "기타" 하나로 접는다 — 접은 것도 금액은 그대로 세므로 합은 늘 총자산이다.

   라이브러리를 쓰지 않는다. 원 하나에 stroke-dasharray 로 조각을 끊으면 되고,
   이 하나 때문에 차트 라이브러리를 넣으면 번들만 커진다. */
import type { CSSProperties } from 'react'

export type DonutSlice = {
  key: string
  label: string
  value: number
  /** 종목 조각만 갖는다. 누르면 그 종목으로 차트를 바꾼다 */
  tickerId?: number
  /** 종목 조각의 아랫줄 — 수량·평단·손익 */
  detail?: string
  pnl?: number
}

type Props = {
  slices: DonutSlice[]
  total: number
  onPick?: (tickerId: number) => void
}

/* 보라 계열에서 시작해 초록·주황으로 벌린다. 옆 조각끼리 색상환에서 멀어야
   가는 조각도 구분된다. 현금은 무채색이다 — 투자한 돈이 아니라 남은 돈이라
   종목들과 같은 채도로 두면 종목처럼 읽힌다. */
const PIE = ['#5457e8', '#7b61ff', '#2f7bf6', '#16a06a', '#e8a13c']
const CASH = '#c3c8dc'

const R = 46
const C = 2 * Math.PI * R

const won = (n: number) => `${Math.round(n).toLocaleString('ko-KR')}원`
const pct = (n: number) => `${n.toFixed(1)}%`

export default function PortfolioDonut({ slices, total, onPick }: Props) {
  const colorOf = (s: DonutSlice, i: number) =>
    s.key === 'CASH' ? CASH : PIE[i % PIE.length]

  /* 조각을 잇는다. 앞 조각들의 길이 합만큼 뒤로 밀면 그 다음이 이어 붙는다.
     -90도 돌려 12시에서 시작한다 — 안 돌리면 3시에서 시작해 어디가 처음인지 모른다. */
  const lens = slices.map((s) => (total > 0 ? (C * s.value) / total : 0))
  const arcs = slices.map((s, i) => ({
    key: s.key,
    color: colorOf(s, i),
    dash: `${lens[i].toFixed(2)} ${(C - lens[i]).toFixed(2)}`,
    offset: (-lens.slice(0, i).reduce((a, b) => a + b, 0)).toFixed(2),
  }))

  return (
    <div className="pd">
      <div className="pd-ring">
        <svg viewBox="0 0 120 120" role="img" aria-label={`총 자산 ${won(total)}`}>
          {/* 바탕 고리. 총자산이 0 이어도 원이 사라지지 않게 둔다 */}
          <circle className="pd-track" cx="60" cy="60" r={R} />
          {arcs.map((a) => (
            <circle
              key={a.key}
              cx="60"
              cy="60"
              r={R}
              stroke={a.color}
              strokeDasharray={a.dash}
              strokeDashoffset={a.offset}
              transform="rotate(-90 60 60)"
            />
          ))}
        </svg>
        <div className="pd-center">
          <span>총 자산</span>
          <b className="num">{won(total)}</b>
        </div>
      </div>

      <ul className="pd-legend">
        {slices.map((s, i) => {
          const share = total > 0 ? (s.value / total) * 100 : 0
          const dot = { '--dot': colorOf(s, i) } as CSSProperties
          const body = (
            <>
              <span className="pd-name">
                <i style={dot} aria-hidden="true" />
                {s.label}
              </span>
              <span className="pd-share num">{pct(share)}</span>
              <span className="pd-val num">{won(s.value)}</span>
              {s.detail && <span className="pd-detail num">{s.detail}</span>}
              {s.pnl !== undefined && (
                <span className={`pd-pnl num ${s.pnl > 0 ? 'up' : s.pnl < 0 ? 'down' : 'flat'}`}>
                  {s.pnl > 0 ? '+' : ''}{won(s.pnl)}
                </span>
              )}
            </>
          )
          return (
            <li key={s.key} className={s.detail ? 'has-detail' : undefined}>
              {s.tickerId !== undefined && onPick ? (
                <button type="button" onClick={() => onPick(s.tickerId as number)}>
                  {body}
                </button>
              ) : (
                <div>{body}</div>
              )}
            </li>
          )
        })}
      </ul>
    </div>
  )
}
