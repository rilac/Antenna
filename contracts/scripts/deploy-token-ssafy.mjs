import { createRequire } from 'node:module';
import { WebSocketProvider, Wallet, ContractFactory, Contract, isAddress } from 'ethers';

const require = createRequire(import.meta.url);
const { readArtifact, emit, requireEnvironment } = require('./emit-artifacts.js');

/**
 * PredictToken(ANT) SSAFY 네트워크 배포 (ANT-CHAIN-03).
 *
 *   npx hardhat compile
 *   DEPLOY_ENV=dev|prod  ANCHOR_ADMIN_PRIVATE_KEY=0x…  TOKEN_OPERATOR=0x…  TOKEN_TREASURY=0x…  node scripts/deploy-token-ssafy.mjs
 *
 * ── 환경 (ANT-CHAIN-12) ───────────────────────────────────────────────
 * dev 와 prod 가 따로 한 벌씩이다. 아래 세 주소도 **그 환경의 것**을 넣는다(표: deployments/README.md).
 * prod 기록(deployments/prod/PredictToken.json)이 이미 있으면 tx 를 보내기 전에 멈춘다 — 잔액이 그 주소에 산다.
 *
 * ── 주소 셋 ──────────────────────────────────────────────────────────
 *   관리자   ANCHOR_ADMIN_PRIVATE_KEY 의 주소. 배포 tx 에 서명한다(배포자 = 관리자). 같은 환경의 CommitAnchor 와 같은 키다 —
 *            키를 늘리지 않는다. 역할 부여·회수와 수납 주소 교체만 할 수 있고 잔액은 못 건드린다.
 *   오퍼레이터 TOKEN_OPERATOR — **주소만** 받는다. 서버 릴레이어 키(backend/.env RELAYER_PRIVATE_KEY)의 주소.
 *            mint · burn · subscribe 를 보낸다. CommitAnchor 의 ANCHOR_ROLE 과 같은 주소를 쓰기로 했다(plan ⑤).
 *   수납     TOKEN_TREASURY — 플랫폼 30% 가 쌓이는 주소. 서버는 이 키를 읽지 않는다. `npm run keygen treasury` 로 만든다.
 *
 * ── 배포 뒤 이 스크립트가 체인에서 직접 확인하는 것 ─────────────────
 *   decimals == 0 · symbol == ANT · 관리자에게 OPERATOR 없음 · 오퍼레이터에게 있음 ·
 *   MOVER 는 관리자·오퍼레이터·수납 셋 다 없음 · treasury() 일치.
 *   하나라도 어긋나면 배포 기록을 남기지 않고 실패한다 — 기록이 남으면 그 주소를 .env 에 넣게 된다.
 *
 * ── 이 체인의 함정 (deploy-ssafy.mjs 와 같다) ─────────────────────────
 *  1. gasPrice 0 명시. 2. evmVersion paris (hardhat.config.js). 3. 노드가 키를 안 들고 있어 로컬 서명.
 *  4. Hardhat networks.url 은 HTTP 전용이라 wss 인 SSAFY 는 ethers 로 직접 붙는다.
 *
 * ── 이 컨트랙트는 재배포를 계획하지 않는다 ─────────────────────────
 * 잔액이 이 주소에 산다. 기능을 더할 땐 위성 컨트랙트에 MOVER_ROLE 을 주고, 토큰 규칙을 바꿔야 할 때만
 * v2 이관(Transfer 로그 스냅샷 → 일괄 mint → v1 롤 회수)이다. README "PredictToken" 절 참고.
 */

const RPC_URL = process.env.CHAIN_RPC_URL || 'wss://ws.ssafy-blockchain.com';
const EXPECTED_CHAIN_ID = 31221n;
const CONTRACT = 'PredictToken';

function requireEnv(name, hint) {
  const v = process.env[name];
  if (!v) {
    console.error(`${name} 가 없다. ${hint}`);
    console.error('  예) DEPLOY_ENV=dev ANCHOR_ADMIN_PRIVATE_KEY=0x… TOKEN_OPERATOR=0x… TOKEN_TREASURY=0x… node scripts/deploy-token-ssafy.mjs');
    process.exit(1);
  }
  return v;
}

function requireAddress(name, hint) {
  const v = requireEnv(name, hint);
  if (!isAddress(v)) {
    console.error(`${name} 가 주소 형식이 아니다: ${v}`);
    process.exit(1);
  }
  return v;
}

