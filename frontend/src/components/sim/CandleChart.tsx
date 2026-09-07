/* 모의투자 캔들 차트. 설계서 §7 이 G-04 · G-05 공용으로 지정한 CandleChart 다.

   CloseChart 와 역할이 다르다. 저쪽은 실전 시세라 법적 제약으로 종가만 받고 단선으로
   그린다. 이쪽은 시즌 가격이라 OHLCV 가 다 있고 봉으로 그린다 — 그래서 합치지 않는다.

   설계 의도
   - **x 축이 날짜가 아니라 gameDay 다.** 시즌 가격에는 실제 날짜가 아예 없다(ERD v0.9).
     타입에 날짜가 없으므로 실수로 그릴 수도 없다.
   - **0 이하는 워밍업 구간이다.** 시즌 시작 전 봉이라 배경을 깔아 플레이 구간과 구분한다.
     이게 없으면 첫날 캔들이 한 개라 이동평균도 MACD 도 값이 없다.
   - **진행일을 넘는 봉은 그리지 않는다.** 서버가 이미 잘라서 주지만(커닝 차단) 여기서도
     자른다 — 로컬에서 진행일을 앞당겨 보는 개발 중에도 화면 규칙이 같아야 한다.
   - 지표는 여기서 계산한다(sim/indicators.ts). 서버는 OHLCV 만 준다.
   - 값 확인은 십자선으로 한다. 키보드는 좌우 화살표 — 마우스가 없으면 못 읽는 정보를
     만들지 않는다. CloseChart 와 같은 규칙이다. */
import { useId, useMemo, useRef, useState } from 'react'
import type { Candle } from '../../api/seasonPlay'
import { bollinger, canShow, sma, type IndicatorKind } from '../../sim/indicators'

type Props = {
  /** 워밍업(0 이하)을 포함한 전체 봉. 오름차순 */
  candles: Candle[]
  /** 여기까지만 그린다. 플레이 구간의 현재 진행일 */
  currentDay: number
  /** 켤 지표. 값이 나올 만큼 봉이 없으면 알아서 빠진다 */
  indicators?: IndicatorKind[]
  height?: number
}

/* viewBox 기준으로 그리고 CSS 로 늘린다. 폭을 재지 않아도 컨테이너에 맞춰
   늘어나므로 리사이즈 관찰자가 필요 없다. CloseChart 와 같은 방식이다. */
const VW = 1000
/* 거래량 막대가 차지하는 비율. 캔들이 주인공이라 5분의 1만 준다 */
const VOL_RATIO = 0.2

const MA_COLOR: Partial<Record<IndicatorKind, string>> = {
  MA5: '#e8a13c',
  MA20: '#7b61ff',
  MA60: '#16a06a',
}
const MA_PERIOD: Partial<Record<IndicatorKind, number>> = { MA5: 5, MA20: 20, MA60: 60 }

const won = (n: number) => Math.round(n).toLocaleString('ko-KR')

