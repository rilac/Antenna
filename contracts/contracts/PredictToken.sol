// SPDX-License-Identifier: MIT
pragma solidity 0.8.28;

import {ERC20} from "@openzeppelin/contracts/token/ERC20/ERC20.sol";
import {AccessControl} from "@openzeppelin/contracts/access/AccessControl.sol";

/**
 * @title PredictToken — ANT (ANT-CHAIN-03)
 * @notice 앤테나 서비스 토큰. 전송 불가 · 서버 오퍼레이터의 mint / burn / subscribe 로만 잔액이 움직인다.
 *
 * ── 왜 자체 컨트랙트인가 ────────────────────────────────────────────────
 *
 * SSAFY 토큰증권 API(ERC-1400)로 발행하면 사용자가 지갑에서 transfer 를 직접 부를 수 있고
 * 그걸 막을 수단이 API 에 없다. "환금·전송 불가" 는 약관이 아니라 코드여야 한다는 게 이 서비스의
 * BM 방어선이라(결정검토서 E3), 그 한 줄을 위해 토큰을 직접 만든다. 구독 70:30 배분도 API 로는
 * tx 2건이라 원자성이 없었다(E4) — 여기서는 한 함수 안에서 끝낸다.
 *
 * ── 단위: decimals = 0, 1 ANT = 1 원 상당 ─────────────────────────────
 *
 * ERC-20 관행(18)을 따르지 않는다. 원화에는 소수 단위가 없고, "1 ANT = 1 원" 이 코드·DB·화면·지갑
 * 어디서나 같은 숫자로 보여야 변환 계층이 사라진다. 나중에 원화 스테이블코인을 붙여도 정수 1:1
 * 매핑이라 이 결정이 길을 막지 않는다. 대가는 70:30 에서 생기는 1 ANT 미만 잔돈뿐이고, 그 처리
 * 규칙(플랫폼 몫 내림, 나머지는 예측가)은 subscribe() 에 고정돼 있다.
 *
 * ── 잔액을 움직이는 길 ─────────────────────────────────────────────────
 *
 *   transfer / transferFrom / approve   → 항상 TransferDisabled. 사용자끼리 P2P 가 막히는 지점이다.
 *   mint / burn(from) / subscribe       → OPERATOR_ROLE (서버 릴레이어 키). 사용자 동의는 서버가
 *                                         EIP-191 서명으로 검증하고, 체인은 서명을 모른다(CommitAnchor 와 같은 구조).
 *                                         즉 "서버는 사용자 토큰을 언제든 태울 수 있다" — 팀이 알아야 하는 사실이다.
 *   burnSelf(amount)                     → 보유자 본인. 지금은 안 쓰지만, 나중에 "소각은 지갑에서 직접" 으로
 *                                         바꾸고 싶을 때 재배포 없이 프론트만 바꾸면 되도록 열어 둔다.
 *   operatorTransfer                     → MOVER_ROLE. 배포 시 구성원 0. 새 잔액 이동 패턴(상금 분배·경매·
 *                                         스테이블코인 교환)이 생기면 그 컨트랙트 주소에만 이 롤을 준다.
 *                                         서버 키에는 주지 않는다 — 그래야 "서버도 마음대로 이체 못 한다" 가 유지된다.
 *
 * ── 안 하는 것 ─────────────────────────────────────────────────────────
 *
 * 1. 업그레이더블 프록시 — "전송 불가" 가 코드 보장에서 관리자 약속으로 내려간다. 기능을 더할 땐 위성
 *    컨트랙트 + MOVER_ROLE, 토큰 규칙 자체를 바꿀 땐 v2 이관(Transfer 로그 스냅샷 → 일괄 mint).
 * 2. Pausable — 오퍼레이터 키가 새면 관리자가 revokeRole 하면 끝난다. 관리자 권한을 더 늘릴 이유가 없다.
 * 3. 총량 상한 — 금액표(ANT-TOKEN-08)가 없는데 상한을 정할 수 없다.
 * 4. reason 어휘 검사 — 어휘의 원천은 서버 상수다. 값이 늘 때마다 재배포할 수 없다.
 */
