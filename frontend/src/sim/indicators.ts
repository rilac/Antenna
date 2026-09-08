/* 보조지표 계산. 모의투자 G-04 · G-05 가 쓴다.

   서버가 주지 않는다. API 명세가 "보조지표(MA 5·20·60, 볼린저, RSI, MACD)는 프론트 계산"
   으로 못박아 뒀다 — 서버는 OHLCV 만 내리고 여기서 만든다. 그래서 백엔드를 기다리지 않는다.

   설계 의도
   - **길이를 줄이지 않는다.** 모든 함수가 입력과 같은 길이의 배열을 돌려주고, 값이 아직
     없는 앞쪽은 null 이다. 캔들 배열과 인덱스가 1:1 로 맞아야 차트가 두 배열을 나란히
     읽을 수 있다 — 짧은 배열을 돌려주면 그리는 쪽에서 매번 오프셋을 더해야 하고 그게
     한 칸 밀린 선의 원인이 된다.
   - **순수 함수다.** 상태도 DOM 도 만지지 않는다. 같은 입력이면 같은 출력이라 값 검증이
     차트를 띄우지 않고도 된다.
   - **종가만 받는다.** 여기 있는 지표 전부가 종가 기반이다. OHLC 를 받으면 어떤 지표가
     무엇을 쓰는지가 흐려진다.
   - 관례값은 국내 HTS 가 쓰는 값이다(MA 5·20·60 · 볼린저 20/2 · RSI 14 · MACD 12/26/9).
     이평선만 5·15·30 으로 좁혔다 — 워밍업이 30봉이라 MA60 은 플레이 중반에야 값이 나온다.

   앞쪽 null 이 몇 칸인지 — 60게임일 시즌에서 이게 문제가 된다. warmupOf() 를 보라. */

/** 입력과 같은 길이. 값이 아직 없는 자리는 null. */
export type Series = (number | null)[]

/**
 * 단순이동평균. i 번째 값은 i 를 포함한 최근 period 개의 평균이다.
 *
 * 합을 굴려서 더한다 — 창마다 다시 합치면 O(n×period) 다.
 */
export function sma(values: number[], period: number): Series {
  const out: Series = new Array(values.length).fill(null)
  if (period <= 0) return out

  let sum = 0
  for (let i = 0; i < values.length; i++) {
    sum += values[i]
    if (i >= period) sum -= values[i - period]
    if (i >= period - 1) out[i] = sum / period
  }
  return out
}

/**
 * 지수이동평균. MACD 의 재료다.
 *
 * 첫 값을 무엇으로 시작하느냐로 결과가 달라진다 — 여기서는 관례대로 **처음 period 개의
 * 단순평균**을 씨앗으로 쓴다. 첫 값 하나를 그대로 쓰면 초반이 그 값에 오래 끌려간다.
 */
export function ema(values: number[], period: number): Series {
  const out: Series = new Array(values.length).fill(null)
  if (period <= 0 || values.length < period) return out

  const k = 2 / (period + 1)
  let seed = 0
  for (let i = 0; i < period; i++) seed += values[i]
  let prev = seed / period
  out[period - 1] = prev

  for (let i = period; i < values.length; i++) {
    prev = values[i] * k + prev * (1 - k)
    out[i] = prev
  }
  return out
}

export type Bollinger = {
  /** 중심선 = SMA(period) */
  mid: Series
  upper: Series
  lower: Series
}

/**
 * 볼린저 밴드. 중심선은 SMA 고 위아래는 표준편차 k 배다.
 *
 * 표준편차는 **모표준편차**(n 으로 나눈다)다. 창 안의 20개가 표본이 아니라 그 구간 전체라
 * 보는 관례이고, HTS 들이 이 쪽을 쓴다 — n−1 로 나누면 밴드가 조금 넓어져 화면이 어긋난다.
 */
export function bollinger(values: number[], period = 20, k = 2): Bollinger {
  const mid = sma(values, period)
  const upper: Series = new Array(values.length).fill(null)
  const lower: Series = new Array(values.length).fill(null)

  for (let i = period - 1; i < values.length; i++) {
    const m = mid[i]
    if (m === null) continue
    let acc = 0
    for (let j = i - period + 1; j <= i; j++) {
      const d = values[j] - m
      acc += d * d
    }
    const sd = Math.sqrt(acc / period)
    upper[i] = m + k * sd
    lower[i] = m - k * sd
  }
  return { mid, upper, lower }
}

