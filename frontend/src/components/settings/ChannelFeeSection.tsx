/* E-04 내 채널 구독료 — H-03 환경 설정 안의 한 섹션. [ANT-FE-CHANNEL-FEE]
   설계서 §3 E · §4 E-04

   설계 제약 (티켓)
   - 가격 변경은 차기 주기부터 적용된다. 기존 구독자는 만료까지 옛 가격이다 —
     이 점을 변경 화면에 명시한다.
   - 변경 시 구독자 전원에게 FEE_CHANGED 알림이 나간다. 되돌리기 어려운 행동이므로
     확인 단계를 둔다.
   - 변경 이력을 보여준다. 가격이 자주 바뀌면 구독자 신뢰가 흔들리므로 이력 자체가
     억제 장치다.

   ── 아직 목업이다 ────────────────────────────────────────
   GET·PUT /me/channel/fee 가 없다(api/channelFee.ts 머리말). 응답 형태도 명세에
   없어 ERD 의 publisher_fees 컬럼에서 따왔다. 서버가 열리면 api/channelFee.ts 의
   타입과 MOCK 한 줄만 바뀌고 이 파일은 손대지 않는다.

   ── 금액은 정수 ANT 다 ──────────────────────────────────
   formatFee(api/channels.ts) 를 쓰지 않는다 — 그 함수는 뒤 18자리를 소수부로 잘라
   12 ANT 를 "0.0000" 으로 만든다(S15P21A507-225). formatAnt 를 쓴다. */
import { useCallback, useState } from 'react'
import {
  FEE_MAX, fetchChannelFee, formatAnt, formatDay, isValidFee, updateChannelFee,
  type FeeApplied,
} from '../../api/channelFee'
import type { ApiError } from '../../api/errors'
import { useAsync } from '../../api/useAsync'
import ErrorState from '../state/ErrorState'
import { errorText } from '../state/errorText'
import '../../styles/screens/channel-fee.css'

/* idle     현재값과 이력을 보는 중
   confirm  새 값을 넣고 확인을 기다린다 — 알림이 나가는 것을 여기서 말한다
   saving   PUT 진행 중
   done     적용 예정을 알린다 */
type Step = 'idle' | 'confirm' | 'saving' | 'done'

