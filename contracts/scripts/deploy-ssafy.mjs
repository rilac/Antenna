import { createRequire } from 'node:module';
import { WebSocketProvider, Wallet, ContractFactory } from 'ethers';

const require = createRequire(import.meta.url);
const { readArtifact, emit } = require('./emit-artifacts.js');

/**
 * SSAFY 네트워크 배포 (ANT-CHAIN-01).
 *
 *   npx hardhat compile
 *   RELAYER_PRIVATE_KEY=0x… node scripts/deploy-ssafy.mjs
 *
 * ── 왜 Hardhat 을 안 쓰나 ────────────────────────────────────────────
 * SSAFY 가 공개한 RPC 는 wss://ws.ssafy-blockchain.com 웹소켓 하나뿐이고
 * (HTTP 엔드포인트가 있는지는 확인되지 않았다), Hardhat 2 의 networks.url 은
 * HTTP 전용이다. 그래서 배포만 ethers 로 직접 붙는다.
 * 컴파일 산출물은 hardhat.config.js 로 만든 artifacts/ 를 그대로 읽으므로
 * evmVersion 설정이 두 경로에서 갈릴 일은 없다.
 *
 * ── 이 체인의 함정 3가지 ─────────────────────────────────────────────
 *  1. gasPrice 를 0 으로 명시해야 한다. 안 넣으면 ethers 가 EIP-1559 필드를
 *     채우려다 어긋난다. 가스가 공짜라 잔액 0 인 주소로도 배포된다.
 *  2. evmVersion 이 paris 이하여야 한다. PUSH0 가 없어서, 컴파일은 성공하고
 *     배포 tx 만 "missing revert data" 로 죽는다. (hardhat.config.js 주석 참고)
 *  3. 노드가 키를 안 들고 있다(eth_accounts 가 빈 배열). 로컬에서 서명해 보낸다.
 */

const RPC_URL = process.env.CHAIN_RPC_URL || 'wss://ws.ssafy-blockchain.com';
const EXPECTED_CHAIN_ID = 31221n;

function requireKey() {
  const pk = process.env.RELAYER_PRIVATE_KEY;
  if (!pk) {
    console.error('RELAYER_PRIVATE_KEY 가 없다. 셸 환경변수로 넘겨라 — 파일에 적지 마라.');
    console.error('  예) RELAYER_PRIVATE_KEY=0x… node scripts/deploy-ssafy.mjs');
    process.exit(1);
  }
  return pk.startsWith('0x') ? pk : `0x${pk}`;
}

async function main() {
  const artifact = readArtifact();
  const provider = new WebSocketProvider(RPC_URL);

  try {
    const net = await provider.getNetwork();
    if (net.chainId !== EXPECTED_CHAIN_ID) {
      // 체인이 바뀌면 SignatureGuard 의 서명 payload 에 박히는 CHAIN_ID 도 같이 틀어진다.
      // 조용히 넘어가면 안 되는 종류의 불일치다.
      throw new Error(
        `chainId 가 ${net.chainId} 다. 기대값은 ${EXPECTED_CHAIN_ID} — RPC 주소를 확인해라.`,
      );
    }

    const wallet = new Wallet(requireKey(), provider);
    const admin = process.env.ANCHOR_ADMIN || wallet.address;

    console.log(`CommitAnchor 배포 → SSAFY (chainId ${net.chainId})`);
    console.log(`  RPC     ${RPC_URL}`);
    console.log(`  배포자  ${wallet.address}`);
    console.log(`  관리자  ${admin}`);

    const factory = new ContractFactory(artifact.abi, artifact.bytecode, wallet);
    const contract = await factory.deploy(admin, { gasPrice: 0, gasLimit: 3_000_000 });
    const tx = contract.deploymentTransaction();
    await contract.waitForDeployment();
    const receipt = await provider.getTransactionReceipt(tx.hash);

    emit(
      {
        network: 'ssafy',
        chainId: Number(net.chainId),
        address: await contract.getAddress(),
        deployer: wallet.address,
        admin,
        txHash: tx.hash,
        blockNumber: receipt.blockNumber,
        gasUsed: Number(receipt.gasUsed),
      },
      artifact,
    );
  } finally {
    // 웹소켓은 명시적으로 닫지 않으면 프로세스가 안 끝난다.
    // destroy 는 waitForDeployment 가 걸어 둔 블록 구독의 eth_unsubscribe 를 취소하며
    // UNSUPPORTED_OPERATION 을 던질 수 있다 — 배포는 이미 끝난 뒤라 무해하다. 삼킨다.
    await provider.destroy().catch(() => {});
  }
}

main().catch((e) => {
  console.error(e);
  process.exitCode = 1;
});
