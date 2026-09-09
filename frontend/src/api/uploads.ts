/* M-07 이미지 업로드. API 명세서 §업로드 · 설계서 §3 M · §4 F-03 · H-04 · §6.

   POST /uploads   multipart(file, purpose) + Idempotency-Key → 201 UploadResponse
   GET  /uploads/{fileId}                                     → 이미지 바이트

   백엔드 ANT-COMMUNITY-06 으로 둘 다 열려 있다. 목업이 아니다.

   ── 티켓의 presigned URL 방식은 쓰지 않는다 ─────────────────
   지라 -191 은 "presigned URL 발급 → 스토리지 직접 PUT → 완료 통지" 3단계를 적고 있지만,
   구현된 백엔드는 **서버로 직접 multipart** 를 받는다. 발급 엔드포인트가 없다.
   UploadController 주석이 이유를 적어 두었다 — "저장 위치가 로컬 볼륨이라 서버가 직접
   내려보낸다. CDN 으로 옮기면 이 엔드포인트는 사라지고 url 이 외부 주소를 담는다."

   그래서 단계가 하나다. 티켓의 "발급 실패와 전송 실패를 구분" 은 그 형태로는 성립하지
   않으므로, 대신 **로컬 검증 실패 · 전송 실패 · 서버 거절** 셋을 구분한다(모달이 그린다).
   CDN 으로 옮겨 발급 단계가 생기면 이 파일에 한 단계를 더한다.

   ── 왜 fetch 가 아니라 XHR 인가 ─────────────────────────────
   설계 제약이 진행률을 요구하는데 fetch 에는 업로드 진행 이벤트가 없다(응답 스트림만 읽는다).
   XHR 의 upload.onprogress 가 유일한 길이다. 그래서 api/client.ts 의 request 를 쓰지 못하고,
   그 파일이 하는 일(토큰 헤더 · 401 재발급 후 1회 재시도 · 오류 계약 변환)을 여기서 되짚는다. */
import { API_BASE, getAccessToken, refreshAccessToken } from './client'
import { ApiError, CLIENT_ERROR_CODE, toApiError } from './errors'
import { idempotencyKey, releaseIdempotencyKey } from './idempotency'

/** 서버 UploadFile.Purpose 와 짝이다. 화면마다 무엇에 쓰는 그림인지 서버가 알아야 한다. */
export const UPLOAD_PURPOSES = ['AD', 'REPORT', 'POST'] as const
export type UploadPurpose = (typeof UPLOAD_PURPOSES)[number]

/** 서버 UploadResponse 와 짝이다. */
export type UploadedImage = {
  fileId: string
  /** 지금은 GET /uploads/{fileId}. CDN 으로 옮기면 외부 주소가 된다 */
  url: string
  width: number
  height: number
  bytes: number
  mime: string
}

/* ── 서버가 거절하는 조건. 같은 값을 여기서도 재 두어 헛업로드를 막는다 ──
   서버 검사를 대신하는 것이 아니다 — 5MB 를 다 올린 뒤 400 을 받으면 사용자는 기다린 만큼을
   잃는다. 서버 UploadProperties 의 기본값과 같은 수를 쓰고, 어긋나면 서버가 최종 판정한다. */

/** 5MB. 서버 app.uploads.max-bytes 와 같다 */
export const MAX_BYTES = 5 * 1024 * 1024

/** 4천만 화소. 서버 maxPixels 와 같다 — 용량이 작아도 픽셀이 크면 디코딩에서 터진다 */
export const MAX_PIXELS = 40_000_000

/**
 * 서버 ImageProbe 가 매직 바이트로 알아보는 세 가지.
 *
 * 브라우저는 확장자로 MIME 을 붙이므로 이 목록과 실제 내용이 다를 수 있다. 그래도 미리 거르는
 * 값은 있다 — .pdf 를 고른 사람에게 업로드를 다 시킨 뒤 거절할 이유가 없다.
 */
export const ACCEPTED_MIME = ['image/png', 'image/jpeg', 'image/webp'] as const

/** 파일 선택 창에 넘길 값 */
export const ACCEPT_ATTR = ACCEPTED_MIME.join(',')

/**
 * 배너 가로세로비 제약. 서버 adAspectRatio · adAspectTolerance 와 같다.
 *
 * AD 에만 걸린다 — 노출 자리가 고정이라 어긋난 그림은 화면에서 잘리거나 늘어나는데,
 * 그때는 이미 광고비를 받은 뒤라 되돌릴 수단이 없다(서버 UploadService 주석).
 */
