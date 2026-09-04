import { keccak256, concat, WebSocketProvider, Contract, Interface } from 'ethers';
import fs from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

/**
 * 체인만 읽어 앵커 배치를 되살린다 (ANT-CHAIN-08, 수용 기준 4).
 *
 *   node scripts/rebuild-from-chain.mjs --batch-id N [--address 0x…] [--from-block B] [--network ssafy|localhost]
 *   --address: 재배포 뒤 옛 배치를 옛 컨트랙트(anchor_batches.contract_address)에서 조회할 때
 *
 * DB 를 전혀 보지 않는다. 컨트랙트 주소는 deployments/<network>.json 에서, 나머지는 전부 체인에서:
 *   ① eth_getLogs(address, Anchored, batchId)  → 커밋 해시 목록(순서 = 리프 순서)
 *   ② 목록으로 트리 재구축                     → root' · 리프별 proof
 *   ③ root' == rootOf(batchId)                  ← 이게 안 맞으면 규격이 어긋난 것이다
 *   ④ 리프마다 isIncluded(batchId, commitHash, proof) == true
 *
 * 인덱서(ANT-CHAIN-04)의 참조 구현이다. 인덱서는 ①을 커서 기반 폴링으로 돌리고 ②를 Java MerkleTree 로 한다.
 * 이 스크립트가 만드는 proof 는 ProofBundle v1 과 같은 필드로 --out 에 떨굴 수 있다.
 */

const here = path.dirname(fileURLToPath(import.meta.url));
const args = process.argv.slice(2);
const argOf = (name, fallback) => {
  const i = args.indexOf(name);
  return i >= 0 ? args[i + 1] : fallback;
};

const NETWORK = argOf('--network', 'ssafy');
// 재배포 뒤 옛 배치는 옛 주소에 있다(anchor_batches.contract_address). 안 주면 현재 배포 주소.
const ADDRESS = argOf('--address', '');
const BATCH_ID = Number(argOf('--batch-id', '0'));
const FROM_BLOCK = Number(argOf('--from-block', '0'));
const OUT_DIR = argOf('--out', '');
const RPC_URL =
  process.env.CHAIN_RPC_URL || (NETWORK === 'ssafy' ? 'wss://ws.ssafy-blockchain.com' : 'ws://127.0.0.1:8545');

// ── 머클 규격 (MerkleTree.java · CommitAnchor.sol 과 동일) ─────────────
const pair = (a, b) => keccak256(a < b ? concat([a, b]) : concat([b, a]));
const leafOf = (c) => keccak256(c);
export function buildLevels(commitHashes) {
  let level = commitHashes.map(leafOf);
  const levels = [level];
  while (level.length > 1) {
    const next = [];
    for (let i = 0; i < level.length; i += 2) {
      next.push(i + 1 < level.length ? pair(level[i], level[i + 1]) : level[i]);
    }
    levels.push(next);
    level = next;
  }
  return levels;
}
export function proofOf(levels, leafIndex) {
  const proof = [];
  let index = leafIndex;
  for (let d = 0; d < levels.length - 1; d++) {
    const sib = index ^ 1;
    if (sib < levels[d].length) proof.push(levels[d][sib]);
    index = Math.floor(index / 2);
  }
  return proof;
}

export const ABI = [
  'function rootOf(uint256) view returns (bytes32)',
  'function isIncluded(uint256,bytes32,bytes32[]) view returns (bool)',
  'event Anchored(uint256 indexed batchId, bytes32 merkleRoot, bytes32[] commitHashes)',
];

/**
 * @returns {{ ok: boolean, root: string, onchainRoot: string, commitHashes: string[], proofs: string[][], txHash: string, blockNumber: number }}
 */
