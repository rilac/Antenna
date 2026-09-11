const { expect } = require('chai');
const { ethers } = require('hardhat');
const fs = require('fs');
const path = require('path');

/**
 * CommitAnchor v3 단위 테스트 (ANT-CHAIN-13 — 저장 칸의 키 = 머클루트).
 *
 * ── 이 파일의 규칙: revert 는 "실패했다"로 세지 않는다 ──────────────────
 * 반드시 revertedWithCustomError 로 **에러 이름까지** 단언한다.
 * ANT-CHAIN-01 수동 검증 때 "리버트했으니 통과"로 셌다가 3케이스가 권한 에러로 위장 통과한
 * 전력이 있다. (docs-personal/CHAIN-onchain-backend/토큰증권_체인접속_검증정보.md §4)
 *
 * ── 머클 규격의 단일 진실은 test/fixtures/merkle-cross-fixture.json 이다 ──
 * ethers 가 만든 기준값을 Java(MerkleTreeTest)와 이 파일(Solidity)이 각자 재현한다.
 * 세 구현 중 하나라도 해시·결합·승격 규칙이 어긋나면 어느 한쪽 테스트가 반드시 깨진다.
 */

const { keccak256, concat, toUtf8Bytes, ZeroHash } = ethers;

// 픽스처 생성기·MerkleTree.java 와 같은 규격의 JS 참조 구현. 테스트 안에서 proof 를 만들 때 쓴다.
const pair = (a, b) => keccak256(a < b ? concat([a, b]) : concat([b, a]));
const leafOf = (c) => keccak256(c);
function buildLevels(commitHashes) {
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
  for (let d = 0; d < levels.length - 1; d++) {
    const sib = index ^ 1;
    if (sib < levels[d].length) proof.push(levels[d][sib]);
    index = Math.floor(index / 2);
  }
  return proof;
}
const rootOfList = (commitHashes) => {
  const levels = buildLevels(commitHashes);
  return levels[levels.length - 1][0];
};
const commits = (n, tag = 'c') =>
  Array.from({ length: n }, (_, i) => keccak256(toUtf8Bytes(`${tag}-${i}`)));

const FIXTURE = JSON.parse(
  fs.readFileSync(path.join(__dirname, 'fixtures', 'merkle-cross-fixture.json'), 'utf8'),
);