/**
 * RSI. 0~100 이고 통상 70 위를 과열, 30 아래를 침체로 읽는다.
 *
 * **Wilder 방식**이다 — 첫 값은 처음 period 개 변화량의 단순평균이고, 그 뒤는
 * {@code (직전평균 × (period−1) + 이번값) / period} 로 굴린다. 단순평균만 쓰는 변형은
 * 같은 구간에서 값이 몇 포인트씩 다르게 나와 화면과 남의 차트가 안 맞는다.
 *
 * 하락이 한 번도 없는 구간은 RSI 100 이다 — 0 으로 나누지 않는다.
 */
export function rsi(values: number[], period = 14): Series {
  const out: Series = new Array(values.length).fill(null)
  if (period <= 0 || values.length <= period) return out

  let gain = 0
  let loss = 0
  for (let i = 1; i <= period; i++) {
    const d = values[i] - values[i - 1]
    if (d >= 0) gain += d
    else loss -= d
  }
  gain /= period
  loss /= period
  out[period] = rsiOf(gain, loss)

  for (let i = period + 1; i < values.length; i++) {
    const d = values[i] - values[i - 1]
    gain = (gain * (period - 1) + Math.max(d, 0)) / period
    loss = (loss * (period - 1) + Math.max(-d, 0)) / period
    out[i] = rsiOf(gain, loss)
  }
  return out
}

const rsiOf = (gain: number, loss: number) =>
  loss === 0 ? 100 : 100 - 100 / (1 + gain / loss)

export type Macd = {
  /** EMA(fast) − EMA(slow) */
  macd: Series
  /** MACD 의 EMA(signal) */
  signal: Series
  /** macd − signal · 막대로 그린다 */
  hist: Series
}

/**
 * MACD. 기본 12/26/9.
 *
 * <p><b>화면에서는 지금 쓰지 않는다</b>(2026-09-08 결정). 이 함수는 남겨 둔다 — 실제 시즌
 * 종가로 검증해 둔 것이고 다시 넣을 때 IndicatorPane 의 kind 만 늘리면 된다.
 *
 * 시그널선은 MACD 선의 EMA 인데, MACD 선 앞쪽은 아직 값이 없다. 그래서 **값이 있는
 * 구간만 잘라** EMA 를 구하고 원래 자리에 되돌린다 — null 을 0 으로 채워 넣고 계산하면
 * 없는 값이 평균에 섞여 초반 시그널이 0 쪽으로 끌려간다.
 */
export function macd(values: number[], fast = 12, slow = 26, signal = 9): Macd {
  const f = ema(values, fast)
  const s = ema(values, slow)

  const line: Series = new Array(values.length).fill(null)
  for (let i = 0; i < values.length; i++) {
    const a = f[i]
    const b = s[i]
    if (a !== null && b !== null) line[i] = a - b
  }

  const from = line.findIndex((v) => v !== null)
  const sig: Series = new Array(values.length).fill(null)
  const hist: Series = new Array(values.length).fill(null)

  if (from >= 0) {
    const packed = line.slice(from) as number[]
    const packedSignal = ema(packed, signal)
    for (let i = 0; i < packedSignal.length; i++) {
      const v = packedSignal[i]
      if (v === null) continue
      sig[from + i] = v
      hist[from + i] = packed[i] - v
    }
  }
  return { macd: line, signal: sig, hist }
}

/**
 * 그 지표가 값을 내기 시작하는 데 필요한 봉 수.
 *
 * <b>워밍업이 이걸 결정한다.</b> 시즌 가격만으로 그리면 첫날 봉이 한 개라 아무 지표도
 * 값이 없다. 워밍업 30봉이면 이평선 5·15·30 과 볼린저(20)는 DAY 1 부터 값이 나오고,
 * MACD 시그널만 34봉이 필요해 DAY 4 부터 나온다.
 *
 * 화면은 이 값으로 "지금 켤 수 있는 지표" 를 가려서 보여준다. 값이 없는 지표를 체크박스로
 * 내놓고 눌러도 아무 선이 안 그려지면 고장으로 보인다.
 */
export function warmupOf(kind: IndicatorKind): number {
  switch (kind) {
    case 'MA5':
      return 5
    case 'MA15':
      return 15
    case 'MA30':
      return 30
    case 'BOLL':
      return 20
    case 'RSI':
      return 15
    case 'MACD':
      // EMA26 이 26번째에 나오고, 그 위의 EMA9 가 8봉 더 걸린다.
      return 34
  }
}

export type IndicatorKind = 'MA5' | 'MA15' | 'MA30' | 'BOLL' | 'RSI' | 'MACD'

/** 봉이 이만큼 있으면 그 지표를 켤 수 있다. */
export const canShow = (kind: IndicatorKind, barCount: number) =>
  barCount >= warmupOf(kind)
