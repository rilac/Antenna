/* 미니차트. 관심 종목 행과 지수 카드에서 최근 30일 종가 흐름만 보여준다.

   설계 의도
   - 단일 계열이라 범례를 두지 않는다. 옆에 등락률 숫자가 같이 있어
     방향을 색으로만 전달하지 않는다(색맹 대비).
   - 상승·하락 색은 앱 토큰(--up / --down)을 쓴다. 국내 관례대로 상승이 빨강이다.
   - 끝점을 점으로 강조한다. 축·격자는 이 크기에서 읽히지 않아 넣지 않는다.
   - 이 크기에서는 툴팁을 붙이지 않는다. 값 확인은 행을 눌러 종목 상세로 간다.
   - area 를 켜면 선 아래를 옅은 그라디언트로 채운다. 지수 카드처럼 차트가
     주인공인 자리에서만 쓰고, 표 안의 작은 칸에서는 선만 그린다. */
import { useId } from 'react'

type Props = {
  series: number[]
  /** 방향. 등락률 부호와 같은 값을 넘긴다. */
  up: boolean
  width?: number
  height?: number
  /** 선 아래 면 채우기. 큰 차트에서만 켠다. */
  area?: boolean
}

export default function Sparkline({
  series, up, width = 88, height = 30, area = false,
}: Props) {
  /* 그라디언트 id 는 문서 전역이라 카드마다 달라야 한다 */
  const gid = useId()
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
  /* 면은 선을 따라간 뒤 바닥으로 내려 닫는다 */
  const fill = `${d} L${ex.toFixed(1)} ${height} L${pts[0][0].toFixed(1)} ${height} Z`

  return (
    <svg className="spark" width={width} height={height} viewBox={`0 0 ${width} ${height}`}
         role="img" aria-hidden="true" focusable="false">
      {area && (
        <>
          <defs>
            <linearGradient id={gid} x1="0" y1="0" x2="0" y2="1">
              <stop offset="0%" stopColor={color} stopOpacity=".26" />
              <stop offset="100%" stopColor={color} stopOpacity="0" />
            </linearGradient>
          </defs>
          <path d={fill} fill={`url(#${gid})`} stroke="none" />
        </>
      )}
      <path d={d} fill="none" stroke={color} strokeWidth="2"
            strokeLinecap="round" strokeLinejoin="round" />
      <circle cx={ex} cy={ey} r="2.6" fill={color} />
    </svg>
  )
}
