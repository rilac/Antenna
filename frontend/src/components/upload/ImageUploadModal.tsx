/* M-07 이미지 업로드 (모달 · 라우트 없음)
   담당 스토리 [ANT-FE-UPLOAD]
   호출 위치: F-03 리포트 작성 · F-04 글 작성 · H-04 광고 등록
   설계서 docs/화면설계서.md §3 M · §4 F-03 · H-04 · §6

   설계 제약
   - 호출하는 화면은 fileId 만 받는다. 외부 URL 입력란을 두지 않는다.
   - 화면별 제약을 모달이 파라미터로 받는다 — H-04 배너는 고정 비율이 있다.
   - 업로드 규격 위반은 400 이다. 인라인 오류로 처리한다(화면 전체를 오류로 덮지 않는다).
   - 중간에 끊겼을 때 어느 단계에서 실패했는지 구분해 복구할 수 있어야 한다.

   ── 티켓의 3단계(presigned URL)는 쓰지 않는다 ────────────────
   구현된 백엔드는 서버로 직접 multipart 를 받는다(api/uploads.ts 머리말). 발급 단계가
   없으므로 "발급 실패 / 전송 실패" 구분은 그 형태로 성립하지 않는다. 대신 사용자가 할 일이
   서로 다른 셋으로 가른다.

     고르기 전 걸림 (로컬 검증)  → 다른 파일을 골라야 한다. 업로드를 시작하지도 않는다
     전송이 끊김                → 같은 파일로 다시 시도하면 된다
     서버가 규격 위반으로 거절   → 다른 파일을 골라야 한다

   ── 왜 로컬에서 미리 거르는가 ───────────────────────────────
   서버가 최종 판정자다. 그래도 5MB 를 다 올린 뒤 400 을 받으면 사용자는 기다린 만큼을
   잃는다. 같은 code 를 쓰므로 로컬에서 걸리든 서버에서 걸리든 문구가 같다. */
import { useCallback, useEffect, useRef, useState } from 'react'
import { ApiError, CLIENT_ERROR_CODE } from '../../api/errors'
import {
  ACCEPT_ATTR, ASPECT_BY_PURPOSE, MAX_BYTES,
  checkPixels, checkSize, cropToAspect, fitsAspect, formatAspect, formatBytes,
  measure, precheck, uploadImage,
  type AspectRule, type UploadPurpose, type UploadedImage,
} from '../../api/uploads'
import { errorText } from '../state/errorText'
import '../../styles/screens/image-upload.css'

/* pick     파일을 고르기 전
   ready    골랐고 로컬 검증을 통과했다. 미리보기가 보인다
   sending  전송 중. 진행률이 돈다
   done     서버가 fileId 를 줬다 */
type Step = 'pick' | 'ready' | 'sending' | 'done'

type Picked = {
  file: File
  width: number
  height: number
  previewUrl: string
  /** 비율을 맞추려고 잘랐다면 잘리기 전 크기. 화면이 그 사실을 반드시 알려야 한다 */
  croppedFrom?: { width: number; height: number }
}

type Props = {
  /** 서버가 무엇에 쓰는 그림인지 알아야 한다. AD 는 비율 제약이 함께 걸린다 */
  purpose: UploadPurpose
  onClose: () => void
  /** 호출부는 fileId 만 쓰면 된다. 미리보기가 필요할 때를 위해 나머지도 함께 준다 */
  onUploaded: (image: UploadedImage) => void
  /** 기본은 purpose 가 정한다. 화면이 다른 제약을 걸어야 하면 여기로 덮는다 */
  aspect?: AspectRule
}

