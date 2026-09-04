import { keccak256, concat, toUtf8Bytes } from 'ethers';
import fs from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

/**
 * 머클 크로스 검증 픽스처 생성기 (ANT-CHAIN-07).
 *
 * ethers(널리 검증된 keccak 구현)가 기준값을 만들고, 다른 두 구현이 이 값을 재현해야 한다:
 *   - Java  backend/src/test/.../MerkleTreeTest       (web3j Hash.sha3)
 *   - Solidity contracts/test/CommitAnchor.test.js     (CommitAnchor v2 의 온체인 _computeRoot, ANT-CHAIN-08)
 *
 * 세 구현이 한 파일을 공유하므로 해시·결합·승격 규칙이 하나라도 어긋나면
 * 어느 한쪽 테스트가 반드시 깨진다. 이 파일이 규격의 실행 가능한 명세다.
 *
 * ── 규격 (plan.md·MerkleTree.java와 동일해야 한다) ──────────────────
 *   리프      = keccak256(commitHash)            ← 도메인 분리 (입력 32B)
 *   내부 노드 = keccak256(min ‖ max)             ← 정렬 결합 (입력 64B)
 *   홀수 꼬리 = 그대로 승격 (복제 금지)
 *
 * 실행: node scripts/gen-merkle-fixture.mjs
 * 산출: test/fixtures/merkle-cross-fixture.json
 *       ../backend/src/test/resources/merkle/merkle-cross-fixture.json (동일 내용 복사)
 */

const pair = (a, b) => keccak256(a < b ? concat([a, b]) : concat([b, a]));
const leafOf = (commitHash) => keccak256(commitHash);

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
  return levels;
}

function proofOf(levels, leafIndex) {
  const proof = [];
  let index = leafIndex;
  for (let depth = 0; depth < levels.length - 1; depth++) {
    const sibling = index ^ 1;
    if (sibling < levels[depth].length) proof.push(levels[depth][sibling]);
    index = Math.floor(index / 2);
  }
  return proof;
}

// 홀수·경계를 고루 밟는 크기들. 리프는 결정적 문자열의 keccak — 누구나 재생성 가능.
const SIZES = [1, 2, 3, 5, 8, 33];

const cases = SIZES.map((n) => {
  const commitHashes = Array.from({ length: n }, (_, i) =>
    keccak256(toUtf8Bytes(`antenna-fixture-commit-${n}-${i}`)),
  );
  const levels = buildTree(commitHashes);
  const root = levels[levels.length - 1][0];
  const proofs = commitHashes.map((_, i) => proofOf(levels, i));

  // 자체 무결성: 생성기 스스로 접어서 루트가 나오는지 확인하고 내보낸다.
  commitHashes.forEach((c, i) => {
    const folded = proofs[i].reduce(pair, leafOf(c));
    if (folded !== root) throw new Error(`self-check 실패: n=${n} i=${i}`);
  });

  return { leafCount: n, commitHashes, root, proofs };
});

const fixture = {
  spec: {
    hash: 'keccak256',
    leaf: 'keccak256(commitHash) — 도메인 분리',
    pair: 'keccak256(sorted(a,b) 연결) — OpenZeppelin MerkleProof 호환',
    odd: '승격(promote) — 복제 금지',
  },
  generator: 'contracts/scripts/gen-merkle-fixture.mjs (ethers v6 기준값)',
  cases,
};

const here = path.dirname(fileURLToPath(import.meta.url));
const json = JSON.stringify(fixture, null, 2) + '\n';
const out1 = path.resolve(here, '..', 'test', 'fixtures', 'merkle-cross-fixture.json');
const out2 = path.resolve(
  here, '..', '..', 'backend', 'src', 'test', 'resources', 'merkle', 'merkle-cross-fixture.json',
);
for (const out of [out1, out2]) {
  fs.mkdirSync(path.dirname(out), { recursive: true });
  fs.writeFileSync(out, json, 'utf8');
  console.log('wrote', path.relative(process.cwd(), out));
}
