/* keccak256 한 곳. 커밋 봉인(C-01 등록·미리보기)과 검산 ①·②(D-03)가 모두 이것을 쓴다.

   왜 ethers 인가 — commitAnchor.ts 머리말과 같은 이유다. 직접 구현하면 이 화면들이
   없애려던 신뢰 대상(우리 코드)을 다시 만들고, 양쪽 기준값 픽스처가 둘 다 ethers 로
   만들어졌다:
     backend/src/test/resources/commit/commit-cross-fixture.json   (커밋 문자열·해시)
     contracts/test/fixtures/merkle-cross-fixture.json             (머클 결합)
   서버는 web3j Hash.sha3 를 쓰는데 같은 keccak256 이다. MessageDigest("SHA3-256") 은
   패딩이 다른 **다른 함수** 라 어느 쪽에서도 쓰면 안 된다.

   왜 동적 import 인가 — ethers 는 메인 번들에 싣기엔 크다. 예측 등록·검산 화면에
   들어갔을 때만 받아 온다. 로더를 여기 하나만 두어 두 번 받지 않는다. */

let loading: Promise<typeof import('ethers')> | null = null

export function ethers() {
  loading ??= import('ethers')
  return loading
}

/**
 * keccak256(UTF-8 바이트) → `0x` + 소문자 64 hex.
 *
 * 서버 CommitHashes.keccak256Hex 와 같은 값이어야 한다. 문자열을 바이트로 바꾸는
 * 것은 toUtf8Bytes 가 하고, 여기서 trim·정규화를 끼워 넣지 않는다 — 손대는 순간
 * 서버가 해시한 바이트와 갈린다.
 */
export async function keccak256Utf8(text: string) {
  const { keccak256, toUtf8Bytes } = await ethers()
  return keccak256(toUtf8Bytes(text))
}
