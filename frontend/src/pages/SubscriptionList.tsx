/* E-03 내 구독 · /me/subscriptions
   담당 스토리 [ANT-FE-SUBSCRIPTION-LIST]
   설계서 docs/화면설계서.md §3 E · §4 E-03 · §4 H-02 · §7

   설계 제약
   - DELETE .../renewal 은 구독이 아니라 **갱신 예약**을 지운다. 라벨을 "자동 갱신 해지" 로
     쓰고, 만료일까지는 계속 이용 가능하다는 점을 함께 표시한다. "구독 취소" 로 쓰면
     지금 접근이 끊긴다고 읽는다.
   - PENDING 은 결제가 블록 확정을 기다리는 상태다. AnchorBadge 로 표현한다(§7 —
     "구독 PENDING 도 같은 컴포넌트").
   - 자동 갱신은 최초 구독 시 오퍼레이터 위임에 동의한 경우에만 배치 B4 가 처리한다.
     동의 상태를 화면에서 확인할 수 있어야 한다.
   - 갱신 실패 알림(RENEW_FAILED)이 이 화면으로 딥링크된다.

   위임 동의 상태를 renewEnabled 로 대신하는 이유
   응답에 operatorAuthorize 를 따로 내려 주는 필드가 없다(명세 §채널·구독 여섯 필드).
   위임 승인은 최초 구독 서명에 포함되므로 구독 행이 있다는 것 자체가 동의가 있었다는
   뜻이고, 그 뒤로 변하는 값은 갱신 예약뿐이다. 그래서 renewEnabled 를 "자동 갱신" 으로
   그린다. 서버가 동의 여부를 따로 내려 주게 되면 이 자리에 한 줄을 더한다.

   해지에 확인 단계를 두는 이유
   **다시 켜는 API 가 없다.** 명세에 DELETE 만 있고 되살리는 경로가 없어, 실수로 누르면
   이 화면에서 되돌릴 방법이 없다. 토글처럼 보이게 만들지 않는다. */
import { useCallback, useState } from 'react'
import { Link } from 'react-router-dom'
import { daysLeft, formatExpiry } from '../api/channels'
import type { ApiError } from '../api/errors'
import {
  STATUS_LABEL, cancelRenewal, fetchMySubscriptions, type Subscription,
} from '../api/subscriptions'
import { useAsync } from '../api/useAsync'
import { formatToken } from '../api/wallet'
import AnchorBadge from '../components/AnchorBadge'
import EmptyState from '../components/state/EmptyState'
import ErrorState from '../components/state/ErrorState'
import '../styles/screens/subscriptions.css'

export default function SubscriptionList() {
  const load = useCallback(() => fetchMySubscriptions(), [])
  const list = useAsync(load)

  /** 확인 단계에 들어간 구독. 한 번에 하나만 연다 */
  const [confirming, setConfirming] = useState<number | null>(null)
  /** 해지 요청 중인 구독. 버튼을 두 번 누르지 못하게 한다 */
  const [canceling, setCanceling] = useState<number | null>(null)
  const [cancelError, setCancelError] = useState<ApiError | null>(null)

  async function confirmCancel(id: number) {
    setCancelError(null)
    setCanceling(id)
    try {
      await cancelRenewal(id)
      setConfirming(null)
      /* 서버가 204 라 새 값을 주지 않는다. 지역 상태로 renewEnabled 를 뒤집는 대신
         목록을 다시 읽는다 — 해지와 함께 status 가 바뀌는 경우(만료 직전 등)를
         화면이 지어내지 않게 한다. */
      list.reload()
    } catch (e) {
      setCancelError(e as ApiError)
    } finally {
      setCanceling(null)
    }
  }

  const items = list.data?.items ?? []

  return (
    <main className="main">
      <div className="main-inner">
        <div className="page-head">
          <h1>내 구독</h1>
          <p>구독 중인 채널과 갱신 상태를 확인합니다</p>
        </div>

        {list.error && <ErrorState error={list.error} onRetry={list.reload} />}
        {list.loading && !list.data && <p className="sb-loading">불러오는 중…</p>}

        {list.data && items.length === 0 && (
          <EmptyState
            title="구독 중인 채널이 없습니다"
            hint="예측가 랭킹이나 채널 프로필에서 구독할 수 있습니다"
          />
        )}

        {items.length > 0 && (
          <ul className="sb-list">
            {items.map((s) => (
              <li key={s.id}>
                <Row
                  sub={s}
                  confirming={confirming === s.id}
                  busy={canceling === s.id}
                  error={confirming === s.id ? cancelError : null}
                  onAsk={() => { setCancelError(null); setConfirming(s.id) }}
                  onCancelAsk={() => { setCancelError(null); setConfirming(null) }}
                  onConfirm={() => void confirmCancel(s.id)}
                />
              </li>
            ))}
          </ul>
        )}
      </div>
    </main>
  )
}

