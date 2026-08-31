/* 미니차트. 관심 종목 행과 지수 카드에서 최근 30일 종가 흐름만 보여준다.

   설계 의도
   - 단일 계열이라 범례를 두지 않는다. 옆에 등락률 숫자가 같이 있어
     방향을 색으로만 전달하지 않는다(색맹 대비).
   - 상승·하락 색은 앱 토큰(--up / --down)을 쓴다. 국내 관례대로 상승이 빨강이다.
   - 끝점을 점으로 강조한다. 축·격자는 이 크기에서 읽히지 않아 넣지 않는다.
   - 이 크기에서는 툴팁을 붙이지 않는다. 값 확인은 행을 눌러 종목 상세로 간다. */

type Props = {
  series: number[]
  /** 방향. 등락률 부호와 같은 값을 넘긴다. */
  up: boolean
  width?: number
  height?: number
}

export default function Sparkline({ series, up, width = 88, height = 30 }: Props) {
  if (series.length < 2) return <span className="spark-empty" aria-hidden="true" />

  const min = Math.min(...series)
  const max = Math.max(...series)
  const span = max - min || 1
  const pad = 2.5
  const w = width - pad * 2
  const h = height - pad * 2

  const pts = series.map((v, i) => {
    const x = pad + (i / (series.length - 1)) * w
    const y = pad + (1 - (v - min) / span) * h
    return [x, y] as const
  })
  const d = pts.map(([x, y], i) => `${i ? 'L' : 'M'}${x.toFixed(1)} ${y.toFixed(1)}`).join(' ')
  const [ex, ey] = pts[pts.length - 1]
  const color = up ? 'var(--up)' : 'var(--down)'

  return (
    <svg className="spark" width={width} height={height} viewBox={`0 0 ${width} ${height}`}
         role="img" aria-hidden="true" focusable="false">
      <path d={d} fill="none" stroke={color} strokeWidth="2"
            strokeLinecap="round" strokeLinejoin="round" />
      <circle cx={ex} cy={ey} r="2.6" fill={color} />
    </svg>
  )
}
