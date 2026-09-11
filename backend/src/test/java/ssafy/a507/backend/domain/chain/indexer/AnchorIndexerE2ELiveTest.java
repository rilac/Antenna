package ssafy.a507.backend.domain.chain.indexer;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.web3j.utils.Numeric;
import ssafy.a507.backend.domain.chain.config.ChainProperties;
import ssafy.a507.backend.domain.chain.config.CommitAnchorProperties;
import ssafy.a507.backend.domain.chain.config.PredictTokenProperties;
import ssafy.a507.backend.domain.chain.merkle.MerkleTree;
import ssafy.a507.backend.domain.chain.relay.ChainConnection;

/**
 * ANT-CHAIN-04 · v3 ANT-CHAIN-13 — 실체인 읽기. 평소 빌드에서는 돌지 않는다(*E2ELiveTest 는 CI 전체 빌드에서 제외).
 *
 * <pre>
 * CHAIN_RPC_URL=ws://127.0.0.1:8545 CONTRACT_COMMIT_ANCHOR=0x… INDEXER_FROM_BLOCK=0 \
 * ./gradlew test --tests '*AnchorIndexerE2ELiveTest*'
 * </pre>
 *
 * <p>SpringBootTest 가 아니다 — DB 없이 {@link Web3jChainLogSource} 만 실제 노드에 붙여 본다. 읽기 전용이라 흔적이 없다.
 * v2 때는 공용 배포본의 batchId 1~4 를 기대값으로 박았지만 그 배포본은 폐기됐다. 그래서 특정 값이 아니라
 * <b>불변식</b>을 본다 — 모든 Anchored 로그에서 "이벤트의 커밋 해시로 다시 접은 루트 == 이벤트의 루트" 이고, 블록 해시 조회가 로그와 같다.
 * 로그가 0건이어도(방금 배포) 실패하지 않는다. {@code Web3jAnchorRelayerE2ELiveTest} 를 먼저 돌리면 로그가 생긴다.
 */
@EnabledIfEnvironmentVariable(named = "CHAIN_RPC_URL", matches = "wss?://.+")
@EnabledIfEnvironmentVariable(named = "CONTRACT_COMMIT_ANCHOR", matches = "0x[0-9a-fA-F]{40}")
class AnchorIndexerE2ELiveTest {

    private final long fromBlock = Long.parseLong(System.getenv().getOrDefault("INDEXER_FROM_BLOCK", "0"));

    private Web3jChainLogSource source() {
        ChainProperties props =
                new ChainProperties(
                        Long.parseLong(System.getenv().getOrDefault("CHAIN_ID", "31221")),
                        System.getenv("CHAIN_RPC_URL"),
                        new ChainProperties.Relayer(null),
                        new ChainProperties.Anchor("-", 60, new ChainProperties.Anchor.Retry(3, 10)),
                        new ChainProperties.Indexer("-", fromBlock, 10_000));
        CommitAnchorProperties contract = new CommitAnchorProperties(System.getenv("CONTRACT_COMMIT_ANCHOR"));
        return new Web3jChainLogSource(new ChainConnection(props), contract, new PredictTokenProperties(null));
    }

    @Test
    @DisplayName("시작 블록부터 Anchored 로그를 읽으면 전부 v3 로 풀리고, 커밋 해시로 다시 접은 루트가 이벤트의 루트와 같다")
    void live_logs_are_self_consistent() {
        Web3jChainLogSource s = source();
        long head = s.latestBlock();

        List<AnchoredLog> logs = s.anchoredLogs(fromBlock, head);
        System.out.println("Anchored 로그 " + logs.size() + "건 (블록 " + fromBlock + " ~ " + head + ")");
        for (AnchoredLog l : logs) {
            System.out.println("  root " + l.merkleRoot() + " block " + l.blockNumber() + " leaves " + l.commitHashes().size());
            byte[] folded = MerkleTree.build(l.commitHashes().stream().map(Numeric::hexStringToByteArray).toList()).root();
            assertThat(Numeric.toHexString(folded)).isEqualTo(l.merkleRoot());
            assertThat(s.blockHash(l.blockNumber())).contains(l.blockHash());
        }
        // 오름차순
        for (int i = 1; i < logs.size(); i++) {
            assertThat(logs.get(i).blockNumber()).isGreaterThanOrEqualTo(logs.get(i - 1).blockNumber());
        }
        assertThat(s.blockHash(head + 1_000_000L)).isEmpty();
    }
}
