/* H-01 지갑·토큰 · /me/wallet
   담당 스토리 [ANT-FE-WALLET]
   설계서 docs/화면설계서.md §3 H · §4 H-01 · §9.1 · §9.2

   설계 제약
   - 잔액 원천은 온체인, DB 는 캐시다. syncedAt 을 반드시 병기한다 —
     숫자만 보이면 실시간 잔액으로 오해한다.
   - reason 은 7종뿐이다. 임의 사유 라벨을 만들지 않는다.
   - 보너스·보상은 서버·배치가 지급한다. 수령 버튼을 두지 않는다.
   - 충전·구매 버튼도 없다. 토큰 유입은 가입 보너스와 성과 보상 mint 뿐이다. */
import { useState } from 'react'
import { useApiQuery } from '../api/useApiQuery'
import { useCursorList } from '../api/useCursorList'
import {
  LEDGER_REASONS, REASON_LABEL, formatDateTime, formatToken,
  type LedgerEntry, type LedgerReason, type WalletBalance, type WalletStatus,
} from '../api/wallet'
import EmptyState from '../components/state/EmptyState'
import ErrorState from '../components/state/ErrorState'
import '../styles/screens/wallet.css'

export default function Wallet() {
  const [reason, setReason] = useState<LedgerReason | null>(null)

  const status = useApiQuery<WalletStatus>('/wallet')
  const balance = useApiQuery<WalletBalance>('/wallet/balance')
  const ledger = useCursorList<LedgerEntry>('/wallet/ledger', { reason: reason ?? undefined })

  return (
    <main className="main">
      <div className="main-inner">
        <div className="page-head">
          <h1>지갑 · 토큰</h1>
          <p>연동 상태와 ANT 잔액, 획득·사용 내역을 확인합니다</p>
        </div>

        {/* ── 연동 상태 + 잔액 ─────────────────────────── */}
        {status.error && <ErrorState error={status.error} onRetry={status.reload} />}

        {!status.error && status.data && !status.data.linked && (
          <EmptyState
            title="아직 지갑을 연동하지 않았습니다"
            hint="지갑을 연동해야 예측을 등록할 수 있습니다"
          />
        )}

        {!status.error && status.data?.linked && (
          <section className="wallet-card">
            <div className="wallet-address">
              <span className="wallet-label">연동 지갑</span>
              <code>{status.data.walletAddress}</code>
            </div>

            {balance.error ? (
              <ErrorState error={balance.error} onRetry={balance.reload} inline />
            ) : balance.data ? (
              <div className="wallet-balance">
                <span className="wallet-label">보유 잔액</span>
                <p className="wallet-amount">
                  <b className="num">{formatToken(balance.data.balance)}</b>
                  <small>{balance.data.symbol}</small>
                </p>
                {/* 대사 시각 — 이게 없으면 실시간 잔액으로 읽힌다 */}
                <p className="wallet-synced">
                  {`온체인 대사 ${formatDateTime(balance.data.syncedAt)} 기준`}
                </p>
              </div>
            ) : (
              <div className="wallet-balance"><span className="wallet-label">잔액을 불러오는 중…</span></div>
            )}
          </section>
        )}

        {/* ── 획득 · 사용 내역 ─────────────────────────── */}
        <div className="wallet-ledger-head">
          <h2>획득 · 사용 내역</h2>
          <div className="wallet-filters" role="group" aria-label="사유 필터">
            <button
              type="button" className={reason === null ? 'on' : ''}
              onClick={() => setReason(null)}
            >전체</button>
            {LEDGER_REASONS.map((r) => (
              <button
                key={r} type="button" className={reason === r ? 'on' : ''}
                onClick={() => setReason(r)}
              >{REASON_LABEL[r]}</button>
            ))}
          </div>
        </div>

        {ledger.error && <ErrorState error={ledger.error} onRetry={ledger.reload} />}

        {!ledger.error && ledger.items.length === 0 && !ledger.loading && (
          <EmptyState
            title={reason ? `${REASON_LABEL[reason]} 내역이 없습니다` : '아직 내역이 없습니다'}
            hint={reason ? '다른 사유로 확인해 보세요' : '가입 보너스와 성과 보상이 여기에 쌓입니다'}
          />
        )}

        {ledger.items.length > 0 && (
          <ul className="wallet-ledger">
            {ledger.items.map((e, i) => {
              const gain = !e.delta.startsWith('-')
              return (
                <li key={`${e.createdAt}-${i}`} className="wallet-entry">
                  <span className="wallet-entry-reason">{REASON_LABEL[e.reason]}</span>
                  <span className="wallet-entry-date">{formatDateTime(e.createdAt)}</span>
                  <span className={`wallet-entry-delta num ${gain ? 'gain' : 'spend'}`}>
                    {`${gain ? '+' : ''}${formatToken(e.delta)}`}
                  </span>
                  {/* 온체인 기록이 있으면 해시를 보여 준다. 배치 지급 등은 null 일 수 있다 */}
                  <span className="wallet-entry-tx num">
                    {e.txHash ? `${e.txHash.slice(0, 10)}…${e.txHash.slice(-6)}` : '—'}
                  </span>
                </li>
              )
            })}
          </ul>
        )}

        {ledger.loading && ledger.items.length > 0 && (
          <p className="wallet-more-hint">불러오는 중…</p>
        )}

        {ledger.hasNext && !ledger.loading && (
          <button type="button" className="wallet-more" onClick={ledger.loadMore}>
            더 보기
          </button>
        )}
      </div>
    </main>
  )
}
