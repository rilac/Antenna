/* H-04 광고 등록 · /ads/new
   담당 스토리 [ANT-FE-AD]
   설계서 docs/화면설계서.md §3 H · §4 H-04 · §4 M-02 · §4 M-07

   설계 제약
   - imageFileId 는 업로드 응답에서 온 값만 받는다. 외부 URL 입력란을 두지 않는다.
   - linkUrl 은 https 만 허용한다.
   - 등록은 202 + operationId 다. M-02 처리 대기로 이어진다.
   - 서명 전 비용을 명확히 보여준다(SignConfirm).
   - 관리자 사전 승인 화면은 2차 범위다. 승인 대기 상태 화면을 만들지 않는다.
   - 등록된 배너는 B-01 홈의 /ads/active 로 노출된다.

   시작일을 고르는 칸이 없는 이유
   요청 본문에 days 만 있고 시작일이 없다. 서버가 startsAt = now 로 잡는다
   (AdService: "게재 시작을 고를 수 없다"). 그래서 화면도 날짜를 묻지 않고,
   대신 **확정되는 즉시 시작한다**고 알린다 — 안 적으면 언제 걸리는지 모른다.

   비용은 서버가 준 단가로만 계산한다
   GET /ads/pricing 의 pricePerDay · minDays · maxDays · slotCount 를 그대로 쓴다. 가격이
   서명 문자열에 없어(days 만 들어간다) 서버가 단가를 바꿔도 서명으로는 막히지 않는다 —
   화면이 상수를 들고 있으면 "1 ANT × 7일" 이라 말하고 7,000 ANT 를 태울 수 있다.
   그래서 단가를 못 받았으면 금액을 짐작해 그리지 않고 등록 버튼을 잠근다. 소각은 되돌릴 수 없다.

   지갑 게이트는 ANT-FE-WALLET-GATE 판단을 따른다
   미설치와 미연동을 여기서 가르지 않는다 — M-01 이 네 갈래를 각각 다르게 안내한다. */
import { useCallback, useState } from 'react'
import { Link } from 'react-router-dom'
import {
  LINK_MAX,
  createAd, adSigningPayload, fetchAdPricing, isValidLink, priceOf, type AdDraft,
} from '../api/ads'
import type { ApiError } from '../api/errors'
import { useOperation } from '../api/operations'
import { useAsync } from '../api/useAsync'
import { formatToken, requestNonce } from '../api/wallet'
import { useAuth } from '../auth/context'
import { connectAddress, hasWallet, personalSign } from '../wallet/provider'
import ImageUploadModal from '../components/upload/ImageUploadModal'
import WalletLinkModal from '../components/wallet/WalletLinkModal'
import ErrorState from '../components/state/ErrorState'
import OperationProgress from '../components/operation/OperationProgress'
import type { UploadedImage } from '../api/uploads'
import { errorText } from '../components/state/errorText'
import '../styles/screens/ad-new.css'

/* form     내용을 채우는 중
   signing  nonce 발급 → 지갑 서명 → 202 수신
   waiting  M-02 — 체인 확정 대기
   done     SUCCEEDED */
type Step = 'form' | 'signing' | 'waiting' | 'done'