export default function ImageUploadModal({ purpose, onClose, onUploaded, aspect }: Props) {
  const rule = aspect ?? ASPECT_BY_PURPOSE[purpose]

  const [step, setStep] = useState<Step>('pick')
  const [picked, setPicked] = useState<Picked | null>(null)
  const [error, setError] = useState<ApiError | null>(null)
  const [progress, setProgress] = useState(0)
  const [dragging, setDragging] = useState(false)

  const dialogRef = useRef<HTMLDivElement>(null)
  const inputRef = useRef<HTMLInputElement>(null)
  const abortRef = useRef<AbortController | null>(null)
  /* 미리보기 URL 은 브라우저 메모리를 잡는다. 새 파일로 바꾸거나 창을 닫을 때 놓아 준다 */
  const previewRef = useRef<string | null>(null)

  const releasePreview = useCallback(() => {
    if (previewRef.current) {
      URL.revokeObjectURL(previewRef.current)
      previewRef.current = null
    }
  }, [])

  /* 전송 중에는 닫지 못하게 한다 — 창을 닫아도 요청은 살아 있어 사용자가
     "취소한 줄 알았는데 올라갔다" 를 겪는다. 취소 버튼으로만 멈춘다. */
  const closable = step !== 'sending'
  const close = useCallback(() => {
    if (!closable) return
    releasePreview()
    onClose()
  }, [closable, onClose, releasePreview])

  useEffect(() => {
    const onKey = (e: KeyboardEvent) => { if (e.key === 'Escape') close() }
    document.addEventListener('keydown', onKey)
    return () => document.removeEventListener('keydown', onKey)
  }, [close])

  // 모달이 열리면 초점을 안으로 들인다. 없으면 탭이 뒤 화면을 돌아다닌다.
  useEffect(() => { dialogRef.current?.focus() }, [])

  // 언마운트될 때도 미리보기를 놓는다 — 호출부가 조건부 렌더로 지울 수 있다
  useEffect(() => releasePreview, [releasePreview])

  async function choose(file: File) {
    setError(null)

    // ① 파일만 보고 알 수 있는 것 — 디코딩 전에 거른다
    const early = precheck(file, rule)
    if (early) { setError(early); return }

    // ② 크기와 비율은 디코딩해야 안다
    let measured
    try {
      measured = await measure(file)
    } catch (e) {
      setError(e as ApiError)
      return
    }

    // ③ 화소 수는 자르기 전에 본다 — 4천만 화소짜리는 캔버스에 올리는 것부터 실패한다
    const tooBig = checkPixels(measured.width, measured.height)
    if (tooBig) {
      URL.revokeObjectURL(measured.previewUrl)
      setError(tooBig)
      return
    }

    /* ④ 비율이 어긋나면 거절하지 않고 가운데를 기준으로 잘라 맞춘다(api/uploads.ts cropToAspect).
       올리는 것은 잘린 파일이고, 미리보기도 잘린 그림이어야 한다 — 원본을 보여 주면
       사용자는 자기가 고른 그림이 그대로 올라가는 줄 안다. */
    let next: Picked
    if (rule && !fitsAspect(measured.width, measured.height, rule)) {
      try {
        const cut = await cropToAspect(file, measured, rule)
        // 자른 결과가 정말 규격 안인지 마지막으로 본다. 아주 작은 그림은 반올림이 오차를 넘는다
        const stillBad = checkSize(cut.width, cut.height, rule)
        if (stillBad) throw stillBad
        URL.revokeObjectURL(measured.previewUrl)
        next = {
          file: cut.file, width: cut.width, height: cut.height,
          previewUrl: URL.createObjectURL(cut.file), croppedFrom: cut.from,
        }
      } catch (e) {
        URL.revokeObjectURL(measured.previewUrl)
        setError(e as ApiError)
        return
      }
    } else {
      next = { file, width: measured.width, height: measured.height, previewUrl: measured.previewUrl }
    }

    releasePreview()
    previewRef.current = next.previewUrl
    setPicked(next)
    setStep('ready')
  }

  async function start() {
    if (!picked) return
    setError(null)
    setProgress(0)
    setStep('sending')

    const controller = new AbortController()
    abortRef.current = controller

    try {
      const image = await uploadImage(picked.file, purpose, {
        onProgress: setProgress,
        signal: controller.signal,
      })
      setStep('done')
      onUploaded(image)
    } catch (e) {
      const err = e as ApiError
      /* 취소는 실패가 아니다. 고른 파일을 그대로 두고 확인 단계로 돌린다 —
         다시 누르면 같은 파일로 바로 보낸다. */
      if (err.code === CLIENT_ERROR_CODE.CLIENT_UPLOAD_ABORTED) {
        setStep('ready')
        return
      }
      setError(err)
      setStep('ready')
    } finally {
      abortRef.current = null
    }
  }

  function onDrop(e: React.DragEvent) {
    e.preventDefault()
    setDragging(false)
    const file = e.dataTransfer.files?.[0]
    if (file) void choose(file)
  }

  const text = error ? errorText(error) : null

  return (
    <div className="iu-backdrop" onClick={close} role="presentation">
      <div
        className="iu-modal" role="dialog" aria-modal="true" aria-labelledby="iu-title"
        tabIndex={-1} ref={dialogRef} onClick={(e) => e.stopPropagation()}
      >
        <div className="iu-head">
          <h2 id="iu-title">{step === 'done' ? '이미지를 올렸습니다' : '이미지 올리기'}</h2>
          {closable && (
            <button type="button" className="iu-x" aria-label="닫기" onClick={close}>
              <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round">
                <path d="M6 6l12 12" /><path d="M18 6 6 18" />
              </svg>
            </button>
          )}
        </div>

        <div className="iu-body">
          {/* ── 고르기 ───────────────────────────────── */}
          {step === 'pick' && (
            <>
              {/* 클릭으로도, 끌어다 놓기로도 고를 수 있게 한다 */}
              <button
                type="button"
                className={`iu-drop ${dragging ? 'is-over' : ''}`}
                onClick={() => inputRef.current?.click()}
                onDragOver={(e) => { e.preventDefault(); setDragging(true) }}
                onDragLeave={() => setDragging(false)}
                onDrop={onDrop}
              >
                <svg width="26" height="26" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.7" strokeLinecap="round" strokeLinejoin="round" aria-hidden="true">
                  <path d="M12 16V4" /><path d="m7 9 5-5 5 5" />
                  <path d="M4 16v2a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2v-2" />
                </svg>
                <b>이미지를 끌어다 놓거나 눌러서 고르세요</b>
                <span>{`PNG · JPG · WebP · ${formatBytes(MAX_BYTES)} 이하`}</span>
                {/* 비율 제약은 있을 때만 알린다. 없는 화면에 규칙을 만들어 보이지 않는다.
                    거절이 아니라 자른다는 것을 고르기 전에 말해 둔다 — 결과가 놀랍지 않게 */}
                {rule && <span className="iu-rule">{`${formatAspect(rule)} 이 아니면 가운데를 기준으로 잘라 넣습니다`}</span>}
              </button>

              <input
                ref={inputRef} type="file" className="sr-only"
                accept={ACCEPT_ATTR}
                onChange={(e) => {
                  const file = e.target.files?.[0]
                  // 같은 파일을 다시 고를 수 있게 값을 비운다 — 안 비우면 change 가 안 뜬다
                  e.target.value = ''
                  if (file) void choose(file)
                }}
              />
            </>
          )}

          {/* ── 고른 뒤 · 전송 중 ─────────────────────── */}
          {picked && step !== 'pick' && (
            <div className="iu-preview">
              <img src={picked.previewUrl} alt="" />
              <dl className="iu-facts">
                <div>
                  <dt>파일</dt>
                  <dd className="iu-name">{picked.file.name}</dd>
                </div>
                <div>
                  <dt>크기</dt>
                  <dd className="num">{`${picked.width} × ${picked.height}`}</dd>
                </div>
                <div>
                  <dt>용량</dt>
                  <dd className="num">{formatBytes(picked.file.size)}</dd>
                </div>
              </dl>
              {/* 잘랐으면 반드시 말한다. 모르는 사이에 그림이 바뀌는 쪽이 거절보다 나쁘다 */}
              {picked.croppedFrom && rule && (
                <p className="iu-cropped">
                  {`${formatAspect(rule)} 에 맞춰 가운데를 잘랐습니다`}
                  <span className="num">
                    {`${picked.croppedFrom.width} × ${picked.croppedFrom.height} → ${picked.width} × ${picked.height}`}
                  </span>
                </p>
              )}
            </div>
          )}

          {step === 'sending' && (
            <div className="iu-progress" aria-live="polite">
              {/* 진행률은 XHR 업로드 이벤트에서 온다. fetch 로는 읽을 수 없다 */}
              <div
                className="iu-bar" role="progressbar"
                aria-valuemin={0} aria-valuemax={100}
                aria-valuenow={Math.round(progress * 100)}
              >
                <span style={{ width: `${Math.round(progress * 100)}%` }} />
              </div>
              <p className="iu-progress-text num">{`${Math.round(progress * 100)}%`}</p>
            </div>
          )}

          {step === 'done' && (
            <p className="iu-done">이 그림을 글에 넣을 수 있습니다.</p>
          )}

          {/* 규격 위반은 화면을 덮지 않고 여기 인라인으로 (설계 제약) */}
          {text && (
            <p className="iu-error" role="alert">
              <b>{text.title}</b>
              {text.hint && <span>{text.hint}</span>}
            </p>
          )}
        </div>

        <div className="iu-actions">
          {step === 'pick' && (
            <button type="button" className="iu-btn" onClick={close}>취소</button>
          )}

          {step === 'ready' && (
            <>
              <button
                type="button" className="iu-btn"
                onClick={() => { releasePreview(); setPicked(null); setError(null); setStep('pick') }}
              >
                다시 고르기
              </button>
              <button type="button" className="iu-btn solid" onClick={() => void start()}>
                {/* 전송 실패 뒤에는 같은 파일로 다시 보내는 것이라 문구를 바꾼다 */}
                {error ? '다시 올리기' : '올리기'}
              </button>
            </>
          )}

          {step === 'sending' && (
            <button type="button" className="iu-btn" onClick={() => abortRef.current?.abort()}>
              취소
            </button>
          )}

          {step === 'done' && (
            <button type="button" className="iu-btn solid" onClick={close}>확인</button>
          )}
        </div>
      </div>
    </div>
  )
}
