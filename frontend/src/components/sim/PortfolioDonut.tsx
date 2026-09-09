/* 포트폴리오 도넛. G-04 아래쪽 "내 포트폴리오 요약" 과 G-08 결과가 같이 쓴다.

   ── 왜 현금까지 한 조각으로 넣는가 ──────────────────────
   이 화면에서 보고 싶은 건 "내 돈이 지금 어디에 있나" 다. 주식만 그리면 3,000만원
   중 300만원만 넣은 사람과 다 넣은 사람이 똑같은 원으로 보인다. 현금을 조각으로
   넣어야 비중이 뜻을 갖는다.

   ── 조각 수를 자른다 ────────────────────────────────────
   종목이 200개라 다 보유하면 원이 실오라기 200개가 된다. 상위 넷만 남기고 나머지는
   "기타" 하나로 접는다 — 접은 것도 금액은 그대로 세므로 합은 늘 총자산이다.

   ── 목록도 접는다(maxRows) ──────────────────────────────
   원은 조각을 접어 짧아지지만 목록은 종목마다 두 줄이라 금세 길어진다. 이 카드가
   옆 칸보다 길어지면 줄 높이를 이 카드가 정해 버려 차트 아래가 빈다. 앞의 몇 개만
   두고 나머지는 "더 보기" 로 접는다 — 현금은 접지 않는다. 얼마가 남았는지는 늘
   보여야 다음 주문을 정할 수 있다.

   라이브러리를 쓰지 않는다. 원 하나에 stroke-dasharray 로 조각을 끊으면 되고,
   이 하나 때문에 차트 라이브러리를 넣으면 번들만 커진다. */
import { useState, type CSSProperties } from 'react'
import '../../styles/portfolio-donut.css'

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
  /**
   * 접힌 줄에 넣는 작은 고리. 목록도 가운데 글자도 없이 원만 그린다.
   *
   * <p>카드를 접어도 비중은 보여야 해서 둔다 — 접으면 원이 사라지는 것이 이 화면에서
   * 제일 아쉬운 부분이었다. 작아서 정확한 값은 못 읽지만 "거의 다 현금" 같은 덩어리는
   * 읽힌다. 정확한 값은 펴면 나온다.
   */
  mini?: boolean
  /** 이보다 종목이 많으면 접고 "더 보기" 를 낸다. 없으면 다 편다 */
  maxRows?: number
}

/* 조각 색.

   앞 세 개를 보라·연보라·파랑으로 두었더니 큰 조각 셋이 다 같은 색으로 보였다.
   색상환을 한 바퀴 돌면서 <b>이웃끼리 가장 멀게</b> 늘어놓는다 —
   보라 → 주황 → 초록 → 파랑 → 자홍. 조각은 큰 것부터 그려지므로 이 순서가 곧
   화면에서 붙어 있는 순서다.

   보라와 파랑을 1·4번에 떼어 놓은 것은 적록색약에서 그 둘이 가장 헷갈리기
   때문이다. 목록에 이름이 함께 있으므로 색만으로 구분하게 두지도 않는다.

   현금은 무채색이다 — 투자한 돈이 아니라 남은 돈이라 종목들과 같은 채도로 두면
   종목처럼 읽힌다. */
const PIE = ['#5457e8', '#e08a2e', '#16a06a', '#2f7bf6', '#c9518f']
const CASH = '#c3c8dc'

const R = 46
const C = 2 * Math.PI * R

const won = (n: number) => `${Math.round(n).toLocaleString('ko-KR')}원`
const pct = (n: number) => `${n.toFixed(1)}%`

export default function PortfolioDonut({ slices, total, onPick, mini, maxRows }: Props) {
  const [all, setAll] = useState(false)

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

  if (mini) {
    return (
      <span className="pd-mini" aria-hidden="true">
        <svg viewBox="0 0 120 120">
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
      </span>
    )
  }

  /* 목록에 낼 조각. 색을 원과 맞춰야 하므로 원래 자리(i)를 들고 다닌다 —
     접힌 뒤 다시 매기면 같은 종목이 다른 색으로 보인다. */
  const rows = slices.map((slice, i) => ({ slice, i }))
  const cashRow = rows.filter((r) => r.slice.key === 'CASH')
  const tickerRows = rows.filter((r) => r.slice.key !== 'CASH')
  const folded = maxRows !== undefined && !all && tickerRows.length > maxRows
  const shown = folded
    ? [...tickerRows.slice(0, maxRows), ...cashRow]
    : [...tickerRows, ...cashRow]
  const hidden = tickerRows.length - (folded ? maxRows : tickerRows.length)

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
        {shown.map(({ slice: s, i }) => {
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

      {maxRows !== undefined && tickerRows.length > maxRows && (
        <button type="button" className="pd-more" onClick={() => setAll((v) => !v)}>
          {folded ? `${hidden}개 더 보기` : '접기'}
          <i aria-hidden="true">⌄</i>
        </button>
      )}
    </div>
  )
}
