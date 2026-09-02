import { keccak256, concat, toUtf8Bytes, Interface, WebSocketProvider, Wallet, Contract } from 'ethers';
import fs from 'node:fs';
import path from 'node:path';
import vm from 'node:vm';
import { fileURLToPath } from 'node:url';

/**
 * proof 번들 사전 배포 — 실체인 엔드투엔드 데모 (ANT-CHAIN-07).
 *
 * 제안서 §7의 수용 기준을 실제로 밟는다:
 *   ① 커밋 5건으로 머클 트리 구성 → SSAFY 체인의 CommitAnchor에 앵커
 *   ② 리프별 Stage 1 번들(.json) 발급
 *   ③ verify.html의 검증 코어(파일에서 추출)로, 앤테나 서버를 전혀 거치지 않고
 *      체인 조회만으로 5건 전부 검증
 *
 * 실행:
 *   RELAYER_PRIVATE_KEY=0x… node scripts/demo-proof-bundles.mjs [--batch-id N] [--out DIR]
 *
 * 컨트랙트 주소는 deployments/ssafy.json에서 읽는다. 같은 batchId를 두 번 앵커하면
 * BatchAlreadyAnchored로 막히므로(그게 정상이다), 재실행 시 --batch-id를 올려라.
 */

const here = path.dirname(fileURLToPath(import.meta.url));
const args = process.argv.slice(2);
const argOf = (name, fallback) => {
  const i = args.indexOf(name);
  return i >= 0 ? args[i + 1] : fallback;
};
const BATCH_ID = Number(argOf('--batch-id', '2'));
const OUT_DIR = argOf('--out', path.resolve(here, '..', 'demo-bundles'));
const RPC_URL = process.env.CHAIN_RPC_URL || 'wss://ws.ssafy-blockchain.com';

// ── 머클 규격 (MerkleTree.java · gen-merkle-fixture.mjs와 동일) ──────────
const pair = (a, b) => keccak256(a < b ? concat([a, b]) : concat([b, a]));
const leafOf = (c) => keccak256(c);

function buildTree(commitHashes) {
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
  return { levels, root: level[0] };
}
function proofOf(levels, leafIndex) {
  const proof = [];
  let index = leafIndex;
  for (let d = 0; d < levels.length - 1; d++) {
    const sib = index ^ 1;
    if (sib < levels[d].length) proof.push(levels[d][sib]);
    index = Math.floor(index / 2);
  }
  return proof;
}

// ── verify.html에서 검증 코어 추출 — 데모의 검증기는 반드시 "사용자가 쓸 그 코드"다 ──
function loadVerifierCore() {
  const html = fs.readFileSync(
    path.resolve(here, '..', '..', 'frontend', 'public', 'verify.html'),
    'utf8',
  );
  const m = html.match(/<script id="verifier-core">([\s\S]*?)<\/script>/);
  const ctx = { TextEncoder };
  vm.createContext(ctx);
  vm.runInContext(m[1], ctx);
  return ctx.AntennaVerify;
}

async function main() {
  const dep = JSON.parse(fs.readFileSync(path.resolve(here, '..', 'deployments', 'ssafy.json'), 'utf8'));
  const pk = process.env.RELAYER_PRIVATE_KEY;
  if (!pk) {
    console.error('RELAYER_PRIVATE_KEY가 없다 (앵커 tx 서명용).');
    process.exit(1);
  }

  // ① 데모 커밋 5건 → 트리 → 앵커
  const commitHashes = Array.from({ length: 5 }, (_, i) =>
    keccak256(toUtf8Bytes(`antenna-demo-batch${BATCH_ID}-commit-${i}`)),
  );
  const { levels, root } = buildTree(commitHashes);

  const provider = new WebSocketProvider(RPC_URL);
  try {
    const wallet = new Wallet(pk, provider);
    const abi = [
      'function anchor(uint256,bytes32,uint256)',
      'function rootOf(uint256) view returns (bytes32)',
    ];
    const anchorContract = new Contract(dep.address, abi, wallet);

    console.log(`① 트리 구성: 리프 ${commitHashes.length}개 → root ${root}`);
    const existing = await anchorContract.rootOf(BATCH_ID);
    if (existing !== '0x' + '0'.repeat(64)) {
      console.error(`batchId ${BATCH_ID}는 이미 앵커돼 있다(${existing}). --batch-id를 올려라.`);
      process.exit(1);
    }
    const tx = await anchorContract.anchor(BATCH_ID, root, commitHashes.length, {
      gasPrice: 0,
      gasLimit: 300000,
    });
    const receipt = await tx.wait();
    console.log(`   앵커 완료: tx ${tx.hash} · block ${receipt.blockNumber}`);

    // ② Stage 1 번들 발급 (ProofBundle.java와 같은 필드 계약)
    fs.mkdirSync(OUT_DIR, { recursive: true });
    const anchoredAt = new Date().toISOString();
    const files = commitHashes.map((commitHash, i) => {
      const bundle = {
        version: 1,
        chainId: dep.chainId,
        contractAddress: dep.address.toLowerCase(),
        batchId: BATCH_ID,
        txHash: tx.hash,
        anchoredAt,
        commitHash,
        proof: proofOf(levels, i),
      };
      const file = path.join(OUT_DIR, `bundle-batch${BATCH_ID}-leaf${i}.json`);
      fs.writeFileSync(file, JSON.stringify(bundle, null, 2) + '\n', 'utf8');
      return file;
    });
    console.log(`② 번들 ${files.length}건 발급 → ${OUT_DIR}`);

    // ③ 서버 0회 검증 — verify.html의 코어 + 체인 조회만
    const V = loadVerifierCore();
    const iface = new Interface(abi);
    let allOk = true;
    for (const file of files) {
      const b = JSON.parse(fs.readFileSync(file, 'utf8'));
      const invalid = V.validateBundle(b);
      const recovered = V.foldProof(b.commitHash, b.proof);
      // verify.html과 같은 호출을 provider로 재현 (calldata는 코어가 만든 것 그대로)
      const onchain = await provider.call({ to: b.contractAddress, data: V.rootOfCalldata(b.batchId) });
      const ok = !invalid && recovered === onchain;
      allOk = allOk && ok;
      console.log(`   ${path.basename(file)}  ${ok ? '✔' : '✘ FAIL'}  복원 ${recovered.slice(0, 18)}… == 체인 ${onchain.slice(0, 18)}…`);
    }
    // 대조군: 위조 번들은 실패해야 한다
    const forged = { ...JSON.parse(fs.readFileSync(files[0], 'utf8')) };
    forged.commitHash = keccak256(toUtf8Bytes('forged'));
    const forgedRoot = V.foldProof(forged.commitHash, forged.proof);
    const onchainRoot = await provider.call({ to: forged.contractAddress, data: V.rootOfCalldata(BATCH_ID) });
    const forgedBlocked = forgedRoot !== onchainRoot;
    console.log(`   위조 번들 대조군  ${forgedBlocked ? '✔ 정상적으로 실패' : '✘ 위조가 통과했다!!'}`);
    console.log(allOk && forgedBlocked ? '③ 전 건 검증 성공 — 앤테나 서버 접촉 0회' : '③ 실패 있음');
    process.exitCode = allOk && forgedBlocked ? 0 : 1;
  } finally {
    await provider.destroy().catch(() => {});
  }
}

main().catch((e) => {
  console.error(e);
  process.exitCode = 1;
});
