package ssafy.a507.backend.domain.chain.relay;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.web3j.crypto.Hash;
import ssafy.a507.backend.domain.chain.config.ChainProperties;
import ssafy.a507.backend.domain.chain.config.CommitAnchorProperties;
import ssafy.a507.backend.domain.chain.merkle.MerkleTree;

/**
 * ANT-CHAIN-05 · v3 ANT-CHAIN-13 — 실체인 왕복. 평소 빌드에서는 돌지 않는다.
 *
 * <pre>
 * # 로컬 Hardhat 체인에서 돌린다(contracts/deployments/README.md "로컬") — 운영 컨트랙트에 테스트 흔적을 남기지 않는다
 * CHAIN_RPC_URL=ws://127.0.0.1:8545 CHAIN_ID=31337 RELAYER_PRIVATE_KEY=0x… CONTRACT_COMMIT_ANCHOR=0x… \
 * ./gradlew test --tests '*Web3jAnchorRelayerE2ELiveTest*'
 * </pre>
 *
 * <p>이름의 E2ELiveTest 는 팀 컨벤션(build.gradle) — CI 전체 빌드에서 제외되고 --tests 로 지목할 때만 돈다.
 * 리프는 실행마다 달라서(nanoTime) 몇 번을 돌려도 새 루트다 — v2 처럼 batchId 를 올려 가며 돌릴 필요가 없다.
 */
@EnabledIfEnvironmentVariable(named = "CHAIN_RPC_URL", matches = "wss?://.+")
@EnabledIfEnvironmentVariable(named = "RELAYER_PRIVATE_KEY", matches = "(0x)?[0-9a-fA-F]{64}")
@EnabledIfEnvironmentVariable(named = "CONTRACT_COMMIT_ANCHOR", matches = "0x[0-9a-fA-F]{40}")
class Web3jAnchorRelayerE2ELiveTest {

    private Web3jAnchorRelayer relayer() {
        ChainProperties props =
                new ChainProperties(
                        Long.parseLong(System.getenv().getOrDefault("CHAIN_ID", "31221")),
                        System.getenv("CHAIN_RPC_URL"),
                        new ChainProperties.Relayer(System.getenv("RELAYER_PRIVATE_KEY")),
                        new ChainProperties.Anchor(
                                "-", 60, new ChainProperties.Anchor.Retry(3, 10)),
                        new ChainProperties.Indexer("-", 0, 10_000));
        CommitAnchorProperties contract = new CommitAnchorProperties(System.getenv("CONTRACT_COMMIT_ANCHOR"));
        ChainConnection connection = new ChainConnection(props);
        return new Web3jAnchorRelayer(props, contract, connection, new TxSender(props, connection));
    }

    @Test
    @DisplayName("anchor(CONFIRMED) → anchoredAt(루트) > 0 → 같은 루트 재전송은 ALREADY_ANCHORED")
    void live_roundtrip() {
        Web3jAnchorRelayer r = relayer();
        assertThat(r.isEnabled()).isTrue();

        String run = Long.toString(System.nanoTime());
        List<byte[]> leaves =
                List.of(
                        Hash.sha3(("live-" + run + "-0").getBytes()),
                        Hash.sha3(("live-" + run + "-1").getBytes()),
                        Hash.sha3(("live-" + run + "-2").getBytes()));
        byte[] root = MerkleTree.build(leaves).root();
        assertThat(r.anchoredAt(root)).isZero();

        AnchorResult first = r.anchor(root, leaves);
        System.out.println("anchor → " + first);
        assertThat(first.status()).isEqualTo(AnchorResult.Status.CONFIRMED);
        assertThat(first.txHash()).startsWith("0x");
        assertThat(first.blockNumber()).isPositive();
        assertThat(r.anchoredAt(root)).isEqualTo(first.blockNumber());

        AnchorResult second = r.anchor(root, leaves);
        System.out.println("resend → " + second);
        assertThat(second.status()).isEqualTo(AnchorResult.Status.ALREADY_ANCHORED);
    }

    @Test
    @DisplayName("틀린 루트는 RootMismatch 로 보내기 전에 막히고 아무것도 박히지 않는다")
    void live_root_mismatch() {
        Web3jAnchorRelayer r = relayer();
        List<byte[]> leaves = List.of(Hash.sha3(("mismatch-" + System.nanoTime()).getBytes()));
        byte[] wrongRoot = Hash.sha3(("wrong-" + System.nanoTime()).getBytes());

        try {
            r.anchor(wrongRoot, leaves);
            throw new AssertionError("RootMismatch 가 나야 한다");
        } catch (AnchorRevertException e) {
            assertThat(e.getErrorName()).isEqualTo("RootMismatch");
        }
        assertThat(r.anchoredAt(wrongRoot)).isZero();
    }
}