export default function CandleChart({
  candles,
  currentDay,
  indicators = ['MA5', 'MA20', 'MA60'],
  height = 340,
}: Props) {
  const gid = useId()
  const svgRef = useRef<SVGSVGElement>(null)
  const [cursor, setCursor] = useState<number | null>(null)

  const padT = 12
  const padB = 22
  const padL = 6
  const padR = 68

  /* 진행일까지만 남긴다. 워밍업(gameDay <= 0)은 전부 남는다 —
     시즌 시작 전 구간이라 커닝이 아니고, 이게 있어야 첫날부터 지표가 나온다. */
  const shown = useMemo(
    () => candles.filter((c) => c.gameDay <= currentDay),
    [candles, currentDay],
  )

  const geom = useMemo(() => {
    if (shown.length < 2) return null

    const closes = shown.map((c) => c.close)
    /* 심지가 있으면 고·저까지 담아야 봉이 잘리지 않는다. 없으면 종가로 대신한다 */
    const highs = shown.map((c) => c.high ?? c.close)
    const lows = shown.map((c) => c.low ?? c.close)

    const on = indicators.filter((k) => canShow(k, shown.length))
    const lines = on
      .filter((k) => MA_PERIOD[k])
      .map((k) => ({ kind: k, values: sma(closes, MA_PERIOD[k]!), color: MA_COLOR[k]! }))
    const band = on.includes('BOLL') ? bollinger(closes) : null

    /* 지표선도 축에 넣는다 — 볼린저 상단이 고가보다 위로 나가면 선이 잘린다 */
    const extra = [
      ...lines.flatMap((l) => l.values),
      ...(band ? [...band.upper, ...band.lower] : []),
    ].filter((v): v is number => v !== null)

    const lo = Math.min(...lows, ...extra)
    const hi = Math.max(...highs, ...extra)
    const margin = (hi - lo || hi * 0.02) * 0.06
    const min = lo - margin
    const max = hi + margin
    const span = max - min || 1

    const w = VW - padL - padR
    const volH = (height - padT - padB) * VOL_RATIO
    const priceH = height - padT - padB - volH - 8

    /* 봉 하나가 차지하는 폭. 봉이 많으면 얇아지고 최소 1 은 남긴다 */
    const step = w / shown.length
    const bodyW = Math.max(1, Math.min(9, step * 0.66))

    const cx = (i: number) => padL + step * (i + 0.5)
    const y = (v: number) => padT + (1 - (v - min) / span) * priceH

    const maxVol = Math.max(1, ...shown.map((c) => c.volume ?? 0))
    const volTop = padT + priceH + 8
    const vy = (v: number) => volTop + volH - (v / maxVol) * volH

    /* null 자리에서 선을 끊는다. 이어 버리면 값이 없는 구간에 가짜 직선이 그려진다 */
    const path = (values: (number | null)[]) => {
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
      return d
    }

    /* 워밍업이 끝나는 자리. 플레이 구간 배경의 왼쪽 경계다 */
    const firstPlay = shown.findIndex((c) => c.gameDay > 0)
    const playX = firstPlay < 0 ? VW - padR : padL + step * firstPlay

    return {
      min, max, lo, hi, cx, y, vy, step, bodyW, volTop, volH, priceH, maxVol, playX,
      lines: lines.map((l) => ({ ...l, d: path(l.values) })),
      band: band && { upper: path(band.upper), lower: path(band.lower) },
    }
  }, [shown, indicators, height])

  if (!geom) {
    return <div className="ck-empty">{'봉이 모이지 않아 차트를 그릴 수 없습니다'}</div>
  }

  const pick = (clientX: number) => {
    const box = svgRef.current?.getBoundingClientRect()
    if (!box) return
    const vx = ((clientX - box.left) / box.width) * VW
    const i = Math.floor((vx - padL) / geom.step)
    setCursor(Math.max(0, Math.min(shown.length - 1, i)))
  }

  const onKey = (e: React.KeyboardEvent) => {
    if (e.key !== 'ArrowLeft' && e.key !== 'ArrowRight') return
    e.preventDefault()
    const at = cursor ?? shown.length - 1
    setCursor(Math.max(0, Math.min(shown.length - 1, at + (e.key === 'ArrowRight' ? 1 : -1))))
  }

  const hit = cursor === null ? null : shown[cursor]
  const last = shown[shown.length - 1]
  const warmup = shown.length - shown.filter((c) => c.gameDay > 0).length

  return (
    <div className="ck">
      <svg
        ref={svgRef}
        className="ck-svg"
        viewBox={`0 0 ${VW} ${height}`}
        style={{ height }}
        preserveAspectRatio="none"
        role="img"
        tabIndex={0}
        aria-label={`캔들 차트. 시즌 시작 전 ${warmup}봉과 진행 ${shown.length - warmup}봉. 좌우 화살표로 게임일별 값을 읽을 수 있습니다.`}
        onPointerMove={(e) => pick(e.clientX)}
        onPointerLeave={() => setCursor(null)}
        onKeyDown={onKey}
        onBlur={() => setCursor(null)}
      >
        <defs>
          <clipPath id={`${gid}-plot`}>
            <rect x={padL} y={padT} width={VW - padL - padR} height={geom.priceH} />
          </clipPath>
        </defs>

        {/* 플레이 구간 배경. 왼쪽이 워밍업이라는 걸 이 경계가 말해 준다 */}
        <rect
          className="ck-play"
          x={geom.playX}
          y={padT}
          width={Math.max(0, VW - padR - geom.playX)}
          height={geom.priceH}
        />
        <line className="ck-play-edge" x1={geom.playX} x2={geom.playX} y1={padT} y2={height - padB} />

        {/* 거래량 — 캔들 아래 띠. 원천에 없으면 막대가 빠진다 */}
        <g className="ck-vols">
          {shown.map((c, i) =>
            c.volume == null ? null : (
              <rect
                key={c.gameDay}
                className={c.close >= (shown[i - 1]?.close ?? c.close) ? 'up' : 'down'}
                x={geom.cx(i) - geom.bodyW / 2}
                y={geom.vy(c.volume)}
                width={geom.bodyW}
                height={Math.max(0.5, geom.volTop + geom.volH - geom.vy(c.volume))}
              />
            ),
          )}
        </g>

        <g clipPath={`url(#${gid}-plot)`}>
          {/* 봉 — 심지 먼저, 몸통 나중. 몸통이 심지를 덮어야 깔끔하다 */}
          {shown.map((c, i) => {
            const o = c.open ?? c.close
            const h = c.high ?? Math.max(o, c.close)
            const l = c.low ?? Math.min(o, c.close)
            const rise = c.close >= o
            const top = geom.y(Math.max(o, c.close))
            const bottom = geom.y(Math.min(o, c.close))
            return (
              <g key={c.gameDay} className={`ck-candle ${rise ? 'up' : 'down'}`}>
                <line x1={geom.cx(i)} x2={geom.cx(i)} y1={geom.y(h)} y2={geom.y(l)} />
                <rect
                  x={geom.cx(i) - geom.bodyW / 2}
                  y={top}
                  width={geom.bodyW}
                  /* 시가와 종가가 같으면 높이가 0 이라 선 하나로 보이게 최소값을 준다 */
                  height={Math.max(1, bottom - top)}
                />
              </g>
            )
          })}

          {geom.band && (
            <g className="ck-band">
              <path d={geom.band.upper} />
              <path d={geom.band.lower} />
            </g>
          )}

          {geom.lines.map((l) => (
            <path key={l.kind} className="ck-ma" d={l.d} stroke={l.color} />
          ))}
        </g>

        {cursor !== null && (
          <line
            className="ck-cursor"
            x1={geom.cx(cursor)}
            x2={geom.cx(cursor)}
            y1={padT}
            y2={height - padB}
          />
        )}

        {/* 세로축은 오른쪽에 최고·최저만. 격자를 촘촘히 깔면 봉이 묻힌다 */}
        <g className="ck-axis">
          <text x={VW - padR + 8} y={geom.y(geom.hi) + 4}>{won(geom.hi)}</text>
          <text x={VW - padR + 8} y={geom.y(geom.lo) + 4}>{won(geom.lo)}</text>
          {/* x 축은 날짜가 아니라 게임일이다 */}
          {warmup > 0 && <text x={geom.playX} y={height - 6} textAnchor="middle">DAY 1</text>}
          <text x={VW - padR} y={height - 6} textAnchor="end">{`DAY ${last.gameDay}`}</text>
        </g>
      </svg>

      {/* 십자선 값. 차트 위에 겹치지 않고 아래 줄에 둔다 — 숫자가 봉을 가리지 않게.
          비어 있어도 자리를 유지해 화면이 튀지 않는다. CloseChart 와 같은 규칙이다. */}
      <p className="ck-readout num" aria-live="polite">
        {hit ? (
          <>
            <span className="ck-ro-day">
              {hit.gameDay > 0 ? `DAY ${hit.gameDay}` : '시작 전'}
            </span>
            <span>시 {hit.open == null ? '—' : won(hit.open)}</span>
            <span>고 {hit.high == null ? '—' : won(hit.high)}</span>
            <span>저 {hit.low == null ? '—' : won(hit.low)}</span>
            <b>종 {won(hit.close)}</b>
            {hit.volume != null && <span>거래량 {hit.volume.toLocaleString('ko-KR')}</span>}
          </>
        ) : (
          <span className="ck-ro-idle">{'차트 위에 올리면 게임일별 값을 봅니다'}</span>
        )}
      </p>
    </div>
  )
}
