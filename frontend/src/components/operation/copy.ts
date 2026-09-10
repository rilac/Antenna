/* M-02 문구표 · 결과 경로. [ANT-FE-OPERATION]

   티켓 제약이 "kind 4종으로 문구만 분기한다. 구조를 분기하지 않는다" 라, 갈리는 것을 전부
   이 표 하나에 모았다. 컴포넌트 쪽에는 분기가 없다 — 새 kind 가 생기면 여기에 한 줄 는다.

   컴포넌트 파일이 아니라 여기 있는 이유는 Fast Refresh 다(react/only-export-components). */
import type { OperationKind, OperationResource } from '../../api/operations'

type Copy = {
  /**
   * PENDING 일 때. **무엇을 기다리는지** 말한다.
   *
   * 티켓이 "로딩 스피너로 뭉뚱그리지 말고" 라고 못박은 자리다. "처리 중" 은 사용자가
   * 무엇을 기다리는지도, 얼마나 걸릴지도 짐작할 수 없게 만든다.
   */
  waiting: string
  done: string
  failed: string
  /** 결과로 보내는 버튼 */
  go: string
}

export const OPERATION_COPY: Record<OperationKind, Copy> = {
  PREDICTION_BURN: {
    waiting: '예측 등록을 체인에서 확인하고 있습니다',
    done: '예측이 등록됐습니다',
    failed: '예측 등록이 체인에서 실패했습니다',
    go: '예측 보기',
  },
  SUBSCRIBE: {
    waiting: '결제를 체인에서 확인하고 있습니다',
    done: '구독이 시작됐습니다',
    failed: '결제가 체인에서 실패했습니다',
    go: '내 구독 보기',
  },
  AD: {
    waiting: '등록을 체인에서 확인하고 있습니다',
    done: '배너 등록이 끝났습니다',
    failed: '등록이 체인에서 실패했습니다',
    go: '홈에서 보기',
  },
  SEASON_JOIN: {
    waiting: '참가 신청을 체인에서 확인하고 있습니다',
    done: '시즌에 참가했습니다',
    failed: '참가 신청이 체인에서 실패했습니다',
    go: '시즌으로 가기',
  },
}

/**
 * 실패 화면에서 가장 먼저 궁금한 것. kind 와 무관하게 같은 문장이라 따로 둔다.
 *
 * 네 흐름 모두 토큰을 쓰는 작업이고, 체인에서 실패했다는 것은 트랜잭션이 되돌아갔다는
 * 뜻이라 차감도 없다. 이 한 줄이 없으면 사용자는 돈이 사라졌는지부터 걱정한다.
 */
export const REFUND_NOTE = '토큰은 소각되지 않았습니다.'

/**
 * SUCCEEDED 일 때 갈 곳. "사용자가 스스로 결과를 찾아가게 두지 않는다"(티켓 제약).
 *
 * SEASON_PARTICIPANT 만 null 이다 — resource.id 가 **참가 id** 라서 시즌 id 를 모르면
 * /sim/{시즌}/play 를 만들 수 없다. 그 화면(G-03)은 시즌 id 를 이미 쥐고 있으므로
 * OperationModal 의 to 로 넘겨 덮는다. 여기서 억지로 짐작하면 없는 주소로 보낸다.
 */
export function resourcePath(resource?: OperationResource): string | null {
  if (!resource) return null
  switch (resource.type) {
    case 'PREDICTION':
      return `/predictions/${resource.id}`
    // 구독 단건 라우트가 없다. 목록(E-03)이 방금 생긴 구독을 맨 위에 보여준다
    case 'SUBSCRIPTION':
      return '/me/subscriptions'
    // 광고 상세 화면은 없다. 배너가 실제로 걸리는 곳이 홈(B-01)이라 거기서 확인시킨다
    case 'AD':
      return '/'
    case 'SEASON_PARTICIPANT':
      return null
  }
}
