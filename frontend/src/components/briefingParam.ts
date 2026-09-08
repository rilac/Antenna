/* M-10 을 여는 쿼리. 설계 제약 §4 M-10 —
   "라우트를 만들지 않는다. 딥링크가 필요하면 ?briefing={id} 쿼리로만 열고
    닫을 때 제거한다. 라우트로 승격시키지 않는다."

   왜 쿼리인가 (라우트가 아니라)
   라우트면 히스토리에 남아 이탈 시 복귀가 어렵고, 무엇보다 **뒤 화면이 언마운트된다.**
   B-03 종목 상세는 블록마다 따로 읽어 온 데이터를 들고 있어서, 브리핑 하나 보려고
   그걸 다 버리면 닫을 때 아홉 블록을 다시 부른다. 쿼리만 바뀌면 App 의 Page 가
   보는 meta 가 그대로라 window.scrollTo(0,0) 도 돌지 않는다 — 스크롤 위치가
   유지되는 것도 이 덕분이다(설계 제약의 셋째 줄).

   왜 열 때도 replace 인가
   히스토리를 쌓지 않는다. B-03 탭 전환(StockDetail 의 goTab)과 같은 판단이다 —
   브리핑을 다섯 개 열어 본 뒤 목록으로 돌아가려고 뒤로가기를 다섯 번 눌러야 하면
   답답하다. 대신 모달이 열린 채 뒤로가기를 누르면 모달이 닫히는 게 아니라 앞 화면을
   벗어난다. 닫기는 X · 배경 · Esc 셋으로 열어 두었다.

   컴포넌트 파일이 아니라 여기 두는 이유
   BriefingModal.tsx 에서 훅을 함께 내보내면 Fast Refresh 가 끊긴다
   (react-refresh/only-export-components). predictForm.ts 와 같은 자리다. */
import { useCallback } from 'react'
import { useSearchParams } from 'react-router-dom'

const KEY = 'briefing'

export function useBriefingParam() {
  const [params, setParams] = useSearchParams()

  /* 주소창은 누구나 고칠 수 있다. 숫자가 아니면 안 연 것으로 친다 —
     getBriefing 에 NaN 을 넘겨 /briefings/NaN 을 부르지 않는다. */
  const raw = params.get(KEY)
  const id = raw !== null && /^\d+$/.test(raw) ? Number(raw) : null

  const open = useCallback((next: number) => {
    const p = new URLSearchParams(params)
    p.set(KEY, String(next))
    setParams(p, { replace: true })
  }, [params, setParams])

  const close = useCallback(() => {
    const p = new URLSearchParams(params)
    p.delete(KEY)
    setParams(p, { replace: true })
  }, [params, setParams])

  return { id, open, close }
}
