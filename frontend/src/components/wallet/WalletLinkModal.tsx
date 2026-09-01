/* M-01 지갑 연동 (모달 · 라우트 없음)
   담당 스토리 [ANT-FE-WALLET-LINK]
   설계서 docs/화면설계서.md §3 M · §4 · §6 · API 명세서 §1.2

   설계 제약
   - SignConfirm: 서명할 payload 를 접지 않고 펼쳐서 보여준 뒤 서명을 요청한다.
     무엇에 서명하는지 모른 채 지나가게 하지 않는다.
   - 미설치 · 서명 거부 · 주소 불일치 · 이미 연동됨은 각각 다른 안내다.
     한 문구로 합치지 않는다(errorText.ts 가 code 별로 나눠 들고 있다).
   - 409 WALLET_ALREADY_LINKED 는 재시도로 풀리지 않는다. 진행 버튼을 내린다.
   - 온보딩(M-09) 안에서 열지 않는다 — 모달 3겹이 된다. 호출부가 온보딩을 닫고 띄운다.
   - 연동 후 원래 하려던 동작으로 이어져야 한다. 그래서 성공을 onLinked 로 올려
     호출부(주로 C-01 예측 등록)가 하던 일을 마저 하게 한다.

   두지 않는 것: 연동 해제 · 다중 지갑 관리. 해당 API 가 없다. */
import { useCallback, useEffect, useRef, useState } from 'react'
import { ApiError, ERROR_CODE } from '../../api/errors'
import { linkWallet, requestNonce, shortAddress, signingPayload } from '../../api/wallet'
import { connectAddress, hasWallet, personalSign } from '../../wallet/provider'
import { errorText } from '../state/errorText'
import { useAuth } from '../../auth/context'
import '../../styles/screens/wallet-link.css'

/* connect  지갑 연결 전
   confirm  주소·payload 를 펼쳐 놓고 서명을 기다린다 (SignConfirm)
   working  지갑 창이 떴거나 서버 응답을 기다린다
   done     연동 완료 */
type Step = 'connect' | 'confirm' | 'working' | 'done'

type Draft = {
  address: string
  payload: string
}

/** 재시도해도 같은 결과가 나오는 오류. "다시 시도"를 띄우면 사용자를 헛돌린다. */
function isTerminal(error: ApiError) {
  return error.code === ERROR_CODE.WALLET_ALREADY_LINKED
}