export const AD_ASPECT = { ratio: 4, tolerance: 0.05 } as const

export type AspectRule = { ratio: number; tolerance: number }

/** 화면별 제약. 모달이 파라미터로 받는다(설계 제약) */
export const ASPECT_BY_PURPOSE: Partial<Record<UploadPurpose, AspectRule>> = {
  AD: AD_ASPECT,
}

/* ── 로컬 검증 ─────────────────────────────────────────── */

/**
 * 올리기 전에 알 수 있는 것만 본다. 통과했다고 서버가 받는다는 뜻은 아니다 —
 * 서버는 매직 바이트로 형식을 다시 보고, 실제 화소도 자기가 읽는다.
 *
 * 서버와 같은 code 를 쓴다. 화면이 로컬·서버 오류를 같은 문구로 그릴 수 있어야 하고,
 * 그러면 나중에 검사를 서버로만 몰아도 화면이 안 바뀐다.
 */
export function precheck(file: File, aspect?: AspectRule): ApiError | null {
  if (file.size > MAX_BYTES) {
    return new ApiError({ code: 'FILE_TOO_LARGE', message: 'local', field: 'file' }, 400)
  }
  /* type 이 빈 문자열로 오는 경우가 있다(확장자 없는 파일). 그때는 막지 않고 서버에 맡긴다
     — 매직 바이트를 보는 쪽이 정확하고, 여기서 막으면 멀쩡한 png 를 거절할 수 있다. */
  if (file.type && !ACCEPTED_MIME.includes(file.type as (typeof ACCEPTED_MIME)[number])) {
    return new ApiError({ code: 'UNSUPPORTED_IMAGE_TYPE', message: 'local', field: 'file' }, 400)
  }
  void aspect // 비율은 이미지를 디코딩해야 알 수 있다 — measure() 가 맡는다
  return null
}

/** 디코딩해야 알 수 있는 것. 실패하면 이미지가 아니거나 깨진 파일이다. */
export function measure(file: File): Promise<{ width: number; height: number; previewUrl: string }> {
  return new Promise((resolve, reject) => {
    const previewUrl = URL.createObjectURL(file)
    const img = new Image()
    img.onload = () => resolve({ width: img.naturalWidth, height: img.naturalHeight, previewUrl })
    img.onerror = () => {
      URL.revokeObjectURL(previewUrl)
      reject(new ApiError({ code: 'UNSUPPORTED_IMAGE_TYPE', message: 'decode failed', field: 'file' }, 400))
    }
    img.src = previewUrl
  })
}

/** 디코딩 뒤 검사. 화소 수와 비율은 크기를 알아야 판단할 수 있다. */
export function checkSize(width: number, height: number, aspect?: AspectRule): ApiError | null {
  if (width * height > MAX_PIXELS) {
    return new ApiError({ code: 'IMAGE_TOO_LARGE', message: 'local', field: 'file' }, 400)
  }
  if (aspect && Math.abs(width / height - aspect.ratio) > aspect.tolerance) {
    return new ApiError({ code: 'INVALID_IMAGE_RATIO', message: 'local', field: 'file' }, 400)
  }
  return null
}

/* ── 업로드 ────────────────────────────────────────────── */

/**
 * 멱등 키 scope. 명세 §1 의 필수 대상 8개 중 하나다.
 *
 * 파일마다 따로 둔다 — 한 화면에서 그림 넉 장을 잇달아 올릴 때(F-04 는 최대 4장) 같은 키가
 * 나가면 두 번째부터 첫 파일의 fileId 를 돌려받는다. 이름·크기가 같아도 다른 파일일 수 있어
 * lastModified 까지 섞는다.
 */
export const uploadScope = (file: File) =>
  `upload:${file.name}:${file.size}:${file.lastModified}`

type UploadOptions = {
  /** 0~1. XHR 업로드 진행률 */
  onProgress?: (ratio: number) => void
  /** 사용자가 취소할 수 있게 한다. abort 하면 CLIENT_UPLOAD_ABORTED 로 끝난다 */
  signal?: AbortSignal
}

/**
 * 한 장 올린다. 성공하면 서버가 만든 fileId 와 실제 크기가 온다.
 *
 * 401 이면 재발급 후 **한 번만** 다시 보낸다 — api/client.ts 의 request 와 같은 규칙이다.
 * 같은 멱등 키로 재전송하므로 서버가 두 번 저장하지 않는다.
 */
