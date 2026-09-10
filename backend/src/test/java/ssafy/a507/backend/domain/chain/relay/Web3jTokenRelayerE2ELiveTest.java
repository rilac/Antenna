package ssafy.a507.backend.domain.chain.relay;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.math.BigInteger;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.web3j.crypto.Keys;
import org.web3j.protocol.Web3j;
import org.web3j.protocol.core.methods.response.Transaction;
import org.web3j.protocol.core.methods.response.TransactionReceipt;
import ssafy.a507.backend.common.error.BusinessException;
import ssafy.a507.backend.common.error.ErrorCode;
import ssafy.a507.backend.domain.chain.config.ChainProperties;
import ssafy.a507.backend.domain.chain.config.PredictTokenProperties;

/**
 * ANT-CHAIN-10 — 실체인 왕복. 평소 빌드에서는 돌지 않는다(build.gradle 의 *E2ELiveTest 제외).
 *
 * <pre>
 * CHAIN_RPC_URL=wss://ws.ssafy-blockchain.com RELAYER_PRIVATE_KEY=0x… CONTRACT_PREDICT_TOKEN=0xe11d… \
 * ./gradlew test --tests '*Web3jTokenRelayerE2ELiveTest*'
 * </pre>
 *
 * <p>팀 공용 배포본에 흔적이 남는다 — 일회용 지갑에 mint 1 · mint 1 · burn 2. 돌렸으면 {@code contracts/README.md}
 * "팀 공용 배포본에 남긴 흔적" 표에 적는다. 총공급은 0 으로 되돌아온다.
 *
 * <p>확인하는 것: ① <b>mint 둘을 receipt 없이 연달아</b> 보내도 nonce 가 N, N+1 로 이어진다({@link TxSender} 락 + pending
 * nonce 의 실증) ② 둘 다 status 1 ③ balanceOf 0 → 2 → 0 ④ 잔액이 0 인 지갑의 burn 은 시뮬레이션에서 INSUFFICIENT_BALANCE 로
 * 막혀 tx 가 나가지 않는다. mint→burn 을 연달아 보내지 않는 이유: burn 의 eth_call 은 latest 상태라 아직 안 채굴된 mint 를
 * 못 보고 잔액 부족으로 막힌다 — 그 자체가 시뮬레이션의 한계를 보여 주는 케이스다.
 */
@EnabledIfEnvironmentVariable(named = "CHAIN_RPC_URL", matches = "wss?://.+")
@EnabledIfEnvironmentVariable(named = "RELAYER_PRIVATE_KEY", matches = "0x[0-9a-fA-F]{64}")
@EnabledIfEnvironmentVariable(named = "CONTRACT_PREDICT_TOKEN", matches = "0x[0-9a-fA-F]{40}")
class Web3jTokenRelayerE2ELiveTest {

    private final ChainProperties props =
            new ChainProperties(
                    31221L,
                    System.getenv("CHAIN_RPC_URL"),
                    new ChainProperties.Relayer(System.getenv("RELAYER_PRIVATE_KEY")),
                    new ChainProperties.Anchor("-", 60, new ChainProperties.Anchor.Retry(3, 10)),
                    new ChainProperties.Indexer("-", 0, 10_000));
    private final ChainConnection connection = new ChainConnection(props);
    private final Web3jTokenRelayer relayer =
            new Web3jTokenRelayer(
                    new PredictTokenProperties(System.getenv("CONTRACT_PREDICT_TOKEN")),
                    connection,
                    new TxSender(props, connection));

    @Test
    @DisplayName("SSAFY 체인 — mint 1 · mint 1 연속 전송(nonce N, N+1) → 잔액 2 → burn 2 → 0 → burn 시뮬 잔액 부족")
    void live_roundtrip() throws Exception {
        assertThat(relayer.isEnabled()).isTrue();
        String wallet = "0x" + Keys.getAddress(Keys.createEcKeyPair()); // 일회용. 키는 버린다
        System.out.println("wallet = " + wallet);
        assertThat(relayer.balanceOf(wallet)).isEqualTo(BigInteger.ZERO);

        // ① receipt 를 기다리지 않고 연달아 둘
        String mint1 = relayer.mint(wallet, BigInteger.ONE, TokenReason.SIGNUP_BONUS);
        String mint2 = relayer.mint(wallet, BigInteger.ONE, TokenReason.SIGNUP_BONUS);
        System.out.println("mint1 = " + mint1 + "\nmint2 = " + mint2);
        assertThat(mint1).isNotEqualTo(mint2);

        TransactionReceipt r1 = waitReceipt(mint1);
        TransactionReceipt r2 = waitReceipt(mint2);
        assertThat(r1.isStatusOK()).isTrue();
        assertThat(r2.isStatusOK()).isTrue();
        BigInteger n1 = nonceOf(mint1);
        BigInteger n2 = nonceOf(mint2);
        System.out.println("blocks " + r1.getBlockNumber() + " / " + r2.getBlockNumber() + ", nonce " + n1 + " / " + n2);
        assertThat(n2).isEqualTo(n1.add(BigInteger.ONE));

        // ③ 잔액
        assertThat(relayer.balanceOf(wallet)).isEqualTo(BigInteger.TWO);
        String burn = relayer.burn(wallet, BigInteger.TWO, TokenReason.SLOT_OVER);
        TransactionReceipt r3 = waitReceipt(burn);
        System.out.println("burn = " + burn + " block " + r3.getBlockNumber());
        assertThat(r3.isStatusOK()).isTrue();
        assertThat(relayer.balanceOf(wallet)).isEqualTo(BigInteger.ZERO);

        // ④ 잔액 0 에서 burn — 보내기 전에 막힌다
        assertThatThrownBy(() -> relayer.burn(wallet, BigInteger.ONE, TokenReason.SLOT_OVER))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.INSUFFICIENT_BALANCE);
    }

    private TransactionReceipt waitReceipt(String txHash) throws Exception {
        Web3j w = connection.web3j();
        for (int i = 0; i < 30; i++) {
            Optional<TransactionReceipt> r = w.ethGetTransactionReceipt(txHash).send().getTransactionReceipt();
            if (r.isPresent()) {
                return r.get();
            }
            Thread.sleep(2_000);
        }
        throw new AssertionError("receipt 60초 대기 초과: " + txHash);
    }

    private BigInteger nonceOf(String txHash) throws IOException {
        Transaction t = connection.web3j().ethGetTransactionByHash(txHash).send().getTransaction().orElseThrow();
        return t.getNonce();
    }
}
