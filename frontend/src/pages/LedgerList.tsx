/* D-01 커밋 원장 · /ledger
   담당 스토리 [ANT-FE-LEDGER-LIST]
   설계서 docs/화면설계서.md §3 D · §4 의 제약을 보고 이 자리를 채운다.

   셸이 미리 붙여 둔 것: useCursorList 로 GET /anchors 를 읽는 배선.
   목록 계약({ items, nextCursor, hasNext })이 실제로 도는지 보이기 위한
   최소 표현이며, 앵커 배지·머클루트 표시 등은 이 스토리에서 만든다. */
import { useCursorList } from '../api/useCursorList'

type Anchor = {
  id: string
  merkleRoot: string
  commitCount: number
  status: 'PENDING' | 'CONFIRMED' | 'FAILED'
}

export default function LedgerList() {
  const { items, hasNext, loading, error, loadMore } = useCursorList<Anchor>('/anchors')

  return (
    <main className="main">
      <div className="main-inner">
        <div className="page-head">
          <h1>커밋 원장</h1>
          <p>{'D-01 · /ledger'}</p>
        </div>

        {error && (
          <div className="placeholder tall">
            {`목록을 불러오지 못했습니다 (${error.code}) — 백엔드 연동 전입니다`}
          </div>
        )}

        {!error && items.length === 0 && !loading && (
          <div className="placeholder tall">{'앵커된 배치가 없습니다'}</div>
        )}

        {items.length > 0 && (
          <ul className="grid">
            {items.map((a) => (
              <li key={a.id} className="card">
                <h2>{a.merkleRoot}</h2>
                <p>{`커밋 ${a.commitCount}건 · ${a.status}`}</p>
              </li>
            ))}
          </ul>
        )}

        {loading && <div className="placeholder">{'불러오는 중…'}</div>}

        {hasNext && !loading && (
          <button type="button" className="card" onClick={loadMore} style={{ marginTop: 14, width: '100%' }}>
            더 보기
          </button>
        )}
      </div>
    </main>
  )
}
