package ssafy.a507.backend.domain.chain.merkle;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.web3j.crypto.Hash;

/**
 * 앵커 배치의 머클 트리 (ANT-CHAIN-07, ANT-CHAIN-02가 소비).
 *
 * <p>커밋 해시들을 하나의 루트로 접고, 리프별 <b>증명 경로(proof)</b>를 함께 내놓는다.
 * proof가 이 클래스의 존재 이유다 — 루트만 필요하면 해시를 반복하면 그만이지만, 사용자에게
 * "네 예측이 이 루트에 들어 있었다"를 서버 없이 증명하게 하려면 형제 해시 경로가 필요하고,
 * 그 경로는 트리를 만드는 쪽만 알 수 있다. DB가 유실되면 영영 못 만드는 값이라
 * 앵커 직후 번들({@link ProofBundle})로 사용자에게 미리 내려보낸다.
 *
 * <p><b>규격 — 서버·컨트랙트(CommitAnchor v2가 온체인에서 같은 계산으로 검산한다)·픽스처 생성기
 * 세 곳이 반드시 같아야 한다.</b> 어긋나면 첫 앵커 tx가 {@code RootMismatch}로 revert한다.
 *
 * <ul>
 *   <li><b>해시</b>: keccak256. {@code MessageDigest.getInstance("SHA3-256")}은 표준화 과정에서
 *       패딩이 바뀐 <b>다른 함수</b>다 — 반드시 web3j {@link Hash#sha3}를 쓴다.
 *   <li><b>리프</b>: {@code keccak256(commitHash)} — commitHash를 그대로 리프로 쓰지 않는다.
 *       내부 노드도 32바이트 해시라서, 그대로 쓰면 내부 노드를 "내 커밋"이라고 위장한
 *       가짜 포함 증명이 검증을 통과한다(2차 프리이미지). 리프 해시는 입력이 32바이트,
 *       내부 노드 해시는 입력이 64바이트라 두 도메인이 원리적으로 겹칠 수 없다.
 *   <li><b>결합</b>: 정렬 결합 {@code keccak256(min ‖ max)} — OpenZeppelin MerkleProof 호환.
 *       좌우 순서 정보가 필요 없어 검증하는 쪽이 proof를 순서대로 접기만 하면 된다.
 *   <li><b>홀수 노드</b>: 마지막 노드를 다음 레벨로 <b>그대로 승격</b>한다. 자기 자신과
 *       짝짓는 방식(Bitcoin)은 같은 리프를 두 번 세는 중복 취약점(CVE-2012-2459류)이 있다.
 *   <li><b>N=0</b>: 거부. 커밋 0건인 날은 앵커 자체를 하지 않는다(CommitAnchor의
 *       {@code EmptyRoot}와 같은 판단). <b>N=1</b>: root = 리프 노드, proof는 빈 배열.
 * </ul>
 */
public final class MerkleTree {

    private static final int HASH_LENGTH = 32;

    /** 각 레벨의 노드들. levels[0] = 리프 노드, 마지막 = [root]. proof 생성에 쓴다. */
    private final List<byte[][]> levels;

    /** 리프 순서 = 입력한 commitHash 순서. 호출자가 순서를 결정·보존한다. */
    private final int leafCount;

    private MerkleTree(List<byte[][]> levels, int leafCount) {
        this.levels = levels;
        this.leafCount = leafCount;
    }

