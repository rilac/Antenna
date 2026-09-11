import { keccak256, toUtf8Bytes, WebSocketProvider, Wallet, Contract } from 'ethers';
import fs from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';
import { buildLevels, rebuild, ABI as READ_ABI } from './rebuild-from-chain.mjs';

/**
 * 복구 경로 실체인 E2E (ANT-CHAIN-08, 수용 기준 4).
 *
 *   RELAYER_PRIVATE_KEY=0x… node scripts/demo-recovery.mjs --batch-id N [--leaves 5] [--network ssafy] [--env dev]
 *   dev 전용이다. prod 는 거부한다(운영 batchId 를 태우면 운영 DB 와 충돌한다).
 *
 *   ① 커밋 해시 N건 생성 → 트리 → 릴레이어 키로 anchor(batchId, root, commitHashes)
 *   ② (DB 는 처음부터 없다) rebuild-from-chain 으로 체인만 읽어 재구축 → rootOf · isIncluded 대조
 *   ③ 대조군: 위조 커밋 해시는 isIncluded 가 false
 *   ④ 릴레이어 키로 grantRole 시도 → AccessControlUnauthorizedAccount (역할 분리 실증)
 *
 * 같은 batchId 를 두 번 앵커하면 BatchAlreadyAnchored 로 막히므로(정상) 재실행 시 --batch-id 를 올려라.
 */
const here = path.dirname(fileURLToPath(import.meta.url));
const args = process.argv.slice(2);
const argOf = (name, fallback) => {
  const i = args.indexOf(name);
  return i >= 0 ? args[i + 1] : fallback;
};
const NETWORK = argOf('--network', 'ssafy');
// 배포 기록 폴더(ANT-CHAIN-12). SSAFY 는 dev 가 기본이다.
const ENVIRONMENT = argOf('--env', process.env.DEPLOY_ENV || (NETWORK === 'ssafy' ? 'dev' : 'local'));
const BATCH_ID = Number(argOf('--batch-id', '1'));
const LEAVES = Number(argOf('--leaves', '5'));
const RPC_URL =
  process.env.CHAIN_RPC_URL || (NETWORK === 'ssafy' ? 'wss://ws.ssafy-blockchain.com' : 'ws://127.0.0.1:8545');

const WRITE_ABI = [
  ...READ_ABI,
  'function anchor(uint256,bytes32,bytes32[])',
  'function grantRole(bytes32,address)',
  'function ANCHOR_ROLE() view returns (bytes32)',
  'error BatchAlreadyAnchored(uint256)',
  'error RootMismatch(bytes32,bytes32)',
  'error AccessControlUnauthorizedAccount(address,bytes32)',
];

async function main() {
  const pk = process.env.RELAYER_PRIVATE_KEY;
  if (!pk) {
    console.error('RELAYER_PRIVATE_KEY 가 없다 (앵커 tx 서명용, ANCHOR_ROLE 보유 키).');
    process.exit(1);
  }
  if (ENVIRONMENT === 'prod') {
    // 데모는 실제로 anchor() 를 보낸다. 운영 컨트랙트의 batchId 는 운영 DB 의 anchor_batches.id 와 1:1 이라,
    // 여기서 하나를 태우면 운영 서버가 그 번호에 도달하는 날 BATCH_ID_COLLISION 이 난다(README 함정 2).
    console.error('demo-recovery 는 prod 에서 돌리지 않는다 — 운영 batchId 를 태운다. --env dev 로 돌려라.');
    process.exit(1);
  }
  const dep = JSON.parse(fs.readFileSync(path.resolve(here, '..', 'deployments', ENVIRONMENT, 'CommitAnchor.json'), 'utf8'));
  const provider = new WebSocketProvider(RPC_URL);
  try {
    const wallet = new Wallet(pk, provider);
    const c = new Contract(dep.address, WRITE_ABI, wallet);

    // ① 앵커
    const commitHashes = Array.from({ length: LEAVES }, (_, i) =>
      keccak256(toUtf8Bytes(`antenna-recovery-demo-batch${BATCH_ID}-commit-${i}`)),
    );
    const levels = buildLevels(commitHashes);
    const root = levels[levels.length - 1][0];
    const existing = await c.rootOf(BATCH_ID);
    if (existing !== '0x' + '0'.repeat(64)) {
      console.error(`batchId ${BATCH_ID} 는 이미 앵커돼 있다(${existing}). --batch-id 를 올려라.`);
      process.exit(1);
    }
    console.log(`① anchor(batchId=${BATCH_ID}, root=${root.slice(0, 18)}…, leaves=${LEAVES}) — 릴레이어 ${wallet.address}`);
    const tx = await c.anchor(BATCH_ID, root, commitHashes, { gasPrice: 0, gasLimit: 5_000_000 });
    const receipt = await tx.wait();
    console.log(`   tx ${tx.hash} · block ${receipt.blockNumber} · gasUsed ${receipt.gasUsed}`);

    // ② 체인만으로 복구 — 위에서 만든 commitHashes·levels 는 일부러 쓰지 않는다
    console.log('② 체인만 읽어 재구축 (DB·로컬 값 사용 안 함)');
    const r = await rebuild(provider, dep.address, BATCH_ID, receipt.blockNumber);
    console.log(`   로그 리프 ${r.commitHashes.length}개 · 재구축 ${r.root.slice(0, 18)}… · rootOf ${r.onchainRoot.slice(0, 18)}… · isIncluded ${r.included}/${r.commitHashes.length}`);
    console.log(r.ok ? '   ✔ 일치' : '   ✘ 불일치');

    // ③ 위조 대조군
    const forged = keccak256(toUtf8Bytes('forged'));
    const forgedOk = await c.isIncluded(BATCH_ID, forged, r.proofs[0]);
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