export async function rebuild(provider, address, batchId, fromBlock = 0) {
  const c = new Contract(address, ABI, provider);
  const iface = new Interface(ABI);
  const topic = iface.getEvent('Anchored').topicHash;

  // ① 로그. batchId 가 indexed 라 topic 으로 바로 좁힌다.
  const logs = await provider.getLogs({
    address,
    fromBlock,
    toBlock: 'latest',
    topics: [topic, iface.encodeFilterTopics('Anchored', [batchId])[1]],
  });
  if (logs.length === 0) {
    return { ok: false, reason: `batchId ${batchId} 의 Anchored 로그가 없다 (fromBlock ${fromBlock})` };
  }
  // 덮어쓰기가 없으므로 로그는 정확히 1건이어야 한다.
  const log = logs[0];
  const parsed = iface.parseLog(log);
  const commitHashes = [...parsed.args.commitHashes];
  const emittedRoot = parsed.args.merkleRoot;

  // ② 재구축
  const levels = buildLevels(commitHashes);
  const root = levels[levels.length - 1][0];
  const proofs = commitHashes.map((_, i) => proofOf(levels, i));

  // ③ 체인 루트와 대조
  const onchainRoot = await c.rootOf(batchId);

  // ④ 포함 증명 전건
  let included = 0;
  for (let i = 0; i < commitHashes.length; i++) {
    if (await c.isIncluded(batchId, commitHashes[i], proofs[i])) included++;
  }

  return {
    ok: root === onchainRoot && emittedRoot === onchainRoot && included === commitHashes.length,
    root,
    emittedRoot,
    onchainRoot,
    commitHashes,
    proofs,
    included,
    txHash: log.transactionHash,
    blockNumber: log.blockNumber,
  };
}

async function main() {
  if (!BATCH_ID) {
    console.error('--batch-id N 이 필요하다.');
    process.exit(1);
  }
  const dep = JSON.parse(fs.readFileSync(path.resolve(here, '..', 'deployments', `${NETWORK}.json`), 'utf8'));
  if (ADDRESS) {
    // 옛 주소 조회: 배포 블록을 모르니 --from-block 이 없으면 0부터(SSAFY 는 전 구간 getLogs 가 된다, 검증정보 §3.1).
    dep.address = ADDRESS;
    dep.blockNumber = 0;
  }
  const provider = new WebSocketProvider(RPC_URL);
  try {
    const r = await rebuild(provider, dep.address, BATCH_ID, FROM_BLOCK || dep.blockNumber || 0);
    if (!r.ok && r.reason) {
      console.error(r.reason);
      process.exitCode = 1;
      return;
    }
    console.log(`batchId ${BATCH_ID} @ ${dep.address} (${NETWORK})`);
    console.log(`  로그      tx ${r.txHash} · block ${r.blockNumber} · 리프 ${r.commitHashes.length}개`);
    console.log(`  재구축    ${r.root}`);
    console.log(`  이벤트    ${r.emittedRoot}`);
    console.log(`  rootOf    ${r.onchainRoot}`);
    console.log(`  isIncluded ${r.included}/${r.commitHashes.length}`);
    console.log(r.ok ? '  ✔ 체인만으로 재구축한 트리가 온체인 루트와 일치한다' : '  ✘ 불일치');

    if (OUT_DIR) {
      fs.mkdirSync(OUT_DIR, { recursive: true });
      const block = await provider.getBlock(r.blockNumber);
      r.commitHashes.forEach((commitHash, i) => {
        const bundle = {
          version: 1,
          chainId: dep.chainId,
          contractAddress: dep.address.toLowerCase(),
          batchId: BATCH_ID,
          txHash: r.txHash,
          anchoredAt: new Date(Number(block.timestamp) * 1000).toISOString(),
          commitHash,
          proof: r.proofs[i],
        };
        fs.writeFileSync(path.join(OUT_DIR, `bundle-batch${BATCH_ID}-leaf${i}.json`), JSON.stringify(bundle, null, 2) + '\n');
      });
      console.log(`  번들 ${r.commitHashes.length}건 → ${OUT_DIR}`);
    }
    process.exitCode = r.ok ? 0 : 1;
  } finally {
    await provider.destroy().catch(() => {});
  }
}

// import 될 때(demo-recovery)는 main 을 돌리지 않는다.
if (process.argv[1] && path.resolve(process.argv[1]) === fileURLToPath(import.meta.url)) {
  main().catch((e) => {
    console.error(e);
    process.exitCode = 1;
  });
}
