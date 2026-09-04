// SPDX-License-Identifier: MIT
pragma solidity 0.8.28;

import {AccessControl} from "@openzeppelin/contracts/access/AccessControl.sol";

/**
 * @title CommitAnchor (v2 — ANT-CHAIN-08)
 * @notice 앤테나가 하루에 한 번 봉인한 예측 커밋들의 머클루트를 체인에 박는다.
 *
 * 이 서비스가 "예측 기록은 위변조 불가"라고 말할 수 있는 근거는 전부 이 컨트랙트 하나에 걸려 있다.
 * 루트가 체인에 올라간 뒤에는 서버가 그 회차의 예측을 단 한 글자도 고쳐 쓸 수 없다 —
 * 고치면 리프 해시가 바뀌고, 머클 증명으로 루트를 복원했을 때 체인의 값과 안 맞는다.
 *
 * ── v1(ANT-CHAIN-01)에서 바뀐 것 ───────────────────────────────────────
 *
 * v1 은 루트 32바이트만 받았다. 루트는 "무엇이 있었다"의 지문이지 내용이 아니라서,
 * 서버 DB 의 커밋 해시 목록이 사라지면 그 루트로는 아무것도 증명하지 못했다.
 * v2 는 커밋 해시 전량을 인자로 받아
 *   ① 컨트랙트가 그 리프들로 루트를 **다시 계산해 서버가 준 루트와 대조**하고 (RootMismatch)
 *   ② 커밋 해시 목록을 이벤트로 남긴다 — 이벤트가 곧 백업이다. 인덱서가 체인만 다시 읽어
 *      리프 → 트리 → 증명 경로를 재구축할 수 있다.
 * 가스가 0 인 체인이라 "리프 500개를 tx 에 싣는 비용"이라는 v1 때의 반대 근거는 사라졌다.
 *
 * ── 여전히 안 하는 것 ─────────────────────────────────────────────────
 *
 * 1. 업그레이더블 프록시를 쓰지 않는다. 앵커링에서 "관리자가 로직을 바꿀 수 있다"는
 *    기능이 아니라 취약점이다 — 덮어쓰기를 허용하는 로직으로 갈아끼우는 순간 과거 앵커가 무의미해진다.
 * 2. 사용자 서명을 검증하지 않는다. tx 는 서버 릴레이어가 자기 키로 보낸다. 사용자 서명은
 *    예측 등록 시점에 백엔드가 EIP-191 로 검증하고, 그 결과가 커밋 해시에 녹아 리프로 들어간다.
 * 3. 리프를 storage 에 저장하지 않는다. 저장은 루트뿐이다. 포함 증명은 isIncluded() 가
 *    proof 를 접어 확인하고, 리프 목록은 이벤트 로그에서 읽는다. "체인 storage 에는 검증에
 *    꼭 필요한 것만"이라는 v1 원칙은 그대로다.
 *
 * ── 머클 규격 — 서버(MerkleTree.java)·픽스처 생성기와 바이트 단위로 같아야 한다 ──
 *
 *   리프      keccak256(commitHash)                 32B 입력 — 도메인 분리
 *   내부 노드 keccak256(min ‖ max)                  64B 입력 — 정렬 결합(OpenZeppelin 호환)
 *   홀수 꼬리 그대로 승격                            복제 금지(같은 리프 두 번 세기 방지)
 *   N=1       root = 리프
 *
 * 리프에 한 겹 더 해시를 얹는 이유: 내부 노드도 32바이트라, 커밋 해시를 그대로 리프로 쓰면
 * 내부 노드를 "내 커밋"이라고 위장한 가짜 포함 증명이 통과한다(2차 프리이미지).
 * 리프 해시는 입력 32B, 내부 노드는 입력 64B 라 두 도메인이 원리적으로 겹칠 수 없다.
 */
