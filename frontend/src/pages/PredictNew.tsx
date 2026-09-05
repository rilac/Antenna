/* C-01 예측 등록 · /predict?code=
   담당 스토리 [ANT-FE-PREDICT-NEW]
   설계서 docs/화면설계서.md §3 C · §4 C-01

   화면 자체는 B-03 종목 상세 안의 안쪽 페이지로 들어갔다. 종목을 보다가 바로
   예측하는 흐름이라 페이지를 갈아 끼우면 방금 읽던 근거와 차트가 사라진다.
   또 evidencePointIds 인계에는 API 가 없어 클라이언트 상태로 넘겨야 하는데,
   라우트를 건너뛰면 그 상태가 끊긴다(§4 B-03).

   그래서 이 라우트는 살려 두되 넘겨보내는 자리로만 쓴다. 설계서에 적힌 주소로
   들어오거나 이전에 만든 링크를 눌러도 같은 화면에 닿는다.
   code 가 없으면 어느 종목인지 알 수 없으므로 종목 탐색으로 보낸다. */
import { Navigate, useSearchParams } from 'react-router-dom'

export default function PredictNew() {
  const [params] = useSearchParams()
  const code = params.get('code')

  return <Navigate to={code ? `/stocks/${code}?tab=predict` : '/stocks'} replace />
}
