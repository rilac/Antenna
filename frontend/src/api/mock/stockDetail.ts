/* B-03 종목 상세에서 **아직 서버가 없는 블록**만 남은 목업.

   전에는 이 파일이 아홉 블록을 전부 흉내 냈고, 종목 열여덟 개를 적은 표를 들고
   있었다. 그 표에 없는 코드는 STOCK_NOT_FOUND 를 던졌는데, 종목 탐색이 실제
   324종목을 보여주게 되면서 **누르는 종목 대부분이 그 오류로 떨어졌다.**
   실제 API 가 답하는 일곱 블록은 걷어 냈고 표도 함께 지웠다.

   남은 것은 예측 심리 하나다. GET /stocks/{code}/sentiment 가 없다
   (ANT-PRED-06 · 담당자 없음). AI 와는 무관하다 — predictions 표의 UP/DOWN 을
   세는 익명 집계이고, 표에는 이미 행이 있다. 코드가 없을 뿐이다.

   **코드를 씨앗으로 값을 만든다.** 표를 두면 다시 "아는 종목/모르는 종목" 이
   갈리고 같은 사고가 되풀이된다. 어떤 코드로 들어와도 답하고, 새로고침해도
   같은 값이 나온다. */
import type { StockSentiment } from '../stockDetail'

const delay = <T,>(value: T, ms: number) =>
  new Promise<T>((resolve) => setTimeout(() => resolve(value), ms))

/** 종목코드를 씨앗으로 쓰는 결정적 난수 */
function seeded(key: string) {
  let s = 0
  for (let i = 0; i < key.length; i++) s = (s * 31 + key.charCodeAt(i)) % 2147483647
  return () => {
    s = (s * 1103515245 + 12345) % 2147483648
    return s / 2147483648
  }
}

export function sentiment(code: string): Promise<StockSentiment> {
  const rnd = seeded(`${code}s`)
  /* 표본을 0 까지 내려 본다 — 예측이 하나도 없는 종목이 훨씬 많고,
     그 경로(빈 배지)가 실제로 자주 그려질 화면이다. */
  const sampleSize = Math.floor(rnd() * 24)
  const up = sampleSize === 0 ? null : Math.round(30 + rnd() * 50)

  return delay({
    upRatio: up,
    downRatio: up === null ? null : 100 - up,
    sampleSize,
    /* 예측이 없으면 기간별도 비운다. 0% 막대를 그리면 "다 하락" 으로 읽힌다 */
    byHorizon: [5, 10, 20, 60].map((horizon) => {
      const n = up === null ? 0 : Math.round(sampleSize * (0.15 + rnd() * 0.35))
      return {
        horizon,
        upRatio: n === 0 || up === null
          ? null
          : Math.max(0, Math.min(100, Math.round(up + (rnd() - 0.5) * 24))),
        sampleSize: n,
      }
    }),
  }, 400)
}