contract CommitAnchor is AccessControl {
    /// 앵커 트랜잭션을 보낼 수 있는 롤. 서버 릴레이어 주소가 갖는다. 관리자는 갖지 않는다.
    bytes32 public constant ANCHOR_ROLE = keccak256("ANCHOR_ROLE");

    /**
     * batchId → 머클루트.
     *
     * batchId 는 컨트랙트가 자동 증가시키지 않고 서버가 넘긴다 (= anchor_batches.id).
     *  ① 온체인 멱등키가 생긴다. 릴레이어가 "tx 는 나갔는데 응답을 못 받은" 상태에서 재시도해도
     *     같은 루트가 두 번 박히지 않는다 — 되돌릴 수 없는 기록이라 재전송 방어가 없으면 못 쓴다.
     *  ② DB id 와 온체인 batchId 가 같은 번호라, 검증하는 사람에게 "GET /anchors/{id} 의 그 번호를
     *     체인에서 rootOf 로 직접 조회해 보라"고 말할 수 있다.
     */
    mapping(uint256 batchId => bytes32 merkleRoot) private _roots;

    /**
     * 인덱서(ANT-CHAIN-04)가 구독하는 유일한 이벤트. commitHashes 의 순서가 곧 리프 순서다 —
     * 복구할 때 이 순서 그대로 트리를 다시 만들어야 같은 proof 가 나온다.
     * batchId 만 indexed 다. 로그 필터로 찾는 키가 이것뿐이다.
     */
    event Anchored(uint256 indexed batchId, bytes32 merkleRoot, bytes32[] commitHashes);

    /// 같은 batchId 가 이미 앵커됐다. 릴레이어 재전송이 여기서 막힌다.
    error BatchAlreadyAnchored(uint256 batchId);
    /// batchId 가 0. 0 은 "아직 앵커 안 됨" 센티널로 비워 둔다.
    error InvalidBatchId();
    /// 서버가 준 머클루트가 0. 체인에 0 루트가 남으면 rootOf 의 "앵커 여부" 판정이 무너진다.
    error EmptyRoot();
    /// 커밋 해시가 0 건. 빈 배치는 앵커 대상이 아니다 — 서버가 애초에 tx 를 보내면 안 된다.
    error EmptyCommitCount();
    /**
     * 서버가 계산한 루트(expected)와 컨트랙트가 리프로 다시 계산한 루트(computed)가 다르다.
     * 서버 MerkleTree 구현이나 리프 순서가 이 컨트랙트와 어긋났다는 뜻이다 — 배치 로직 버그이며
     * 재시도할 일이 아니다. 두 값을 모두 실어 어느 쪽이 무엇을 계산했는지 바로 보이게 한다.
     */
    error RootMismatch(bytes32 expected, bytes32 computed);

    /**
     * @param admin   롤을 부여·회수할 관리자. 서버 밖에 두는 키다. anchor 는 못 한다.
     * @param relayer 앵커 tx 를 보낼 서버 릴레이어. anchor 만 할 수 있고 롤은 못 나눠 준다.
     *
     * 둘을 생성자에서 분리해 받는 이유: 릴레이어 키는 서버 .env 에 있어 유출면이 넓다.
     * 그 키가 새도 피해는 "안 쓴 batchId 에 쓰레기 루트를 올린다"까지이고, 관리자 키로
     * revokeRole + grantRole 하면 재배포 없이 끝난다. 관리자 키까지 같은 키였다면 공격자가
     * 롤을 영구히 가져가 재배포(새 주소·과거 배치 검증 경로 분기)밖에 답이 없다.
     * 로컬 개발에서는 같은 주소를 둘 다 넘겨도 된다.
     */
    constructor(address admin, address relayer) {
        _grantRole(DEFAULT_ADMIN_ROLE, admin);
        _grantRole(ANCHOR_ROLE, relayer);
    }

    /**
     * @notice 한 회차의 머클루트를 체인에 박는다. 매일 1회, 앵커 배치(ANT-CHAIN-02)가 릴레이어로 호출한다.
     * @param batchId      anchor_batches.id. 서버가 정한다
     * @param merkleRoot   서버가 계산한 루트. 컨트랙트가 commitHashes 로 다시 계산해 대조한다
     * @param commitHashes 그 회차의 커밋 해시 전량. 순서 = 리프 순서(서버는 prediction id 오름차순)
     *
     * 검사 순서는 batchId → 루트 0 → 빈 배열 → 중복 → 루트 대조다. 대조를 마지막에 두는 건
     * 싼 검사로 걸러질 오류가 RootMismatch 로 위장되면 원인을 엉뚱한 데서 찾게 되기 때문이다.
     * 덮어쓰기가 없다는 게 이 함수의 전부다. 한 번 쓰면 관리자도 못 바꾼다.
     */
    function anchor(uint256 batchId, bytes32 merkleRoot, bytes32[] calldata commitHashes)
        external
        onlyRole(ANCHOR_ROLE)
    {
        if (batchId == 0) revert InvalidBatchId();
        if (merkleRoot == bytes32(0)) revert EmptyRoot();
        if (commitHashes.length == 0) revert EmptyCommitCount();
        if (_roots[batchId] != bytes32(0)) revert BatchAlreadyAnchored(batchId);

        bytes32 computed = _computeRoot(commitHashes);
        if (computed != merkleRoot) revert RootMismatch(merkleRoot, computed);

        _roots[batchId] = merkleRoot;
        emit Anchored(batchId, merkleRoot, commitHashes);
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

    /**
     * @notice 커밋 해시가 그 배치에 포함됐는지 proof 로 확인한다. 권한 제한 없음.
     * @param commitHash 커밋 해시 원본(리프가 아니다 — 안에서 도메인 분리 해시를 얹는다)
     * @param proof      아래에서 위로, 각 레벨의 형제 해시. 정렬 결합이라 좌우 정보가 필요 없다
     * @return 앵커되지 않은 batchId 면 항상 false (루트가 0 이라 어떤 proof 도 0 을 만들지 못한다)
     *
     * FE 검증 화면과 검증 API(ANT-CHAIN-06)가 eth_call 한 번으로 쓴다. 클라이언트마다 머클
     * 접기를 다시 구현하지 않게 하려는 것이다 — 규격이 이 함수 하나에 고정된다.
     */
    function isIncluded(uint256 batchId, bytes32 commitHash, bytes32[] calldata proof)
        external
        view
        returns (bool)
    {
        bytes32 root = _roots[batchId];
        if (root == bytes32(0)) return false;

        bytes32 node = keccak256(abi.encodePacked(commitHash));
        for (uint256 i = 0; i < proof.length; i++) {
            node = _hashPair(node, proof[i]);
        }
        return node == root;
    }

    /**
     * 리프 목록으로 루트를 계산한다. 배열을 제자리에서 접는다 — 다음 레벨의 i/2 번째 칸은
     * 항상 현재 레벨의 i 번째 칸보다 앞이거나 같아서 덮어써도 아직 안 읽은 값을 건드리지 않는다.
     */
    function _computeRoot(bytes32[] calldata commitHashes) private pure returns (bytes32) {
        uint256 n = commitHashes.length;
        bytes32[] memory level = new bytes32[](n);
        for (uint256 i = 0; i < n; i++) {
            level[i] = keccak256(abi.encodePacked(commitHashes[i])); // 도메인 분리 리프
        }
        while (n > 1) {
            uint256 next = 0;
            for (uint256 i = 0; i < n; i += 2) {
                level[next++] = (i + 1 < n)
                    ? _hashPair(level[i], level[i + 1])
                    : level[i]; // 홀수 꼬리는 승격 — 복제하지 않는다
            }
            n = next;
        }
        return level[0];
    }

    /// 정렬 결합. bytes32 비교는 부호 없는 바이트 사전순 — Java 의 compareUnsigned 와 같다.
    function _hashPair(bytes32 a, bytes32 b) private pure returns (bytes32) {
        return a < b ? keccak256(abi.encodePacked(a, b)) : keccak256(abi.encodePacked(b, a));
    }
}
