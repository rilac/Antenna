/* RSI 패널. G-04 가 캔들 차트 아래에 붙인다.

   ── 왜 캔들에 얹지 않고 따로 두는가 ──────────────────────
   축이 다르다. 이평선·볼린저는 가격 단위(원)라서 캔들 위에 그대로 얹히지만 RSI 는
   0~100 이다. 캔들 축(50,000~60,000원)에 얹으면 아예 화면 밖이다. HTS 들이 아래에
   칸을 따로 내는 이유다.

   ── 가로 좌표는 캔들과 같아야 한다 ──────────────────────
   같은 봉이 위아래에서 다른 x 에 서면 두 그림을 나란히 읽을 수 없다. 그래서
   CandleChart 의 VW·PAD_L·PAD_R 를 그대로 쓴다.

   ── 기준선이 없으면 값이 뜻을 잃는다 ────────────────────
   RSI 는 70·30 을 넘나드는 걸 보는 지표다. 그 두 선 없이 곡선만 그리면 읽을 수가 없다.
   축도 0~100 으로 고정한다 — 데이터에 맞춰 늘리면 70선이 매일 다른 높이에 서서
   "과열이다" 를 눈으로 못 읽는다.

   MACD 는 뺐다(2026-09-08). 계산은 sim/indicators.ts 에 그대로 있으니 다시 넣을 때는
   여기 kind 를 늘리면 된다. */
import { useMemo } from 'react'
import type { Candle } from '../../api/seasonPlay'
import { canShow, rsi as rsiOf, warmupOf } from '../../sim/indicators'
import { PAD_L, PAD_R, VW } from './CandleChart'

export type PaneKind = 'RSI'

type Props = {
  /** 워밍업(0 이하)을 포함한 전체 봉 */
  candles: Candle[]
  currentDay: number
  kind: PaneKind
  height?: number
}

const LABEL: Record<PaneKind, string> = { RSI: 'RSI 14' }
/** 70 위는 과열, 30 아래는 침체. 50 은 가운데 참고선이다 */
const GUIDES = [70, 50, 30]

export default function IndicatorPane({ candles, currentDay, kind, height = 96 }: Props) {
  const padT = 10
  const padB = 12

  const shown = useMemo(
    () => candles.filter((c) => c.gameDay <= currentDay),
    [candles, currentDay],
  )

  const geom = useMemo(() => {
    if (shown.length < 2 || !canShow(kind, shown.length)) return null

    const closes = shown.map((c) => c.close)
    const step = (VW - PAD_L - PAD_R) / shown.length
    const cx = (i: number) => PAD_L + step * (i + 0.5)
    const h = height - padT - padB
    const y = (v: number) => padT + (1 - v / 100) * h

    const values = rsiOf(closes)

    /* null 자리에서 선을 끊는다. 이어 버리면 값이 없는 구간에 가짜 직선이 그려진다 */
    let d = ''
    let open = false
    values.forEach((v, i) => {
      if (v === null) {
        open = false
        return
      }
      d += `${open ? 'L' : 'M'}${cx(i).toFixed(1)} ${y(v).toFixed(1)}`
      open = true
    })

    return { y, line: d, last: values[values.length - 1] }
  }, [shown, kind, height])

  if (!geom) {
    return (
      <div className="ip">
        <p className="ip-empty">
          {LABEL[kind]} 은 봉 {warmupOf(kind)}개가 모여야 나옵니다.
          <span> 지금 {shown.length}개</span>
        </p>
      </div>
    )
  }

  const tone = geom.last === null ? undefined : geom.last >= 70 ? 'up' : geom.last <= 30 ? 'down' : undefined
  const state = geom.last === null ? null : geom.last >= 70 ? '과열' : geom.last <= 30 ? '침체' : '중립'

  return (
    <div className="ip">
      <p className="ip-head num">
        <b>{LABEL[kind]}</b>
        <span className={tone}>{geom.last === null ? '—' : geom.last.toFixed(1)}</span>
        {state && <span className="ip-state">{state}</span>}
      </p>

      <svg
        className="ip-svg"
        viewBox={`0 0 ${VW} ${height}`}
        style={{ height }}
        preserveAspectRatio="none"
        role="img"
        aria-label={
          geom.last === null
            ? 'RSI 값이 아직 없습니다'
            : `RSI ${geom.last.toFixed(1)}, ${state} 구간입니다`
        }
      >
        {GUIDES.map((v) => (
          <g key={v}>
            <line
              className={v === 50 ? 'ip-mid' : 'ip-guide'}
              x1={PAD_L}
              x2={VW - PAD_R}
              y1={geom.y(v)}
              y2={geom.y(v)}
            />
            <text className="ip-axis" x={VW - PAD_R + 8} y={geom.y(v) + 3.5}>{v}</text>
          </g>
        ))}
        <path className="ip-rsi" d={geom.line} />
      </svg>
    </div>
  )
}
