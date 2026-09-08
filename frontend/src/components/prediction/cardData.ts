/* DTO → PredictionCard props 변환. 카드와 파일을 나눈 이유는 두 가지다.

   1. 컴포넌트 파일이 컴포넌트만 내보내야 Fast Refresh 가 동작한다(oxlint react/only-export-components).
   2. 변환은 화면마다 늘어난다. 카드 본체는 그대로 두고 이 파일만 자라야 한다.

   변환을 화면 안에 두지 않는 이유 — 네 화면이 조금씩 다르게 옮기게 되고, 그러면
   "게이팅 분기를 화면마다 쓰지 않는다" 는 §7 의 목적이 무너진다. */
import type { MyPrediction, StockPrediction } from '../../api/predictions'
import type { PredictionCardData } from './PredictionCard'

/** B-03 종목 상세의 남의 예측. 종목이 화면 제목이라 카드에 코드를 다시 적지 않는다 */
export function fromStockPrediction(p: StockPrediction): PredictionCardData {
  return {
    id: p.id,
    stockCode: null,
    direction: p.direction,
    status: p.status,
    locked: p.locked,
    channelId: p.channelId,
    author: p.author,
    accuracy: p.accuracy,
    targetPrice: p.targetPrice,
    basePrice: p.basePrice,
    errorRate: p.errorRate,
    horizon: p.horizon,
    dueDate: p.dueDate,
    to: `/predictions/${p.id}`,
  }
}

/** C-02 내 예측. 내 것이라 잠금도 작성자 줄도 없다 */
export function fromMyPrediction(p: MyPrediction): PredictionCardData {
  return {
    id: p.id,
    stockCode: p.stockCode,
    stockName: p.stockName,
    direction: p.direction,
    status: p.status,
    targetPrice: p.targetPrice,
    errorRate: p.errorRate,
    horizon: p.horizon,
    dueDate: p.settleDate,
    dday: p.dday,
    to: `/predictions/${p.id}`,
  }
}