export default function WalletLinkModal({
  onClose,
  onLinked,
}: {
  onClose: () => void
  /** 연동된 주소. 호출부는 이걸 받아 원래 하려던 동작을 이어 간다. */
  onLinked?: (walletAddress: string) => void
}) {
  const { setWalletLinked } = useAuth()
  const [step, setStep] = useState<Step>('connect')
  const [draft, setDraft] = useState<Draft | null>(null)
  const [linked, setLinked] = useState<string | null>(null)
  const [error, setError] = useState<ApiError | null>(null)

  const dialogRef = useRef<HTMLDivElement>(null)

  /* 지갑 창이 떠 있는 동안 닫으면 서명이 허공에 뜬다. working 에서는 잠근다. */
  const closable = step !== 'working'
  const close = useCallback(() => { if (closable) onClose() }, [closable, onClose])

  useEffect(() => {
    const onKey = (e: KeyboardEvent) => { if (e.key === 'Escape') close() }
    document.addEventListener('keydown', onKey)
    return () => document.removeEventListener('keydown', onKey)
  }, [close])

  // 모달이 열리면 초점을 안으로 들인다. 없으면 탭이 뒤 화면을 돌아다닌다.
  useEffect(() => { dialogRef.current?.focus() }, [])

  /* ① 지갑 연결 → ② nonce 발급 → ③ payload 조립.
     서명은 여기서 하지 않는다 — 사용자가 payload 를 본 뒤 confirm 에서 한다. */
  async function connect() {
    setError(null)
    setStep('working')
    try {
      const address = await connectAddress()
      const nonce = await requestNonce('WALLET_LINK')
      setDraft({ address, payload: signingPayload('WALLET_LINK', address, nonce) })
      setStep('confirm')
    } catch (e) {
      setError(e as ApiError)
      setStep('connect')
    }
  }

  /* ④ 서명 → ⑤ 연동 확정.
     nonce 는 본문에 넣지 않는다. 서버가 userId 로 꺼내 payload 를 재조립한다. */
  async function sign() {
    if (!draft) return
    setError(null)
    setStep('working')
    try {
      const signature = await personalSign(draft.payload, draft.address)
      const { walletAddress } = await linkWallet(draft.address, signature)
      setLinked(walletAddress)
      setWalletLinked(true)
      setStep('done')
    } catch (e) {
      setError(e as ApiError)
      /* nonce 가 만료(5분)됐으면 payload 가 죽은 것이라 그 문자열로는 다시 못 한다.
         연결 단계로 되돌려 nonce 를 새로 받게 한다. */
      if ((e as ApiError).code === ERROR_CODE.NONCE_NOT_FOUND) {
        setDraft(null)
        setStep('connect')
      } else {
        setStep('confirm')
      }
    }
  }

  const text = error ? errorText(error) : null
  const terminal = error !== null && isTerminal(error)
  const walletReady = hasWallet()

  return (
    <div className="wl-backdrop" onClick={close}>
      <div
        className="wl-modal" role="dialog" aria-modal="true" aria-labelledby="wl-title"
        tabIndex={-1} ref={dialogRef}
        // 배경 클릭으로 닫히므로 내부 클릭이 거기까지 올라가지 않게 막는다
        onClick={(e) => e.stopPropagation()}
      >
        <div className="wl-head">
          <h2 id="wl-title">지갑 연동</h2>
          <button
            type="button" className="wl-x" aria-label="닫기"
            onClick={close} disabled={!closable}
          >
            <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.8" strokeLinecap="round">
              <path d="M6 6l12 12M18 6L6 18" />
            </svg>
          </button>
        </div>

        {step === 'done' && linked ? (
          <div className="wl-body">
            <p className="wl-done-mark" aria-hidden="true">
              <svg width="40" height="40" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.7" strokeLinecap="round" strokeLinejoin="round">
                <circle cx="12" cy="12" r="9" /><path d="m8.5 12.2 2.4 2.4 4.6-4.9" />
              </svg>
            </p>
            <p className="wl-done-title">지갑을 연동했습니다</p>
            <code className="wl-address">{linked}</code>
            <p className="wl-note">이제 예측을 등록하고 토큰을 받을 수 있습니다.</p>
            <div className="wl-actions">
              <button
                type="button" className="wl-btn solid"
                onClick={() => { onLinked?.(linked); onClose() }}
              >
                확인
              </button>
            </div>
          </div>
        ) : (
          <div className="wl-body">
            {/* 지갑 확장이 아예 없으면 연결 버튼을 눌러도 할 수 있는 게 없다.
                누르게 한 뒤 실패시키지 않고 처음부터 안내로 바꾼다. */}
            {!walletReady ? (
              <>
                <p className="wl-lead">브라우저에서 지갑을 찾을 수 없습니다.</p>
                <p className="wl-note">
                  지갑 확장을 설치하고 잠금을 해제한 뒤 이 창을 다시 열어 주세요.
                </p>
              </>
            ) : (
              <>
                <p className="wl-lead">
                  {draft
                    ? '아래 내용에 서명하면 이 지갑이 계정에 연결됩니다.'
                    : '계정에 연결할 지갑을 선택해 주세요.'}
                </p>

                {draft && (
                  <>
                    <div className="wl-field">
                      <span className="wl-label">연결할 주소</span>
                      <code className="wl-address" title={draft.address}>
                        {shortAddress(draft.address)}
                      </code>
                    </div>

                    {/* SignConfirm — 접지 않는다. 서명할 원문을 그대로 보여준다 */}
                    <div className="wl-field">
                      <span className="wl-label">서명할 내용</span>
                      <pre className="wl-payload">{draft.payload}</pre>
                    </div>

                    <p className="wl-note">
                      이 서명은 소유 확인에만 쓰입니다. 자산이 이동하거나 수수료가 들지 않습니다.
                    </p>
                  </>
                )}
              </>
            )}

            {text && (
              <div className="wl-error" role="alert">
                <b>{text.title}</b>
                {text.hint && <span>{text.hint}</span>}
              </div>
            )}

            <div className="wl-actions">
              <button type="button" className="wl-btn ghost" onClick={close} disabled={!closable}>
                {terminal ? '닫기' : '취소'}
              </button>

              {/* 409 는 재시도로 풀리지 않는다 — 진행 버튼 자체를 내린다 */}
              {walletReady && !terminal && (
                <button
                  type="button" className="wl-btn solid"
                  onClick={draft ? sign : connect}
                  disabled={step === 'working'}
                >
                  {step === 'working' ? '지갑 확인 중…' : draft ? '서명하고 연동하기' : '지갑 연결하기'}
                </button>
              )}
            </div>
          </div>
        )}
      </div>
    </div>
  )
}