export default function Ad() {
  const { user } = useAuth()

  const [banner, setBanner] = useState<UploadedImage | null>(null)
  const [linkUrl, setLinkUrl] = useState('')
  const [days, setDays] = useState(7)

  const [step, setStep] = useState<Step>('form')
  const [error, setError] = useState<ApiError | null>(null)
  const [operationId, setOperationId] = useState<string | null>(null)

  const [uploading, setUploading] = useState(false)
  const [linking, setLinking] = useState(false)

  const op = useOperation(operationId)
  const status = op.operation?.status

  /* 성공은 폴링 결과에서 바로 읽는다 — setStep 으로 옮겨 적으면 같은 사실이 두 곳에 남고
     effect 안 setState 가 된다(M-03 과 같은 판단). */
  const view: Step = status === 'SUCCEEDED' ? 'done' : step

  /* 단가 · 일수 범위 · 자리 수는 서버에서만 온다. 못 받은 동안 terms 가 null 이라 등록이 잠긴다 */
  const pricing = useAsync(fetchAdPricing)
  const terms = pricing.data

  const linkOk = isValidLink(linkUrl)
  const daysOk = terms !== null && days >= terms.minDays && days <= terms.maxDays
  /* 계산할 수 없으면(소수 일수 등) null 이다. 금액을 모르는 채로 서명시키지 않는다 */
  const price = terms ? priceOf(terms, days) : null
  const ready = banner !== null && linkOk && daysOk && price !== null

  const onUploaded = useCallback((image: UploadedImage) => {
    setBanner(image)
    setError(null)
  }, [])

  async function submit() {
    if (!ready || !banner) return

    /* 미설치와 미연동을 가르지 않는다 — M-01 이 네 갈래를 각각 다르게 안내하고,
       늦게 주입되는 확장까지 기다려 준다(ANT-FE-WALLET-GATE). */
    if (!hasWallet() || !user?.walletLinked) {
      setLinking(true)
      return
    }

    setError(null)
    setStep('signing')
    try {
      const draft: AdDraft = { imageFileId: banner.fileId, linkUrl, days }
      const address = await connectAddress()
      const nonce = await requestNonce('AD')
      /* 지갑 연동용 signingPayload 가 아니다. 신청 내용이 통째로 들어간다 —
         days 가 빠지면 3일치 서명으로 30일치를 등록할 수 있다(서버 주석). */
      const signature = await personalSign(adSigningPayload(draft, nonce), address)

      const accepted = await createAd(draft, signature)
      setOperationId(accepted.operationId)
      setStep('waiting')
    } catch (e) {
      setError(e as ApiError)
      /* 폼으로 돌린다. 멱등 키는 신청 내용에서 나오므로 같은 내용으로 재시도하면 같은 키가
         나가 게재료가 두 번 나가지 않는다(api/ads.ts adScope). */
      setStep('form')
    }
  }

  const text = error ? errorText(error) : null

  return (
    <main className="main">
      <div className="main-inner ad">
        <div className="page-head">
          <h1>광고 등록</h1>
          <p>홈 상단 배너 자리에 노출됩니다</p>
        </div>

        {view === 'form' && (
          <>
            {/* ── 배너 ─────────────────────────────────── */}
            <section className="ad-card">
              <h2>배너 이미지</h2>
              {/* 비율이 어긋나면 모달이 가운데를 잘라 맞춘다(M-07). 미리 알려 둔다 */}
              <p className="ad-hint">
                {`5MB 이하의 PNG · JPG · WebP. 가로세로 4:1 이 아니면 가운데를 기준으로 잘라 넣습니다.`}
              </p>

              {banner ? (
                <div className="ad-banner">
                  <img src={banner.url} alt="" />
                  <div className="ad-banner-foot">
                    <span className="num">{`${banner.width} × ${banner.height}`}</span>
                    <button type="button" className="ad-btn" onClick={() => setUploading(true)}>
                      바꾸기
                    </button>
                  </div>
                </div>
              ) : (
                <button type="button" className="ad-pick" onClick={() => setUploading(true)}>
                  <svg width="22" height="22" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.7" strokeLinecap="round" strokeLinejoin="round" aria-hidden="true">
                    <path d="M12 16V4" /><path d="m7 9 5-5 5 5" />
                    <path d="M4 16v2a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2v-2" />
                  </svg>
                  배너 올리기
                </button>
              )}
            </section>

            {/* ── 링크 ─────────────────────────────────── */}
            <section className="ad-card">
              <h2>이동할 주소</h2>
              <input
                type="url" className="ad-input"
                value={linkUrl} maxLength={LINK_MAX}
                placeholder="https://"
                onChange={(e) => setLinkUrl(e.target.value)}
                aria-invalid={linkUrl !== '' && !linkOk}
              />
              {/* https 제약은 입력 전에 알린다 — 다 쓰고 나서 거절하면 다시 써야 한다 */}
              <p className={`ad-hint ${linkUrl !== '' && !linkOk ? 'is-bad' : ''}`}>
                {linkUrl !== '' && !linkOk
                  ? 'https 로 시작하는 주소만 등록할 수 있습니다'
                  : 'https 주소만 등록할 수 있습니다'}
              </p>
            </section>

            {/* ── 기간 ─────────────────────────────────── */}
            <section className="ad-card">
              <h2>노출 기간</h2>
              <div className="ad-days">
                <input
                  type="number" className="ad-input is-num"
                  value={days} min={terms?.minDays} max={terms?.maxDays}
                  onChange={(e) => setDays(Number(e.target.value))}
                />
                <span>일</span>
              </div>
              {/* 시작일 칸이 없는 이유를 적는다. 안 적으면 언제 걸리는지 모른다 */}
              <p className="ad-hint">
                {terms
                  ? `${terms.minDays}~${terms.maxDays}일. 시작일은 고를 수 없고, 결제가 확정되는 즉시 시작합니다.`
                  : '시작일은 고를 수 없고, 결제가 확정되는 즉시 시작합니다.'}
              </p>
            </section>

            {/* ── 비용 (SignConfirm) ───────────────────── */}
            <section className="ad-cost">
              {pricing.error ? (
                /* 단가를 못 받으면 금액을 짐작해 채우지 않는다. 이유를 말하고 등록을 잠근다 */
                <ErrorState error={pricing.error} onRetry={pricing.reload} inline />
              ) : !terms ? (
                <p className="ad-hint" aria-live="polite">게재 단가를 불러오는 중입니다…</p>
              ) : (
                <>
                  <div className="ad-cost-line">
                    <span>{`${formatToken(terms.pricePerDay)} ANT × ${days}일`}</span>
                    <b className="num">{price === null ? '—' : `${formatToken(price)} ANT`}</b>
                  </div>
                  {/* 소각이라 되돌릴 수 없다. 서명 전에 분명히 말한다 */}
                  <p className="ad-cost-note">
                    {price === null
                      ? '노출 기간을 정수 일수로 넣으면 금액이 계산됩니다.'
                      : <>등록하면 <b>{`${formatToken(price)} ANT 가 소각`}</b>됩니다. 되돌릴 수 없습니다.</>}
                    {' '}
                    {terms.slotCount === 1
                      ? '자리가 하나뿐이라 이미 게재 중인 광고가 있으면 등록되지 않습니다.'
                      : `자리가 ${terms.slotCount}개라 모두 차 있으면 등록되지 않습니다.`}
                  </p>
                </>
              )}
            </section>

            {text && (
              <p className="ad-error" role="alert">
                <b>{text.title}</b>
                {text.hint && <span>{text.hint}</span>}
              </p>
            )}

            <div className="ad-actions">
              <button
                type="button" className="ad-btn solid"
                disabled={!ready}
                onClick={() => void submit()}
              >
                서명하고 등록하기
              </button>
            </div>
          </>
        )}

        {/* ── 서명 · 대기 · 완료 ─────────────────────────── */}
        {view === 'signing' && (
          <section className="ad-progress" aria-live="polite">
            <span className="ad-spinner" aria-hidden="true" />
            <p className="ad-progress-title">지갑에서 서명을 기다리고 있습니다</p>
            <p className="ad-sub">지갑 창이 뜨지 않으면 확장 아이콘을 눌러 확인해 주세요.</p>
          </section>
        )}

        {/* 대기·실패 갈래는 M-02 공유 본문이 그린다([ANT-FE-OPERATION]).
            네 흐름이 같은 상황을 제각기 말하지 않게 하는 것이 그 티켓의 요지다 */}
        {view === 'waiting' && (
          <section className="ad-op">
            <OperationProgress op={op} kind="AD" />
          </section>
        )}

        {view === 'done' && (
          <section className="ad-progress" aria-live="polite">
            <span className="ad-check" aria-hidden="true">
              <svg width="24" height="24" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2.2" strokeLinecap="round" strokeLinejoin="round">
                <path d="m5 12.5 4.5 4.5L19 7.5" />
              </svg>
            </span>
            <p className="ad-progress-title">{`배너가 ${days}일간 노출됩니다`}</p>
            <p className="ad-sub">지금부터 홈 상단에서 볼 수 있습니다.</p>
            {/* 승인 대기 화면은 2차 범위라 만들지 않는다(설계 제약). 노출을 바로 확인하게 한다 */}
            <Link className="ad-btn solid" to="/">홈에서 보기</Link>
          </section>
        )}

        {/* M-07 · M-01 은 라우트가 없는 모달이라 이 화면이 열고 닫는다 */}
        {uploading && (
          <ImageUploadModal
            purpose="AD"
            onClose={() => setUploading(false)}
            onUploaded={onUploaded}
          />
        )}

        {linking && (
          <WalletLinkModal
            onClose={() => setLinking(false)}
            // 연동이 끝나면 하려던 일로 되돌린다 — 다시 찾아 누르게 하지 않는다
            onLinked={() => { setLinking(false); void submit() }}
          />
        )}

        {/* 폼 밖에서 난 오류(폴링 조회 실패 등)는 여기 */}
        {view !== 'form' && error && <ErrorState error={error} />}
      </div>
    </main>
  )
}
