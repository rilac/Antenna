/* D-02 앵커 배치 상세 · /ledger/anchors/:id
   담당 스토리 [ANT-FE-ANCHOR]
   설계서 docs/화면설계서.md §3 D

   설계 제약
   - 포함 커밋 목록이 길 수 있다. 목록 성능과 복사 편의를 함께 고려한다.
     → 화면에는 PAGE 개씩만 그리고, 복사는 전량을 한 번에 준다. 500건짜리 배치에서
       DOM 노드 500개를 한꺼번에 만들지 않으면서도 원본 전량을 가져갈 수 있다.
   - 커밋 순서를 바꾸지 않는다. 리프 순서(prediction id 오름차순) 그대로여야
     이 순서로 트리를 다시 접어 merkleRoot 를 재현할 수 있다. 정렬 기능을 두지 않는 이유다.

   - "여기서 특정 커밋을 골라 D-03 검산으로 이어갈 수 있게 한다"
     → 각 행이 /ledger/verify/{predictionId} 로 간다. ANT-CHAIN-09 가 리프에
       predictionId 를 동봉하기로 바꿔(결정 F2 뒤집음) 가능해졌다.

   익스플로러 링크를 두지 않는 이유
   설계서 §3 D 는 "트랜잭션 해시에서 외부 블록 익스플로러로 나가는 링크를 둔다" 고 하지만
   SSAFY 는 사설 Besu 망이라 익스플로러의 존재 자체가 확인되지 않는다. 공개된 접점은
   wss://ws.ssafy-blockchain.com RPC 하나뿐이다. 익스플로러는 RPC 를 대신 읽어 주는
   웹사이트일 뿐이라, 브라우저가 직접 읽으면 같은 값을 얻는다 — D-03 이 그 방식으로
   온체인 대조를 한다(ANT-CHAIN-09 결정). 여기서는 해시 복사만 둔다. */
import { useState } from 'react'
import { Link, useParams } from 'react-router-dom'
import { useApiQuery } from '../api/useApiQuery'
import {
  STATUS_LABEL, formatBusinessDate, formatConfirmedAt, type AnchorDetail,
} from '../api/anchors'
import AnchorBadge from '../components/AnchorBadge'
import CopyHash from '../components/CopyHash'
import ErrorState from '../components/state/ErrorState'
import '../styles/screens/anchor.css'

/** 한 번에 그리는 커밋 수. 배치가 500건까지 갈 수 있어 전량을 한꺼번에 그리지 않는다. */
const PAGE = 50

