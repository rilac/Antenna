/* F-03 리포트 작성 · /reports/new
   담당 스토리 [ANT-FE-REPORT-NEW]
   설계서 docs/화면설계서.md §3 F · §4 F-03 · §9.1 · §9.2

   설계 제약
   - 본문 필드는 title · body · visibility 셋이고 visibility 는 필수다.
     공개 / 구독자 전용 두 선택지를 반드시 노출한다 — 기본값으로 숨기지 않는다.
     그래서 처음에는 아무것도 고르지 않은 상태로 두고, 고르기 전에는 발행을 막는다.
   - 임시 저장(초안)을 두지 않는다. DRAFT 상태도 수정·삭제 API 도 없다.
     작성 중 이탈 시 경고만 띄운다.
   - 두지 않는 것: 분야 · 한 줄 요약 · 임시 저장.

   이미지 첨부를 넣지 않은 이유
   두 겹으로 막혀 있다. POST /uploads 가 아직 없고(M-07 [ANT-FE-UPLOAD]),
   그보다 먼저 서버 ReportCreateRequest 가 (title, body, visibility) 뿐이라
   fileId 를 받을 자리 자체가 없다. 업로드 모달만 만들어도 발행에 실을 곳이 없다.
   두 가지가 다 열린 뒤에 붙인다.

   이탈 경고의 범위
   beforeunload 로 새로고침 · 창 닫기 · 외부 이동을 막는다. 앱 안에서 사이드바를
   눌러 나가는 것은 막지 못한다 — react-router 의 useBlocker 는 데이터 라우터에서만
   동작하는데 이 앱은 BrowserRouter 다(main.tsx). 라우터 교체는 셸 전체에 영향이
   가는 [ANT-FE-LAYOUT] 몫이라 이 스토리에서 건드리지 않는다.
   대신 화면 안의 취소 버튼은 직접 확인을 받는다. */
