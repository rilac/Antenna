/* C-01 예측 입력값의 모양.

   **컴포넌트 파일이 아니라 여기 둔다.** 값(EMPTY_PREDICT_FORM)을 컴포넌트와 같은
   파일에서 내보내면 Vite Fast Refresh 가 그 모듈을 컴포넌트 모듈로 보지 못해
   갱신이 통째로 새로고침이 된다(auth/context.ts 를 AuthContext 에서 가른 것과
   같은 이유다).

   이 값을 PredictTab 이 아니라 부모(StockDetail)가 든다. 근거를 고르러 종목 정보
   탭으로 가면 PredictTab 이 언마운트되므로, 안에 두면 돌아왔을 때 적어 둔 것이
   전부 사라진다 — picked 를 부모가 들고 있는 것과 같은 이유다. */
import { newNoteSalt } from '../../api/predictions'
import type { Direction, Horizon } from '../../api/predictions'

export type PredictForm = {
  direction: Direction | null
  target: string
  horizon: Horizon | null
  note: string
  /** 방향을 사람이 직접 골랐는가. 참이면 목표가로 다시 정하지 않는다 */
  directionTouched: boolean
  /**
   * 근거를 봉인하는 난수(ANT-PRED-02). 폼과 함께 산다 — 글자를 칠 때마다 새로 뽑으면
   * 미리보기의 noteHash·commitHash 가 매번 바뀌어 "무엇에 서명하는지" 를 볼 수 없고,
   * 서명한 값과 보낸 값이 어긋날 수도 있다. 등록이 끝나면 폼과 함께 버린다.
   */
  noteSalt: string
}

/**
 * 빈 폼. **상수가 아니라 함수다** — noteSalt 를 모듈 상수에 담으면 한 번 켠 브라우저의
 * 모든 예측이 같은 난수를 쓰게 되고, 그건 난수가 없는 것과 같다.
 */
export const emptyPredictForm = (): PredictForm => ({
  direction: null, target: '', horizon: null, note: '', directionTouched: false,
  noteSalt: newNoteSalt(),
})
