package ssafy.a507.backend.domain.chain.relay;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.web3j.crypto.Hash;
import org.web3j.utils.Numeric;
import ssafy.a507.backend.domain.chain.config.ChainProperties;
import ssafy.a507.backend.domain.chain.config.CommitAnchorProperties;
import ssafy.a507.backend.domain.chain.merkle.MerkleTree;

/**
 * ANT-CHAIN-05 — 실체인 왕복. 평소 빌드에서는 돌지 않는다.
 *
 * <pre>
 * CHAIN_RPC_URL=wss://ws.ssafy-blockchain.com RELAYER_PRIVATE_KEY=0x… CONTRACT_COMMIT_ANCHOR=0x… \
 * LIVE_BATCH_ID=2 ./gradlew test --tests '*Web3jAnchorRelayerE2ELiveTest*'
 * </pre>
 *
 * <p>이름의 E2ELiveTest 는 팀 컨벤션(build.gradle, 임대연 bd36570) — CI 전체 빌드에서 제외되고 --tests 로 지목할 때만 돈다.
 * CI 변수에 체인 키가 있어도 배포 파이프라인이 실체인에 tx 를 보내지 않게 하려는 것이다.
 *
 * <p>SpringBootTest 가 아니다 — DB 없이 릴레이어만 실제 노드에 붙여 본다. 같은 batchId 를 두 번 보내면
 * 두 번째가 ALREADY_ANCHORED 로 돌아오는 것까지 본다. 재실행 시 LIVE_BATCH_ID 를 올려라.
 */
@EnabledIfEnvironmentVariable(named = "CHAIN_RPC_URL", matches = "wss?://.+")
@EnabledIfEnvironmentVariable(named = "RELAYER_PRIVATE_KEY", matches = "0x[0-9a-fA-F]{64}")
@EnabledIfEnvironmentVariable(named = "CONTRACT_COMMIT_ANCHOR", matches = "0x[0-9a-fA-F]{40}")
class Web3jAnchorRelayerE2ELiveTest {

    private Web3jAnchorRelayer relayer() {
        ChainProperties props =
                new ChainProperties(
                        31221L,
                        System.getenv("CHAIN_RPC_URL"),
                        new ChainProperties.Relayer(System.getenv("RELAYER_PRIVATE_KEY")),
                        new ChainProperties.Anchor(
                                "-", 60, new ChainProperties.Anchor.Retry(3, 10)));
        CommitAnchorProperties contract = new CommitAnchorProperties(System.getenv("CONTRACT_COMMIT_ANCHOR"));
        return new Web3jAnchorRelayer(props, contract, new ChainConnection(props));
    }

    @Test
    @DisplayName("SSAFY 체인 — rootOf · anchor(CONFIRMED) · 재전송(ALREADY_ANCHORED) 왕복")
    void live_roundtrip() {
        Web3jAnchorRelayer r = relayer();
        assertThat(r.isEnabled()).isTrue();

        // CHAIN-08 복구 데모가 batchId 1 을 박아 뒀다.
        assertThat(r.rootOf(1)).isNotEqualTo(new byte[32]);

        long batchId = Long.parseLong(System.getenv().getOrDefault("LIVE_BATCH_ID", "2"));
        List<byte[]> leaves =
                List.of(
                        Hash.sha3(("live-" + batchId + "-0").getBytes()),
                        Hash.sha3(("live-" + batchId + "-1").getBytes()),
                        Hash.sha3(("live-" + batchId + "-2").getBytes()));
        byte[] root = MerkleTree.build(leaves).root();

        AnchorResult first = r.anchor(batchId, root, leaves);
        System.out.println("anchor → " + first);
        assertThat(first.status()).isIn(AnchorResult.Status.CONFIRMED, AnchorResult.Status.ALREADY_ANCHORED);
        if (first.status() == AnchorResult.Status.CONFIRMED) {
            assertThat(first.txHash()).startsWith("0x");
            assertThat(first.blockNumber()).isPositive();
        }
        assertThat(Numeric.toHexString(r.rootOf(batchId))).isEqualTo(Numeric.toHexString(root));

        AnchorResult second = r.anchor(batchId, root, leaves);
        System.out.println("resend → " + second);
        assertThat(second.status()).isEqualTo(AnchorResult.Status.ALREADY_ANCHORED);
    }

    @Test
    @DisplayName("SSAFY 체인 — 틀린 루트는 RootMismatch 로 보내기 전에 막힌다")
    void live_root_mismatch() {
        Web3jAnchorRelayer r = relayer();
        List<byte[]> leaves = List.of(Hash.sha3("mismatch".getBytes()));
        byte[] wrongRoot = Hash.sha3("wrong".getBytes());
        long batchId = 999_999_999L; // 절대 안 쓸 id — 시뮬레이션에서 막히므로 소모되지 않는다

        try {
            r.anchor(batchId, wrongRoot, leaves);
            throw new AssertionError("RootMismatch 가 나야 한다");
        } catch (AnchorRevertException e) {
            assertThat(e.getErrorName()).isEqualTo("RootMismatch");
        }
        assertThat(r.rootOf(batchId)).isEqualTo(new byte[32]);
    }
}
