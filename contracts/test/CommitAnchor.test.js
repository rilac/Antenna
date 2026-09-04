const { expect } = require('chai');
const { ethers } = require('hardhat');
const fs = require('fs');
const path = require('path');

/**
 * CommitAnchor v2 단위 테스트 (ANT-CHAIN-08).
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

describe('CommitAnchor v2', function () {
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
      await expect(anchorContract.connect(admin).anchor(1, THREE_ROOT, THREE))
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
    it('Anchored 이벤트가 batchId·root·commitHashes 를 순서 그대로 싣는다', async function () {
      await expect(asRelayer().anchor(1, THREE_ROOT, THREE))
        .to.emit(anchorContract, 'Anchored')
        .withArgs(1n, THREE_ROOT, THREE);
    });

    it('batchId 는 연속일 필요가 없다 — DB 시퀀스가 갭을 낼 수 있다', async function () {
      await asRelayer().anchor(1, THREE_ROOT, THREE);
      const other = commits(2, 'd');
      await expect(asRelayer().anchor(999, rootOfList(other), other))
        .to.emit(anchorContract, 'Anchored')
        .withArgs(999n, rootOfList(other), other);
    });

    it('리프 1개면 root = keccak256(commitHash) 다', async function () {
      const one = commits(1);
      expect(rootOfList(one)).to.equal(leafOf(one[0]));
      await expect(asRelayer().anchor(1, leafOf(one[0]), one)).to.emit(anchorContract, 'Anchored');
    });
  });

  describe('2. 크로스 픽스처 — Solidity 루트가 ethers·Java 기준값과 같다', function () {
    for (const c of FIXTURE.cases) {
      it(`N=${c.leafCount}: 픽스처 root 로 앵커가 성공한다`, async function () {
        await expect(asRelayer().anchor(c.leafCount, c.root, c.commitHashes))
          .to.emit(anchorContract, 'Anchored')
          .withArgs(BigInt(c.leafCount), c.root, c.commitHashes);
        expect(await anchorContract.rootOf(c.leafCount)).to.equal(c.root);
      });

      it(`N=${c.leafCount}: 픽스처 proof 전부가 isIncluded 를 통과한다`, async function () {
        await asRelayer().anchor(c.leafCount, c.root, c.commitHashes);
        for (let i = 0; i < c.leafCount; i++) {
          expect(
            await anchorContract.isIncluded(c.leafCount, c.commitHashes[i], c.proofs[i]),
            `leaf ${i}`,
          ).to.equal(true);
        }
      });
    }

    it('리프 순서가 바뀌면 다른 루트다 — 서버는 순서를 결정적으로 넘겨야 한다', async function () {
      const c = FIXTURE.cases.find((x) => x.leafCount === 5);
      const shuffled = [...c.commitHashes].reverse();
      await expect(asRelayer().anchor(5, c.root, shuffled))
        .to.be.revertedWithCustomError(anchorContract, 'RootMismatch')
        .withArgs(c.root, rootOfList(shuffled));
    });
  });

  describe('3. RootMismatch — 서버 계산과 온체인 계산의 대조', function () {
    it('틀린 루트를 주면 expected(서버값)·computed(컨트랙트값)를 모두 실어 revert', async function () {
      const wrong = '0x' + '11'.repeat(32);
      await expect(asRelayer().anchor(1, wrong, THREE))
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
      await expect(asRelayer().anchor(1, noDomain, THREE)).to.be.revertedWithCustomError(
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
      await expect(asRelayer().anchor(1, dup, THREE)).to.be.revertedWithCustomError(
        anchorContract,
        'RootMismatch',
      );
    });

    it('RootMismatch 로 실패하면 아무것도 저장되지 않는다', async function () {
      await expect(asRelayer().anchor(1, '0x' + '11'.repeat(32), THREE)).to.be.reverted;
      expect(await anchorContract.rootOf(1)).to.equal(ZeroHash);
      // 같은 batchId 로 올바른 루트를 다시 보내면 된다 — 실패가 배치를 태우지 않는다.
      await expect(asRelayer().anchor(1, THREE_ROOT, THREE)).to.emit(anchorContract, 'Anchored');
    });
  });

  describe('4. isIncluded — 포함 증명', function () {
    let levels;
    beforeEach(async function () {
      levels = buildLevels(THREE);
      await asRelayer().anchor(1, THREE_ROOT, THREE);
    });

    it('올바른 proof 는 true', async function () {
      for (let i = 0; i < THREE.length; i++) {
        expect(await anchorContract.isIncluded(1, THREE[i], proofOf(levels, i))).to.equal(true);
      }
    });

    it('다른 커밋 해시는 false', async function () {
      const forged = keccak256(toUtf8Bytes('forged'));
      expect(await anchorContract.isIncluded(1, forged, proofOf(levels, 0))).to.equal(false);
    });

    it('내부 노드를 커밋으로 위장한 2차 프리이미지 위조는 false (도메인 분리)', async function () {
      // 리프 0·1 의 부모 노드를 "내 커밋"이라 주장하고 그 형제(승격된 리프 2)를 proof 로 낸다.
      // 리프 해시를 한 겹 더 얹지 않는 규격이었다면 이 조합이 통과한다.
      const parent01 = levels[1][0];
      const proofForParent = [levels[1][1]];
      // 도메인 분리가 없다면 pair(parent01, leaf2) == root 라 통과했을 것.
      expect(pair(parent01, levels[1][1])).to.equal(THREE_ROOT);
      expect(await anchorContract.isIncluded(1, parent01, proofForParent)).to.equal(false);
    });

    it('proof 순서가 틀리면 false', async function () {
      const p = proofOf(levels, 0);
      if (p.length >= 2) {
        expect(await anchorContract.isIncluded(1, THREE[0], [...p].reverse())).to.equal(false);
      }
    });

    it('앵커되지 않은 batchId 는 어떤 proof 로도 false', async function () {
      expect(await anchorContract.isIncluded(2, THREE[0], proofOf(levels, 0))).to.equal(false);
      expect(await anchorContract.isIncluded(2, THREE[0], [])).to.equal(false);
    });
  });

  describe('5. 복구 — DB 없이 이벤트만으로 트리를 되살린다 (AC4)', function () {
    it('Anchored 로그의 commitHashes 로 재구축한 root·proof 가 체인과 일치한다', async function () {
      const c = FIXTURE.cases.find((x) => x.leafCount === 33);
      await asRelayer().anchor(7, c.root, c.commitHashes);

      // 인덱서가 하는 일: 주소 + batchId 토픽으로 로그를 읽는다. DB 는 없다.
      const logs = await anchorContract.queryFilter(anchorContract.filters.Anchored(7));
      expect(logs).to.have.lengthOf(1);
      const recovered = logs[0].args.commitHashes;
      expect(recovered).to.deep.equal(c.commitHashes);

      const levels = buildLevels([...recovered]);
      const rebuiltRoot = levels[levels.length - 1][0];
      expect(rebuiltRoot).to.equal(await anchorContract.rootOf(7));
      for (let i = 0; i < recovered.length; i++) {
        expect(proofOf(levels, i)).to.deep.equal(c.proofs[i]);
        expect(await anchorContract.isIncluded(7, recovered[i], proofOf(levels, i))).to.equal(true);
      }
    });
  });

  describe('6. 같은 batchId 재전송 (릴레이어 재시도 방어)', function () {
    it('BatchAlreadyAnchored 로 막고 batchId 를 인자로 알려준다', async function () {
      await asRelayer().anchor(7, THREE_ROOT, THREE);
      await expect(asRelayer().anchor(7, THREE_ROOT, THREE))
        .to.be.revertedWithCustomError(anchorContract, 'BatchAlreadyAnchored')
        .withArgs(7n);
    });

    it('다른 내용으로 재전송해도 첫 앵커는 그대로 살아 있다', async function () {
      await asRelayer().anchor(7, THREE_ROOT, THREE);
      const other = commits(2, 'd');
      await expect(asRelayer().anchor(7, rootOfList(other), other)).to.be.reverted;
      expect(await anchorContract.rootOf(7)).to.equal(THREE_ROOT);
    });
  });

  describe('7. 무권한 · 빈 값 · batchId 0', function () {
    it('롤 없는 주소는 AccessControlUnauthorizedAccount', async function () {
      await expect(anchorContract.connect(outsider).anchor(1, THREE_ROOT, THREE))
        .to.be.revertedWithCustomError(anchorContract, 'AccessControlUnauthorizedAccount')
        .withArgs(outsider.address, ANCHOR_ROLE);
    });

    it('merkleRoot 가 0 이면 EmptyRoot', async function () {
      await expect(asRelayer().anchor(1, ZeroHash, THREE)).to.be.revertedWithCustomError(
        anchorContract,
        'EmptyRoot',
      );
    });

    it('commitHashes 가 빈 배열이면 EmptyCommitCount — RootMismatch 가 아니다', async function () {
      await expect(asRelayer().anchor(1, THREE_ROOT, [])).to.be.revertedWithCustomError(
        anchorContract,
        'EmptyCommitCount',
      );
    });

    it('batchId 0 은 InvalidBatchId — 루트·배열이 어떻든 이게 먼저다', async function () {
      await expect(asRelayer().anchor(0, ZeroHash, [])).to.be.revertedWithCustomError(
        anchorContract,
        'InvalidBatchId',
      );
    });
  });

  describe('8. 롤 부여 · 회수 (릴레이어 키 교체 경로)', function () {
    it('관리자가 새 릴레이어에 부여하면 호출할 수 있다', async function () {
      await anchorContract.connect(admin).grantRole(ANCHOR_ROLE, outsider.address);
      await expect(anchorContract.connect(outsider).anchor(1, THREE_ROOT, THREE)).to.emit(
        anchorContract,
        'Anchored',
      );
    });

    it('회수하면 다시 막힌다 — 재배포 없이 키를 교체한다', async function () {
      await anchorContract.connect(admin).revokeRole(ANCHOR_ROLE, relayer.address);
      await expect(asRelayer().anchor(1, THREE_ROOT, THREE))
        .to.be.revertedWithCustomError(anchorContract, 'AccessControlUnauthorizedAccount')
        .withArgs(relayer.address, ANCHOR_ROLE);
    });

    it('롤을 회수해도 이미 박힌 루트는 남는다', async function () {
      await asRelayer().anchor(1, THREE_ROOT, THREE);
      await anchorContract.connect(admin).revokeRole(ANCHOR_ROLE, relayer.address);
      expect(await anchorContract.rootOf(1)).to.equal(THREE_ROOT);
    });

    it('릴레이어는 롤을 나눠 주지 못한다 — 서버 키 유출의 피해 상한', async function () {
      await expect(asRelayer().grantRole(ANCHOR_ROLE, outsider.address))
        .to.be.revertedWithCustomError(anchorContract, 'AccessControlUnauthorizedAccount')
        .withArgs(relayer.address, DEFAULT_ADMIN_ROLE);
    });
  });

  describe('9. 미앵커 조회', function () {
    it('없는 batchId 는 revert 하지 않고 0 을 준다', async function () {
      expect(await anchorContract.rootOf(12345)).to.equal(ZeroHash);
      expect(await anchorContract.rootOf(0)).to.equal(ZeroHash);
    });
  });

  describe('10. 큰 배치', function () {
    it('리프 500개도 한 tx 로 앵커되고 복구된다', async function () {
      const big = commits(500, 'big');
      const root = rootOfList(big);
      const tx = await asRelayer().anchor(3, root, big);
      const receipt = await tx.wait();
      // 가스는 판단 근거가 아니지만(gasPrice 0), 로컬 30M 한도 안이라는 건 확인해 둔다.
      expect(receipt.gasUsed).to.be.lessThan(30_000_000n);
      const logs = await anchorContract.queryFilter(anchorContract.filters.Anchored(3));
      expect(logs[0].args.commitHashes).to.have.lengthOf(500);
      expect(rootOfList([...logs[0].args.commitHashes])).to.equal(await anchorContract.rootOf(3));
    });
  });
});
