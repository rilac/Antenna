/* E-02 채널의 예측 카드를 폈을 때 아래에 붙는 판단 내용.
   설계서 §4 C-03 · §5

   왜 목록 응답으로 못 그리는가
   채널 예측 목록(ChannelPrediction)에는 근거가 없다. 종목·방향·목표가·기간까지다.
   근거 본문(note)은 GET /predictions/{id} 에만 있다. 그래서 펼 때 한 건만 따로
   읽는다 — 목록을 그릴 때 미리 다 읽지 않는다.

   **고른 근거 포인트는 여기서 보여주지 않는다**
   응답의 evidencePoints 는 그리지 않는다. 이 자리는 "무슨 판단을 했는가" 를 훑는
   곳이고, 어떤 리서치 포인트를 골랐는지는 그 판단을 검증하는 재료라 C-03 에서
   본다. 목록 안에 다 펼쳐 두면 카드 하나가 화면을 덮어 여러 건을 비교할 수 없고,
   "자세히 보기" 가 더 보여줄 것이 없어진다.

   카드에 미리보기를 두지 않는 규칙과 부딪히지 않는다
   PredictionCard 머리말이 "근거 본문은 카드에 없다" 고 적어 두었다. 그 규칙은
   **목록에 미리보기를 흘리지 말라**는 뜻이다. 여기는 사용자가 눌러서 연 자리이고,
   보여주는 것도 서버가 noteLocked=false 로 내려준 경우뿐이다. 잠긴 건은 본문 대신
   잠겼다는 사실만 말한다 — 3줄 미리보기 같은 것은 주지 않는다(리포트와 다르다).

   마운트될 때 읽는다
   펼칠 때 이 컴포넌트가 생기고 접으면 사라진다. 그래서 훅을 조건부로 부르지 않고도
   "열 때만 읽기" 가 된다. */
import { useCallback } from 'react'
import { Link } from 'react-router-dom'
import { getPredictionDetail, type PredictionDetail } from '../../api/predictions'
import { useAsync } from '../../api/useAsync'
import ErrorState from '../state/ErrorState'

/**
 * @param locked 목록이 이미 잠금으로 내려준 건. 그러면 근거도 반드시 잠겨 있다 —
 *   서버에서 locked = 판정 전 && !canRead 이고 noteLocked = !canRead 이므로
 *   locked 면 noteLocked 다. 답이 정해진 요청을 보내지 않는다.
 */
export default function PredictionNote(
  { id, isMe, locked }: { id: string; isMe: boolean; locked: boolean },
) {
  const load = useCallback(
    () => (locked ? Promise.resolve(null) : getPredictionDetail(id)),
    [id, locked],
  )
  const detail = useAsync<PredictionDetail | null>(load)

  return (
    <div className="ch-note">
      {/* locked 면 카드가 이미 "근거는 구독자에게만 공개됩니다" 잠금 카드를 그리고 있다.
          바로 아래에서 같은 말을 되풀이하지 않는다 — 여기서는 상세로 가는 길만 준다.
          판정 후 비구독(locked=false · noteLocked=true)은 카드에 잠금 표시가 없으므로
          그때는 아래 Body 가 이유를 말해야 한다. */}
      {locked
        ? null
        : (
          <>
            {detail.loading && <p className="ch-note-msg">판단 내용을 불러오는 중…</p>}
            {detail.error && <ErrorState error={detail.error} onRetry={detail.reload} inline />}
            {detail.data && <Body d={detail.data} isMe={isMe} />}
          </>
        )}

      {/* 상세로 가는 길은 상태와 무관하게 늘 둔다 — 근거가 잠겨 있어도 커밋 해시·
          앵커·진행률은 C-03 에서 볼 수 있다(§4 C-03 은 그것들을 잠금 대상에서 뺐다) */}
      <Link className="ch-note-more" to={`/predictions/${id}`}>자세히 보기</Link>
    </div>
  )
}

/* 내 채널에서 이 분기가 보이면 서버가 나를 작성자로 보지 않는다는 뜻이라
   구독 안내가 엉뚱한 말이 된다. 그래서 문구를 가른다. */
function LockedMsg({ isMe }: { isMe: boolean }) {
  return (
    <p className="ch-note-msg is-locked">
      {isMe
        ? '이 예측의 판단 내용을 불러오지 못했습니다.'
        : '판단 내용은 구독자에게만 보입니다. 판정이 끝난 뒤에도 그렇습니다.'}
    </p>
  )
}

function Body({ d, isMe }: { d: PredictionDetail; isMe: boolean }) {
  if (d.noteLocked) return <LockedMsg isMe={isMe} />

  if (!d.note) {
    /* 잠긴 게 아니라 작성자가 안 적은 경우다. 둘을 같은 문구로 말하면 안 된다.
       근거 포인트가 있어도 여기서는 안 그리므로(위 머리말) note 하나로 판단한다. */
    return <p className="ch-note-msg">적어 둔 판단 내용이 없습니다.</p>
  }

  return (
    <>
      {/* 줄바꿈이 문단 구분이다. 서버가 주는 문자열이라 마크업으로 해석하지 않는다 */}
      {d.note.split('\n').filter((line) => line.trim() !== '').map((line, i) => (
        <p className="ch-note-body" key={i}>{line}</p>
      ))}
    </>
  )
}
