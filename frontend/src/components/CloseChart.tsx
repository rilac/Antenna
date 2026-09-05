/* 종가 단선 차트. 설계서 §7 이 B-01 · B-03 · B-04 공용으로 지정한 컴포넌트다.

   설계 의도
   - **OHLC 를 받지 않는다.** 실전 시세는 법적 제약으로 종가만 내려온다(§7 legal).
     타입에 close 밖에 없으므로 나중에 봉차트로 잘못 늘어날 수 없다.
   - 기준선은 구간 첫 종가다. 이 선 위/아래로 면을 채워 "구간 대비 얼마나"
     를 눈으로 읽게 한다. 색만으로 방향을 전하지 않도록 옆에 등락률 숫자가 함께 온다.
   - 선·면은 글자가 아니라 그래픽이므로 밝은 토큰(--up-vivid / --down-vivid)을 쓴다.
   - Sparkline 과 역할이 다르다. 저쪽은 표 안의 30일 흐름 요약이고, 이쪽은
     축·눈금·툴팁이 있는 주인공 차트다. 그래서 합치지 않고 따로 둔다.
   - 값 확인은 포인터를 올리면 나오는 십자선으로 한다. 키보드는 좌우 화살표로
     같은 커서를 움직인다 — 마우스가 없으면 못 읽는 정보를 만들지 않는다. */
import { useId, useMemo, useRef, useState } from 'react'

export type ClosePoint = {
  /** YYYY-MM-DD */
  tradeDate: string
  close: number
}

type Props = {
  series: ClosePoint[]
  height?: number
  /** 축·눈금을 그린다. 작은 자리에서는 끈다. */
  axis?: boolean
  /** 접근성 문구에 쓰는 종목명 */
  label?: string
}

/* viewBox 기준 좌표계로 그리고 CSS 로 늘린다. 폭을 재지 않아도
   컨테이너에 맞춰 늘어나므로 리사이즈 관찰자가 필요 없다. */
const VW = 720

const fmtPrice = (n: number) => n.toLocaleString('ko-KR')
const fmtDate = (d: string) => d.slice(2).replace(/-/g, '.')

