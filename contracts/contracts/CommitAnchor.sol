// SPDX-License-Identifier: MIT
pragma solidity 0.8.28;

import {AccessControl} from "@openzeppelin/contracts/access/AccessControl.sol";

/**
 * @title CommitAnchor
 * @notice 앤테나가 매 영업일 봉인한 예측 커밋들의 머클루트 1건을 체인에 박는다.
 *
 * 이 서비스가 "예측 기록은 위변조 불가"라고 말할 수 있는 근거는 전부 이 컨트랙트 하나에 걸려 있다.
 * 루트가 체인에 올라간 뒤에는 서버가 그 회차의 예측을 단 한 글자도 고쳐 쓸 수 없다 —
 * 고치면 리프 해시가 바뀌고, 머클 증명으로 루트를 복원했을 때 체인의 값과 안 맞는다.
 *
 * 자체 Solidity 는 이 1본뿐이다. 서비스 토큰은 SSAFY 토큰증권 API 가 맡고,
 * 머클루트 앵커링만 그 API 의 범위 밖이라 직접 짠다. (API 명세서 §4)
 *
 * ── 설계에서 일부러 안 한 것들 ─────────────────────────────────────────
 *
 * 1. 업그레이더블 프록시를 쓰지 않는다.
 *    프록시를 쓰면 주소가 고정되는 이점이 있지만, 동시에 "관리자가 구현을 갈아끼울 수 있다"는
 *    사실이 남는다. 덮어쓰기를 허용하는 anchor() 로 업그레이드하는 순간 과거 앵커가 전부
 *    무의미해진다 — 이 컨트랙트가 파는 주장을 컨트랙트가 스스로 부정하는 셈이다.
 *    앵커링에서 업그레이더빌리티는 기능이 아니라 취약점이다.
 *
 * 2. 사용자 서명을 검증하지 않는다.
 *    tx 는 서버 릴레이어가 자기 키로 보낸다(가스 대납). 사용자 지갑 서명은 예측 등록 시점에
 *    백엔드가 EIP-191 로 검증하고, 그 결과가 커밋 해시에 녹아 리프로 들어간다.
 *
 * 3. commitCount 를 storage 에 저장하지 않는다.
 *    온체인 검증에 쓰이지 않는 값이다. 이벤트 인자로만 흘려서 인덱서가 DB 에 넣는다.
 *    가스 한 슬롯 아끼자는 게 아니라 "체인에는 검증에 꼭 필요한 것만 둔다"는 원칙이다.
 *    (이 체인은 gasPrice 가 0 이라 가스는 애초에 판단 근거가 못 된다.)
 */
contract CommitAnchor is AccessControl {
    /// 앵커 트랜잭션을 보낼 수 있는 롤. 서버 릴레이어 주소가 갖는다.
    bytes32 public constant ANCHOR_ROLE = keccak256("ANCHOR_ROLE");

    /**
     * batchId → 머클루트.
     *
     * batchId 는 컨트랙트가 자동 증가시키지 않고 서버가 넘긴다 (= anchor_batches.id).
     * 이유가 둘이다.
     *  ① 온체인 멱등키가 생긴다. 릴레이어가 "tx 는 나갔는데 응답을 못 받은" 상태에서 재시도해도
     *     같은 루트가 두 번 박히지 않는다 — 되돌릴 수 없는 기록이라 재전송 방어가 없으면 못 쓴다.
     *  ② DB id 와 온체인 batchId 가 같은 번호라, 검증하는 사람에게 "GET /anchors/{id} 의 그 번호를
     *     체인에서 rootOf 로 직접 조회해 보라"고 말할 수 있다. 매핑 테이블이 필요 없다.
     */
    mapping(uint256 batchId => bytes32 merkleRoot) private _roots;

    /**
     * 인덱서(ANT-CHAIN-04)가 구독하는 유일한 이벤트.
     * batchId 만 indexed 다 — 로그 필터로 찾는 키가 이것뿐이고, merkleRoot 는
     * DB(anchor_batches.merkle_root)에 유니크로 이미 있어 역조회가 된다.
     */
    event Anchored(uint256 indexed batchId, bytes32 merkleRoot, uint256 commitCount);

    /// 같은 batchId 가 이미 앵커됐다. 릴레이어 재전송이 여기서 막힌다.
    error BatchAlreadyAnchored(uint256 batchId);
    /// batchId 가 0. 0 은 "아직 앵커 안 됨" 센티널로 비워 둔다.
    error InvalidBatchId();
    /// 머클루트가 0. 체인에 0 루트가 남으면 rootOf 의 "앵커 여부" 판정이 무너진다.
    error EmptyRoot();
    /// 커밋 수가 0. 루트가 있는데 개수가 0 이면 배치 로직 버그다.
    error EmptyCommitCount();

    /**
     * @param admin 롤을 부여·회수할 관리자. 배포자와 분리할 수 있게 인자로 받는다.
     *
     * 관리자 키를 멀티시그에 두지 않은 건, 관리자가 할 수 있는 최악이 "앵커를 막는 것"이지
     * "이미 박힌 루트를 바꾸는 것"이 아니기 때문이다. anchor() 가 덮어쓰기를 금지하므로
     * 관리자 탈취의 피해 상한이 가용성 손실이고, 그건 재배포로 복구된다.
     */
    constructor(address admin) {
        _grantRole(DEFAULT_ADMIN_ROLE, admin);
        _grantRole(ANCHOR_ROLE, admin);
    }

    /**
     * @notice 한 회차의 머클루트를 체인에 박는다. 영업일 1회, 배치 B2 가 호출한다.
     * @param batchId     anchor_batches.id. 서버가 정한다
     * @param merkleRoot  그 회차 커밋 해시들의 머클루트
     * @param commitCount 포함된 커밋 수. 저장하지 않고 이벤트로만 흘린다
     *
     * 덮어쓰기가 없다는 게 이 함수의 전부다. 한 번 쓰면 관리자도 못 바꾼다.
     */
    function anchor(uint256 batchId, bytes32 merkleRoot, uint256 commitCount)
        external
        onlyRole(ANCHOR_ROLE)
    {
        if (batchId == 0) revert InvalidBatchId();
        if (merkleRoot == bytes32(0)) revert EmptyRoot();
        if (commitCount == 0) revert EmptyCommitCount();
        if (_roots[batchId] != bytes32(0)) revert BatchAlreadyAnchored(batchId);

        _roots[batchId] = merkleRoot;
        emit Anchored(batchId, merkleRoot, commitCount);
    }

    /**
     * @notice 저장된 머클루트를 읽는다. 권한 제한 없음 — 누구나 검증할 수 있어야 한다.
     * @return 앵커되지 않은 batchId 면 bytes32(0).
     *
     * 없는 batchId 에 revert 하지 않는 건 의도한 것이다. 검증 화면이 "아직 앵커 전" 상태를
     * 예외 처리 없이 그대로 표시할 수 있어야 한다. anchor() 가 0 루트를 막아 두므로
     * "0 이 아니다 == 앵커됐다" 가 빈틈없이 성립한다.
     */
    function rootOf(uint256 batchId) external view returns (bytes32) {
        return _roots[batchId];
    }
}
