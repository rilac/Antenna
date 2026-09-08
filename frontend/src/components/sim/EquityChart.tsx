/* 자산 곡선. G-08 결과 화면이 쓴다.

   ── 무엇을 그리는가 ─────────────────────────────────────
   게임일마다의 총자산이다. 예수금 + 그날 종가로 평가한 주식이고, 다음 영업일로
   넘어갈 때 한 줄씩 적어 둔 값이다(useSeasonSim 의 equity).

   ── 왜 시작선을 굵게 긋는가 ─────────────────────────────
   이 화면에서 답해야 하는 질문은 "벌었나 잃었나" 하나다. 초기 예수금 선을 그어
   두면 곡선이 그 위인지 아래인지가 한눈에 보인다. 축 숫자를 읽을 필요가 없다.

   ── 왜 축을 0 에서 시작하지 않는가 ──────────────────────
   3,000만원으로 시작해 3,050만원이 됐다면 0 부터 그리면 거의 직선이다. 움직인
   구간만 보여야 변화가 보인다. 대신 초기 예수금 선을 늘 범위 안에 넣어 기준을
   잃지 않게 한다.

   라이브러리를 쓰지 않는다. 꺾은선 하나에 차트 라이브러리를 넣으면 번들만 커진다. */
import { useMemo } from 'react'

export type EquityPoint = { gameDay: number; totalAsset: number }

type Props = {
  points: EquityPoint[]
  /** 초기 예수금. 이 선 위인지 아래인지가 이 그림의 요점이다 */
  base: number
  height?: number
}

const VW = 1000
const PAD_L = 6
const PAD_R = 74
const PAD_T = 10
const PAD_B = 20

const won = (n: number) => `${Math.round(n).toLocaleString('ko-KR')}원`
/** 축 눈금은 만원 단위로 줄인다 — 3,050만 이 30,500,000 보다 빨리 읽힌다 */
const tick = (n: number) => `${Math.round(n / 10000).toLocaleString('ko-KR')}만`

export default function EquityChart({ points, base, height = 190 }: Props) {
  const geom = useMemo(() => {
    if (points.length < 2) return null

    const values = points.map((p) => p.totalAsset)
    /* 초기 예수금을 범위에 반드시 넣는다. 기준선이 화면 밖으로 나가면
       "위냐 아래냐" 를 눈으로 못 읽는다. */
    const lo = Math.min(...values, base)
    const hi = Math.max(...values, base)
    /* 위아래로 조금 띄운다. 최고점이 천장에 붙으면 잘린 것처럼 보인다 */
    const pad = (hi - lo) * 0.12 || Math.max(1, hi * 0.001)
    const min = lo - pad
    const max = hi + pad

    const w = VW - PAD_L - PAD_R
    const h = height - PAD_T - PAD_B
    const step = points.length > 1 ? w / (points.length - 1) : 0
    const x = (i: number) => PAD_L + step * i
    const y = (v: number) => PAD_T + (1 - (v - min) / (max - min)) * h

    const line = points.map((p, i) => `${i ? 'L' : 'M'}${x(i).toFixed(1)} ${y(p.totalAsset).toFixed(1)}`).join('')
    /* 곡선 아래를 옅게 채운다. 선 하나보다 오르내림이 눈에 잘 들어온다 */
    const area = `${line}L${x(points.length - 1).toFixed(1)} ${(PAD_T + h).toFixed(1)}L${x(0).toFixed(1)} ${(PAD_T + h).toFixed(1)}Z`

    const last = points[points.length - 1].totalAsset
    return { x, y, line, area, baseY: y(base), min, max, last, h }
  }, [points, base, height])

  if (!geom) {
    return (
      <p className="eq-empty">
        게임일이 두 날은 지나야 곡선이 그려집니다.
        <span> 지금 {points.length}일</span>
      </p>
    )
  }

  const up = geom.last >= base

  return (
    <div className="eq">
      <svg
        className="eq-svg"
        viewBox={`0 0 ${VW} ${height}`}
        style={{ height }}
        preserveAspectRatio="none"
        role="img"
        aria-label={`총자산이 ${won(base)} 에서 ${won(geom.last)} 로 끝났습니다`}
      >
        <path className={up ? 'eq-area up' : 'eq-area down'} d={geom.area} />
        <path className={up ? 'eq-line up' : 'eq-line down'} d={geom.line} />

        {/* 시작선. 이 위면 벌었고 아래면 잃었다 */}
        <line className="eq-base" x1={PAD_L} x2={VW - PAD_R} y1={geom.baseY} y2={geom.baseY} />
        <text className="eq-axis" x={VW - PAD_R + 8} y={geom.baseY + 3.5}>
          {tick(base)}
        </text>

        {/* 위·아래 끝값만 적는다. 눈금을 촘촘히 넣으면 곡선보다 숫자가 먼저 보인다 */}
        <text className="eq-axis" x={VW - PAD_R + 8} y={PAD_T + 4}>{tick(geom.max)}</text>
        <text className="eq-axis" x={VW - PAD_R + 8} y={PAD_T + geom.h}>{tick(geom.min)}</text>

        {/* 마지막 점. 곡선이 어디서 끝났는지가 결론이다 */}
        <circle
          className={up ? 'eq-dot up' : 'eq-dot down'}
          cx={geom.x(points.length - 1)}
          cy={geom.y(geom.last)}
          r="4"
        />
      </svg>

      <p className="eq-foot num">
        <span>DAY {points[0].gameDay}</span>
        <span>DAY {points[points.length - 1].gameDay}</span>
      </p>
    </div>
  )
}
