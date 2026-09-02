/* 브라우저 지갑(EIP-1193) 접점. 설계서 §4 M-01 · API 명세서 §1.2.

   라이브러리를 쓰지 않는다 — 필요한 건 eth_requestAccounts 와 personal_sign
   두 개뿐이고, ethers/web3 는 이 둘을 쓰자고 들이기엔 번들이 너무 크다.

   서명 방식은 personal_sign 이다. EIP-712 가 아니다 —
   컨트랙트가 사용자 서명을 검증하지 않는 구조(릴레이어가 서버 키로 전송)라
   구조화 서명의 이점이 없다. 명세 §1.2 에서 2026-08-31 확정. */
import { ApiError, CLIENT_ERROR_CODE } from '../api/errors'

/** EIP-1193 요청 인터페이스. 지갑이 주입하는 객체의 우리가 쓰는 부분만 적는다. */
type Eip1193 = {
  request: (args: { method: string; params?: unknown[] }) => Promise<unknown>
}

declare global {
  interface Window {
    ethereum?: Eip1193
  }
}

function provider(): Eip1193 | null {
  return typeof window !== 'undefined' && window.ethereum ? window.ethereum : null
}

/** 지갑 확장이 주입돼 있는지. 없으면 버튼을 눌러도 할 수 있는 게 없다. */
export function hasWallet() {
  return provider() !== null
}

/* EIP-1193 표준 오류 코드. 지갑마다 문구는 달라도 code 는 같다.
   문구로 분기하면 지갑을 바꾸는 순간 죽는 분기가 된다. */
const REJECTED = 4001
const PENDING = -32002

/** 지갑이 던진 것을 우리 오류 계약으로 옮긴다. status 는 서버가 준 게 아니라 0 이다. */
function fromProviderError(e: unknown): ApiError {
  const code = (e as { code?: unknown } | null)?.code
  if (code === REJECTED) {
    return new ApiError({ code: CLIENT_ERROR_CODE.CLIENT_SIGN_REJECTED, message: 'user rejected' }, 0)
  }
  if (code === PENDING) {
    return new ApiError({ code: CLIENT_ERROR_CODE.CLIENT_WALLET_BUSY, message: 'request pending' }, 0)
  }
  return new ApiError(
    { code: CLIENT_ERROR_CODE.UNKNOWN, message: e instanceof Error ? e.message : String(e) },
    0,
  )
}

function requireProvider(): Eip1193 {
  const p = provider()
  if (!p) {
    throw new ApiError({ code: CLIENT_ERROR_CODE.CLIENT_WALLET_MISSING, message: 'no provider' }, 0)
  }
  return p
}

/**
 * 지갑 연결을 요청하고 주소 하나를 받는다.
 *
 * 서버가 소문자로 정규화해 저장하므로 여기서도 소문자로 맞춘다 —
 * payload 의 address 줄과 본문 address 가 서버 조립본과 한 글자라도
 * 다르면 복원 주소가 달라져 SIGNER_MISMATCH 가 난다.
 */
export async function connectAddress(): Promise<string> {
  const p = requireProvider()
  let accounts: unknown
  try {
    accounts = await p.request({ method: 'eth_requestAccounts' })
  } catch (e) {
    throw fromProviderError(e)
  }

  const first = Array.isArray(accounts) ? accounts[0] : undefined
  if (typeof first !== 'string' || first.length === 0) {
    // 지갑이 잠겨 있거나 사용자가 계정을 하나도 고르지 않았다.
    throw new ApiError({ code: CLIENT_ERROR_CODE.CLIENT_NO_ACCOUNT, message: 'no account' }, 0)
  }
  return first.toLowerCase()
}

/**
 * personal_sign. 파라미터 순서는 [message, address] 다 — eth_sign 과 반대라
 * 뒤집으면 지갑이 조용히 거부한다.
 *
 * 메시지를 hex 로 감싸지 않고 평문 그대로 넘긴다. 그래야 지갑 창에
 * 사용자가 읽을 수 있는 payload 가 뜬다 — 무엇에 서명하는지 보여 주는 것이
 * 이 화면의 설계 제약이다.
 */
export async function personalSign(message: string, address: string): Promise<string> {
  const p = requireProvider()
  try {
    const signature = await p.request({ method: 'personal_sign', params: [message, address] })
    if (typeof signature !== 'string') {
      throw new ApiError({ code: CLIENT_ERROR_CODE.UNKNOWN, message: 'signature not a string' }, 0)
    }
    return signature
  } catch (e) {
    if (e instanceof ApiError) throw e
    throw fromProviderError(e)
  }
}