    /**
     * @param commitHashes 그 배치의 커밋 해시들(32바이트). 순서가 곧 leafIndex다 —
     *     호출자(CHAIN-02 배치)는 결정적 순서(예: prediction id 오름차순)로 넘겨야
     *     재계산 때 같은 트리가 나온다.
     */
    public static MerkleTree build(List<byte[]> commitHashes) {
        if (commitHashes == null || commitHashes.isEmpty()) {
            throw new IllegalArgumentException("리프 0개로는 트리를 만들지 않는다 — 빈 배치는 앵커 대상이 아니다.");
        }
        byte[][] level = new byte[commitHashes.size()][];
        for (int i = 0; i < commitHashes.size(); i++) {
            byte[] commitHash = commitHashes.get(i);
            if (commitHash == null || commitHash.length != HASH_LENGTH) {
                throw new IllegalArgumentException("commitHash는 32바이트여야 한다: index " + i);
            }
            // 도메인 분리 리프 — 규격 주석 참고. 여기서 한 겹 더 해시한다.
            level[i] = Hash.sha3(commitHash);
        }

        List<byte[][]> levels = new ArrayList<>();
        levels.add(level);
        while (level.length > 1) {
            byte[][] next = new byte[(level.length + 1) / 2][];
            for (int i = 0; i < level.length; i += 2) {
                next[i / 2] =
                        (i + 1 < level.length)
                                ? hashPair(level[i], level[i + 1])
                                : level[i]; // 홀수 꼬리는 승격 — 복제하지 않는다
            }
            levels.add(next);
            level = next;
        }
        return new MerkleTree(levels, commitHashes.size());
    }

    /** 앵커 tx에 실리는 루트. */
    public byte[] root() {
        byte[] root = levels.get(levels.size() - 1)[0];
        return Arrays.copyOf(root, root.length);
    }

    public int leafCount() {
        return leafCount;
    }

    /**
     * leafIndex번째 리프의 증명 경로 — 아래에서 위로, 각 레벨의 형제 해시.
     * 홀수 꼬리로 승격된 레벨에서는 형제가 없으므로 그 레벨은 건너뛴다(검증 쪽은
     * proof를 순서대로 접기만 하면 되니 "건너뜀"을 알 필요가 없다).
     */
    public List<byte[]> proof(int leafIndex) {
        if (leafIndex < 0 || leafIndex >= leafCount) {
            throw new IllegalArgumentException("리프 범위 밖: " + leafIndex + " / " + leafCount);
        }
        List<byte[]> proof = new ArrayList<>();
        int index = leafIndex;
        for (int depth = 0; depth < levels.size() - 1; depth++) {
            byte[][] level = levels.get(depth);
            int sibling = index ^ 1;
            if (sibling < level.length) {
                proof.add(Arrays.copyOf(level[sibling], HASH_LENGTH));
            }
            index /= 2;
        }
        return proof;
    }

    /**
     * 번들 검증 — 컨트랙트 {@code isIncluded}가 온체인에서 하는 것과 같은 계산의 서버판.
     * CHAIN-06 검증 API가 쓰고, 테스트에서 자체 무결성 확인에 쓴다.
     *
     * @param commitHash 커밋 해시(리프 아님 — 안에서 도메인 분리 해시를 얹는다)
     */
    public static boolean verify(byte[] commitHash, List<byte[]> proof, byte[] root) {
        if (commitHash == null || commitHash.length != HASH_LENGTH) {
            return false;
        }
        byte[] node = Hash.sha3(commitHash);
        for (byte[] sibling : proof) {
            if (sibling == null || sibling.length != HASH_LENGTH) {
                return false;
            }
            node = hashPair(node, sibling);
        }
        return Arrays.equals(node, root);
    }

    /** 정렬 결합. 비교는 부호 없는 바이트 사전순 — JS의 hex 문자열 비교와 같은 결과여야 한다. */
    private static byte[] hashPair(byte[] a, byte[] b) {
        byte[] first = a;
        byte[] second = b;
        if (compareUnsigned(a, b) > 0) {
            first = b;
            second = a;
        }
        byte[] joined = new byte[HASH_LENGTH * 2];
        System.arraycopy(first, 0, joined, 0, HASH_LENGTH);
        System.arraycopy(second, 0, joined, HASH_LENGTH, HASH_LENGTH);
        return Hash.sha3(joined);
    }

    private static int compareUnsigned(byte[] a, byte[] b) {
        for (int i = 0; i < HASH_LENGTH; i++) {
            int cmp = Integer.compare(a[i] & 0xFF, b[i] & 0xFF);
            if (cmp != 0) {
                return cmp;
            }
        }
        return 0;
    }
}
