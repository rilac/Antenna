package ssafy.a507.backend.domain.chain.indexer;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigInteger;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import ssafy.a507.backend.domain.chain.config.ChainProperties;
import ssafy.a507.backend.domain.chain.config.CommitAnchorProperties;
import ssafy.a507.backend.domain.chain.config.PredictTokenProperties;
import ssafy.a507.backend.domain.chain.relay.ChainConnection;
import ssafy.a507.backend.domain.chain.relay.TokenReason;

/**
 * ANT-CHAIN-11 — SSAFY 실체인에서 배포 블록부터 PredictToken 이벤트를 읽어 {@code contracts/README.md} 흔적표와 대조한다.
 * 읽기 전용 — 흔적을 남기지 않는다. 평소 빌드에서는 돌지 않는다.
 *
 * <pre>
 * CHAIN_RPC_URL=wss://ws.ssafy-blockchain.com CONTRACT_PREDICT_TOKEN=0xe11d… ./gradlew test --tests '*TokenIndexerE2ELiveTest*'
 * </pre>
 */
@EnabledIfEnvironmentVariable(named = "CHAIN_RPC_URL", matches = "wss?://.+")
@EnabledIfEnvironmentVariable(named = "CONTRACT_PREDICT_TOKEN", matches = "0x[0-9a-fA-F]{40}")
class TokenIndexerE2ELiveTest {

    static final long DEPLOY_BLOCK = 11_233_486L;
    static final String MINT_1000_TX = "0x663377b8b1d697ec5bd97b8357ea40a30e6c408f20e3d07da3dd087554bda15b";

    private Web3jChainLogSource source() {
        ChainProperties props =
                new ChainProperties(
                        31221L,
                        System.getenv("CHAIN_RPC_URL"),
                        new ChainProperties.Relayer(null),
                        new ChainProperties.Anchor("-", 60, new ChainProperties.Anchor.Retry(3, 10)),
                        new ChainProperties.Indexer("-", DEPLOY_BLOCK, 10_000));
        return new Web3jChainLogSource(
                new ChainConnection(props),
                new CommitAnchorProperties(null),
                new PredictTokenProperties(System.getenv("CONTRACT_PREDICT_TOKEN")));
    }

    @Test
    @DisplayName("SSAFY 체인 — 배포 블록부터 README 흔적표의 mint 1000 · burn 1000 · mint 1 · mint 1 · burn 2 가 그 블록에 있다")
    void live_read() {
        Web3jChainLogSource s = source();
        long head = s.latestBlock();
        assertThat(head).isGreaterThan(DEPLOY_BLOCK);

        List<TokenLog> all = new java.util.ArrayList<>();
        for (long from = DEPLOY_BLOCK; from <= head; from += 10_000) {
            all.addAll(s.tokenLogs(from, Math.min(head, from + 9_999)));
        }
        all.forEach(l -> System.out.println(l.blockNumber() + " " + l.kind() + " " + l.amount() + " " + l.reasonText() + " " + l.txHash()));

        assertThat(all).anySatisfy(l -> {
            assertThat(l.blockNumber()).isEqualTo(11_233_493L);
            assertThat(l.kind()).isEqualTo(TokenLog.Kind.MINTED);
            assertThat(l.amount()).isEqualTo(BigInteger.valueOf(1000));
            assertThat(l.reason()).contains(TokenReason.SIGNUP_BONUS);
        });
        assertThat(all).anySatisfy(l -> {
            assertThat(l.blockNumber()).isEqualTo(11_233_494L);
            assertThat(l.kind()).isEqualTo(TokenLog.Kind.BURNED);
            assertThat(l.amount()).isEqualTo(BigInteger.valueOf(1000));
            assertThat(l.reason()).contains(TokenReason.SLOT_OVER);
        });
        assertThat(all.stream().filter(l -> l.blockNumber() == 11_234_142L && l.kind() == TokenLog.Kind.MINTED && l.amount().equals(BigInteger.ONE)))
                .as("CHAIN-10 Live 가 한 블록에 연달아 넣은 mint 둘")
                .hasSize(2);
        assertThat(all).anySatisfy(l -> {
            assertThat(l.blockNumber()).isEqualTo(11_234_143L);
            assertThat(l.kind()).isEqualTo(TokenLog.Kind.BURNED);
            assertThat(l.amount()).isEqualTo(BigInteger.TWO);
        });
        // ERC-20 Transfer 는 필터에서 빠진다 — Minted/Burned/Subscribed 만
        assertThat(all).allSatisfy(l -> assertThat(l.kind()).isNotNull());

        assertThat(s.receiptStatus(MINT_1000_TX)).contains(true);
        assertThat(s.receiptStatus("0x" + "ab".repeat(32))).isEmpty();
    }
}