type RowProps = {
  sub: Subscription
  confirming: boolean
  busy: boolean
  error: ApiError | null
  onAsk: () => void
  onCancelAsk: () => void
  onConfirm: () => void
}

function Row({ sub, confirming, busy, error, onAsk, onCancelAsk, onConfirm }: RowProps) {
  const { publisher, fee, status, expiresAt, renewEnabled } = sub

  return (
    <article className={`sb-item is-${status.toLowerCase()}`}>
      <div className="sb-head">
        <Link className="sb-channel" to={`/channels/${publisher.userId}`}>
          {publisher.nickname}
        </Link>

        {/* PENDING 만 AnchorBadge 다 — 블록 확정 대기라는 뜻이 앵커와 같다(§7).
            ACTIVE·EXPIRED 는 온체인 상태가 아니라 구독 기간의 문제라 별도 배지를 쓴다. */}
        {status === 'PENDING' ? (
          <AnchorBadge status="PENDING" label={STATUS_LABEL.PENDING} />
        ) : (
          <span className={`sb-badge is-${status.toLowerCase()}`}>{STATUS_LABEL[status]}</span>
        )}
      </div>

      <dl className="sb-facts">
        <div>
          <dt>구독료</dt>
          {/* 결제 시점 박제값이다. 채널의 현재 가격을 나란히 두지 않는다 —
              두 숫자가 붙으면 어느 쪽이 내가 내는 값인지 흐려진다 */}
          <dd className="num">{`${formatToken(fee)} ANT`}</dd>
        </div>

        {/* PENDING 은 아직 개시 전이라 만료일이 없다. 빈 칸을 만들지 않고 자리째 뺀다 */}
        {expiresAt && (
          <div>
            <dt>{status === 'EXPIRED' ? '만료일' : '이용 기간'}</dt>
            <dd className="num">
              {status === 'EXPIRED'
                ? formatExpiry(expiresAt)
                : `${formatExpiry(expiresAt)}까지 · ${daysLeft(expiresAt)}일 남음`}
            </dd>
          </div>
        )}

        <div>
          <dt>자동 갱신</dt>
          <dd>{renewEnabled ? '켜짐' : '꺼짐'}</dd>
        </div>
      </dl>

      {/* ── 갱신 상태와 조작 ─────────────────────────── */}
      {status === 'PENDING' && (
        <p className="sb-note">
          결제가 체인에서 확정되면 구독이 시작됩니다. 완료되면 알림으로 알려 드립니다.
        </p>
      )}

      {status === 'ACTIVE' && !renewEnabled && expiresAt && (
        <p className="sb-note">
          {`자동 갱신을 해지했습니다. ${formatExpiry(expiresAt)}까지 이용하고 종료됩니다.`}
        </p>
      )}

      {status === 'EXPIRED' && (
        <p className="sb-note">
          이용 기간이 끝났습니다. 다시 보시려면 채널에서 구독해 주세요.
        </p>
      )}

      {status === 'ACTIVE' && renewEnabled && !confirming && (
        <div className="sb-renew">
          <p className="sb-note">
            {expiresAt
              ? `${formatExpiry(expiresAt)}에 같은 금액으로 자동 결제됩니다.`
              : '만료일에 같은 금액으로 자동 결제됩니다.'}
          </p>
          {/* "구독 취소" 가 아니다. 지금 끊는 것이 아니라 다음 결제를 막는 것이다 */}
          <button type="button" className="sb-btn" onClick={onAsk}>자동 갱신 해지</button>
        </div>
      )}

      {confirming && (
        <div className="sb-confirm" role="group" aria-label="자동 갱신 해지 확인">
          <p className="sb-confirm-title">자동 갱신을 해지할까요?</p>
          <p className="sb-confirm-body">
            {expiresAt
              ? `구독은 지금 끊기지 않습니다. ${formatExpiry(expiresAt)}까지 그대로 이용하고, 그 뒤로 결제되지 않습니다.`
              : '구독은 지금 끊기지 않습니다. 만료일까지 그대로 이용하고, 그 뒤로 결제되지 않습니다.'}
          </p>
          {/* 되돌리는 API 가 없다. 토글처럼 보이지 않게 미리 알린다 */}
          <p className="sb-confirm-warn">
            해지하면 이 화면에서 다시 켤 수 없습니다. 계속 이용하시려면 만료 후 채널에서
            새로 구독해야 합니다.
          </p>

          {error && (
            <p className="sb-error" role="alert">해지하지 못했습니다. 잠시 후 다시 시도해 주세요.</p>
          )}

          <div className="sb-confirm-actions">
            <button type="button" className="sb-btn" onClick={onCancelAsk} disabled={busy}>
              그대로 두기
            </button>
            <button type="button" className="sb-btn danger" onClick={onConfirm} disabled={busy}>
              {busy ? '해지하는 중…' : '자동 갱신 해지'}
            </button>
          </div>
        </div>
      )}
    </article>
  )
}
