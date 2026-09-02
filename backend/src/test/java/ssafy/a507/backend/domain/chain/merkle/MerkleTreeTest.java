package ssafy.a507.backend.domain.chain.merkle;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.web3j.crypto.Hash;
import org.web3j.utils.Numeric;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * MerkleTree 단위 + 크로스 검증 (ANT-CHAIN-07).
 *
 * <p>크로스 검증이 이 파일의 핵심이다 — 픽스처는 ethers(JS)가 만든 기준값이고
 * ({@code contracts/scripts/gen-merkle-fixture.mjs}), Java가 같은 리프에서 같은
 * root·proof를 내야 한다. 해시(keccak vs SHA3-256)·결합(정렬)·홀수 노드(승격) 규칙이
 * 하나라도 어긋나면 여기서 깨진다. 서버와 검증 페이지가 어긋나면 발급된 번들 전부가
 * 무의미해지므로, 이 테스트가 그 어긋남의 첫 방어선이다.
 */
@DisplayName("MerkleTree — 머클 증명 규격")
class MerkleTreeTest {

    private static byte[] commit(String seed) {
        return Hash.sha3(seed.getBytes(java.nio.charset.StandardCharsets.UTF_8));
    }

    private static List<byte[]> commits(int n) {
        List<byte[]> list = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            list.add(commit("unit-commit-" + i));
        }
        return list;
    }

    @Test
    @DisplayName("모든_리프의_proof가_루트로_접힌다_홀수_짝수_모두")
    void 모든_리프의_proof가_루트로_접힌다_홀수_짝수_모두() {
        for (int n : new int[] {1, 2, 3, 4, 5, 7, 8, 16, 33}) {
            List<byte[]> leaves = commits(n);
            MerkleTree tree = MerkleTree.build(leaves);
            byte[] root = tree.root();
            for (int i = 0; i < n; i++) {
                assertThat(MerkleTree.verify(leaves.get(i), tree.proof(i), root))
                        .as("n=%d, leaf=%d", n, i)
                        .isTrue();
            }
        }
    }

    @Test
    @DisplayName("리프_1개면_proof는_비고_root는_리프_노드다")
    void 리프_1개면_proof는_비고_root는_리프_노드다() {
        byte[] one = commit("only");
        MerkleTree tree = MerkleTree.build(List.of(one));
        assertThat(tree.proof(0)).isEmpty();
        // 도메인 분리 규칙: root == keccak256(commitHash). commitHash 그대로가 아니다.
        assertThat(tree.root()).isEqualTo(Hash.sha3(one));
    }

    @Test
    @DisplayName("리프_0개는_거부한다_빈_배치는_앵커_대상이_아니다")
    void 리프_0개는_거부한다_빈_배치는_앵커_대상이_아니다() {
        assertThatThrownBy(() -> MerkleTree.build(List.of()))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("변조_검출_다른_커밋이나_다른_proof는_실패한다")
    void 변조_검출_다른_커밋이나_다른_proof는_실패한다() {
        List<byte[]> leaves = commits(5);
        MerkleTree tree = MerkleTree.build(leaves);
        byte[] root = tree.root();
        List<byte[]> proof0 = tree.proof(0);

        // 커밋을 바꾸면 실패 — "예측을 고치면 검증이 깨진다"의 최소 재현
        assertThat(MerkleTree.verify(commit("tampered"), proof0, root)).isFalse();
        // 남의 proof로도 실패
        assertThat(MerkleTree.verify(leaves.get(0), tree.proof(1), root)).isFalse();
        // proof 순서를 뒤집어도 실패
        List<byte[]> reversed = new ArrayList<>(proof0);
        java.util.Collections.reverse(reversed);
        if (reversed.size() > 1) {
            assertThat(MerkleTree.verify(leaves.get(0), reversed, root)).isFalse();
        }
    }

    @Test
    @DisplayName("도메인_분리_내부_노드를_커밋으로_위장한_가짜_포함_증명이_막힌다")
    void 도메인_분리_내부_노드를_커밋으로_위장한_가짜_포함_증명이_막힌다() {
        // 제안서 부록의 verify가 뚫리는 시나리오다: 리프 = commitHash 그대로면,
        // "내부 노드 값 + 그 위쪽 경로"가 유효한 포함 증명으로 통과한다("나 앵커했었다" 위조).
        // 우리 규격은 리프에 keccak을 한 겹 더 얹으므로(입력 32B vs 내부 64B),
        // 내부 노드 I를 commitHash 자리에 넣으면 검증기가 keccak(I)에서 접기 시작해 실패한다.
        List<byte[]> leaves = commits(4);
        MerkleTree tree = MerkleTree.build(leaves);
        byte[] root = tree.root();

        // 내부 노드(리프 0·1의 부모)를 손으로 재구성: 정렬 결합
        byte[] n0 = Hash.sha3(leaves.get(0));
        byte[] n1 = Hash.sha3(leaves.get(1));
        byte[] parent01 = sortedPair(n0, n1);
        byte[] parent23 = sortedPair(Hash.sha3(leaves.get(2)), Hash.sha3(leaves.get(3)));
        // 검증: parent01을 "커밋"이라 주장하고 proof = [parent23] — 도메인 분리가 없다면 통과했을 조합
        assertThat(sortedPair(parent01, parent23)).isEqualTo(root); // 트리 구조 자체는 이 모양이 맞다
        assertThat(MerkleTree.verify(parent01, List.of(parent23), root)).isFalse(); // 그러나 위장은 막힌다
    }

    private static byte[] sortedPair(byte[] a, byte[] b) {
        byte[] lo = a;
        byte[] hi = b;
        for (int i = 0; i < 32; i++) {
            int cmp = Integer.compare(a[i] & 0xFF, b[i] & 0xFF);
            if (cmp != 0) {
                if (cmp > 0) {
                    lo = b;
                    hi = a;
                }
                break;
            }
        }
        byte[] joined = new byte[64];
        System.arraycopy(lo, 0, joined, 0, 32);
        System.arraycopy(hi, 0, joined, 32, 32);
        return Hash.sha3(joined);
    }

    @Nested
    @DisplayName("크로스 검증 — ethers 기준값 재현")
    class 크로스검증 {

        private JsonNode loadFixture() {
            try (InputStream in =
                    getClass().getResourceAsStream("/merkle/merkle-cross-fixture.json")) {
                return JsonMapper.builder().build().readTree(in);
            } catch (Exception e) {
                throw new IllegalStateException(
                        "픽스처가 없다. contracts 에서 node scripts/gen-merkle-fixture.mjs 를 돌려라.", e);
            }
        }

        @Test
        @DisplayName("Java_트리가_ethers가_만든_root와_proof를_전부_재현한다")
        void Java_트리가_ethers가_만든_root와_proof를_전부_재현한다() {
            JsonNode cases = loadFixture().get("cases");
            assertThat(cases).isNotNull();
            assertThat(cases.size()).isGreaterThanOrEqualTo(5);

            for (JsonNode c : cases) {
                int n = c.get("leafCount").asInt();
                List<byte[]> leaves = new ArrayList<>();
                c.get("commitHashes").forEach(h -> leaves.add(Numeric.hexStringToByteArray(h.asText())));

                MerkleTree tree = MerkleTree.build(leaves);
                assertThat(Numeric.toHexString(tree.root()))
                        .as("root, n=%d", n)
                        .isEqualTo(c.get("root").asText());

                for (int i = 0; i < n; i++) {
                    List<String> expected = new ArrayList<>();
                    c.get("proofs").get(i).forEach(p -> expected.add(p.asText()));
                    List<String> actual =
                            tree.proof(i).stream().map(Numeric::toHexString).toList();
                    assertThat(actual).as("proof, n=%d leaf=%d", n, i).isEqualTo(expected);
                }
            }
        }
    }

    @Test
    @DisplayName("ProofBundle_조립_hex_직렬화가_규격대로다")
    void ProofBundle_조립_hex_직렬화가_규격대로다() {
        List<byte[]> leaves = commits(3);
        MerkleTree tree = MerkleTree.build(leaves);
        ProofBundle bundle =
                ProofBundle.of(
                        31221L,
                        "0x9eec7fb9c14bc248b2a1f5b0829c1a1f098d45df",
                        7L,
                        "0xabc",
                        "2026-09-02T15:00:00Z",
                        leaves.get(1),
                        tree.proof(1));
        assertThat(bundle.version()).isEqualTo(ProofBundle.CURRENT_VERSION);
        assertThat(bundle.commitHash()).isEqualTo(Numeric.toHexString(leaves.get(1)));
        assertThat(bundle.proof())
                .isEqualTo(tree.proof(1).stream().map(Numeric::toHexString).toList());
        // 번들에 실린 값으로 검증이 왕복되는지 — 직렬화가 정보를 잃지 않는다는 확인
        assertThat(
                        MerkleTree.verify(
                                Numeric.hexStringToByteArray(bundle.commitHash()),
                                bundle.proof().stream().map(Numeric::hexStringToByteArray).toList(),
                                tree.root()))
                .isTrue();
    }
}