export async function uploadImage(
  file: File,
  purpose: UploadPurpose,
  options: UploadOptions = {},
): Promise<UploadedImage> {
  const scope = uploadScope(file)
  /* 실패하면 키를 놓지 않는다 — 전송이 끊긴 것인지 서버가 저장까지 마치고 응답만 못 준
     것인지 클라이언트는 알 수 없다. 같은 키로 재시도해야 중복 저장이 안 생긴다.
     그래서 여기에는 catch 가 없다(오류는 그대로 호출부로 간다). */
  const result = await send(file, purpose, scope, options)
  /* 확정됐으니 키를 놓는다. 같은 파일을 나중에 다시 올리면 새 키로 새 fileId 가 나온다 —
     사용자가 지웠다가 다시 넣는 것은 다른 요청이다. */
  releaseIdempotencyKey(scope)
  return result
}

function send(
  file: File,
  purpose: UploadPurpose,
  scope: string,
  options: UploadOptions,
  retried = false,
): Promise<UploadedImage> {
  return new Promise((resolve, reject) => {
    const form = new FormData()
    form.append('file', file)
    form.append('purpose', purpose)

    const xhr = new XMLHttpRequest()
    xhr.open('POST', `${API_BASE}/uploads`)
    // refresh 쿠키를 함께 보낸다 — fetch 의 credentials:'include' 와 같은 뜻
    xhr.withCredentials = true

    const token = getAccessToken()
    if (token) xhr.setRequestHeader('Authorization', `Bearer ${token}`)
    xhr.setRequestHeader('Idempotency-Key', idempotencyKey(scope))
    /* Content-Type 은 넣지 않는다. FormData 를 주면 브라우저가 multipart 경계값까지
       붙여 만들어 주는데, 직접 쓰면 그 경계값이 빠져 서버가 본문을 못 가른다. */

    if (options.onProgress) {
      xhr.upload.onprogress = (e) => {
        if (e.lengthComputable) options.onProgress?.(e.loaded / e.total)
      }
    }

    const abort = () => xhr.abort()
    options.signal?.addEventListener('abort', abort, { once: true })
    const cleanup = () => options.signal?.removeEventListener('abort', abort)

    xhr.onload = () => {
      cleanup()
      const payload = parse(xhr.responseText)

      if (xhr.status === 201 || xhr.status === 200) {
        resolve(payload as UploadedImage)
        return
      }

      /* 401 은 access 가 만료됐을 뿐일 수 있다. 재발급 후 한 번만 다시 보낸다.
         두 번째도 401 이면 세션이 죽은 것이라 그대로 올린다. */
      if (xhr.status === 401 && !retried) {
        refreshAccessToken()
          .then((ok) => (ok
            ? send(file, purpose, scope, options, true)
            : Promise.reject(toApiError(401, payload))))
          .then(resolve, reject)
        return
      }

      reject(toApiError(xhr.status, payload))
    }

    xhr.onerror = () => {
      cleanup()
      // 서버에 닿지 못했다. status 0 은 "서버까지 가지 않은 실패" 라는 이 앱의 규칙이다.
      reject(new ApiError(
        { code: CLIENT_ERROR_CODE.CLIENT_UPLOAD_FAILED, message: 'network' }, 0,
      ))
    }

    xhr.onabort = () => {
      cleanup()
      reject(new ApiError(
        { code: CLIENT_ERROR_CODE.CLIENT_UPLOAD_ABORTED, message: 'aborted' }, 0,
      ))
    }

    xhr.send(form)
  })
}

/** 오류 응답이 JSON 이 아닐 수 있다(서블릿이 먼저 끊은 413 등). 그때는 null 로 넘긴다. */
function parse(text: string): unknown {
  try {
    return JSON.parse(text) as unknown
  } catch {
    return null
  }
}

/* ── 표시 도우미 ─────────────────────────────────────── */

export function formatBytes(bytes: number) {
  if (bytes < 1024) return `${bytes}B`
  if (bytes < 1024 * 1024) return `${Math.round(bytes / 1024)}KB`
  return `${(bytes / 1024 / 1024).toFixed(1)}MB`
}

/** "4:1" 처럼 읽히게. 비율 제약을 숫자로만 적으면 무엇을 맞춰야 하는지 모른다 */
export function formatAspect(rule: AspectRule) {
  return `${rule.ratio}:1`
}