describe('CommitAnchor v3', function () {
  let anchorContract;
  let admin; // DEFAULT_ADMIN_ROLE 만
  let relayer; // ANCHOR_ROLE 만
  let outsider; // 아무 롤도 없음
  let ANCHOR_ROLE;
  let DEFAULT_ADMIN_ROLE;

  const THREE = commits(3);
  const THREE_ROOT = rootOfList(THREE);

  beforeEach(async function () {
    [admin, relayer, outsider] = await ethers.getSigners();
    const factory = await ethers.getContractFactory('CommitAnchor');
    anchorContract = await factory.deploy(admin.address, relayer.address);
    await anchorContract.waitForDeployment();
    ANCHOR_ROLE = await anchorContract.ANCHOR_ROLE();
    DEFAULT_ADMIN_ROLE = await anchorContract.DEFAULT_ADMIN_ROLE();
  });

  const asRelayer = () => anchorContract.connect(relayer);

  describe('배포 — 역할 분리', function () {
    it('admin 은 관리자 롤만, relayer 는 앵커 롤만 갖는다', async function () {
      expect(await anchorContract.hasRole(DEFAULT_ADMIN_ROLE, admin.address)).to.equal(true);
      expect(await anchorContract.hasRole(ANCHOR_ROLE, admin.address)).to.equal(false);
      expect(await anchorContract.hasRole(ANCHOR_ROLE, relayer.address)).to.equal(true);
      expect(await anchorContract.hasRole(DEFAULT_ADMIN_ROLE, relayer.address)).to.equal(false);
    });

    it('관리자는 앵커를 못 한다 — 관리자 키가 서버 밖에 있어도 되는 이유', async function () {
      await expect(anchorContract.connect(admin).anchor(THREE_ROOT, THREE))
        .to.be.revertedWithCustomError(anchorContract, 'AccessControlUnauthorizedAccount')
        .withArgs(admin.address, ANCHOR_ROLE);
    });

    it('같은 주소를 둘 다 넘기면 두 롤을 다 갖는다 (로컬 개발용)', async function () {
      const factory = await ethers.getContractFactory('CommitAnchor');
      const c = await factory.deploy(admin.address, admin.address);
      await c.waitForDeployment();
      expect(await c.hasRole(ANCHOR_ROLE, admin.address)).to.equal(true);
      expect(await c.hasRole(DEFAULT_ADMIN_ROLE, admin.address)).to.equal(true);
    });
  });

  describe('1. 정상 앵커 + 이벤트', function () {
    it('Anchored 이벤트가 root·commitHashes 를 순서 그대로 싣는다', async function () {
      await expect(asRelayer().anchor(THREE_ROOT, THREE))
        .to.emit(anchorContract, 'Anchored')
        .withArgs(THREE_ROOT, THREE);
    });

    it('anchoredAt(root) 은 루트가 박힌 블록 번호다', async function () {
      const tx = await asRelayer().anchor(THREE_ROOT, THREE);
      const receipt = await tx.wait();
      expect(await anchorContract.anchoredAt(THREE_ROOT)).to.equal(BigInt(receipt.blockNumber));
    });

    it('리프 1개면 root = keccak256(commitHash) 다', async function () {
      const one = commits(1);
      expect(rootOfList(one)).to.equal(leafOf(one[0]));
      await expect(asRelayer().anchor(leafOf(one[0]), one)).to.emit(anchorContract, 'Anchored');
    });
  });

  describe('2. 크로스 픽스처 — Solidity 루트가 ethers·Java 기준값과 같다', function () {
    for (const c of FIXTURE.cases) {
      it(`N=${c.leafCount}: 픽스처 root 로 앵커가 성공한다`, async function () {
        await expect(asRelayer().anchor(c.root, c.commitHashes))
          .to.emit(anchorContract, 'Anchored')
          .withArgs(c.root, c.commitHashes);
        expect(await anchorContract.anchoredAt(c.root)).to.be.greaterThan(0n);
      });

      it(`N=${c.leafCount}: 픽스처 proof 전부가 isIncluded 를 통과한다`, async function () {
        await asRelayer().anchor(c.root, c.commitHashes);
        for (let i = 0; i < c.leafCount; i++) {
          expect(
            await anchorContract.isIncluded(c.root, c.commitHashes[i], c.proofs[i]),
            `leaf ${i}`,
          ).to.equal(true);
        }
      });
    }

    it('리프 순서가 바뀌면 다른 루트다 — 서버는 순서를 결정적으로 넘겨야 한다', async function () {
      const c = FIXTURE.cases.find((x) => x.leafCount === 5);
      const shuffled = [...c.commitHashes].reverse();
      await expect(asRelayer().anchor(c.root, shuffled))
        .to.be.revertedWithCustomError(anchorContract, 'RootMismatch')
        .withArgs(c.root, rootOfList(shuffled));
    });
  });

  describe('3. RootMismatch — 서버 계산과 온체인 계산의 대조', function () {
    it('틀린 루트를 주면 expected(서버값)·computed(컨트랙트값)를 모두 실어 revert', async function () {
      const wrong = '0x' + '11'.repeat(32);
      await expect(asRelayer().anchor(wrong, THREE))
        .to.be.revertedWithCustomError(anchorContract, 'RootMismatch')
        .withArgs(wrong, THREE_ROOT);
    });

    it('도메인 분리 없이(리프=commitHash 그대로) 계산한 루트는 거부된다', async function () {
      // 규격 위반 서버 구현을 잡는 테스트. 리프에 keccak 을 한 겹 더 얹지 않은 루트.
      const noDomain = (() => {
        let level = [...THREE];
        while (level.length > 1) {
          const next = [];
          for (let i = 0; i < level.length; i += 2)
            next.push(i + 1 < level.length ? pair(level[i], level[i + 1]) : level[i]);
          level = next;
        }
        return level[0];
      })();
      expect(noDomain).to.not.equal(THREE_ROOT);
      await expect(asRelayer().anchor(noDomain, THREE)).to.be.revertedWithCustomError(
        anchorContract,
        'RootMismatch',
      );
    });

    it('홀수 꼬리를 복제(Bitcoin 식)한 루트는 거부된다', async function () {
      const dup = (() => {
        let level = THREE.map(leafOf);
        while (level.length > 1) {
          const next = [];
          for (let i = 0; i < level.length; i += 2)
            next.push(pair(level[i], i + 1 < level.length ? level[i + 1] : level[i]));
          level = next;
        }
        return level[0];
      })();
      expect(dup).to.not.equal(THREE_ROOT);
      await expect(asRelayer().anchor(dup, THREE)).to.be.revertedWithCustomError(
        anchorContract,
        'RootMismatch',
      );
    });

    it('RootMismatch 로 실패하면 아무것도 저장되지 않는다', async function () {
      const wrong = '0x' + '11'.repeat(32);
      await expect(asRelayer().anchor(wrong, THREE)).to.be.reverted;
      expect(await anchorContract.anchoredAt(wrong)).to.equal(0n);
      expect(await anchorContract.anchoredAt(THREE_ROOT)).to.equal(0n);
      // 올바른 루트로 다시 보내면 된다 — 실패가 칸을 태우지 않는다.
      await expect(asRelayer().anchor(THREE_ROOT, THREE)).to.emit(anchorContract, 'Anchored');
    });
  });

  describe('4. isIncluded — 포함 증명', function () {
    let levels;
    beforeEach(async function () {
      levels = buildLevels(THREE);
      await asRelayer().anchor(THREE_ROOT, THREE);
    });

    it('올바른 proof 는 true', async function () {
      for (let i = 0; i < THREE.length; i++) {
        expect(await anchorContract.isIncluded(THREE_ROOT, THREE[i], proofOf(levels, i))).to.equal(true);
      }
    });

    it('다른 커밋 해시는 false', async function () {
      const forged = keccak256(toUtf8Bytes('forged'));
      expect(await anchorContract.isIncluded(THREE_ROOT, forged, proofOf(levels, 0))).to.equal(false);
    });

    it('내부 노드를 커밋으로 위장한 2차 프리이미지 위조는 false (도메인 분리)', async function () {
      // 리프 0·1 의 부모 노드를 "내 커밋"이라 주장하고 그 형제(승격된 리프 2)를 proof 로 낸다.
      const parent01 = levels[1][0];
      const proofForParent = [levels[1][1]];
      expect(pair(parent01, levels[1][1])).to.equal(THREE_ROOT);
      expect(await anchorContract.isIncluded(THREE_ROOT, parent01, proofForParent)).to.equal(false);
    });

    it('proof 순서가 틀리면 false', async function () {
      const p = proofOf(levels, 0);
      if (p.length >= 2) {
        expect(await anchorContract.isIncluded(THREE_ROOT, THREE[0], [...p].reverse())).to.equal(false);
      }
    });

    it('앵커되지 않은 루트는 올바르게 접히는 proof 로도 false — 체인에 박힌 루트만 증명이 된다', async function () {
      const other = commits(2, 'unanchored');
      const otherLevels = buildLevels(other);
      const otherRoot = rootOfList(other);
      expect(await anchorContract.isIncluded(otherRoot, other[0], proofOf(otherLevels, 0))).to.equal(false);
    });
  });

  describe('5. 복구 — DB 없이 이벤트만으로 트리를 되살린다', function () {
    it('Anchored 로그의 commitHashes 로 재구축한 root·proof 가 체인과 일치한다', async function () {
      const c = FIXTURE.cases.find((x) => x.leafCount === 33);
      await asRelayer().anchor(c.root, c.commitHashes);

      // 인덱서가 하는 일: 주소 + 루트 토픽으로 로그를 읽는다. DB 는 없다.
      const logs = await anchorContract.queryFilter(anchorContract.filters.Anchored(c.root));
      expect(logs).to.have.lengthOf(1);
      const recovered = logs[0].args.commitHashes;
      expect(recovered).to.deep.equal(c.commitHashes);

      const levels = buildLevels([...recovered]);
      const rebuiltRoot = levels[levels.length - 1][0];
      expect(rebuiltRoot).to.equal(c.root);
      expect(await anchorContract.anchoredAt(rebuiltRoot)).to.be.greaterThan(0n);
      for (let i = 0; i < recovered.length; i++) {
        expect(proofOf(levels, i)).to.deep.equal(c.proofs[i]);
        expect(await anchorContract.isIncluded(c.root, recovered[i], proofOf(levels, i))).to.equal(true);
      }
    });
  });

  describe('6. 칸의 키가 루트다 — v2 의 batchId 충돌이 사라진다', function () {
    it('같은 루트 재전송은 AlreadyAnchored 로 막고 루트를 인자로 알려준다 (서버는 성공으로 본다)', async function () {
      await asRelayer().anchor(THREE_ROOT, THREE);
      await expect(asRelayer().anchor(THREE_ROOT, THREE))
        .to.be.revertedWithCustomError(anchorContract, 'AlreadyAnchored')
        .withArgs(THREE_ROOT);
    });

    it('내용이 다른 두 배치는 칸이 달라 둘 다 박힌다 — DB 가 여러 개여도, 초기화돼도 부딪히지 않는다', async function () {
      // v2 에서는 두 DB 가 같은 batchId 1 을 쓰면 두 번째가 남의 칸을 만났다. v3 에는 번호가 없다.
      const dbA = commits(3, 'db-a');
      const dbB = commits(3, 'db-b');
      await expect(asRelayer().anchor(rootOfList(dbA), dbA)).to.emit(anchorContract, 'Anchored');
      await expect(asRelayer().anchor(rootOfList(dbB), dbB)).to.emit(anchorContract, 'Anchored');
      expect(await anchorContract.anchoredAt(rootOfList(dbA))).to.be.greaterThan(0n);
      expect(await anchorContract.anchoredAt(rootOfList(dbB))).to.be.greaterThan(0n);
    });

    it('재전송이 막혀도 첫 앵커의 블록 번호는 그대로다 — 덮어쓰기 없음', async function () {
      const tx = await asRelayer().anchor(THREE_ROOT, THREE);
      const first = (await tx.wait()).blockNumber;
      await expect(asRelayer().anchor(THREE_ROOT, THREE)).to.be.reverted;
      expect(await anchorContract.anchoredAt(THREE_ROOT)).to.equal(BigInt(first));
    });
  });

  describe('7. 무권한 · 빈 값', function () {
    it('롤 없는 주소는 AccessControlUnauthorizedAccount', async function () {
      await expect(anchorContract.connect(outsider).anchor(THREE_ROOT, THREE))
        .to.be.revertedWithCustomError(anchorContract, 'AccessControlUnauthorizedAccount')
        .withArgs(outsider.address, ANCHOR_ROLE);
    });

    it('merkleRoot 가 0 이면 EmptyRoot — 배열이 비었어도 이게 먼저다', async function () {
      await expect(asRelayer().anchor(ZeroHash, [])).to.be.revertedWithCustomError(
        anchorContract,
        'EmptyRoot',
      );
    });

    it('commitHashes 가 빈 배열이면 EmptyCommitCount — RootMismatch 가 아니다', async function () {
      await expect(asRelayer().anchor(THREE_ROOT, [])).to.be.revertedWithCustomError(
        anchorContract,
        'EmptyCommitCount',
      );
    });
  });

  describe('8. 롤 부여 · 회수 (릴레이어 키 교체 경로)', function () {
    it('관리자가 새 릴레이어에 부여하면 호출할 수 있다', async function () {
      await anchorContract.connect(admin).grantRole(ANCHOR_ROLE, outsider.address);
      await expect(anchorContract.connect(outsider).anchor(THREE_ROOT, THREE)).to.emit(
        anchorContract,
        'Anchored',
      );
    });

    it('회수하면 다시 막힌다 — 재배포 없이 키를 교체한다', async function () {
      await anchorContract.connect(admin).revokeRole(ANCHOR_ROLE, relayer.address);
      await expect(asRelayer().anchor(THREE_ROOT, THREE))
        .to.be.revertedWithCustomError(anchorContract, 'AccessControlUnauthorizedAccount')
        .withArgs(relayer.address, ANCHOR_ROLE);
    });

    it('롤을 회수해도 이미 박힌 루트는 남는다', async function () {
      await asRelayer().anchor(THREE_ROOT, THREE);
      await anchorContract.connect(admin).revokeRole(ANCHOR_ROLE, relayer.address);
      expect(await anchorContract.anchoredAt(THREE_ROOT)).to.be.greaterThan(0n);
    });

    it('릴레이어는 롤을 나눠 주지 못한다 — 서버 키 유출의 피해 상한', async function () {
      await expect(asRelayer().grantRole(ANCHOR_ROLE, outsider.address))
        .to.be.revertedWithCustomError(anchorContract, 'AccessControlUnauthorizedAccount')
        .withArgs(relayer.address, DEFAULT_ADMIN_ROLE);
    });
  });

  describe('9. 미앵커 조회', function () {
    it('없는 루트는 revert 하지 않고 0 을 준다', async function () {
      expect(await anchorContract.anchoredAt(keccak256(toUtf8Bytes('nothing')))).to.equal(0n);
      expect(await anchorContract.anchoredAt(ZeroHash)).to.equal(0n);
    });
  });

  describe('10. 큰 배치', function () {
    it('리프 500개도 한 tx 로 앵커되고 복구된다', async function () {
      const big = commits(500, 'big');
      const root = rootOfList(big);
      const tx = await asRelayer().anchor(root, big);
      const receipt = await tx.wait();
      // 가스는 판단 근거가 아니지만(gasPrice 0), 로컬 30M 한도 안이라는 건 확인해 둔다.
      expect(receipt.gasUsed).to.be.lessThan(30_000_000n);
      const logs = await anchorContract.queryFilter(anchorContract.filters.Anchored(root));
      expect(logs[0].args.commitHashes).to.have.lengthOf(500);
      expect(rootOfList([...logs[0].args.commitHashes])).to.equal(root);
    });
  });
});