async function main() {
  // tx 를 보내기 전에 환경부터 확정한다. prod 기록이 이미 있으면 여기서 멈춘다 — 잔액이 그 주소에 산다.
  const environment = requireEnvironment(CONTRACT);
  const artifact = readArtifact(CONTRACT);
  const adminKey = requireEnv('ANCHOR_ADMIN_PRIVATE_KEY', '관리자 키로 배포한다. 셸 환경변수로 넘겨라 — 파일에 적지 마라.');
  const operator = requireAddress('TOKEN_OPERATOR', '오퍼레이터 **주소**(키가 아니다). backend/.env 의 RELAYER_PRIVATE_KEY 에 대응하는 주소.');
  const treasury = requireAddress('TOKEN_TREASURY', '플랫폼 30% 수납 **주소**. `npm run keygen treasury` 로 만든다.');

  const provider = new WebSocketProvider(RPC_URL);
  try {
    const net = await provider.getNetwork();
    if (net.chainId !== EXPECTED_CHAIN_ID) {
      throw new Error(`chainId 가 ${net.chainId} 다. 기대값은 ${EXPECTED_CHAIN_ID} — RPC 주소를 확인해라.`);
    }

    const adminWallet = new Wallet(adminKey.startsWith('0x') ? adminKey : `0x${adminKey}`, provider);
    const admin = adminWallet.address;

    console.log(`PredictToken(ANT) 배포 → SSAFY ${environment} (chainId ${net.chainId})`);
    console.log(`  RPC        ${RPC_URL}`);
    console.log(`  관리자     ${admin}  (배포자)`);
    console.log(`  오퍼레이터 ${operator}`);
    console.log(`  수납       ${treasury}`);

    const factory = new ContractFactory(artifact.abi, artifact.bytecode, adminWallet);
    // ERC20 + AccessControl 이라 CommitAnchor(0.8M) 보다 크다. 가스가 0 인 체인이라 상한만 넉넉히.
    const contract = await factory.deploy(admin, operator, treasury, { gasPrice: 0, gasLimit: 5_000_000 });
    const tx = contract.deploymentTransaction();
    await contract.waitForDeployment();
    const receipt = await provider.getTransactionReceipt(tx.hash);
    const address = await contract.getAddress();

    // 배포 기록에 남기기 전에 체인에서 직접 읽는다.
    const c = new Contract(address, artifact.abi, provider);
    const OPERATOR_ROLE = await c.OPERATOR_ROLE();
    const MOVER_ROLE = await c.MOVER_ROLE();
    const DEFAULT_ADMIN_ROLE = await c.DEFAULT_ADMIN_ROLE();
    const facts = {
      name: await c.name(),
      symbol: await c.symbol(),
      decimals: Number(await c.decimals()),
      treasury: await c.treasury(),
      platformShareBps: Number(await c.PLATFORM_SHARE_BPS()),
    };
    const roles = {
      adminHasAdmin: await c.hasRole(DEFAULT_ADMIN_ROLE, admin),
      adminHasOperator: await c.hasRole(OPERATOR_ROLE, admin),
      operatorHasOperator: await c.hasRole(OPERATOR_ROLE, operator),
      operatorHasAdmin: await c.hasRole(DEFAULT_ADMIN_ROLE, operator),
      moverAdmin: await c.hasRole(MOVER_ROLE, admin),
      moverOperator: await c.hasRole(MOVER_ROLE, operator),
      moverTreasury: await c.hasRole(MOVER_ROLE, treasury),
    };
    console.log('  토큰 확인', facts);
    console.log('  역할 확인', roles);

    const ok =
      facts.decimals === 0 &&
      facts.symbol === 'ANT' &&
      facts.treasury.toLowerCase() === treasury.toLowerCase() &&
      facts.platformShareBps === 3000 &&
      roles.adminHasAdmin &&
      !roles.adminHasOperator &&
      roles.operatorHasOperator &&
      !roles.operatorHasAdmin &&
      !roles.moverAdmin &&
      !roles.moverOperator &&
      !roles.moverTreasury;
    if (!ok) {
      throw new Error('토큰 파라미터 또는 역할 분리가 기대와 다르다. 위 결과를 보고 재배포해라. 기록은 남기지 않았다.');
    }

    emit(
      {
        network: 'ssafy',
        chainId: Number(net.chainId),
        address,
        deployer: admin,
        admin,
        operator,
        treasury,
        name: facts.name,
        symbol: facts.symbol,
        decimals: facts.decimals,
        platformShareBps: facts.platformShareBps,
        txHash: tx.hash,
        blockNumber: receipt.blockNumber,
        gasUsed: Number(receipt.gasUsed),
      },
      artifact,
      { environment, contractName: CONTRACT, version: 1, envVar: 'CONTRACT_PREDICT_TOKEN' },
    );
    console.log('  토큰 인덱서(ANT-CHAIN-11) 시작 블록으로 쓸 값:', receipt.blockNumber);
  } finally {
    await provider.destroy().catch(() => {});
  }
}

main().catch((e) => {
  console.error(e);
  process.exitCode = 1;
});
