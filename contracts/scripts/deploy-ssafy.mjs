import { createRequire } from 'node:module';
import { WebSocketProvider, Wallet, ContractFactory, Contract, isAddress } from 'ethers';

const require = createRequire(import.meta.url);
const { readArtifact, emit } = require('./emit-artifacts.js');

/**
 * SSAFY 네트워크 배포 (ANT-CHAIN-08, v2).
 *
 *   npx hardhat compile
 *   ANCHOR_ADMIN_PRIVATE_KEY=0x…  ANCHOR_RELAYER=0x…  node scripts/deploy-ssafy.mjs
 *
 * ── 키가 두 개다 ─────────────────────────────────────────────────────
 * 배포 tx 는 **관리자 키**로 서명한다(배포자 = 관리자). 릴레이어는 **주소만** 받는다 —
 * 릴레이어 키는 서버 .env 에 있고, 배포하는 머신에 있을 이유가 없다.
 * v1 은 RELAYER_PRIVATE_KEY 하나로 배포했고 그 키가 관리자까지 겸했다. 그 키가 새면
 * 재배포밖에 답이 없었다. 지금은 관리자 키로 revokeRole/grantRole 하면 끝난다.
 *
 * 배포 후 스크립트가 직접 확인하는 것: 관리자에게 ANCHOR_ROLE 이 **없고**, 릴레이어에게 **있다**.
 *
 * ── 왜 Hardhat 을 안 쓰나 ────────────────────────────────────────────
 * SSAFY 가 공개한 RPC 는 wss://ws.ssafy-blockchain.com 웹소켓 하나뿐이고 Hardhat 2 의
 * networks.url 은 HTTP 전용이다. 컴파일 산출물은 hardhat.config.js 로 만든 artifacts/ 를
 * 그대로 읽으므로 evmVersion 설정이 두 경로에서 갈릴 일은 없다.
 *
 * ── 이 체인의 함정 ───────────────────────────────────────────────────
 *  1. gasPrice 0 명시. 안 넣으면 ethers 가 EIP-1559 필드를 채우려다 어긋난다.
 *  2. evmVersion paris 이하. PUSH0 가 없어서 컴파일은 성공하고 배포 tx 만 "missing revert data".
 *  3. 노드가 키를 안 들고 있다(eth_accounts 빈 배열). 로컬에서 서명해 보낸다.
 */

const RPC_URL = process.env.CHAIN_RPC_URL || 'wss://ws.ssafy-blockchain.com';
const EXPECTED_CHAIN_ID = 31221n;

function requireEnv(name, hint) {
  const v = process.env[name];
  if (!v) {
    console.error(`${name} 가 없다. ${hint}`);
    console.error('  예) ANCHOR_ADMIN_PRIVATE_KEY=0x… ANCHOR_RELAYER=0x… node scripts/deploy-ssafy.mjs');
    process.exit(1);
  }
  return v;
}

async function main() {
  const artifact = readArtifact();
  const adminKey = requireEnv('ANCHOR_ADMIN_PRIVATE_KEY', '관리자 키로 배포한다. 셸 환경변수로 넘겨라 — 파일에 적지 마라.');
  const relayer = requireEnv('ANCHOR_RELAYER', '릴레이어 **주소**(키가 아니다). backend/.env 의 RELAYER_PRIVATE_KEY 에 대응하는 주소.');
  if (!isAddress(relayer)) {
    console.error(`ANCHOR_RELAYER 가 주소 형식이 아니다: ${relayer}`);
    process.exit(1);
  }

  const provider = new WebSocketProvider(RPC_URL);
  try {
    const net = await provider.getNetwork();
    if (net.chainId !== EXPECTED_CHAIN_ID) {
      // 체인이 바뀌면 SignatureGuard 의 서명 payload 에 박히는 CHAIN_ID 도 같이 틀어진다.
      throw new Error(`chainId 가 ${net.chainId} 다. 기대값은 ${EXPECTED_CHAIN_ID} — RPC 주소를 확인해라.`);
    }

    const adminWallet = new Wallet(adminKey.startsWith('0x') ? adminKey : `0x${adminKey}`, provider);
    const admin = adminWallet.address;

    console.log(`CommitAnchor v2 배포 → SSAFY (chainId ${net.chainId})`);
    console.log(`  RPC      ${RPC_URL}`);
    console.log(`  관리자   ${admin}  (배포자)`);
    console.log(`  릴레이어 ${relayer}`);

    const factory = new ContractFactory(artifact.abi, artifact.bytecode, adminWallet);
    const contract = await factory.deploy(admin, relayer, { gasPrice: 0, gasLimit: 3_000_000 });
    const tx = contract.deploymentTransaction();
    await contract.waitForDeployment();
    const receipt = await provider.getTransactionReceipt(tx.hash);
    const address = await contract.getAddress();

    // 역할 분리 실증 — 배포 기록에 남기기 전에 체인에서 직접 읽는다.
    const c = new Contract(address, artifact.abi, provider);
    const ANCHOR_ROLE = await c.ANCHOR_ROLE();
    const DEFAULT_ADMIN_ROLE = await c.DEFAULT_ADMIN_ROLE();
    const check = {
      adminHasAdmin: await c.hasRole(DEFAULT_ADMIN_ROLE, admin),
      adminHasAnchor: await c.hasRole(ANCHOR_ROLE, admin),
      relayerHasAnchor: await c.hasRole(ANCHOR_ROLE, relayer),
      relayerHasAdmin: await c.hasRole(DEFAULT_ADMIN_ROLE, relayer),
    };
    console.log('  역할 확인', check);
    if (!check.adminHasAdmin || check.adminHasAnchor || !check.relayerHasAnchor || check.relayerHasAdmin) {
      throw new Error('역할 분리가 기대와 다르다. 위 결과를 보고 재배포해라.');
    }

    emit(
      {
        network: 'ssafy',
        chainId: Number(net.chainId),
        address,
        deployer: admin,
        admin,
        relayer,
        txHash: tx.hash,
        blockNumber: receipt.blockNumber,
        gasUsed: Number(receipt.gasUsed),
      },
      artifact,
    );
  } finally {
    // destroy 는 waitForDeployment 가 걸어 둔 블록 구독 해지를 취소하며 UNSUPPORTED_OPERATION 을
    // 던질 수 있다 — 배포는 이미 끝난 뒤라 무해하다. 삼킨다.
    await provider.destroy().catch(() => {});
  }
}

main().catch((e) => {
  console.error(e);
  process.exitCode = 1;
});