export default function Anchor() {
  const { id } = useParams<{ id: string }>()
  const anchor = useApiQuery<AnchorDetail>(`/anchors/${id}`)
  const [shown, setShown] = useState(PAGE)

  const commits = anchor.data?.commits ?? []
  const visible = commits.slice(0, shown)

  /** 목록이 잘려 있어도 원본 전량을 가져갈 수 있어야 한다(설계 제약).
      복사는 해시만 준다 — 트리를 다시 접을 때 쓰는 재료가 해시라서다. */
  const [copiedAll, setCopiedAll] = useState(false)
  async function copyAll() {
    try {
      await navigator.clipboard?.writeText(commits.map((c) => c.commitHash).join('\n'))
      setCopiedAll(true)
      setTimeout(() => setCopiedAll(false), 1600)
    } catch { /* 권한이 막힌 경우. 개별 행 복사는 그대로 동작한다 */ }
  }

  return (
    <main className="main">
      <div className="main-inner">
        <Link className="ac-back" to="/ledger">
          <svg width="15" height="15" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
            <path d="M15 18l-6-6 6-6" />
          </svg>
          커밋 원장
        </Link>

        {/* 없는 배치는 404 ANCHOR_NOT_FOUND 다 */}
        {anchor.error && <ErrorState error={anchor.error} onRetry={anchor.reload} />}

        {!anchor.error && !anchor.data && <p className="ac-loading">불러오는 중…</p>}

        {anchor.data && (
          <>
            <header className="ac-head">
              <div className="ac-title">
                <h1><span className="num">{`#${anchor.data.id}`}</span> 앵커 배치</h1>
                <AnchorBadge status={anchor.data.status} />
              </div>
              <p className="ac-sub">{`${formatBusinessDate(anchor.data.businessDate)} 영업일 · 커밋 ${anchor.data.commitCount.toLocaleString('ko-KR')}건`}</p>
            </header>

            <section className="ac-card">
              <dl className="ac-fields">
                <div className="ac-field">
                  <dt>머클루트</dt>
                  <dd><CopyHash value={anchor.data.merkleRoot} /></dd>
                </div>

                {/* 전송 전(PENDING 초기)에는 트랜잭션이 아직 없다 */}
                {anchor.data.txHash && (
                  <div className="ac-field">
                    <dt>트랜잭션</dt>
                    <dd><CopyHash value={anchor.data.txHash} /></dd>
                  </div>
                )}

                {anchor.data.blockNumber !== null && (
                  <div className="ac-field">
                    <dt>블록</dt>
                    <dd className="num">{anchor.data.blockNumber.toLocaleString('ko-KR')}</dd>
                  </div>
                )}

                {anchor.data.sentAt && (
                  <div className="ac-field">
                    <dt>전송</dt>
                    <dd>{formatConfirmedAt(anchor.data.sentAt)}</dd>
                  </div>
                )}

                {anchor.data.confirmedAt && (
                  <div className="ac-field">
                    <dt>확정</dt>
                    <dd>{formatConfirmedAt(anchor.data.confirmedAt)}</dd>
                  </div>
                )}

                {/* 재시도가 있었을 때만 보여준다. 1회는 정상이라 알릴 것이 없다 */}
                {anchor.data.attempts > 1 && (
                  <div className="ac-field">
                    <dt>전송 시도</dt>
                    <dd className="num">{`${anchor.data.attempts}회`}</dd>
                  </div>
                )}

                <div className="ac-field">
                  <dt>장부</dt>
                  <dd>
                    <CopyHash value={anchor.data.contractAddress} head={10} tail={8} />
                    <span className="ac-chain num">{`chainId ${anchor.data.chainId}`}</span>
                  </dd>
                </div>
              </dl>

              {/* 실패 사유는 서버가 준 문자열 그대로 보여준다 — 체인 오류 이름이라
                  옮기면 검색이 안 된다(예: RootMismatch · CHAIN_UNAVAILABLE) */}
              {anchor.data.status === 'FAILED' && anchor.data.lastError && (
                <div className="ac-error" role="alert">
                  <b>{`${STATUS_LABEL.FAILED} 사유`}</b>
                  <code>{anchor.data.lastError}</code>
                </div>
              )}
            </section>

            <section className="ac-commits">
              <div className="ac-commits-head">
                <h2>{`포함 커밋 ${commits.length.toLocaleString('ko-KR')}건`}</h2>
                {commits.length > 0 && (
                  <button type="button" className={`ac-copyall ${copiedAll ? 'copied' : ''}`} onClick={copyAll}>
                    {copiedAll ? '전체 복사됨' : '전체 복사'}
                  </button>
                )}
              </div>

              {/* 순서가 곧 검증 재료다. 정렬 기능을 두지 않는다 */}
              <p className="ac-order-note">
                리프 순서 그대로입니다. 이 순서로 트리를 접으면 위 머클루트가 나옵니다.
              </p>

              {commits.length === 0 ? (
                <p className="ac-empty">이 배치에 담긴 커밋이 없습니다</p>
              ) : (
                <>
                  <ol className="ac-commit-list" start={1}>
                    {visible.map((c, i) => (
                      <li key={c.predictionId}>
                        <span className="ac-commit-no num">{i + 1}</span>
                        <CopyHash value={c.commitHash} head={14} tail={10} />
                        {/* 이 커밋이 정말 루트에 들어 있는지 브라우저에서 직접 확인하는 화면으로 */}
                        <Link className="ac-verify" to={`/ledger/verify/${c.predictionId}`}>
                          검산<em aria-hidden="true">›</em>
                        </Link>
                      </li>
                    ))}
                  </ol>

                  {shown < commits.length && (
                    <button
                      type="button" className="ac-more"
                      onClick={() => setShown((n) => n + PAGE)}
                    >
                      {`더 보기 (${(commits.length - shown).toLocaleString('ko-KR')}건 남음)`}
                    </button>
                  )}
                </>
              )}
            </section>
          </>
        )}
      </div>
    </main>
  )
}
