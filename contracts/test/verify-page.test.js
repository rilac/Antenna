const { expect } = require('chai');
const fs = require('fs');
const path = require('path');
const vm = require('vm');
const { keccak256, randomBytes, toUtf8Bytes, Interface } = require('ethers');

/**
 * verify.html 크로스 검증 (ANT-CHAIN-07).
 *
 * verify.html은 외부 의존이 0이어야 해서(keccak 인라인) 라이브러리 대신 직접 구현이 들어
 * 있다. 직접 구현은 조용히 틀릴 수 있으므로, 여기서 그 파일의 <script id="verifier-core">
 * 블록을 그대로 추출해 실행하고 ethers(널리 검증된 구현)·공유 픽스처와 대조한다.
 *
 * 이 테스트가 지키는 계약:
 *   ① 인라인 keccak == ethers keccak (패딩 경계 포함)
 *   ② verify.html의 루트 복원 == 픽스처(ethers 생성) == Java MerkleTree (같은 픽스처를 쓰므로)
 *   ③ eth_call 데이터 인코딩 == ethers Interface 인코딩
 *   ④ 내부 노드 위장(2차 프리이미지)이 도메인 분리로 막힌다
 */
function loadVerifierCore() {
  const html = fs.readFileSync(
    path.resolve(__dirname, '..', '..', 'frontend', 'public', 'verify.html'),
    'utf8',
  );
  const m = html.match(/<script id="verifier-core">([\s\S]*?)<\/script>/);
  if (!m) throw new Error('verify.html에서 verifier-core 블록을 찾지 못했다');
  const context = { TextEncoder };
  vm.createContext(context);
  vm.runInContext(m[1], context);
  return context.AntennaVerify;
}

const fixture = JSON.parse(
  fs.readFileSync(path.resolve(__dirname, 'fixtures', 'merkle-cross-fixture.json'), 'utf8'),
);

describe('verify.html — 검증 코어 크로스 체크', function () {
  const V = loadVerifierCore();

  it('① 인라인 keccak256이 ethers와 일치한다 (0~500바이트, 패딩 경계 포함)', function () {
    for (const len of [0, 1, 31, 32, 33, 64, 135, 136, 137, 271, 272, 500]) {
      const input = randomBytes(len);
      expect(V.keccak256(input), `len=${len}`).to.equal(keccak256(input));
    }
    // 고정 벡터 하나 — 난수만으로는 "우연히 둘 다 틀림"을 못 잡는다
    expect(V.keccak256(toUtf8Bytes('abc'))).to.equal(
      '0x4e03657aea45a94fc7d47ba826c8d667c0d1e6e33a64a036ec44f58fa12d6c45',
    );
  });

  it('② 모든 픽스처 케이스에서 foldProof가 루트를 재현한다', function () {
    for (const c of fixture.cases) {
      c.commitHashes.forEach((commit, i) => {
        expect(V.foldProof(commit, c.proofs[i]), `n=${c.leafCount} leaf=${i}`).to.equal(c.root);
      });
    }
  });

  it('② 변조된 커밋·남의 proof는 루트가 어긋난다', function () {
    const c = fixture.cases.find((x) => x.leafCount === 5);
    const forged = keccak256(toUtf8Bytes('tampered'));
    expect(V.foldProof(forged, c.proofs[0])).to.not.equal(c.root);
    expect(V.foldProof(c.commitHashes[0], c.proofs[1])).to.not.equal(c.root);
  });

  it('③ rootOf 호출 데이터가 ethers Interface 인코딩과 일치한다', function () {
    const iface = new Interface(['function rootOf(uint256) view returns (bytes32)']);
    for (const id of [1, 7, 42, 2 ** 31]) {
      expect(V.rootOfCalldata(id)).to.equal(iface.encodeFunctionData('rootOf', [id]));
    }
  });

  it('④ 내부 노드를 커밋으로 위장한 가짜 증명이 막힌다 (도메인 분리)', function () {
    // n=2 케이스: root = pair(leaf0, leaf1). 공격자가 "commitHash = leaf0(내부/리프 노드 값)"
    // 이라 주장하며 proof=[leaf1]을 내면, 도메인 분리가 없으면 정확히 루트가 나온다.
    const c = fixture.cases.find((x) => x.leafCount === 2);
    const leaf0 = V.leafOf(c.commitHashes[0]);
    const leaf1 = V.leafOf(c.commitHashes[1]);
    expect(V.pairHash(leaf0, leaf1)).to.equal(c.root); // 트리 구조 확인
    // 위장 시도: 노드 값 leaf0을 commitHash 자리에 넣는다 → 검증기는 keccak(leaf0)에서 시작해 실패
    expect(V.foldProof(leaf0, [leaf1])).to.not.equal(c.root);
  });

  it('번들 형식 검사가 필수 필드를 강제한다', function () {
    const good = {
      version: 1,
      chainId: 31221,
      contractAddress: '0x9eec7fb9c14bc248b2a1f5b0829c1a1f098d45df',
      batchId: 2,
      commitHash: fixture.cases[0].commitHashes[0],
      proof: [],
    };
    expect(V.validateBundle(good)).to.equal(null);
    expect(V.validateBundle({ ...good, version: 2 })).to.be.a('string');
    expect(V.validateBundle({ ...good, batchId: 0 })).to.be.a('string');
    expect(V.validateBundle({ ...good, commitHash: '0x123' })).to.be.a('string');
    expect(V.validateBundle({ ...good, proof: ['0xzz'] })).to.be.a('string');
  });
});