contract PredictToken is ERC20, AccessControl {
    /// mint · burn(from) · subscribe. 서버 릴레이어 키가 갖는다. 관리자는 갖지 않는다.
    bytes32 public constant OPERATOR_ROLE = keccak256("OPERATOR_ROLE");
    /// operatorTransfer. 위성 컨트랙트 주소에만 준다. 배포 시점엔 아무도 없다.
    bytes32 public constant MOVER_ROLE = keccak256("MOVER_ROLE");

    /// 구독료 중 플랫폼 몫. 상수인 이유: 관리자 설정값이면 "컨트랙트가 배분한다" 는 말이 "관리자가 바꿀 수 있다" 가 된다.
    uint256 public constant PLATFORM_SHARE_BPS = 3000;
    uint256 private constant BPS_DENOMINATOR = 10_000;

    /// 플랫폼 30% 수납 주소. 운영값이라 관리자가 바꿀 수 있다 — 이것 하나 때문에 이관하지 않기 위해서.
    address public treasury;

    /// 인덱서(ANT-CHAIN-11)가 구독하는 이벤트 셋. reason 은 token_ledger.reason(varchar 32) 과 길이가 같은 bytes32 ASCII.
    event Minted(address indexed to, uint256 amount, bytes32 indexed reason);
    event Burned(address indexed from, uint256 amount, bytes32 indexed reason);
    /// creatorShare + platformShare == amount. 서버는 이 값을 다시 계산하지 않고 사본으로 저장한다.
    event Subscribed(
        address indexed subscriber,
        address indexed creator,
        uint256 amount,
        uint256 creatorShare,
        uint256 platformShare
    );
    event TreasuryChanged(address indexed previous, address indexed next);

    /// transfer · transferFrom · approve. 사용자끼리 옮기는 길은 없다.
    error TransferDisabled();
    /// 자기 채널 구독. DB CHECK(subscriber_id <> publisher_id) 와 이중 방어.
    error SelfSubscribe();
    /// 0 ANT 는 mint·burn·subscribe·operatorTransfer 어디서도 의미가 없다. 이벤트만 남는 tx 를 막는다.
    error ZeroAmount();
    /// 0 주소로 mint 하거나 수납 주소를 0 으로 바꾸려 했다.
    error ZeroAddress();
    /// operatorTransfer 의 from == to. 잔액은 안 변하고 이벤트만 남는다.
    error SameAddress();

    /**
     * @param admin     롤 부여·회수, 수납 주소 교체. 잔액은 못 건드린다. 서버 밖에 두는 키다.
     * @param operator  서버 릴레이어. mint · burn · subscribe.
     * @param treasury_ 플랫폼 30% 수납 주소. 서버는 이 키를 읽지 않는다.
     */
    constructor(address admin, address operator, address treasury_) ERC20("Antenna", "ANT") {
        if (admin == address(0) || operator == address(0) || treasury_ == address(0)) revert ZeroAddress();
        _grantRole(DEFAULT_ADMIN_ROLE, admin);
        _grantRole(OPERATOR_ROLE, operator);
        treasury = treasury_;
        emit TreasuryChanged(address(0), treasury_);
    }

    /// 1 ANT 가 최소 단위다. 위 "단위" 절 참고.
    function decimals() public pure override returns (uint8) {
        return 0;
    }

    // ── 막힌 길 ──────────────────────────────────────────────────────────

    /// @dev 사용자 P2P 차단. ERC-20 표면은 지갑이 잔액을 보여 주기 위해 남기고, 진입점만 막는다.
    function transfer(address, uint256) public pure override returns (bool) {
        revert TransferDisabled();
    }

    /// @dev approve 를 막으면 allowance 는 영원히 0 이라 transferFrom 도 원리적으로 못 통과한다. 그래도 명시적으로 막는다.
    function transferFrom(address, address, uint256) public pure override returns (bool) {
        revert TransferDisabled();
    }

    /// @dev "이 컨트랙트가 내 토큰을 대신 써도 된다" 는 허락 자체를 없앤다. 거래소·DEX 에 맡길 수 없다.
    function approve(address, uint256) public pure override returns (bool) {
        revert TransferDisabled();
    }

    // ── 열린 길: OPERATOR_ROLE ────────────────────────────────────────────

    /// @notice 가입 보너스·시즌 상금 등. 사유는 서버 상수(SIGNUP_BONUS · SEASON_REWARD …).
    function mint(address to, uint256 amount, bytes32 reason) external onlyRole(OPERATOR_ROLE) {
        if (to == address(0)) revert ZeroAddress();
        if (amount == 0) revert ZeroAmount();
        _mint(to, amount);
        emit Minted(to, amount, reason);
    }

    /**
     * @notice 슬롯 초과·참가비·광고 등 소각. 보유자 승인 없이 오퍼레이터가 태운다.
     * 사용자 동의는 서버가 EIP-191 서명으로 이미 검증했다(AUTH-06). 잔액 부족은 OZ ERC20InsufficientBalance.
     */
    function burn(address from, uint256 amount, bytes32 reason) external onlyRole(OPERATOR_ROLE) {
        if (amount == 0) revert ZeroAmount();
        _burn(from, amount);
        emit Burned(from, amount, reason);
    }

    /**
     * @notice 구독 결제. 구독자 → 예측가 70% · 플랫폼 30% 를 한 tx 에서 옮긴다. 둘 중 하나만 성공하는 상태는 없다.
     *
     * 잔돈 규칙: 플랫폼 몫을 내림하고 나머지를 예측가에게. amount=9 면 플랫폼 2, 예측가 7.
     * 예측가 몫은 언제나 70% 이상이다 — 플랫폼이 잔돈까지 챙기는 그림은 BM 설명에서 불리하다.
     * 잔액이 모자라면 두 _transfer 중 어디서 막히든 tx 전체가 되감긴다 — 예측가만 받고 플랫폼은 못 받는 상태는 없다.
     */
    function subscribe(address subscriber, address creator, uint256 amount) external onlyRole(OPERATOR_ROLE) {
        if (subscriber == creator) revert SelfSubscribe();
        if (amount == 0) revert ZeroAmount();

        uint256 platformShare = (amount * PLATFORM_SHARE_BPS) / BPS_DENOMINATOR;
        uint256 creatorShare = amount - platformShare;

        _transfer(subscriber, creator, creatorShare);
        if (platformShare > 0) {
            _transfer(subscriber, treasury, platformShare);
        }
        emit Subscribed(subscriber, creator, amount, creatorShare, platformShare);
    }

    // ── 열린 길: 보유자 본인 ──────────────────────────────────────────────

    /**
     * @notice 자기 토큰을 자기가 태운다. 권한 불필요. 남의 잔액은 못 건드리니 위험이 없다.
     * 지금 서버는 이 길을 안 쓴다 — 소각을 사용자 지갑에서 직접 보내는 구조로 바꿀 때를 위해 열어 둔다.
     * 이름을 burn 오버로드로 두지 않는 이유: ethers 가 같은 이름의 오버로드를 시그니처 문자열로만 부를 수 있어
     * 프론트·스크립트 호출이 전부 `token['burn(uint256,bytes32)']` 꼴이 된다. 이름을 나누면 그 마찰이 없다.
     */
    function burnSelf(uint256 amount, bytes32 reason) external {
        if (amount == 0) revert ZeroAmount();
        _burn(msg.sender, amount);
        emit Burned(msg.sender, amount, reason);
    }

    // ── 열린 길: MOVER_ROLE (위성 컨트랙트) ───────────────────────────────

    /**
     * @notice 위성 컨트랙트 전용 일반 이체. 사유 이벤트를 내지 않는다 — 사유는 이걸 부른 위성이 자기 이벤트로 남긴다.
     * 토큰이 모든 위성의 사유 어휘를 알 수 없어서다. OZ 표준 Transfer 이벤트는 그대로 난다.
     */
    function operatorTransfer(address from, address to, uint256 amount) external onlyRole(MOVER_ROLE) {
        if (from == to) revert SameAddress();
        if (amount == 0) revert ZeroAmount();
        _transfer(from, to, amount);
    }

    // ── 관리자 ───────────────────────────────────────────────────────────

    /// @notice 수납 주소 교체. 옛 주소에 쌓인 잔액은 옮기지 않는다 — 옮기려면 MOVER 를 가진 위성으로.
    function setTreasury(address next) external onlyRole(DEFAULT_ADMIN_ROLE) {
        if (next == address(0)) revert ZeroAddress();
        address previous = treasury;
        treasury = next;
        emit TreasuryChanged(previous, next);
    }
}
