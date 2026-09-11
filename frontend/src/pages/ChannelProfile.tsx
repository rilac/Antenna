/* E-02 채널 프로필 · /channels/:userId
   담당 스토리 [ANT-FE-CHANNEL-PROFILE]
   설계서 docs/화면설계서.md §3 E · §4 E-02 · §5 · §9.1 · §9.2

   설계 제약
   - 구독은 즉시 토글이 아니다. M-03 확인 → 서명 → 202 → M-02 → ACTIVE.
     그래서 구독 버튼이 상태를 바꾸지 않고 모달을 연다.
   - 결제 시점 fee 가 박제된다. 내가 낸 값과 지금 값이 다를 수 있어 둘 다 보여준다.
   - 백테스트는 무료이고 파라미터가 고정이다(3개월 · 상수 원금 · 수수료 0).
     사용자 입력 칸을 두지 않는다.
   - 미판정 예측은 구독자만 본다. 비구독자에게는 잠금 카드로 존재를 알린다 —
     없는 것처럼 감추면 구독 유인이 사라진다(§5).
   - 실적 통계는 hitRate · doneCount · avgError 세 개다.

   두지 않는 것
   핸들(@) · 구독자 수 · 작성 카운트 · 무료 팔로우 · 신뢰도 점수.
   /channels/{userId} 응답에 없고, 채널 관계는 유료 구독뿐이라 팔로우 개념이 없다.

   PENDING 을 ACTIVE 와 같게 그리지 않는다
   결제가 체인에서 확정되기를 기다리는 중이라 아직 열람 권한이 없다. 여기서
   "구독 중" 이라고 쓰면 잠긴 예측을 보고 고장으로 읽는다. */
import { useCallback, useState } from 'react'
import { Link, useParams } from 'react-router-dom'
import {
  daysLeft, fetchBacktest, fetchChannel, fetchChannelPredictions,
  formatExpiry, formatFee,
  type Backtest, type Channel, type ChannelPrediction,
} from '../api/channels'
import { fetchChannelReports, formatDate, type ChannelReportItem } from '../api/reports'
import { useAsync } from '../api/useAsync'
import PredictionCard from '../components/prediction/PredictionCard'
import SubscribeModal from '../components/channel/SubscribeModal'
import EmptyState from '../components/state/EmptyState'
import ErrorState from '../components/state/ErrorState'
import WalletLinkModal from '../components/wallet/WalletLinkModal'
import '../styles/screens/channel-profile.css'

const SUB_LABEL: Record<'PENDING' | 'ACTIVE' | 'EXPIRED', string> = {
  PENDING: '결제 확인 중',
  ACTIVE: '구독 중',
  EXPIRED: '만료됨',
}

/** 지표가 null 이면 "—" 다. 0 으로 그리면 "한 번도 못 맞혔다" 로 읽힌다 */
const pct = (v: number | null) => (v === null ? '—' : `${v}%`)

/** 채널 예측을 카드 props 로. 채널 화면이라 작성자 줄은 넣지 않는다 — 전부 같은 사람이다 */
function toCard(p: ChannelPrediction) {
  return {
    id: p.id,
    stockCode: p.stockCode,
    stockName: p.stockName,
    direction: p.direction,
    status: p.status,
    locked: p.locked,
    targetPrice: p.targetPrice,
    errorRate: p.errorRate,
    horizon: p.horizon,
    dueDate: p.dueDate,
    to: `/predictions/${p.id}`,
  }
}

