/* D-01 커밋 원장 · /ledger
   담당 스토리 [ANT-FE-LEDGER-LIST]
   설계서 docs/화면설계서.md §3 D · §7

   설계 제약
   - 상태 표시는 AnchorBadge 를 쓴다. C-01 · C-02 · M-02 와 같은 컴포넌트다
     (구독 PENDING 도 여기 포함).
   - 머클루트·트랜잭션 해시는 잘라 보여주더라도 전체 값을 복사할 수 있어야 한다
     → CopyHash. 체인 탐색기에 붙여 넣어 직접 확인하는 길을 막지 않는다.
   - 앵커는 배치 B2 가 만든다. 실시간이 아니므로 갱신 주기를 오해하지 않게 표시한다.

   PENDING 행은 txHash · blockNumber · confirmedAt 이 비어 있다.
   아직 체인에 안 올랐을 뿐이고 실패가 아니다 — 빈 칸을 오류처럼 그리지 않는다. */
import { Link } from 'react-router-dom'
import { useCursorList } from '../api/useCursorList'
import {
  formatBusinessDate, formatConfirmedAt, type Anchor,
} from '../api/anchors'
import AnchorBadge from '../components/AnchorBadge'
import CopyHash from '../components/CopyHash'
import EmptyState from '../components/state/EmptyState'
import ErrorState from '../components/state/ErrorState'
import '../styles/screens/ledger.css'

export default function LedgerList() {
  const { items, hasNext, loading, error, loadMore, reload } = useCursorList<Anchor>('/anchors')

  return (
    <main className="main">
      <div className="main-inner">
        <div className="page-head">
          <h1>커밋 원장</h1>
          <p>봉인된 예측이 배치로 묶여 체인에 기록된 기록입니다</p>
        </div>

        {/* 배치 주기를 알려 준다 — 방금 등록한 예측이 여기 없다고 고장으로 읽히면 안 된다 */}
        <p className="lg-note">
          <svg width="13" height="13" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.9" strokeLinecap="round" strokeLinejoin="round" aria-hidden="true">
            <circle cx="12" cy="12" r="9" /><path d="M12 7.5V12l3 1.8" />
          </svg>
          앵커는 매 영업일 배치로 묶여 올라갑니다. 방금 등록한 예측은 다음 배치에 담깁니다.
        </p>

        {error && <ErrorState error={error} onRetry={reload} />}

        {!error && items.length === 0 && !loading && (
          <EmptyState
            title="앵커된 배치가 없습니다"
            hint="예측이 봉인되면 배치로 묶여 체인에 기록됩니다"
          />
        )}

        {items.length > 0 && (
          <ul className="lg-list">
            {items.map((a) => (
              <li key={a.id} className="lg-item">
                <div className="lg-head">
                  <div className="lg-title">
                    {/* DB 배치 번호. 체인 조회 키는 루트다(v3, ANT-CHAIN-13) */}
                    <span className="lg-batch num">{`#${a.id}`}</span>
                    <span className="lg-date">{formatBusinessDate(a.businessDate)}</span>
                  </div>
                  <AnchorBadge status={a.status} />
                </div>

                <dl className="lg-fields">
                  <div className="lg-field">
                    <dt>머클루트</dt>
                    <dd><CopyHash value={a.merkleRoot} /></dd>
                  </div>

                  <div className="lg-field">
                    <dt>커밋</dt>
                    <dd className="num">{`${a.commitCount.toLocaleString('ko-KR')}건`}</dd>
                  </div>

                  {/* 아래 셋은 확정된 배치에만 있다. PENDING·FAILED 는 자리를 비운다 */}
                  {a.txHash && (
                    <div className="lg-field">
                      <dt>트랜잭션</dt>
                      <dd><CopyHash value={a.txHash} /></dd>
                    </div>
                  )}

                  {a.blockNumber !== null && (
                    <div className="lg-field">
                      <dt>블록</dt>
                      <dd className="num">{a.blockNumber.toLocaleString('ko-KR')}</dd>
                    </div>
                  )}

                  {a.confirmedAt && (
                    <div className="lg-field">
                      <dt>확정</dt>
                      <dd>{formatConfirmedAt(a.confirmedAt)}</dd>
                    </div>
                  )}
                </dl>

                <div className="lg-foot">
                  {/* 장부 컨트랙트는 배치마다 같지만, 어느 장부에 올렸는지 밝혀 둔다 */}
                  <span className="lg-contract">
                    {'장부 '}<CopyHash value={a.contractAddress} head={8} tail={6} />
                    <span className="lg-chain num">{`chainId ${a.chainId}`}</span>
                  </span>
                  {/* routes.ts 의 D-02 경로가 /ledger/anchors/:id 다 */}
                  <Link className="lg-go" to={`/ledger/anchors/${a.id}`}>배치 상세<em aria-hidden="true">›</em></Link>
                </div>
              </li>
            ))}
          </ul>
        )}

        {loading && <p className="lg-loading">불러오는 중…</p>}

        {hasNext && !loading && (
          <button type="button" className="lg-more" onClick={loadMore}>더 보기</button>
        )}
      </div>
    </main>
  )
}
