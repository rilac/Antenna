// SPDX-License-Identifier: MIT
pragma solidity 0.8.28;

import {AccessControl} from "@openzeppelin/contracts/access/AccessControl.sol";

/**
 * @title CommitAnchor (v3 — ANT-CHAIN-13)
 * @notice 앤테나가 하루에 한 번 봉인한 예측 커밋들의 머클루트를 체인에 박는다.
 *
 * 이 서비스가 "예측 기록은 위변조 불가"라고 말할 수 있는 근거는 전부 이 컨트랙트 하나에 걸려 있다.
 * 루트가 체인에 올라간 뒤에는 서버가 그 회차의 예측을 단 한 글자도 고쳐 쓸 수 없다 —
 * 고치면 리프 해시가 바뀌고, 머클 증명으로 루트를 복원했을 때 체인의 값과 안 맞는다.
 *
 * ── v2(ANT-CHAIN-08)에서 바뀐 것 — 저장 칸의 키 ─────────────────────────
 *
 * v2 는 루트를 `_roots[batchId]` 에 적었다. batchId 는 서버 DB 의 anchor_batches.id 였다. 그런데 DB 는
 * 하나가 아니다(팀원 로컬 · 테스트 · 운영 · 백업에서 복원한 DB). 컨트랙트의 번호 공간은 하나라서 두 DB 가
 * 같은 번호를 쓰는 순간 "남의 루트가 박힌 칸" 을 만났고(BATCH_ID_COLLISION), DB 를 초기화하면 번호가 1 로
 * 돌아가 운영도 같은 일을 겪을 수 있었다.
 *
 * v3 는 **머클루트 자체를 칸의 키로** 쓴다. 루트는 그 회차 커밋 해시들로 계산한 값이라
 *   ① 내용이 다르면 칸이 다르다 — DB 가 몇 개든, 몇 번 초기화하든 부딪힐 수 없다
 *   ② 같은 루트가 두 번 오면 그건 같은 내용이다 — 재전송을 성공으로 봐도 남의 것을 내 것으로 오판하지 않는다
 * 그래서 서버의 "충돌 판정" 코드가 통째로 사라진다. 서버가 넘기던 batchId 는 인자에서도 빠진다.
 *
 * ── v2 에서 그대로인 것 ─────────────────────────────────────────────
 *
 * 1. 커밋 해시 전량을 인자로 받아 컨트랙트가 **루트를 다시 계산해 대조**한다(RootMismatch). 체인에 박힌 루트는
 *    이벤트에 공개된 목록과 반드시 짝이 맞는다 — DB 가 사라져도 체인만으로 증명을 재구축할 수 있다.
 * 2. 업그레이더블 프록시를 쓰지 않는다. 앵커링에서 "관리자가 로직을 바꿀 수 있다"는 기능이 아니라 취약점이다.
 * 3. 사용자 서명을 검증하지 않는다. tx 는 서버 릴레이어가 자기 키로 보낸다.
 * 4. 리프를 storage 에 저장하지 않는다. 저장은 "이 루트가 몇 번 블록에 박혔나" 하나뿐이다.
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
 */