export default function ChannelProfile() {
  const { userId = '' } = useParams<{ userId: string }>()

  const loadChannel = useCallback(() => fetchChannel(userId), [userId])
  const loadPredictions = useCallback(() => fetchChannelPredictions(userId), [userId])
  const loadReports = useCallback(() => fetchChannelReports(userId), [userId])
  const loadBacktest = useCallback(() => fetchBacktest(userId), [userId])

  const ch = useAsync<Channel>(loadChannel)
  const preds = useAsync(loadPredictions)
  const reports = useAsync(loadReports)
  const back = useAsync<Backtest>(loadBacktest)

  /* M-03 은 라우트가 없는 모달이라 이 화면이 열고 닫는다.
     지갑이 없으면 M-03 을 닫고 M-01 을 띄운다 — 모달 2겹을 만들지 않는다. */
  const [subscribing, setSubscribing] = useState(false)
  const [linking, setLinking] = useState(false)

  const channel = ch.data
  const sub = channel?.mySubscription

  /* 구독이 ACTIVE 로 바뀌면 예측 목록의 잠금이 풀린다. 그런데 **바로 다시 읽으면 안 된다** —
     useAsync.reload 가 data 를 null 로 되돌리는 순간 아래 {channel && …} 가 거짓이 되어
     모달까지 언마운트된다. 그러면 성공 화면을 못 보고, 다시 마운트된 모달이 confirm 부터
     시작해 결제가 끝난 사람에게 결제 버튼을 다시 보여준다.
     그래서 표시만 해 두고 모달이 닫힐 때 읽는다. 뒤 화면은 모달에 가려 어차피 안 보인다.
     리포트·백테스트는 목록 단계에 게이팅이 없어 그대로 둔다. */
  const [staleAfterSubscribe, setStaleAfterSubscribe] = useState(false)

  /* 모달의 effect 의존성에 들어가므로 매 렌더 새로 만들지 않는다 */
  const markStale = useCallback(() => setStaleAfterSubscribe(true), [])

  const closeSubscribe = useCallback(() => {
    setSubscribing(false)
    if (!staleAfterSubscribe) return
    setStaleAfterSubscribe(false)
    ch.reload()
    preds.reload()
  }, [staleAfterSubscribe, ch, preds])

  return (
    <main className="main">
      <div className="main-inner ch">
        {ch.error && <ErrorState error={ch.error} onRetry={ch.reload} />}
        {ch.loading && !channel && <p className="ch-loading">채널을 불러오는 중…</p>}

        {channel && (
          <>
            {/* ── 채널 헤더 ─────────────────────────────── */}
            <header className="ch-head">
              {channel.avatarUrl && <img className="ch-avatar" src={channel.avatarUrl} alt="" />}
              <div className="ch-id">
                <h1>{channel.nickname}</h1>
                {channel.bio && <p className="ch-bio">{channel.bio}</p>}

                {channel.interests.length > 0 && (
                  <ul className="ch-interests">
                    {channel.interests.map((tag) => <li key={tag}>{tag}</li>)}
                  </ul>
                )}

                {channel.externalLinks.length > 0 && (
                  <ul className="ch-links">
                    {channel.externalLinks.map((l) => (
                      <li key={l.url}>
                        {/* 외부로 나가는 링크다. 참조자 정보를 넘기지 않는다 */}
                        <a href={l.url} target="_blank" rel="noreferrer noopener">
                          {l.label}
                          <svg width="11" height="11" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2.2" strokeLinecap="round" strokeLinejoin="round" aria-hidden="true">
                            <path d="M14 5h5v5" /><path d="M19 5l-8 8" />
                            <path d="M18 14v4a2 2 0 0 1-2 2H6a2 2 0 0 1-2-2V8a2 2 0 0 1 2-2h4" />
                          </svg>
                        </a>
                      </li>
                    ))}
                  </ul>
                )}
              </div>
            </header>

            {/* ── 실적 통계 — 세 개 고정 ────────────────── */}
            <section className="ch-stats">
              <div>
                <dt>적중률</dt>
                <dd className="num">{pct(channel.stats.hitRate)}</dd>
              </div>
              <div>
                <dt>판정 완료</dt>
                <dd className="num">{`${channel.stats.doneCount.toLocaleString('ko-KR')}건`}</dd>
              </div>
              <div>
                <dt>평균 오차</dt>
                <dd className="num">{pct(channel.stats.avgError)}</dd>
              </div>
            </section>

            {/* ── 구독 카드 ─────────────────────────────── */}
            {channel.isMe ? (
              <section className="ch-sub is-me">
                {/* 채널은 가입과 함께 있지만 구독료는 정해야 생긴다. 아직이면 그 사실부터
                    말한다 — "바꿉니다" 라고만 하면 이미 값이 있는 줄 안다(S15P21A507-230) */}
                <p>
                  {channel.fee === null
                    ? '내 채널입니다. 구독료를 아직 정하지 않아 아무도 구독할 수 없습니다.'
                    : '내 채널입니다. 구독료는 설정에서 바꿉니다.'}
                </p>
                <Link className="ch-sub-link" to="/settings">
                  {channel.fee === null ? '구독료 정하기' : '설정으로'}
                </Link>
              </section>
            ) : (
              <section className="ch-sub">
                <div className="ch-sub-price">
                  <span className="ch-sub-label">구독료</span>
                  {/* 채널은 가입과 함께 존재하지만 구독료는 정해야 생긴다. 그 사이 상태가
                      fee === null 이다. 0 으로 그리면 무료 채널로 읽힌다(S15P21A507-230). */}
                  {channel.fee === null ? (
                    <p className="ch-sub-unset">아직 정해지지 않았습니다</p>
                  ) : (
                    <p className="num">
                      <b>{formatFee(channel.fee)}</b><small>ANT</small>
                      <em>/ 30일</em>
                    </p>
                  )}
                </div>

                {sub?.status === 'ACTIVE' && sub.expiresAt ? (
                  <div className="ch-sub-state">
                    <span className="ch-sub-badge is-active">{SUB_LABEL.ACTIVE}</span>
                    <p className="ch-sub-until">
                      {`${formatExpiry(sub.expiresAt)}까지 · ${daysLeft(sub.expiresAt)}일 남음`}
                    </p>
                    {/* 박제된 가격이 지금 가격과 다르면 알려 준다. 안 알리면 나중에
                        결제 금액이 달라 보이는 이유를 알 수 없다 */}
                    {sub.paidFee && sub.paidFee !== channel.fee && (
                      <p className="ch-sub-locked-fee">
                        {`${formatFee(sub.paidFee)} ANT 로 결제한 구독입니다. 만료까지 이 가격입니다.`}
                      </p>
                    )}
                    <p className="ch-sub-renew">
                      {sub.autoRenew
                        ? '만료일에 자동으로 갱신됩니다'
                        : '자동 갱신을 해지했습니다. 만료일에 종료됩니다'}
                    </p>
                  </div>
                ) : sub?.status === 'PENDING' ? (
                  /* 아직 열람 권한이 없다. ACTIVE 와 같게 그리면 잠긴 예측이 고장으로 읽힌다 */
                  <div className="ch-sub-state">
                    <span className="ch-sub-badge is-pending">{SUB_LABEL.PENDING}</span>
                    <p className="ch-sub-until">
                      결제가 체인에서 확정되면 구독이 시작됩니다. 완료되면 알림으로 알려 드립니다.
                    </p>
                  </div>
                ) : channel.fee === null ? (
                  /* 값이 없는 결제를 시작시키지 않는다. 버튼을 그려 두고 누르면 실패하게
                     하는 것보다, 왜 지금 구독할 수 없는지 말하는 편이 낫다 */
                  <p className="ch-sub-unset-note">
                    이 채널은 아직 구독료를 정하지 않아 구독할 수 없습니다.
                  </p>
                ) : (
                  <button type="button" className="ch-sub-cta" onClick={() => setSubscribing(true)}>
                    {sub?.status === 'EXPIRED' ? '다시 구독하기' : '구독하기'}
                  </button>
                )}
              </section>
            )}

            {subscribing && (
              <SubscribeModal
                channel={channel}
                onClose={closeSubscribe}
                onSubscribed={markStale}
                onNeedWallet={() => { setSubscribing(false); setLinking(true) }}
              />
            )}

            {linking && (
              <WalletLinkModal
                onClose={() => setLinking(false)}
                // 연동이 끝나면 하려던 일로 되돌린다 — 다시 찾아 누르게 하지 않는다
                onLinked={() => { setLinking(false); setSubscribing(true) }}
              />
            )}

            {/* ── 예측 목록 ─────────────────────────────── */}
            <section className="ch-section">
              <h2>예측</h2>
              {preds.error && <ErrorState error={preds.error} onRetry={preds.reload} inline />}
              {!preds.error && preds.data?.items.length === 0 && (
                <EmptyState title="아직 등록한 예측이 없습니다" />
              )}
              {preds.data && preds.data.items.length > 0 && (
                <ul className="ch-cards">
                  {preds.data.items.map((p) => (
                    <li key={p.id}>
                      {/* channelId 를 넘기지 않는다 — LockedCard 의 "채널 구독하고 보기" 가
                          /channels/{id} 로 가는데 그게 바로 이 화면이라 눌러도 제자리다.
                          구독 CTA 는 위 구독 카드 하나로 충분하다. 다른 화면(B-03 등)에서는
                          여기로 보내야 하므로 카드가 channelId 를 받는 것 자체는 남긴다. */}
                      <PredictionCard data={toCard(p)} />
                    </li>
                  ))}
                </ul>
              )}
            </section>

            {/* ── 리포트 목록 ───────────────────────────── */}
            <section className="ch-section">
              <h2>리포트</h2>
              {reports.error && <ErrorState error={reports.error} onRetry={reports.reload} inline />}
              {!reports.error && reports.data?.items.length === 0 && (
                <EmptyState title="아직 발행한 리포트가 없습니다" />
              )}
              {reports.data && reports.data.items.length > 0 && (
                <ul className="ch-reports">
                  {reports.data.items.map((r: ChannelReportItem) => (
                    <li key={r.id}>
                      <Link to={`/reports/${r.id}`}>
                        <span className="ch-report-title">{r.title}</span>
                        <span className="ch-report-meta">
                          {/* 두 배지는 뜻이 다르다 — visibility 는 글의 속성,
                              locked 는 내 권한이다(서버 주석). 합치지 않는다 */}
                          {!r.visibility && <em className="ch-badge">구독자 전용</em>}
                          {r.locked && (
                            <em className="ch-badge is-locked">
                              <svg width="10" height="10" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2.2" strokeLinecap="round" strokeLinejoin="round" aria-hidden="true">
                                <rect x="4" y="10" width="16" height="10" rx="2" />
                                <path d="M8 10V7a4 4 0 0 1 8 0v3" />
                              </svg>
                              잠김
                            </em>
                          )}
                          <span className="num">{formatDate(r.publishedAt)}</span>
                        </span>
                      </Link>
                    </li>
                  ))}
                </ul>
              )}
            </section>

            {/* ── 백테스트 — 무료 · 파라미터 고정 ───────── */}
            <section className="ch-section">
              <h2>백테스트</h2>
              {back.error && <ErrorState error={back.error} onRetry={back.reload} inline />}

              {back.data && back.data.sampleCount === 0 ? (
                /* 표본이 없으면 0% 를 그리지 않는다 — "손실 없음" 으로 읽힌다 */
                <EmptyState
                  title="아직 검증할 판정 결과가 없습니다"
                  hint="예측이 판정되면 이 채널을 따라갔을 때의 수익률을 계산합니다"
                />
              ) : back.data && (
                <div className="ch-backtest">
                  <div className="ch-bt-figures">
                    <div>
                      <dt>수익률</dt>
                      <dd className={`num ${back.data.returnRate >= 0 ? 'up' : 'down'}`}>
                        {`${back.data.returnRate > 0 ? '+' : ''}${back.data.returnRate}%`}
                      </dd>
                    </div>
                    <div>
                      <dt>KOSPI</dt>
                      <dd className="num">
                        {back.data.benchmarkRate === null
                          ? '—'
                          : `${back.data.benchmarkRate > 0 ? '+' : ''}${back.data.benchmarkRate}%`}
                      </dd>
                    </div>
                    <div>
                      <dt>표본</dt>
                      <dd className="num">{`${back.data.sampleCount}건`}</dd>
                    </div>
                  </div>
                  {/* 조건을 적어 둔다. 안 적으면 어떤 가정의 숫자인지 알 수 없다.
                      값을 화면에 박지 않고 응답에서 받는 이유 — 기간이 바뀌면
                      문구와 계산 근거가 어긋난다 */}
                  <p className="ch-bt-terms">
                    {`최근 ${back.data.months}개월 · 원금 ${back.data.principal.toLocaleString('ko-KR')}원 · 수수료 0 기준입니다. 무료로 제공합니다.`}
                  </p>
                </div>
              )}
            </section>
          </>
        )}
      </div>
    </main>
  )
}
