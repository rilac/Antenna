import { keccak256, toUtf8Bytes, WebSocketProvider, Wallet, Contract } from 'ethers';
import fs from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';
import { buildLevels, rebuild, ABI as READ_ABI } from './rebuild-from-chain.mjs';

/**
 * 복구 경로 실체인 E2E (ANT-CHAIN-08 · v3 ANT-CHAIN-13).
 *
 *   npx hardhat node                         (터미널 1)
 *   npm run deploy:local                     (터미널 2)
 *   RELAYER_PRIVATE_KEY=0x… node scripts/demo-recovery.mjs [--seed S] [--leaves 5] [--network localhost] [--env local]
 *   로컬 Hardhat 전용이다. prod 는 거부한다 — 운영 장부에 데모 흔적을 남기지 않는다.
 *
 *   ① 커밋 해시 N건 생성 → 트리 → 릴레이어 키로 anchor(root, commitHashes)
 *   ② (DB 는 처음부터 없다) rebuild-from-chain 으로 체인만 읽어 재구축 → anchoredAt · isIncluded 대조
 *   ③ 대조군: 위조 커밋 해시는 isIncluded 가 false
 *   ④ 릴레이어 키로 grantRole 시도 → AccessControlUnauthorizedAccount (역할 분리 실증)
 *
 * 같은 루트를 두 번 앵커하면 AlreadyAnchored 로 막히므로(정상) 재실행 시 --seed 를 바꿔라(기본값은 실행 시각).
 */
const here = path.dirname(fileURLToPath(import.meta.url));
const args = process.argv.slice(2);
const argOf = (name, fallback) => {
  const i = args.indexOf(name);
  return i >= 0 ? args[i + 1] : fallback;
};
const NETWORK = argOf('--network', 'localhost');
const ENVIRONMENT = argOf('--env', process.env.DEPLOY_ENV || (NETWORK === 'ssafy' ? 'prod' : 'local'));
const SEED = argOf('--seed', String(Date.now()));
const LEAVES = Number(argOf('--leaves', '5'));
const RPC_URL =
  process.env.CHAIN_RPC_URL || (NETWORK === 'ssafy' ? 'wss://ws.ssafy-blockchain.com' : 'ws://127.0.0.1:8545');

const WRITE_ABI = [
  ...READ_ABI,
  'function anchor(bytes32,bytes32[])',
  'function grantRole(bytes32,address)',
  'function ANCHOR_ROLE() view returns (bytes32)',
  'error AlreadyAnchored(bytes32)',
  'error RootMismatch(bytes32,bytes32)',
  'error AccessControlUnauthorizedAccount(address,bytes32)',
];

async function main() {
  const pk = process.env.RELAYER_PRIVATE_KEY;
  if (!pk) {
    console.error('RELAYER_PRIVATE_KEY 가 없다 (앵커 tx 서명용, ANCHOR_ROLE 보유 키 — Hardhat 이면 signer 1 의 키).');
    process.exit(1);
  }
  if (ENVIRONMENT === 'prod') {
    // 데모는 실제로 anchor() 를 보낸다. v3 는 루트가 키라 운영 DB 와 부딪히진 않지만, 운영 장부에 데모 흔적이 영원히 남는다.
    console.error('demo-recovery 는 prod 에서 돌리지 않는다 — 로컬 Hardhat(--network localhost --env local)에서 돌려라.');
    process.exit(1);
  }
  const dep = JSON.parse(fs.readFileSync(path.resolve(here, '..', 'deployments', ENVIRONMENT, 'CommitAnchor.json'), 'utf8'));
  const provider = new WebSocketProvider(RPC_URL);
  try {
    const wallet = new Wallet(pk, provider);
    const c = new Contract(dep.address, WRITE_ABI, wallet);

    // ① 앵커
    const commitHashes = Array.from({ length: LEAVES }, (_, i) =>
      keccak256(toUtf8Bytes(`antenna-recovery-demo-${SEED}-commit-${i}`)),
    );
    const levels = buildLevels(commitHashes);
    const root = levels[levels.length - 1][0];
    if ((await c.anchoredAt(root)) > 0n) {
      console.error(`루트 ${root} 는 이미 앵커돼 있다. --seed 를 바꿔라.`);
      process.exit(1);
    }
    console.log(`① anchor(root=${root.slice(0, 18)}…, leaves=${LEAVES}) — 릴레이어 ${wallet.address}`);
    // gasPrice 0 은 SSAFY 규칙이다. 로컬 Hardhat 은 EIP-1559 base fee 가 있어 0 이면 거부된다 — 수수료는 ethers 에 맡긴다.
    const tx = await c.anchor(root, commitHashes, { gasLimit: 5_000_000 });
    const receipt = await tx.wait();
    console.log(`   tx ${tx.hash} · block ${receipt.blockNumber} · gasUsed ${receipt.gasUsed}`);

    // ② 체인만으로 복구 — 위에서 만든 commitHashes·levels 는 일부러 쓰지 않는다
    console.log('② 체인만 읽어 재구축 (DB·로컬 값 사용 안 함)');
    const r = await rebuild(provider, dep.address, root, receipt.blockNumber);
    console.log(`   로그 리프 ${r.commitHashes.length}개 · 재구축 ${r.root.slice(0, 18)}… · anchoredAt 블록 ${r.anchoredBlock} · isIncluded ${r.included}/${r.commitHashes.length}`);
    console.log(r.ok ? '   ✔ 일치' : '   ✘ 불일치');

    // ③ 위조 대조군
    const forged = keccak256(toUtf8Bytes('forged'));
    const forgedOk = await c.isIncluded(root, forged, r.proofs[0]);
    console.log(`③ 위조 커밋 isIncluded → ${forgedOk} ${forgedOk ? '✘ 위조가 통과했다!!' : '✔ 정상적으로 거부'}`);

    // ④ 역할 분리 — 릴레이어가 롤을 나눠 주려 하면 막혀야 한다
    let roleBlocked = false;
    try {
      await c.grantRole.staticCall(await c.ANCHOR_ROLE(), wallet.address);
    } catch (e) {
      roleBlocked = /AccessControlUnauthorizedAccount/.test(String(e?.message || e)) || e?.revert?.name === 'AccessControlUnauthorizedAccount';
      console.log(`④ 릴레이어 grantRole → ${e?.revert?.name || e?.shortMessage || 'revert'} ${roleBlocked ? '✔ 막힘' : '✘ 예상과 다른 에러'}`);
    }
    if (!roleBlocked) console.log('④ ✘ 릴레이어가 롤을 부여할 수 있다 — 역할 분리 실패');

    const allOk = r.ok && !forgedOk && roleBlocked;
    console.log(allOk ? '\n전 항목 통과 — 체인이 곧 백업이다.' : '\n실패 항목 있음');
    process.exitCode = allOk ? 0 : 1;
  } finally {
    await provider.destroy().catch(() => {});
  }
}

main().catch((e) => {
  console.error(e);
  process.exitCode = 1;
});