contract CommitAnchor is AccessControl {
    /// 앵커 트랜잭션을 보낼 수 있는 롤. 서버 릴레이어 주소가 갖는다. 관리자는 갖지 않는다.
    bytes32 public constant ANCHOR_ROLE = keccak256("ANCHOR_ROLE");

    /**
     * 머클루트 → 그 루트가 박힌 블록 번호. 0 이면 미앵커.
     *
     * 값을 bool 이 아니라 블록 번호로 두는 이유: 검증하는 사람이 "언제 박혔나" 를 이벤트를 뒤지지 않고 한 번에 읽는다.
     * tx 는 0번 블록(제네시스)에 들어갈 수 없으므로 0 을 "미앵커" 센티널로 쓸 수 있다.
     */
    mapping(bytes32 merkleRoot => uint256 blockNumber) private _anchoredAt;

    /**
     * 인덱서(ANT-CHAIN-04)가 구독하는 유일한 이벤트. commitHashes 의 순서가 곧 리프 순서다 —
     * 복구할 때 이 순서 그대로 트리를 다시 만들어야 같은 proof 가 나온다.
     * merkleRoot 만 indexed 다. 로그 필터로 찾는 키이자 서버 DB(anchor_batches.merkle_root)와 맞추는 키다.
     */
    event Anchored(bytes32 indexed merkleRoot, bytes32[] commitHashes);

    /// 같은 루트가 이미 앵커됐다. 같은 내용의 재전송이라 서버는 이것을 성공으로 본다.
    error AlreadyAnchored(bytes32 merkleRoot);
    /// 서버가 준 머클루트가 0. 0 은 어떤 칸의 키로도 쓰지 않는다.
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
     * 릴레이어 키가 새도 피해는 "쓰레기 루트를 올린다"까지이고, 관리자 키로 revokeRole + grantRole 하면
     * 재배포 없이 끝난다. 로컬 개발에서는 같은 주소를 둘 다 넘겨도 된다.
     */
    constructor(address admin, address relayer) {
        _grantRole(DEFAULT_ADMIN_ROLE, admin);
        _grantRole(ANCHOR_ROLE, relayer);
    }

    /**
     * @notice 한 회차의 머클루트를 체인에 박는다. 매일 1회, 앵커 배치(ANT-CHAIN-02)가 릴레이어로 호출한다.
     * @param merkleRoot   서버가 계산한 루트. 컨트랙트가 commitHashes 로 다시 계산해 대조한다. 칸의 키가 된다
     * @param commitHashes 그 회차의 커밋 해시 전량. 순서 = 리프 순서(서버는 prediction id 오름차순)
     *
     * 검사 순서는 루트 0 → 빈 배열 → 이미 있음 → 루트 대조다. 대조를 마지막에 두는 건
     * 싼 검사로 걸러질 오류가 RootMismatch 로 위장되면 원인을 엉뚱한 데서 찾게 되기 때문이다.
     * 덮어쓰기가 없다는 게 이 함수의 전부다. 한 번 쓰면 관리자도 못 바꾼다.
     */
    function anchor(bytes32 merkleRoot, bytes32[] calldata commitHashes) external onlyRole(ANCHOR_ROLE) {
        if (merkleRoot == bytes32(0)) revert EmptyRoot();
        if (commitHashes.length == 0) revert EmptyCommitCount();
        if (_anchoredAt[merkleRoot] != 0) revert AlreadyAnchored(merkleRoot);

        bytes32 computed = _computeRoot(commitHashes);
        if (computed != merkleRoot) revert RootMismatch(merkleRoot, computed);

        _anchoredAt[merkleRoot] = block.number;
        emit Anchored(merkleRoot, commitHashes);
    }

    /**
     * @notice 이 루트가 박힌 블록 번호. 권한 제한 없음 — 누구나 검증할 수 있어야 한다.
     * @return 앵커되지 않은 루트면 0
     *
     * 없는 루트에 revert 하지 않는 건 의도한 것이다. 검증 화면이 "아직 앵커 전" 상태를
     * 예외 처리 없이 그대로 표시할 수 있어야 한다.
     */
    function anchoredAt(bytes32 merkleRoot) external view returns (uint256) {
        return _anchoredAt[merkleRoot];
    }

    /**
     * @notice 커밋 해시가 그 루트의 회차에 포함됐는지 proof 로 확인한다. 권한 제한 없음.
     * @param merkleRoot 확인할 회차의 루트. 앵커되지 않았으면 어떤 proof 로도 false
     * @param commitHash 커밋 해시 원본(리프가 아니다 — 안에서 도메인 분리 해시를 얹는다)
     * @param proof      아래에서 위로, 각 레벨의 형제 해시. 정렬 결합이라 좌우 정보가 필요 없다
     *
     * FE 검증 화면이 eth_call 한 번으로 쓴다. 클라이언트마다 머클 접기를 다시 구현하지 않게 — 규격이 이 함수에 고정된다.
     */
    function isIncluded(bytes32 merkleRoot, bytes32 commitHash, bytes32[] calldata proof)
        external
        view
        returns (bool)
    {
        if (_anchoredAt[merkleRoot] == 0) return false;

        bytes32 node = keccak256(abi.encodePacked(commitHash));
        for (uint256 i = 0; i < proof.length; i++) {
            node = _hashPair(node, proof[i]);
        }
        return node == merkleRoot;
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