export default function ChannelFeeSection() {
  const fee = useAsync(fetchChannelFee)

  const [step, setStep] = useState<Step>('idle')
  const [draft, setDraft] = useState('')
  const [error, setError] = useState<ApiError | null>(null)
  const [applied, setApplied] = useState<FeeApplied | null>(null)

  /* ── "현재" 와 "예정" 을 가른다 ─────────────────────────
     publisher_fees.effective_from 은 미래일 수 있다(차기 주기 적용). 명세는 현재값을
     "최신 행" 이라고 적었지만, 최신 행이 아직 시작하지 않았는데 그 값을 "현재 구독료" 로
     쓰면 **화면이 거짓을 말한다** — 방금 20 으로 바꾸고 10월 1일부터라고 적어 놓고
     동시에 지금 구독료가 20 이라고 하게 된다.
     그래서 지금 받는 값은 이미 시작한 행에서 찾고, 시작 전 행은 따로 알린다. */
  const history = fee.data?.history ?? []
  /* 기준 시각을 마운트 때 한 번만 잡는다. 렌더마다 Date.now() 를 부르면 같은 데이터로
     결과가 달라질 수 있고(oxlint react/purity), 날짜 단위 비교라 고정해도 틀리지 않는다. */
  const [now] = useState(() => Date.now())
  const isFuture = (iso: string) => new Date(iso).getTime() > now
  const pending = history.find((h) => isFuture(h.effectiveFrom)) ?? null
  const current = history.find((h) => !isFuture(h.effectiveFrom))?.fee
    ?? (history.length === 0 ? fee.data?.fee ?? null : null)
  /** 다음 변경이 덮어쓸 값. 예정이 있으면 그것과 비교해야 한다 */
  const latest = history[0]?.fee ?? fee.data?.fee ?? null

  const valid = isValidFee(draft)
  /* 같은 값으로 바꾸면 알림만 나가고 바뀌는 것이 없다. 서버가 막아 주지 않으므로
     여기서 막는다 — 구독자에게 의미 없는 알림을 보내는 것이 이 화면의 실수다. */
  const unchanged = latest !== null && draft !== '' && valid && BigInt(draft) === BigInt(latest)

  const reset = useCallback(() => {
    setStep('idle')
    setDraft('')
    setError(null)
    setApplied(null)
  }, [])

  async function save() {
    if (!valid || unchanged) return
    setError(null)
    setStep('saving')
    try {
      setApplied(await updateChannelFee(draft))
      setStep('done')
      // 이력에 행이 하나 늘었다. 목록을 다시 읽어 방금 넣은 값이 맨 위에 서게 한다
      fee.reload()
    } catch (e) {
      setError(e as ApiError)
      setStep('confirm')
    }
  }

  const text = error ? errorText(error) : null

  return (
    <section className="st-block">
      <h2>내 채널</h2>

      {fee.loading && <p className="cf-loading">구독료를 불러오는 중입니다…</p>}

      {fee.error && <ErrorState error={fee.error} onRetry={fee.reload} />}

      {fee.data && (
        <div className="cf">
          {/* ── 현재 구독료 ─────────────────────────── */}
          <div className="cf-now">
            <span className="cf-label">현재 구독료</span>
            {current === null ? (
              /* 한 번도 정한 적이 없거나, 정한 값이 아직 시작 전인 상태.
                 0 으로 보여주면 무료 채널로 읽힌다 */
              <b className="cf-none">아직 정하지 않았습니다</b>
            ) : (
              <b className="cf-amount num">{`${formatAnt(current)} ANT`}<span>/ 30일</span></b>
            )}
            {/* 예정된 변경이 있으면 여기서 말한다. 안 적으면 위 숫자가 최신인 줄 안다 */}
            {pending && (
              <span className="cf-pending">
                {`${formatDay(pending.effectiveFrom)}부터 ${formatAnt(pending.fee)} ANT 로 바뀝니다`}
              </span>
            )}
          </div>

          {/* ── 변경 ────────────────────────────────── */}
          {step === 'done' && applied ? (
            <div className="cf-done" role="status">
              <p className="cf-done-title">{`${formatAnt(applied.fee)} ANT 로 바뀝니다`}</p>
              {/* 언제부터인지 말하지 않으면 지금 바뀐 줄 안다 */}
              <p className="cf-sub">
                {`${formatDay(applied.effectiveFrom)}부터 적용됩니다. 지금 구독 중인 사람은 만료까지 옛 가격 그대로입니다.`}
              </p>
              <button type="button" className="st-btn" onClick={reset}>확인</button>
            </div>
          ) : (
            <div className="cf-edit">
              <label className="cf-field">
                <span className="cf-label">새 구독료</span>
                <span className="cf-input-wrap">
                  <input
                    type="number" className="cf-input num"
                    value={draft} min={0} max={FEE_MAX}
                    placeholder={latest ?? '0'}
                    disabled={step === 'saving'}
                    aria-invalid={draft !== '' && !valid}
                    onChange={(e) => { setDraft(e.target.value); setStep('idle'); setError(null) }}
                  />
                  <span className="cf-unit">ANT / 30일</span>
                </span>
              </label>

              {/* 입력 규칙은 누르기 전에 알린다 */}
              <p className={`cf-hint ${draft !== '' && !valid ? 'is-bad' : ''}`}>
                {draft !== '' && !valid
                  ? `0 이상 ${formatAnt(String(FEE_MAX))} 이하의 정수만 넣을 수 있습니다`
                  : 'ANT 는 소수점이 없어 정수로만 정할 수 있습니다'}
              </p>

              {step === 'confirm' && (
                /* 확인 단계 — 되돌리기 어려운 행동이라 무엇이 일어나는지 먼저 말한다 */
                <div className="cf-confirm">
                  <p className="cf-confirm-title">
                    {latest === null
                      ? `구독료를 ${formatAnt(draft)} ANT 로 정합니다`
                      : `${formatAnt(latest)} ANT → ${formatAnt(draft)} ANT 로 바꿉니다`}
                  </p>
                  <ul className="cf-confirm-list">
                    <li><b>구독자 전원에게 알림이 갑니다.</b> 되돌려도 알림은 취소되지 않습니다.</li>
                    <li>차기 결제 주기부터 적용됩니다. <b>지금 구독 중인 사람은 만료까지 옛 가격</b>입니다.</li>
                    <li>변경 이력에 남습니다. 구독자가 볼 수 있습니다.</li>
                  </ul>
                </div>
              )}

              {text && (
                <p className="cf-error" role="alert">
                  <b>{text.title}</b>
                  {text.hint && <span>{text.hint}</span>}
                </p>
              )}

              <div className="cf-actions">
                {step === 'confirm' && (
                  <button type="button" className="st-btn" onClick={() => setStep('idle')}>취소</button>
                )}
                <button
                  type="button" className="st-btn solid"
                  disabled={!valid || unchanged || step === 'saving'}
                  onClick={() => (step === 'confirm' ? void save() : setStep('confirm'))}
                >
                  {step === 'saving' ? '적용하는 중…' : step === 'confirm' ? '알림 보내고 변경' : '변경하기'}
                </button>
              </div>

              {/* 버튼이 왜 잠겼는지 말한다. 같은 값은 통과시켜도 얻는 것이 없다 */}
              {unchanged && <p className="cf-hint">지금 구독료와 같습니다.</p>}
            </div>
          )}

          {/* ── 변경 이력 ───────────────────────────── */}
          <div className="cf-history">
            <h3>변경 이력</h3>
            {history.length === 0 ? (
              <p className="cf-hint">아직 변경한 적이 없습니다.</p>
            ) : (
              <ol className="cf-list">
                {history.map((h, i) => {
                  const prev = history[i + 1]
                  const dir = prev ? compare(h.fee, prev.fee) : 'first'
                  return (
                    <li key={h.id}>
                      <span className="cf-list-day">
                        {formatDay(h.effectiveFrom)}
                        {/* 아직 시작 전인 행. 지난 변경과 같은 모양으로 두면 이미 적용된 줄 안다 */}
                        {isFuture(h.effectiveFrom) && <em className="cf-soon">예정</em>}
                      </span>
                      <b className="num">{`${formatAnt(h.fee)} ANT`}</b>
                      <span className={`cf-dir is-${dir}`}>
                        {dir === 'up' ? `↑ ${formatAnt(prev!.fee)} 에서 인상`
                          : dir === 'down' ? `↓ ${formatAnt(prev!.fee)} 에서 인하`
                            : '처음 정함'}
                      </span>
                    </li>
                  )
                })}
              </ol>
            )}
          </div>
        </div>
      )}
    </section>
  )
}

/** 이력 줄의 방향. 같은 값이 연달아 오는 경우는 서버가 막지만 화면도 대비한다 */
function compare(now: string, prev: string): 'up' | 'down' | 'first' {
  try {
    const a = BigInt(now)
    const b = BigInt(prev)
    if (a > b) return 'up'
    if (a < b) return 'down'
    return 'first'
  } catch {
    return 'first'
  }
}
