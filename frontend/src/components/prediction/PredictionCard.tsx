/* 예측 카드. 설계서 §7 공용 — C-02 · C-03 · E-02 · F-04 가 함께 쓴다.
   "잠금 상태 내장 — 게이팅 분기를 화면마다 쓰지 않는다" 가 이 컴포넌트의 일이다.

   왜 DTO 를 그대로 받지 않는가
   네 화면의 응답 모양이 실제로 다르다. C-02 는 dday·settleDate 를 주고 author 가 없다
   (내 예측이라 잠금도 없다). B-03·E-02 는 author·accuracy·locked 를 주고 dday 가 없다.
   F-04 의 서버 PredictionCardResponse 는 여섯 필드뿐이다. 하나로 합치면 어느 화면에도
   맞지 않는 타입이 나오므로, 여기서는 **화면에 그릴 것** 만 받고 DTO→props 변환은
   아래 mapper 가 맡는다. 새 화면이 붙어도 이 파일은 안 바뀐다.

   PredictionStatus 가 먼저 빠져 있었고(상태 어휘·배지), 그 파일 주석이 "카드는 C-03 을
   만들 때 함께 정리한다" 고 미뤄 두었다. E-02 가 네 번째 소비처라 지금 뺀다 — 다만
   C-03·F-04 는 아직 화면이 없어 요구를 모른다. 그래서 그 둘이 붙을 때 필드가 늘 수는
   있어도, 지금 있는 세 모양(B-03·C-02·E-02)은 추측 없이 실제 응답에서 나왔다.

   무엇을 그리지 않는가
   근거 본문은 카드에 없다. 만기 리빌 후에도 구독자 전용이라 미리보기조차 두지 않는다
   (§4 C-03). 잠긴 카드도 종목·방향·작성자·적중률까지는 보여준다 — 존재를 감추면
   구독 유인이 사라진다(§5). */
import { Link } from 'react-router-dom'
import type { Direction, PredictionStatus as Status } from '../../api/predictions'
import LockedCard from '../state/LockedCard'
import PredictionStatus from './PredictionStatus'
import '../../styles/prediction-card.css'

export type PredictionCardData = {
  id: string | number
  stockCode: string | null
  stockName?: string | null
  direction: Direction
  status: Status
  /** 근거 본문을 볼 권한이 없을 때. 카드의 다른 값은 그대로 온다 */
  locked?: boolean
  /** 잠금을 푸는 채널. 없으면 구독 CTA 를 숨긴다 */
  channelId?: string | null
  author?: { userId: string; nickname: string } | null
  /** 작성자 적중률 %. 판정 이력이 없으면 null — 0% 로 그리지 않는다 */
  accuracy?: number | null
  targetPrice?: number | null
  /** 배치 B2 가 확정한다. 등록 직후에는 비어 있다 */
  basePrice?: number | null
  /** 판정 완료만 */
  errorRate?: number | null
  horizon?: number
  /** 만기 영업일 YYYY-MM-DD */
  dueDate?: string | null
  /** 만기까지 남은 일수. 판정이 끝났으면 null — 0 으로 그리지 않는다 */
  dday?: number | null
  /** 카드를 눌렀을 때 갈 곳. 없으면 링크를 만들지 않는다 */
  to?: string
}

const DIRECTION_LABEL: Record<Direction, string> = { UP: '상승', DOWN: '하락' }

const won = (n: number) => `${n.toLocaleString('ko-KR')}원`
const day = (iso: string) => iso.slice(2, 10).replace(/-/g, '.')

export default function PredictionCard({ data }: { data: PredictionCardData }) {
  const {
    stockCode, stockName, direction, status, locked, channelId,
    author, accuracy, targetPrice, errorRate, horizon, dueDate, dday, to,
  } = data

  /* 종목이 지워졌으면 서버가 코드·이름 둘 다 null 로 내린다. 이름이 없으면 코드로,
     코드까지 없으면 그 자리를 비운다 — "null" 을 그리지 않는다. */
  const title = stockName ?? stockCode ?? '—'

  const body = (
    <>
      <div className="pc-head">
        <span className="pc-stock">
          <b>{title}</b>
          {stockName && stockCode && <em className="num">{stockCode}</em>}
        </span>
        <span className={`pc-dir is-${direction.toLowerCase()}`}>{DIRECTION_LABEL[direction]}</span>
        <PredictionStatus status={status} />
      </div>

      {author && (
        <p className="pc-author">
          {author.nickname}
          {/* 적중률은 판정 이력이 있을 때만. 없으면 칸째 빼서 0% 로 오해하지 않게 한다 */}
          {accuracy !== null && accuracy !== undefined && (
            <span className="pc-accuracy num">{`적중률 ${accuracy}%`}</span>
          )}
        </p>
      )}

      <dl className="pc-figures">
        <div>
          <dt>목표가</dt>
          {/* 잠겨도 값이 온다. 비어 오는 것은 종목이 지워진 경우뿐이다 */}
          <dd className="num">{targetPrice === null || targetPrice === undefined ? '—' : won(targetPrice)}</dd>
        </div>
        {horizon !== undefined && (
          <div>
            <dt>기간</dt>
            <dd className="num">{`${horizon}영업일`}</dd>
          </div>
        )}
        {dueDate && (
          <div>
            <dt>만기</dt>
            <dd className="num">{day(dueDate)}</dd>
          </div>
        )}
        {/* 판정이 끝난 건은 dday 가 null 이다 — 그때는 이 칸을 그리지 않는다 */}
        {dday !== null && dday !== undefined && (
          <div>
            <dt>남은 기간</dt>
            <dd className="num">{`D-${dday}`}</dd>
          </div>
        )}
        {errorRate !== null && errorRate !== undefined && (
          <div>
            <dt>오차율</dt>
            <dd className="num">{`${errorRate}%`}</dd>
          </div>
        )}
      </dl>
    </>
  )

  /* 잠금은 카드를 대체하지 않고 카드 안에 든다. 무엇이 잠겼는지 보이지 않으면
     구독 유인이 사라진다(§5).

     **잠기는 것은 근거뿐이다**(2026-09-08 결정). 예측가·종목·방향·목표가·기간은
     누구에게나 보인다 — 전에는 목표가까지 가렸는데, 그러면 "누가 무언가를
     예측했다" 만 남아 예측가를 고를 근거가 안 된다. 대신 판단의 이유(근거 본문)는
     만기가 지나도 풀리지 않는다(payload 에 noteHash 만 들어가므로 애초에 공개
     대상이 아니다 — §4 C-03). 그게 구독으로 사는 것이다. */
  if (locked) {
    return (
      <article className="pc is-locked">
        {body}
        <LockedCard label="근거" channelId={channelId ?? undefined} />
      </article>
    )
  }

  return to
    ? <Link className="pc is-link" to={to}>{body}</Link>
    : <article className="pc">{body}</article>
}
