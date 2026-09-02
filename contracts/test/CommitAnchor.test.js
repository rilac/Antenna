const { expect } = require('chai');
const { ethers } = require('hardhat');

/**
 * CommitAnchor 단위 테스트 (ANT-CHAIN-01).
 *
 * ── 이 파일의 규칙: revert 는 "실패했다"로 세지 않는다 ──────────────────
 * 반드시 revertedWithCustomError 로 **에러 이름까지** 단언한다.
 *
 * 실제로 여기서 한 번 데였다. SSAFY 네트워크 수동 검증 때 "리버트했으니 통과"로 셌는데,
 * 나중에 리버트 사유를 디코딩해 보니 중복·빈루트·batchId 0 세 케이스가 전부
 * AccessControlUnauthorizedAccount 였다. 앞 단계에서 롤을 회수한 상태라 비즈니스 규칙에
 * 닿기도 전에 권한에서 막힌 것이었다. 3케이스가 거짓 통과였다.
 * (docs-personal/CHAIN-onchain-backend/토큰증권_체인접속_검증정보.md §4)
 */
describe('CommitAnchor', function () {
  const ROOT = '0x' + '11'.repeat(32);
  const ROOT2 = '0x' + '22'.repeat(32);
  const ZERO32 = ethers.ZeroHash;

  let anchorContract;
  let admin; // 배포자 = DEFAULT_ADMIN_ROLE + ANCHOR_ROLE
  let relayer; // 롤을 나중에 받는 주소
  let outsider; // 아무 롤도 없는 주소
  let ANCHOR_ROLE;

  beforeEach(async function () {
    [admin, relayer, outsider] = await ethers.getSigners();
    const factory = await ethers.getContractFactory('CommitAnchor');
    anchorContract = await factory.deploy(admin.address);
    await anchorContract.waitForDeployment();
    ANCHOR_ROLE = await anchorContract.ANCHOR_ROLE();
  });

  describe('배포', function () {
    it('생성자에 넘긴 주소가 관리자 롤과 앵커 롤을 모두 갖는다', async function () {
      const DEFAULT_ADMIN_ROLE = await anchorContract.DEFAULT_ADMIN_ROLE();
      expect(await anchorContract.hasRole(DEFAULT_ADMIN_ROLE, admin.address)).to.equal(true);
      expect(await anchorContract.hasRole(ANCHOR_ROLE, admin.address)).to.equal(true);
    });

    it('관리자를 배포자와 분리할 수 있다', async function () {
      const factory = await ethers.getContractFactory('CommitAnchor');
      // admin 이 배포하지만 관리자는 relayer 로 지정한다.
      const c = await factory.connect(admin).deploy(relayer.address);
      await c.waitForDeployment();
      expect(await c.hasRole(ANCHOR_ROLE, relayer.address)).to.equal(true);
      expect(await c.hasRole(ANCHOR_ROLE, admin.address)).to.equal(false);
    });
  });

  describe('1. 정상 앵커 + 이벤트 인자', function () {
    it('Anchored 이벤트가 batchId·root·commitCount 를 그대로 싣는다', async function () {
      await expect(anchorContract.anchor(1, ROOT, 3))
        .to.emit(anchorContract, 'Anchored')
        .withArgs(1n, ROOT, 3n);
    });

    it('batchId 는 연속일 필요가 없다 — DB 시퀀스가 갭을 낼 수 있다', async function () {
      await anchorContract.anchor(1, ROOT, 3);
      await expect(anchorContract.anchor(999, ROOT2, 7))
        .to.emit(anchorContract, 'Anchored')
        .withArgs(999n, ROOT2, 7n);
    });
  });

  describe('2. rootOf 왕복', function () {
    it('저장한 루트를 그대로 되읽는다', async function () {
      await anchorContract.anchor(42, ROOT, 5);
      expect(await anchorContract.rootOf(42)).to.equal(ROOT);
    });

    it('서로 다른 batchId 가 섞이지 않는다', async function () {
      await anchorContract.anchor(1, ROOT, 1);
      await anchorContract.anchor(2, ROOT2, 2);
      expect(await anchorContract.rootOf(1)).to.equal(ROOT);
      expect(await anchorContract.rootOf(2)).to.equal(ROOT2);
    });
  });

  describe('3. 같은 batchId 재전송 (릴레이어 재시도 방어)', function () {
    it('BatchAlreadyAnchored 로 막고 batchId 를 인자로 알려준다', async function () {
      await anchorContract.anchor(7, ROOT, 3);
      await expect(anchorContract.anchor(7, ROOT2, 9))
        .to.be.revertedWithCustomError(anchorContract, 'BatchAlreadyAnchored')
        .withArgs(7n);
    });

    it('두 번째 전송이 실패해도 첫 앵커는 그대로 살아 있다', async function () {
      await anchorContract.anchor(7, ROOT, 3);
      await expect(anchorContract.anchor(7, ROOT2, 9)).to.be.reverted;
      // 덮어쓰기가 일어나지 않는다는 것이 이 컨트랙트의 존재 이유다.
      expect(await anchorContract.rootOf(7)).to.equal(ROOT);
    });
  });

  describe('4. 무권한 호출', function () {
    it('롤 없는 주소는 AccessControlUnauthorizedAccount 로 막힌다', async function () {
      await expect(anchorContract.connect(outsider).anchor(1, ROOT, 3))
        .to.be.revertedWithCustomError(anchorContract, 'AccessControlUnauthorizedAccount')
        .withArgs(outsider.address, ANCHOR_ROLE);
    });

    it('관리자 롤만 있고 앵커 롤이 없으면 호출하지 못한다', async function () {
      const DEFAULT_ADMIN_ROLE = await anchorContract.DEFAULT_ADMIN_ROLE();
      await anchorContract.grantRole(DEFAULT_ADMIN_ROLE, relayer.address);
      await expect(anchorContract.connect(relayer).anchor(1, ROOT, 3))
        .to.be.revertedWithCustomError(anchorContract, 'AccessControlUnauthorizedAccount')
        .withArgs(relayer.address, ANCHOR_ROLE);
    });
  });

  describe('5. 빈 루트 / 빈 커밋 수', function () {
    it('merkleRoot 가 0 이면 EmptyRoot', async function () {
      await expect(anchorContract.anchor(1, ZERO32, 3)).to.be.revertedWithCustomError(
        anchorContract,
        'EmptyRoot',
      );
    });

    it('commitCount 가 0 이면 EmptyCommitCount — EmptyRoot 가 아니다', async function () {
      // 에러를 둘로 쪼갠 이유가 이것이다. 루트는 멀쩡한데 EmptyRoot 가 뜨면
      // 원인을 루트에서 찾다가 시간을 날린다.
      await expect(anchorContract.anchor(1, ROOT, 0)).to.be.revertedWithCustomError(
        anchorContract,
        'EmptyCommitCount',
      );
    });
  });

  describe('6. batchId == 0', function () {
    it('InvalidBatchId 로 막는다 — 0 은 "앵커 안 됨" 센티널이다', async function () {
      await expect(anchorContract.anchor(0, ROOT, 3)).to.be.revertedWithCustomError(
        anchorContract,
        'InvalidBatchId',
      );
    });

    it('검사 순서: batchId 가 0 이면 루트가 0 이어도 InvalidBatchId 가 먼저다', async function () {
      await expect(anchorContract.anchor(0, ZERO32, 0)).to.be.revertedWithCustomError(
        anchorContract,
        'InvalidBatchId',
      );
    });
  });

  describe('7. 롤 부여 · 회수 (릴레이어 키 교체 경로)', function () {
    it('부여하면 호출할 수 있다', async function () {
      await anchorContract.grantRole(ANCHOR_ROLE, relayer.address);
      await expect(anchorContract.connect(relayer).anchor(1, ROOT, 3))
        .to.emit(anchorContract, 'Anchored')
        .withArgs(1n, ROOT, 3n);
    });

    it('회수하면 다시 막힌다 — 컨트랙트 재배포 없이 키를 교체한다', async function () {
      await anchorContract.grantRole(ANCHOR_ROLE, relayer.address);
      await anchorContract.connect(relayer).anchor(1, ROOT, 3);
      await anchorContract.revokeRole(ANCHOR_ROLE, relayer.address);
      await expect(anchorContract.connect(relayer).anchor(2, ROOT2, 3))
        .to.be.revertedWithCustomError(anchorContract, 'AccessControlUnauthorizedAccount')
        .withArgs(relayer.address, ANCHOR_ROLE);
    });

    it('롤을 회수해도 이미 박힌 루트는 남는다', async function () {
      await anchorContract.grantRole(ANCHOR_ROLE, relayer.address);
      await anchorContract.connect(relayer).anchor(1, ROOT, 3);
      await anchorContract.revokeRole(ANCHOR_ROLE, relayer.address);
      expect(await anchorContract.rootOf(1)).to.equal(ROOT);
    });

    it('앵커 롤 보유자가 롤을 마음대로 나눠 주지는 못한다', async function () {
      await anchorContract.grantRole(ANCHOR_ROLE, relayer.address);
      const DEFAULT_ADMIN_ROLE = await anchorContract.DEFAULT_ADMIN_ROLE();
      await expect(anchorContract.connect(relayer).grantRole(ANCHOR_ROLE, outsider.address))
        .to.be.revertedWithCustomError(anchorContract, 'AccessControlUnauthorizedAccount')
        .withArgs(relayer.address, DEFAULT_ADMIN_ROLE);
    });
  });

  describe('8. 미앵커 조회', function () {
    it('없는 batchId 는 revert 하지 않고 0 을 준다', async function () {
      expect(await anchorContract.rootOf(12345)).to.equal(ZERO32);
    });

    it('batchId 0 조회도 revert 하지 않는다 — 읽기는 관대하게', async function () {
      expect(await anchorContract.rootOf(0)).to.equal(ZERO32);
    });

    it('"0 이 아니면 앵커됨" 이 빈틈없이 성립한다', async function () {
      expect(await anchorContract.rootOf(1)).to.equal(ZERO32);
      await anchorContract.anchor(1, ROOT, 3);
      expect(await anchorContract.rootOf(1)).to.not.equal(ZERO32);
    });
  });
});
