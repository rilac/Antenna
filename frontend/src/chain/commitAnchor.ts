/* 브라우저가 장부 컨트랙트를 직접 읽는 통로. D-03 3단계 검산 §② 가 쓴다.

   왜 서버를 통하지 않는가
   서버가 "검증됐습니다" 라고 답해 주면 그건 증명이 아니라 주장이다. 우리 서버를 믿어야
   하기 때문이다. 그래서 ② 는 브라우저가 (1) 서버가 준 proof 를 직접 접어 루트를 복원하고
   (2) 그 루트가 체인에 박혀 있는지 anchoredAt(root) 로 묻는다. 두 값 모두 우리 서버가
   개입할 수 없는 곳에서 나온다 — 묻는 루트조차 서버가 준 값이 아니라 내가 접은 값이다.

   v3(ANT-CHAIN-13) — 장부 칸의 키가 머클루트다. v2 는 rootOf(batchId) 로 "그 번호 칸의 루트" 를 읽어 비교했지만,
   이제는 루트 자체가 키라 "이 루트가 박혀 있나(몇 번 블록에)" 를 한 번에 묻는다.

   왜 익스플로러가 아니라 RPC 인가
   SSAFY 는 사설 Besu 망이라 공개 익스플로러가 없다. 익스플로러는 RPC 를 대신 읽어 주는
   웹사이트일 뿐이므로, 브라우저가 같은 RPC 를 직접 읽으면 같은 값을 얻는다.
   VITE_CHAIN_RPC_URL 은 wss 하나뿐이다 — http 종단점이 없어 fetch 로는 닿지 못한다.

   왜 ethers 인가
   ① keccak256 을 직접 구현하면 이 화면이 없애려던 신뢰 대상(우리 코드)을 다시 만든다.
   ② 서버 MerkleTree · 컨트랙트 _computeRoot · 이 파일 셋이 같은 규격이어야 하는데,
      그 규격의 기준값 픽스처(contracts/test/fixtures/merkle-cross-fixture.json)가
      바로 ethers 로 만들어졌다. 같은 구현을 쓰는 것이 어긋남을 없애는 가장 짧은 길이다.
   메인 번들에 싣지 않으려고 동적 import 로 이 화면에서만 불러온다 — 로더는 chain/keccak.ts
   하나뿐이라 ①단계와 여기가 같은 모듈을 나눠 쓴다. */
import { ApiError, CLIENT_ERROR_CODE } from '../api/errors'
import { ethers } from './keccak'

/** 장부 컨트랙트에서 검증에 쓰는 두 함수. 둘 다 view — 권한도 가스도 필요 없다. */
const ABI = [
  'function anchoredAt(bytes32 merkleRoot) view returns (uint256)',
  'function isIncluded(bytes32 merkleRoot, bytes32 commitHash, bytes32[] proof) view returns (bool)',
]

/** RPC 가 답이 없을 때 화면이 영영 "조회 중" 에 머물지 않도록 끊는다. */
const TIMEOUT_MS = 15_000

function clientError(code: string, message: string) {
  // status 0 — 서버까지 가지 않은 실패다. wallet/provider.ts 와 같은 규칙.
  return new ApiError({ code, message }, 0)
}

function withTimeout<T>(work: Promise<T>, what: string) {
  return new Promise<T>((resolve, reject) => {
    const timer = setTimeout(
      () => reject(clientError(CLIENT_ERROR_CODE.CLIENT_CHAIN_UNREACHABLE, `${what} 응답 없음`)),
      TIMEOUT_MS,
    )
    work.then(resolve, reject).finally(() => clearTimeout(timer))
  })
}

/**
 * proof 를 아래에서 위로 접어 루트를 복원한다. 체인도 서버도 부르지 않는 순수 계산이다.
 *
 * 규격 세 가지가 서버 MerkleTree · 컨트랙트 _hashPair 와 한 글자도 달라선 안 된다.
 * - 리프는 keccak256(commitHash) — commitHash 를 그대로 쓰지 않는다(2차 프리이미지 방지)
 * - 결합은 정렬 결합 keccak256(min ‖ max) — 그래서 proof 에 좌우 정보가 없다
 * - 홀수 꼬리는 승격이라 그 레벨에 형제가 없다. proof 가 그 레벨을 빼고 오므로
 *   여기서는 "받은 형제를 순서대로 접기" 만 하면 되고 승격을 알 필요가 없다
 */
export async function foldRoot(commitHash: string, proof: string[]) {
  const { keccak256, concat, getBytes } = await ethers()
  let node = keccak256(getBytes(commitHash))
  for (const sibling of proof) {
    // 정렬 비교. 둘 다 소문자 0x hex 32바이트라 문자열 사전순이 바이트 사전순과 같다.
    node = node.toLowerCase() <= sibling.toLowerCase()
      ? keccak256(concat([node, sibling]))
      : keccak256(concat([sibling, node]))
  }
  return node
}

/** 체인이 답한 것들. 서버 응답과 대조하는 건 화면의 몫이다 — 여기서는 판정하지 않는다. */
export type ChainRead = {
  /** RPC 가 스스로 밝힌 체인. 서버가 말한 chainId 와 다르면 다른 장부를 본 것이다 */
  chainId: number
  /** anchoredAt(root) — 그 루트가 박힌 블록. 앵커 전이면 0 */
  anchoredBlock: number
  /** isIncluded(root, commitHash, proof) — 체인이 직접 접어 낸 판정 */
  included: boolean
}

/**
 * 루트 하나에 대해 체인에 세 가지를 묻는다. 연결은 한 번만 열고 반드시 닫는다.
 *
 * contractAddress 를 서버 응답에서 받는 이유 — 컨트랙트를 재배포하면 옛 배치는
 * 옛 주소의 장부에 남는다. 화면에 주소를 박아 두면 재배포 뒤 옛 배치가 검증 불가가 된다.
 */
export async function readChain(
  contractAddress: string,
  merkleRoot: string,
  commitHash: string,
  proof: string[],
): Promise<ChainRead> {
  const url = import.meta.env.VITE_CHAIN_RPC_URL as string | undefined
  if (!url) {
    throw clientError(CLIENT_ERROR_CODE.CLIENT_CHAIN_NOT_CONFIGURED, 'VITE_CHAIN_RPC_URL 없음')
  }

  const { WebSocketProvider, Contract } = await ethers()
  let provider: InstanceType<typeof WebSocketProvider> | null = null
  try {
    /* staticNetwork — 붙자마자 체인을 알아내려 드는 탐색을 끈다. 사설망에서 그 탐색이
       막히면 eth_call 을 보내 보지도 못하고 멈춘다. 대신 chainId 는 아래에서 직접 묻는다. */
    provider = new WebSocketProvider(url, undefined, { staticNetwork: true })
    const contract = new Contract(contractAddress, ABI, provider)

    const [chainIdHex, block, included] = await withTimeout(
      Promise.all([
        provider.send('eth_chainId', []) as Promise<string>,
        contract.anchoredAt(merkleRoot) as Promise<bigint>,
        contract.isIncluded(merkleRoot, commitHash, proof) as Promise<boolean>,
      ]),
      '체인 조회',
    )
    return { chainId: Number(chainIdHex), anchoredBlock: Number(block), included }
  } catch (e) {
    if (e instanceof ApiError) throw e
    throw clientError(
      CLIENT_ERROR_CODE.CLIENT_CHAIN_UNREACHABLE,
      e instanceof Error ? e.message : '체인 연결 실패',
    )
  } finally {
    // WebSocket 을 열어 둔 채 화면을 떠나면 연결이 쌓인다.
    provider?.destroy()
  }
}