export default function CloseChart({ series, height = 260, axis = true, label = '' }: Props) {
  const gid = useId()
  const svgRef = useRef<SVGSVGElement>(null)
  /* 십자선 위치. null 이면 십자선을 그리지 않는다. */
  const [cursor, setCursor] = useState<number | null>(null)

  const padT = 14
  const padB = axis ? 26 : 8
  const padL = 6
  const padR = axis ? 62 : 6

  const geom = useMemo(() => {
    if (series.length < 2) return null
    const closes = series.map((p) => p.close)
    const lo = Math.min(...closes)
    const hi = Math.max(...closes)
    /* 위아래로 6% 여유를 둬야 꼭짓점이 테두리에 붙지 않는다 */
    const margin = (hi - lo || hi * 0.02) * 0.06
    const min = lo - margin
    const max = hi + margin
    const span = max - min || 1

    const w = VW - padL - padR
    const h = height - padT - padB
    const x = (i: number) => padL + (i / (series.length - 1)) * w
    const y = (v: number) => padT + (1 - (v - min) / span) * h

    const pts = series.map((p, i) => [x(i), y(p.close)] as const)
    const line = pts.map(([px, py], i) => `${i ? 'L' : 'M'}${px.toFixed(1)} ${py.toFixed(1)}`).join(' ')
    const area = `${line} L${pts[pts.length - 1][0].toFixed(1)} ${padT + h} L${pts[0][0].toFixed(1)} ${padT + h} Z`

    const first = series[0].close
    const last = series[series.length - 1].close
    return { min, max, x, y, pts, line, area, lo, hi, first, last, baseY: y(first), plotH: h }
  }, [series, height, padB, padR])

  if (!geom) {
    return <div className="cc-empty">{'시세를 그릴 만큼 자료가 모이지 않았습니다'}</div>
  }

  const up = geom.last >= geom.first
  const color = up ? 'var(--up-vivid)' : 'var(--down-vivid)'
  const rate = geom.first ? ((geom.last - geom.first) / geom.first) * 100 : 0

  /* 포인터 x 를 가장 가까운 데이터 인덱스로 바꾼다 */
  const pick = (clientX: number) => {
    const box = svgRef.current?.getBoundingClientRect()
    if (!box) return
    const vx = ((clientX - box.left) / box.width) * VW
    const ratio = (vx - padL) / (VW - padL - padR)
    const i = Math.round(ratio * (series.length - 1))
    setCursor(Math.max(0, Math.min(series.length - 1, i)))
  }

  const onKey = (e: React.KeyboardEvent) => {
    if (e.key !== 'ArrowLeft' && e.key !== 'ArrowRight') return
    e.preventDefault()
    const at = cursor ?? series.length - 1
    setCursor(Math.max(0, Math.min(series.length - 1, at + (e.key === 'ArrowRight' ? 1 : -1))))
  }

  const hit = cursor === null ? null : series[cursor]
  const hitXY = cursor === null ? null : geom.pts[cursor]

  return (
    <div className="cc">
      <svg
        ref={svgRef}
        className="cc-svg"
        viewBox={`0 0 ${VW} ${height}`}
        style={{ height }}
        preserveAspectRatio="none"
        role="img"
        tabIndex={0}
        aria-label={`${label} 종가 추이. ${fmtDate(series[0].tradeDate)}부터 ${fmtDate(series[series.length - 1].tradeDate)}까지 ${
          up ? '상승' : '하락'} ${Math.abs(rate).toFixed(2)}퍼센트. 좌우 화살표로 날짜별 종가를 읽을 수 있습니다.`}
        onPointerMove={(e) => pick(e.clientX)}
        onPointerLeave={() => setCursor(null)}
        onKeyDown={onKey}
        onBlur={() => setCursor(null)}
      >
        <defs>
          <linearGradient id={gid} x1="0" y1="0" x2="0" y2="1">
            <stop offset="0%" stopColor={color} stopOpacity=".22" />
            <stop offset="100%" stopColor={color} stopOpacity="0" />
          </linearGradient>
        </defs>

        {/* 구간 첫 종가 기준선. 이 선이 있어야 면의 두께가 뜻을 갖는다 */}
        <line className="cc-base" x1={padL} x2={VW - padR} y1={geom.baseY} y2={geom.baseY} />

        <path d={geom.area} fill={`url(#${gid})`} stroke="none" />
        <path className="cc-line" d={geom.line} fill="none" stroke={color} />

        {/* 끝점 — 마지막 종가가 어디인지 눈으로 잡아 준다 */}
        <circle cx={geom.pts[geom.pts.length - 1][0]} cy={geom.pts[geom.pts.length - 1][1]} r="3.6" fill={color} />

        {hitXY && (
          <g className="cc-cursor">
            <line x1={hitXY[0]} x2={hitXY[0]} y1={padT} y2={height - padB} />
            <circle cx={hitXY[0]} cy={hitXY[1]} r="4.4" fill={color} />
          </g>
        )}

        {axis && (
          <g className="cc-axis">
            {/* 세로축은 오른쪽에 최고·최저만. 격자를 촘촘히 깔면 선이 묻힌다 */}
            <text x={VW - padR + 8} y={geom.y(geom.hi) + 4}>{fmtPrice(geom.hi)}</text>
            <text x={VW - padR + 8} y={geom.y(geom.lo) + 4}>{fmtPrice(geom.lo)}</text>
            <text x={padL} y={height - 8} textAnchor="start">{fmtDate(series[0].tradeDate)}</text>
            <text x={VW - padR} y={height - 8} textAnchor="end">
              {fmtDate(series[series.length - 1].tradeDate)}
            </text>
          </g>
        )}
      </svg>

      {/* 십자선이 가리키는 값. 차트 위에 겹치지 않고 아래 줄에 둬서
          숫자가 선을 가리지 않게 한다. 비어 있어도 자리를 유지해 화면이 튀지 않는다. */}
      <p className="cc-readout num" aria-live="polite">
        {hit ? (
          <>
            <span className="cc-ro-date">{fmtDate(hit.tradeDate)}</span>
            <b>{fmtPrice(hit.close)}원</b>
          </>
        ) : (
          <span className="cc-ro-idle">{'차트 위에 올리면 날짜별 종가를 봅니다'}</span>
        )}
      </p>
    </div>
  )
}
