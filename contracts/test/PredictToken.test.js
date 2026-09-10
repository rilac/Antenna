const { expect } = require('chai');
const { ethers } = require('hardhat');

/**
 * PredictToken(ANT) 단위 테스트 (ANT-CHAIN-03).
 *
 * ── 규칙은 CommitAnchor.test.js 와 같다 ──────────────────────────────
 * revert 는 "실패했다" 로 세지 않는다. revertedWithCustomError 로 **에러 이름까지** 단언한다.
 * 권한 에러(AccessControlUnauthorizedAccount)로 위장 통과하는 케이스를 걸러내기 위해서다.
 *
 * ── 이 파일이 지키는 성질 ──────────────────────────────────────────────
 *  1. 사용자끼리는 어떤 경로로도 못 옮긴다 (transfer · transferFrom · approve → TransferDisabled).
 *  2. 잔액을 움직이는 건 오퍼레이터(mint · burn · subscribe), 보유자 자가 소각(burnSelf), MOVER(operatorTransfer)뿐.
 *  3. subscribe 는 한 tx 원자 — 잔액 부족이면 예측가도 못 받는다.
 *  4. decimals 0 — 1 ANT 가 최소 단위, 잔돈은 예측가에게.
 *  5. 배포 직후 MOVER 는 아무도 없다.
 */

const { encodeBytes32String, ZeroAddress } = ethers;
const REASON = encodeBytes32String('SIGNUP_BONUS');
const SLOT_OVER = encodeBytes32String('SLOT_OVER');