import { useEffect, useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { ApiError } from '../api/client'
import { releaseIdempotencyKey } from '../api/idempotency'
import { BODY_MAX, REPORT_CREATE_SCOPE, TITLE_MAX, createReport } from '../api/reports'
import { ERROR_CODE } from '../api/errors'
import { errorText } from '../components/state/errorText'
import FieldError from '../components/state/FieldError'
import '../styles/screens/report-new.css'

/* 서버는 boolean 하나를 받지만 화면은 "고르지 않음"을 구분해야 한다.
   null 이면 아직 안 골랐다는 뜻이고, 그 상태로는 발행하지 않는다. */
type Visibility = boolean | null

const VISIBILITY_OPTIONS = [
  { value: true, label: '공개', desc: '누구나 전문을 읽을 수 있습니다' },
  { value: false, label: '구독자 전용', desc: '구독자만 전문을 읽고, 나머지는 앞 3줄만 봅니다' },
] as const

export default function ReportNew() {
  const navigate = useNavigate()

  const [title, setTitle] = useState('')
  const [body, setBody] = useState('')
  const [visibility, setVisibility] = useState<Visibility>(null)

  const [submitting, setSubmitting] = useState(false)
  const [error, setError] = useState<ApiError | null>(null)

  const dirty = title !== '' || body !== '' || visibility !== null
  const ready = title.trim() !== '' && body.trim() !== '' && visibility !== null

  /* 초안이 없으므로 나가면 글이 사라진다. 발행 중에는 경고하지 않는다
     — 이미 서버로 갔고, 여기서 막으면 사용자가 더 헷갈린다. */
  useEffect(() => {
    if (!dirty || submitting) return
    const warn = (e: BeforeUnloadEvent) => e.preventDefault()
    window.addEventListener('beforeunload', warn)
    return () => window.removeEventListener('beforeunload', warn)
  }, [dirty, submitting])

  /* 내용이 바뀌면 이전 시도의 멱등키를 버린다.
     같은 키로 다른 본문을 보내면 409 IDEMPOTENCY_KEY_REUSED 다.
     반대로 내용을 그대로 두고 다시 누르면 키가 남아 있어 중복 발행이 막힌다. */
  function edited() {
    releaseIdempotencyKey(REPORT_CREATE_SCOPE)
    setError(null)
  }

  async function submit(event: React.FormEvent) {
    event.preventDefault()
    if (!ready || submitting) return

    setSubmitting(true)
    setError(null)
    try {
      const { id } = await createReport({ title: title.trim(), body, visibility })
      // 발행이 확정됐으니 키를 놓는다. 다음 리포트는 새 키를 받는다.
      releaseIdempotencyKey(REPORT_CREATE_SCOPE)
      // 뒤로 가기로 작성 화면에 되돌아오지 않게 replace 로 바꿔치운다
      navigate(`/reports/${id}`, { replace: true })
    } catch (e) {
      setError(e as ApiError)
      setSubmitting(false)
    }
  }

  function cancel() {
    // 초안이 없으니 나가면 사라진다. 앱 안 이동은 여기서만 확인을 받을 수 있다.
    if (dirty && !window.confirm('작성 중인 내용이 사라집니다. 나가시겠습니까?')) return
    navigate('/reports')
  }

  /* 400 은 어느 필드가 틀렸는지 field 로 온다. 화면 전체 오류가 아니라
     그 입력란 옆에 붙인다(설계서 §6). */
  const fieldError = error?.code === ERROR_CODE.INVALID_REQUEST ? error.field : undefined
  const formError = error && !fieldError ? errorText(error) : null

  return (
    <main className="main">
      <div className="main-inner">
        <div className="page-head">
          <h1>리포트 작성</h1>
          <p>발행하면 수정하거나 지울 수 없습니다</p>
        </div>

        <form className="rn" onSubmit={submit}>
          <div className="rn-field">
            <label className="rn-label" htmlFor="rn-title">
              제목
              <span className="rn-count num">{`${title.length}/${TITLE_MAX}`}</span>
            </label>
            <input
              className="rn-input" id="rn-title" type="text"
              value={title}
              onChange={(e) => { setTitle(e.target.value); edited() }}
              maxLength={TITLE_MAX}
              disabled={submitting}
              aria-invalid={fieldError === 'title'}
              placeholder="무엇에 대한 리포트인가요?"
            />
            {fieldError === 'title' && <FieldError>제목을 다시 확인해 주세요.</FieldError>}
          </div>

          <div className="rn-field">
            <label className="rn-label" htmlFor="rn-body">
              본문
              <span className="rn-count num">{`${body.length.toLocaleString('ko-KR')}/${BODY_MAX.toLocaleString('ko-KR')}`}</span>
            </label>

            <textarea
              className="rn-textarea" id="rn-body"
              value={body}
              onChange={(e) => { setBody(e.target.value); edited() }}
              maxLength={BODY_MAX}
              disabled={submitting}
              rows={16}
              aria-invalid={fieldError === 'body'}
              placeholder={'근거와 판단을 적어 주세요.\n줄바꿈은 그대로 보입니다.'}
            />
            {fieldError === 'body' && <FieldError>본문을 다시 확인해 주세요.</FieldError>}
          </div>

          {/* 두 선택지를 반드시 노출한다. 기본값을 미리 골라 두지 않는다
              — 구독자 전용인 줄 모르고 공개로 발행하는 사고를 막는다 */}
          <fieldset className="rn-field rn-visibility" disabled={submitting}>
            <legend className="rn-label">공개 범위</legend>
            {VISIBILITY_OPTIONS.map((o) => (
              <label key={o.label} className={`rn-option ${visibility === o.value ? 'on' : ''}`}>
                <input
                  type="radio" name="visibility"
                  checked={visibility === o.value}
                  onChange={() => { setVisibility(o.value); edited() }}
                />
                <span className="rn-option-text">
                  <b>{o.label}</b>
                  <small>{o.desc}</small>
                </span>
              </label>
            ))}
            {fieldError === 'visibility' && <FieldError>공개 범위를 선택해 주세요.</FieldError>}
          </fieldset>

          {formError && (
            <div className="rn-error" role="alert">
              <b>{formError.title}</b>
              {formError.hint && <span>{formError.hint}</span>}
            </div>
          )}

          <div className="rn-actions">
            <button type="button" className="rn-btn ghost" onClick={cancel} disabled={submitting}>
              취소
            </button>
            <button type="submit" className="rn-btn solid" disabled={!ready || submitting}>
              {submitting ? '발행 중…' : '발행'}
            </button>
          </div>

          {!ready && !submitting && (
            <p className="rn-hint">제목 · 본문 · 공개 범위를 모두 채우면 발행할 수 있습니다.</p>
          )}
        </form>
      </div>
    </main>
  )
}
