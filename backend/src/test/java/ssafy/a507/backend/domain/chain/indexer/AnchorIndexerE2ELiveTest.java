package ssafy.a507.backend.domain.chain.indexer;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import ssafy.a507.backend.domain.chain.config.ChainProperties;
import ssafy.a507.backend.domain.chain.config.CommitAnchorProperties;
import ssafy.a507.backend.domain.chain.config.PredictTokenProperties;
import ssafy.a507.backend.domain.chain.relay.ChainConnection;

/**
 * ANT-CHAIN-04 — 실체인 읽기. 평소 빌드에서는 돌지 않는다(*E2ELiveTest 는 CI 전체 빌드에서 제외).
 *
 * <pre>
 * CHAIN_RPC_URL=wss://ws.ssafy-blockchain.com CONTRACT_COMMIT_ANCHOR=0x07f8CfE2bc6174D62BE8226E5e8699ffBf0d6D6a \
 * ./gradlew test --tests '*AnchorIndexerE2ELiveTest*'
 * </pre>
 *
 * <p>SpringBootTest 가 아니다 — DB 없이 {@link Web3jChainLogSource} 만 실제 노드에 붙여 본다. 릴레이어 키는 필요 없다.
 * 읽기만 하므로 몇 번을 돌려도 체인에 아무것도 남지 않는다. 기대값은 2026-09-04 SSAFY 배포본(contracts/README.md 함정 2 표)이다.
 */
@EnabledIfEnvironmentVariable(named = "CHAIN_RPC_URL", matches = "wss?://.+")
@EnabledIfEnvironmentVariable(named = "CONTRACT_COMMIT_ANCHOR", matches = "0x[0-9a-fA-F]{40}")
class AnchorIndexerE2ELiveTest {

    /** contracts/deployments/ssafy.json 의 blockNumber. */
    static final long DEPLOY_BLOCK = 11_186_682L;

    private Web3jChainLogSource source() {
        ChainProperties props =
                new ChainProperties(
                        31221L,
                        System.getenv("CHAIN_RPC_URL"),
                        new ChainProperties.Relayer(null),
                        new ChainProperties.Anchor("-", 60, new ChainProperties.Anchor.Retry(3, 10)),
                        new ChainProperties.Indexer("-", DEPLOY_BLOCK, 10_000));
        CommitAnchorProperties contract = new CommitAnchorProperties(System.getenv("CONTRACT_COMMIT_ANCHOR"));
        return new Web3jChainLogSource(new ChainConnection(props), contract, new PredictTokenProperties(null));
    }

    @Test
    @DisplayName("SSAFY 체인 — 배포 블록부터 Anchored 로그를 읽으면 batchId 1~4 가 README 표의 루트로 온다")
    void live_logs_since_deploy() {
        Web3jChainLogSource s = source();

        long head = s.latestBlock();
        assertThat(head).isGreaterThan(11_187_694L);

        List<AnchoredLog> logs = s.anchoredLogs(DEPLOY_BLOCK, head);
        System.out.println("Anchored 로그 " + logs.size() + "건 (블록 " + DEPLOY_BLOCK + " ~ " + head + ")");
        for (AnchoredLog l : logs) {
            System.out.println("  batchId " + l.batchId() + " block " + l.blockNumber() + " root " + l.merkleRoot() + " leaves " + l.commitHashes().size());
        }
        assertThat(logs.size()).isGreaterThanOrEqualTo(4);

        assertThat(find(logs, 1).merkleRoot()).startsWith("0x5c9ab357");
        assertThat(find(logs, 1).commitHashes()).hasSize(7);
        assertThat(find(logs, 3).merkleRoot()).startsWith("0x95a9ac4e");
        assertThat(find(logs, 4).merkleRoot()).startsWith("0xe44f2a9f");
        assertThat(find(logs, 4).blockNumber()).isEqualTo(11_187_694L);
        assertThat(find(logs, 4).txHash()).isEqualTo(AnchoredLogDecoderTest.TX4);
        // 오름차순
        for (int i = 1; i < logs.size(); i++) {
            assertThat(logs.get(i).blockNumber()).isGreaterThanOrEqualTo(logs.get(i - 1).blockNumber());
        }
    }

    @Test
    @DisplayName("SSAFY 체인 — 블록 해시 조회는 픽스처와 같고, 미래 블록은 empty")
    void live_block_hash() {
        Web3jChainLogSource s = source();

        Optional<String> hash = s.blockHash(11_187_694L);
        assertThat(hash).contains(AnchoredLogDecoderTest.BLOCK_HASH4);
        assertThat(s.blockHash(s.latestBlock() + 1_000_000L)).isEmpty();
    }

    private static AnchoredLog find(List<AnchoredLog> logs, long batchId) {
        return logs.stream().filter(l -> l.batchId() == batchId).findFirst().orElseThrow();
    }
}