describe('PredictToken (ANT)', function () {
  let token;
  let admin; // DEFAULT_ADMIN_ROLE 만
  let operator; // OPERATOR_ROLE 만
  let treasury; // 수납 주소. 롤 없음
  let alice; // 구독자
  let bob; // 예측가
  let satellite; // MOVER 를 받을 주소 (테스트에선 EOA 로 대신)
  let OPERATOR_ROLE;
  let MOVER_ROLE;
  let DEFAULT_ADMIN_ROLE;

  beforeEach(async function () {
    [admin, operator, treasury, alice, bob, satellite] = await ethers.getSigners();
    const factory = await ethers.getContractFactory('PredictToken');
    token = await factory.deploy(admin.address, operator.address, treasury.address);
    await token.waitForDeployment();
    OPERATOR_ROLE = await token.OPERATOR_ROLE();
    MOVER_ROLE = await token.MOVER_ROLE();
    DEFAULT_ADMIN_ROLE = await token.DEFAULT_ADMIN_ROLE();
  });

  const asOp = () => token.connect(operator);
  const mintTo = (who, amount) => asOp().mint(who.address, amount, REASON);

  describe('배포', function () {
    it('이름 Antenna · 심볼 ANT · decimals 0', async function () {
      expect(await token.name()).to.equal('Antenna');
      expect(await token.symbol()).to.equal('ANT');
      expect(await token.decimals()).to.equal(0);
      expect(await token.totalSupply()).to.equal(0n);
    });

    it('관리자는 관리자 롤만, 오퍼레이터는 오퍼레이터 롤만, MOVER 는 아무도 없다', async function () {
      expect(await token.hasRole(DEFAULT_ADMIN_ROLE, admin.address)).to.equal(true);
      expect(await token.hasRole(OPERATOR_ROLE, admin.address)).to.equal(false);
      expect(await token.hasRole(MOVER_ROLE, admin.address)).to.equal(false);
      expect(await token.hasRole(OPERATOR_ROLE, operator.address)).to.equal(true);
      expect(await token.hasRole(DEFAULT_ADMIN_ROLE, operator.address)).to.equal(false);
      expect(await token.hasRole(MOVER_ROLE, operator.address)).to.equal(false);
    });

    it('수납 주소가 기록되고 TreasuryChanged(0 → treasury) 가 난다', async function () {
      expect(await token.treasury()).to.equal(treasury.address);
      const factory = await ethers.getContractFactory('PredictToken');
      const fresh = await factory.deploy(admin.address, operator.address, treasury.address);
      await expect(fresh.deploymentTransaction())
        .to.emit(fresh, 'TreasuryChanged')
        .withArgs(ZeroAddress, treasury.address);
    });

    it('플랫폼 몫 상수는 30% (3000 bps)', async function () {
      expect(await token.PLATFORM_SHARE_BPS()).to.equal(3000n);
    });

    it('0 주소는 관리자·오퍼레이터·수납 어디에도 못 넣는다', async function () {
      const factory = await ethers.getContractFactory('PredictToken');
      await expect(factory.deploy(ZeroAddress, operator.address, treasury.address)).to.be.revertedWithCustomError(
        factory,
        'ZeroAddress',
      );
      await expect(factory.deploy(admin.address, ZeroAddress, treasury.address)).to.be.revertedWithCustomError(
        factory,
        'ZeroAddress',
      );
      await expect(factory.deploy(admin.address, operator.address, ZeroAddress)).to.be.revertedWithCustomError(
        factory,
        'ZeroAddress',
      );
    });

    it('같은 주소를 셋 다 넘겨도 된다 (로컬 개발용)', async function () {
      const factory = await ethers.getContractFactory('PredictToken');
      const c = await factory.deploy(admin.address, admin.address, admin.address);
      await c.waitForDeployment();
      expect(await c.hasRole(OPERATOR_ROLE, admin.address)).to.equal(true);
      expect(await c.treasury()).to.equal(admin.address);
    });
  });

  describe('1. 전송 차단 — 사용자끼리는 어떤 길로도 못 옮긴다', function () {
    beforeEach(async function () {
      await mintTo(alice, 1000);
    });

    it('transfer → TransferDisabled', async function () {
      await expect(token.connect(alice).transfer(bob.address, 1)).to.be.revertedWithCustomError(
        token,
        'TransferDisabled',
      );
    });

    it('approve → TransferDisabled, allowance 는 0 그대로', async function () {
      await expect(token.connect(alice).approve(bob.address, 1)).to.be.revertedWithCustomError(
        token,
        'TransferDisabled',
      );
      expect(await token.allowance(alice.address, bob.address)).to.equal(0n);
    });

    it('transferFrom → TransferDisabled', async function () {
      await expect(token.connect(bob).transferFrom(alice.address, bob.address, 1)).to.be.revertedWithCustomError(
        token,
        'TransferDisabled',
      );
    });

    it('오퍼레이터가 불러도 똑같이 막힌다 — 롤이 있어도 P2P 함수는 열리지 않는다', async function () {
      await expect(asOp().transfer(bob.address, 1)).to.be.revertedWithCustomError(token, 'TransferDisabled');
      await expect(asOp().approve(bob.address, 1)).to.be.revertedWithCustomError(token, 'TransferDisabled');
      await expect(asOp().transferFrom(alice.address, bob.address, 1)).to.be.revertedWithCustomError(
        token,
        'TransferDisabled',
      );
    });

    it('관리자가 불러도 막힌다', async function () {
      await expect(token.connect(admin).transfer(bob.address, 1)).to.be.revertedWithCustomError(
        token,
        'TransferDisabled',
      );
    });

    it('막힌 뒤 잔액은 그대로다', async function () {
      expect(await token.balanceOf(alice.address)).to.equal(1000n);
      expect(await token.balanceOf(bob.address)).to.equal(0n);
    });
  });

  describe('2. mint — OPERATOR_ROLE', function () {
    it('Minted(to, amount, reason) 와 표준 Transfer(0 → to) 가 함께 난다', async function () {
      await expect(asOp().mint(alice.address, 1000, REASON))
        .to.emit(token, 'Minted')
        .withArgs(alice.address, 1000n, REASON)
        .and.to.emit(token, 'Transfer')
        .withArgs(ZeroAddress, alice.address, 1000n);
      expect(await token.balanceOf(alice.address)).to.equal(1000n);
      expect(await token.totalSupply()).to.equal(1000n);
    });

    it('reason 은 검사하지 않고 그대로 싣는다 — 어휘의 원천은 서버 상수다', async function () {
      const weird = encodeBytes32String('NOT_IN_LEDGER_VOCAB');
      await expect(asOp().mint(alice.address, 1, weird)).to.emit(token, 'Minted').withArgs(alice.address, 1n, weird);
    });

    it('관리자는 mint 못 한다 — 관리자 키가 서버 밖에 있어도 되는 이유', async function () {
      await expect(token.connect(admin).mint(alice.address, 1, REASON))
        .to.be.revertedWithCustomError(token, 'AccessControlUnauthorizedAccount')
        .withArgs(admin.address, OPERATOR_ROLE);
    });

    it('외부인은 mint 못 한다', async function () {
      await expect(token.connect(alice).mint(alice.address, 1, REASON))
        .to.be.revertedWithCustomError(token, 'AccessControlUnauthorizedAccount')
        .withArgs(alice.address, OPERATOR_ROLE);
    });

    it('0 주소 · 0 금액은 거절', async function () {
      await expect(asOp().mint(ZeroAddress, 1, REASON)).to.be.revertedWithCustomError(token, 'ZeroAddress');
      await expect(asOp().mint(alice.address, 0, REASON)).to.be.revertedWithCustomError(token, 'ZeroAmount');
    });
  });

  describe('3. burn(from) — OPERATOR_ROLE, 보유자 승인 없음', function () {
    beforeEach(async function () {
      await mintTo(alice, 1000);
    });

    it('Burned(from, amount, reason) 와 Transfer(from → 0) 가 난다', async function () {
      await expect(asOp().burn(alice.address, 300, SLOT_OVER))
        .to.emit(token, 'Burned')
        .withArgs(alice.address, 300n, SLOT_OVER)
        .and.to.emit(token, 'Transfer')
        .withArgs(alice.address, ZeroAddress, 300n);
      expect(await token.balanceOf(alice.address)).to.equal(700n);
      expect(await token.totalSupply()).to.equal(700n);
    });

    it('잔액보다 많이 태우면 ERC20InsufficientBalance', async function () {
      await expect(asOp().burn(alice.address, 1001, SLOT_OVER))
        .to.be.revertedWithCustomError(token, 'ERC20InsufficientBalance')
        .withArgs(alice.address, 1000n, 1001n);
    });

    it('관리자·외부인은 남의 토큰을 못 태운다', async function () {
      await expect(token.connect(admin).burn(alice.address, 1, SLOT_OVER))
        .to.be.revertedWithCustomError(token, 'AccessControlUnauthorizedAccount')
        .withArgs(admin.address, OPERATOR_ROLE);
      await expect(token.connect(bob).burn(alice.address, 1, SLOT_OVER))
        .to.be.revertedWithCustomError(token, 'AccessControlUnauthorizedAccount')
        .withArgs(bob.address, OPERATOR_ROLE);
    });

    it('0 금액은 거절', async function () {
      await expect(asOp().burn(alice.address, 0, SLOT_OVER)).to.be.revertedWithCustomError(token, 'ZeroAmount');
    });
  });

  describe('4. burnSelf(amount) — 보유자 자가 소각, 권한 불필요', function () {
    beforeEach(async function () {
      await mintTo(alice, 1000);
    });

    it('자기 잔액을 태우면 Burned(msg.sender …) 가 난다', async function () {
      await expect(token.connect(alice).burnSelf(100, SLOT_OVER))
        .to.emit(token, 'Burned')
        .withArgs(alice.address, 100n, SLOT_OVER);
      expect(await token.balanceOf(alice.address)).to.equal(900n);
    });

    it('잔액 초과는 ERC20InsufficientBalance', async function () {
      await expect(token.connect(alice).burnSelf(1001, SLOT_OVER))
        .to.be.revertedWithCustomError(token, 'ERC20InsufficientBalance')
        .withArgs(alice.address, 1000n, 1001n);
    });

    it('잔액 0 인 계정도 부를 수는 있지만 태울 게 없어 실패한다 — 남의 것은 절대 안 건드린다', async function () {
      await expect(token.connect(bob).burnSelf(1, SLOT_OVER))
        .to.be.revertedWithCustomError(token, 'ERC20InsufficientBalance')
        .withArgs(bob.address, 0n, 1n);
      expect(await token.balanceOf(alice.address)).to.equal(1000n);
    });

    it('0 금액은 거절', async function () {
      await expect(token.connect(alice).burnSelf(0, SLOT_OVER)).to.be.revertedWithCustomError(
        token,
        'ZeroAmount',
      );
    });
  });

  describe('5. subscribe — 한 tx 안에서 70:30', function () {
    beforeEach(async function () {
      await mintTo(alice, 30_000);
    });

    it('14,900 → 예측가 10,430 · 플랫폼 4,470 (정확히 나눠떨어지는 경우)', async function () {
      await expect(asOp().subscribe(alice.address, bob.address, 14_900))
        .to.emit(token, 'Subscribed')
        .withArgs(alice.address, bob.address, 14_900n, 10_430n, 4_470n);
      expect(await token.balanceOf(alice.address)).to.equal(15_100n);
      expect(await token.balanceOf(bob.address)).to.equal(10_430n);
      expect(await token.balanceOf(treasury.address)).to.equal(4_470n);
    });

    it('잔돈은 예측가에게 — 9 → 예측가 7 · 플랫폼 2', async function () {
      await expect(asOp().subscribe(alice.address, bob.address, 9))
        .to.emit(token, 'Subscribed')
        .withArgs(alice.address, bob.address, 9n, 7n, 2n);
      expect(await token.balanceOf(bob.address)).to.equal(7n);
      expect(await token.balanceOf(treasury.address)).to.equal(2n);
    });

    it('1 ANT 면 전부 예측가 · 플랫폼 0 (두 번째 _transfer 를 건너뛰어도 이벤트는 난다)', async function () {
      await expect(asOp().subscribe(alice.address, bob.address, 1))
        .to.emit(token, 'Subscribed')
        .withArgs(alice.address, bob.address, 1n, 1n, 0n);
      expect(await token.balanceOf(bob.address)).to.equal(1n);
      expect(await token.balanceOf(treasury.address)).to.equal(0n);
    });

    it('creatorShare + platformShare == amount 가 항상 성립한다 (1 ~ 200 전수)', async function () {
      // 잔돈 규칙이 총량을 새지 않게 하는지. 잔액 변화로 본다.
      for (let amount = 1; amount <= 200; amount += 1) {
        const before = await token.totalSupply();
        const a0 = await token.balanceOf(alice.address);
        await asOp().subscribe(alice.address, bob.address, amount);
        expect(await token.totalSupply()).to.equal(before);
        expect(a0 - (await token.balanceOf(alice.address))).to.equal(BigInt(amount));
      }
      const expectedPlatform = [...Array(200).keys()].map((i) => Math.floor(((i + 1) * 3000) / 10000)).reduce((a, b) => a + b, 0);
      expect(await token.balanceOf(treasury.address)).to.equal(BigInt(expectedPlatform));
    });

    it('잔액 부족이면 예측가도 플랫폼도 못 받는다 — 부분 실패 없음', async function () {
      // 30,001 → 예측가 21,001 은 첫 _transfer 로 나가고(30,000 ≥ 21,001), 플랫폼 9,000 을 옮기는 둘째 _transfer 에서
      // 잔액 8,999 로 막힌다. 그런데 revert 는 tx 전체를 되감아 첫 이체까지 없던 일이 된다 — 그게 "한 tx 원자" 의 뜻이다.
      await expect(asOp().subscribe(alice.address, bob.address, 30_001))
        .to.be.revertedWithCustomError(token, 'ERC20InsufficientBalance')
        .withArgs(alice.address, 8_999n, 9_000n);
      expect(await token.balanceOf(alice.address)).to.equal(30_000n);
      expect(await token.balanceOf(bob.address)).to.equal(0n);
      expect(await token.balanceOf(treasury.address)).to.equal(0n);
    });

    it('자기 구독은 SelfSubscribe', async function () {
      await expect(asOp().subscribe(alice.address, alice.address, 100)).to.be.revertedWithCustomError(
        token,
        'SelfSubscribe',
      );
    });

    it('0 금액은 ZeroAmount', async function () {
      await expect(asOp().subscribe(alice.address, bob.address, 0)).to.be.revertedWithCustomError(
        token,
        'ZeroAmount',
      );
    });

    it('오퍼레이터가 아니면 못 부른다 — 구독자 본인이 직접 불러도', async function () {
      await expect(token.connect(alice).subscribe(alice.address, bob.address, 100))
        .to.be.revertedWithCustomError(token, 'AccessControlUnauthorizedAccount')
        .withArgs(alice.address, OPERATOR_ROLE);
    });

    it('예측가가 수납 주소와 같아도 된다 — 100% 가 한 주소로', async function () {
      await asOp().subscribe(alice.address, treasury.address, 10);
      expect(await token.balanceOf(treasury.address)).to.equal(10n);
    });
  });

  describe('6. operatorTransfer — MOVER_ROLE (배포 시 아무도 없음)', function () {
    beforeEach(async function () {
      await mintTo(alice, 1000);
    });

    it('배포 직후엔 관리자·오퍼레이터·외부인 전부 못 부른다', async function () {
      for (const who of [admin, operator, alice]) {
        await expect(token.connect(who).operatorTransfer(alice.address, bob.address, 1))
          .to.be.revertedWithCustomError(token, 'AccessControlUnauthorizedAccount')
          .withArgs(who.address, MOVER_ROLE);
      }
    });

    it('관리자가 MOVER 를 준 주소만 옮길 수 있고 Transfer 이벤트만 난다', async function () {
      await token.connect(admin).grantRole(MOVER_ROLE, satellite.address);
      const tx = token.connect(satellite).operatorTransfer(alice.address, bob.address, 400);
      await expect(tx).to.emit(token, 'Transfer').withArgs(alice.address, bob.address, 400n);
      await expect(tx).to.not.emit(token, 'Minted');
      await expect(tx).to.not.emit(token, 'Burned');
      await expect(tx).to.not.emit(token, 'Subscribed');
      expect(await token.balanceOf(alice.address)).to.equal(600n);
      expect(await token.balanceOf(bob.address)).to.equal(400n);
    });

    it('MOVER 는 수납 주소의 잔액도 옮길 수 있다 — 수익 재분배 경로', async function () {
      await asOp().subscribe(alice.address, bob.address, 100); // treasury 30
      await token.connect(admin).grantRole(MOVER_ROLE, satellite.address);
      await token.connect(satellite).operatorTransfer(treasury.address, bob.address, 30);
      expect(await token.balanceOf(treasury.address)).to.equal(0n);
      expect(await token.balanceOf(bob.address)).to.equal(100n);
    });

    it('MOVER 를 회수하면 다시 못 부른다', async function () {
      await token.connect(admin).grantRole(MOVER_ROLE, satellite.address);
      await token.connect(admin).revokeRole(MOVER_ROLE, satellite.address);
      await expect(token.connect(satellite).operatorTransfer(alice.address, bob.address, 1))
        .to.be.revertedWithCustomError(token, 'AccessControlUnauthorizedAccount')
        .withArgs(satellite.address, MOVER_ROLE);
    });

    it('같은 주소 · 0 금액 · 잔액 부족은 거절', async function () {
      await token.connect(admin).grantRole(MOVER_ROLE, satellite.address);
      const asSat = token.connect(satellite);
      await expect(asSat.operatorTransfer(alice.address, alice.address, 1)).to.be.revertedWithCustomError(
        token,
        'SameAddress',
      );
      await expect(asSat.operatorTransfer(alice.address, bob.address, 0)).to.be.revertedWithCustomError(
        token,
        'ZeroAmount',
      );
      await expect(asSat.operatorTransfer(alice.address, bob.address, 1001))
        .to.be.revertedWithCustomError(token, 'ERC20InsufficientBalance')
        .withArgs(alice.address, 1000n, 1001n);
    });

    it('MOVER 가 있어도 transfer 는 여전히 막힌다 — 롤은 operatorTransfer 만 연다', async function () {
      await token.connect(admin).grantRole(MOVER_ROLE, satellite.address);
      await mintTo(satellite, 10);
      await expect(token.connect(satellite).transfer(bob.address, 1)).to.be.revertedWithCustomError(
        token,
        'TransferDisabled',
      );
    });
  });

  describe('7. treasury — 관리자만 교체', function () {
    it('교체하면 TreasuryChanged(prev, next) 가 나고 그 뒤 subscribe 가 새 주소로 간다', async function () {
      await mintTo(alice, 100);
      await expect(token.connect(admin).setTreasury(satellite.address))
        .to.emit(token, 'TreasuryChanged')
        .withArgs(treasury.address, satellite.address);
      expect(await token.treasury()).to.equal(satellite.address);

      await asOp().subscribe(alice.address, bob.address, 100);
      expect(await token.balanceOf(satellite.address)).to.equal(30n);
      expect(await token.balanceOf(treasury.address)).to.equal(0n);
    });

    it('옛 수납 주소의 잔액은 옮기지 않는다', async function () {
      await mintTo(alice, 100);
      await asOp().subscribe(alice.address, bob.address, 100); // treasury 30
      await token.connect(admin).setTreasury(satellite.address);
      expect(await token.balanceOf(treasury.address)).to.equal(30n);
      expect(await token.balanceOf(satellite.address)).to.equal(0n);
    });

    it('오퍼레이터·외부인은 못 바꾼다', async function () {
      await expect(asOp().setTreasury(satellite.address))
        .to.be.revertedWithCustomError(token, 'AccessControlUnauthorizedAccount')
        .withArgs(operator.address, DEFAULT_ADMIN_ROLE);
      await expect(token.connect(alice).setTreasury(satellite.address))
        .to.be.revertedWithCustomError(token, 'AccessControlUnauthorizedAccount')
        .withArgs(alice.address, DEFAULT_ADMIN_ROLE);
    });

    it('0 주소로는 못 바꾼다', async function () {
      await expect(token.connect(admin).setTreasury(ZeroAddress)).to.be.revertedWithCustomError(
        token,
        'ZeroAddress',
      );
    });
  });

  describe('8. 키 교체 — 재배포 없이 오퍼레이터를 바꾼다 (유출 대응 절차)', function () {
    it('revokeRole + grantRole 뒤 옛 키는 막히고 새 키가 mint 한다', async function () {
      await token.connect(admin).revokeRole(OPERATOR_ROLE, operator.address);
      await token.connect(admin).grantRole(OPERATOR_ROLE, satellite.address);
      await expect(asOp().mint(alice.address, 1, REASON))
        .to.be.revertedWithCustomError(token, 'AccessControlUnauthorizedAccount')
        .withArgs(operator.address, OPERATOR_ROLE);
      await expect(token.connect(satellite).mint(alice.address, 1, REASON)).to.emit(token, 'Minted');
    });

    it('오퍼레이터는 롤을 나눠 주지 못한다', async function () {
      await expect(asOp().grantRole(OPERATOR_ROLE, alice.address))
        .to.be.revertedWithCustomError(token, 'AccessControlUnauthorizedAccount')
        .withArgs(operator.address, DEFAULT_ADMIN_ROLE);
    });
  });
});
