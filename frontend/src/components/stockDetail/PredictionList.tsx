/* 이 종목에 걸린 남의 예측 — B-03 예측 탭 왼쪽.

   판정 대기와 판정 완료를 한 목록에 섞지 않는다. 둘은 읽는 법이 아예 다르다 —
   대기 건은 "누가 언제까지 무엇을 걸었나" 이고, 완료 건은 "얼마나 맞혔나" 다.
   섞어 두면 오차와 만기일이 번갈아 나와 어느 쪽도 눈에 들어오지 않는다.

   거르기는 서버가 한다(phase). 커서 페이징이라 서버가 잘라 준 뒤에 클라이언트가
   거르면 페이지마다 줄 수가 들쭉날쭉해진다.

   설계서 §5 게이팅이 나머지 절반이다.
   - 판정 완료(HIT/MISS)는 전체 공개다. 방향·목표가·오차를 다 보여준다.
   - 미판정(BASE/OPEN)은 작성자·구독자만 본다. 남에게는 잠금과 구독 CTA 를
     띄우되 **없는 것처럼 감추지 않는다** — 404 로 그리면 구독 유인이 사라진다.
     작성자·적중률·기간은 잠긴 줄에서도 보인다.
   - 근거 본문은 여기에 나오지 않는다. 만기 리빌 후에도 구독자 전용이라
     목록에 미리보기조차 두지 않는다(§4 C-03). */
import { useState } from 'react'
import { Link } from 'react-router-dom'
import { useCursorList } from '../../api/useCursorList'
import {
  PHASE_LABEL, PREDICTION_PHASES, fetchStockPredictions,
} from '../../api/predictions'
import type {
  PredictionPhase, PredictionStatus, StockPrediction, StockPredictionMeta,
} from '../../api/predictions'
import ErrorState from '../state/ErrorState'
import { Panel } from './Block'

const STATUS_LABEL: Record<PredictionStatus, string> = {
  BASE: '기준가 대기',
  OPEN: '판정 대기',
  HIT: '적중',
  MISS: '빗나감',
}

/* 상태 전이는 BASE → OPEN → HIT/MISS 다. 앞의 둘은 이름만으로 차이가 잘 읽히지
   않아 설명을 붙인다 — 둘 다 "대기" 라 무엇을 기다리는지가 구분점이다. */
const STATUS_HINT: Record<PredictionStatus, string> = {
  BASE: '등록은 됐지만 판정의 출발점이 될 기준가가 아직 정해지지 않았습니다. 다음 영업일 종가로 확정됩니다.',
  OPEN: '기준가가 정해졌고, 만기일 종가가 나오면 판정합니다.',
  HIT: '만기 종가가 목표가에 닿아 적중으로 판정됐습니다.',
  MISS: '만기 종가가 목표가에 닿지 못했습니다.',
}

const won = (n: number) => `${n.toLocaleString('ko-KR')}원`
const day = (iso: string) => iso.slice(0, 10).replace(/-/g, '.').slice(2)

export default function PredictionList({ code, span }: { code: string; span?: 5 | 6 | 7 }) {
  const [phase, setPhase] = useState<PredictionPhase>('PENDING')

  /* 종목이나 묶음이 바뀌면 새 fetcher 가 만들어져 훅이 처음부터 다시 읽는다 */
  const list = useCursorList<StockPrediction, StockPredictionMeta>(
    fetchStockPredictions(code, phase),
  )

  /* 건수·적중률은 목록 전체 값이라 서버가 준다. 불러온 페이지로 세면
     "더 보기" 를 누를 때마다 숫자가 바뀐다. */
  const count: Record<PredictionPhase, number | null> = {
    PENDING: list.meta?.pendingCount ?? null,
    JUDGED: list.meta?.judgedCount ?? null,
  }
  const hitRate = list.meta?.hitRate ?? null

  return (
    <Panel
      title="이 종목의 예측"
      note={phase === 'JUDGED' && hitRate !== null ? `적중 ${hitRate}%` : undefined}
      span={span}
    >
      {/* 묶음 전환. 탭이 아니라 목록의 거르개라 role=tablist 를 쓰지 않는다 */}
      <div className="pl-phase" role="group" aria-label="예측 묶음">
        {PREDICTION_PHASES.map((p) => (
          <button
            key={p}
            type="button"
            className={p === phase ? 'is-on' : undefined}
            aria-pressed={p === phase}
            onClick={() => setPhase(p)}
          >
            {PHASE_LABEL[p]}
            {count[p] !== null && <span className="pl-phase-n num">{count[p]}</span>}
          </button>
        ))}
      </div>

      {list.error ? (
        <ErrorState error={list.error} onRetry={list.reload} inline />
      ) : list.loading && list.items.length === 0 ? (
        <div className="sd-skel" style={{ height: 300 }} aria-hidden="true" />
      ) : list.items.length === 0 ? (
        <p className="sd-block-empty">
          {phase === 'PENDING'
            ? '판정을 기다리는 예측이 없습니다. 첫 예측을 남겨 보세요.'
            : '아직 판정이 끝난 예측이 없습니다. 만기가 지나면 이곳에 쌓입니다.'}
        </p>
      ) : (
        <>
          <ul className="pl-list">
            {list.items.map((p) => (
              <li key={p.id} className={p.locked ? 'pl-row is-locked' : 'pl-row'}>
                <div className="pl-who">
                  <Link className="pl-name" to={`/channels/${p.author.userId}`}>
                    {p.author.nickname}
                  </Link>
                  {/* 적중률이 없는 사람은 판정 이력이 없다. 0% 로 그리면 실력이 나쁜 것처럼 보인다 */}
                  <span className="pl-acc num">
                    {p.accuracy === null ? '판정 이력 없음' : `적중률 ${p.accuracy}%`}
                  </span>
                </div>

                <div className="pl-main">
                  {/* 방향은 잠겨도 보인다. 잠기는 것은 목표가뿐이다 —
                      방향까지 가리면 "누가 무엇을 걸었는지" 가 통째로 사라진다 */}
                  <p className="pl-call num">
                    <b className={p.direction === 'UP' ? 'up' : 'down'}>
                      {p.direction === 'UP' ? '상승' : '하락'}
                    </b>

                    {p.locked ? (
                      <span className="pl-locked">
                        <svg width="12" height="12" viewBox="0 0 24 24" fill="none" stroke="currentColor"
                             strokeWidth="2" strokeLinecap="round" strokeLinejoin="round" aria-hidden="true">
                          <rect x="4" y="10" width="16" height="10" rx="2" />
                          <path d="M8 10V7a4 4 0 0 1 8 0v3" />
                        </svg>
                        목표가는 구독자에게만
                        {p.channelId && (
                          <Link className="pl-sub" to={`/channels/${p.channelId}`}>구독하고 보기</Link>
                        )}
                      </span>
                    ) : (
                      <span className="pl-target">{p.targetPrice === null ? '—' : won(p.targetPrice)}</span>
                    )}

                    {/* 판정 완료만 오차가 있다. 대기 중인 건 0 으로 그리지 않는다 */}
                    {p.errorRate !== null && (
                      <span className={`pl-err ${Math.abs(p.errorRate) <= 3 ? 'is-near' : ''}`}>
                        {`오차 ${p.errorRate > 0 ? '+' : ''}${p.errorRate}%`}
                      </span>
                    )}
                  </p>
                </div>

                <div className="pl-meta">
                  <span className={`pl-status is-${p.status.toLowerCase()}`} title={STATUS_HINT[p.status]}>
                    {STATUS_LABEL[p.status]}
                  </span>
                  {/* 대기 건은 언제까지인지, 완료 건은 언제 끝났는지가 궁금하다 */}
                  <span className="pl-when num">
                    {phase === 'PENDING'
                      ? `${p.horizon}일 · ~${day(p.dueDate)}`
                      : `${p.horizon}일 · ${day(p.dueDate)} 판정`}
                  </span>
                </div>
              </li>
            ))}
          </ul>

          {list.hasNext && (
            <button type="button" className="pl-more" disabled={list.loading} onClick={list.loadMore}>
              {list.loading ? '불러오는 중…' : '더 보기'}
            </button>
          )}
        </>
      )}
    </Panel>
  )
}
