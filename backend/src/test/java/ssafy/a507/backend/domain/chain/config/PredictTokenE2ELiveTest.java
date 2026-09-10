package ssafy.a507.backend.domain.chain.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.math.BigInteger;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.web3j.abi.FunctionEncoder;
import org.web3j.abi.FunctionReturnDecoder;
import org.web3j.abi.TypeReference;
import org.web3j.abi.datatypes.Address;
import org.web3j.abi.datatypes.Bool;
import org.web3j.abi.datatypes.Function;
import org.web3j.abi.datatypes.Type;
import org.web3j.abi.datatypes.Utf8String;
import org.web3j.abi.datatypes.generated.Bytes32;
import org.web3j.abi.datatypes.generated.Uint256;
import org.web3j.abi.datatypes.generated.Uint8;
import org.web3j.crypto.Hash;
import org.web3j.protocol.Web3j;
import org.web3j.protocol.core.DefaultBlockParameterName;
import org.web3j.protocol.core.methods.request.Transaction;
import org.web3j.protocol.core.methods.response.EthCall;
import ssafy.a507.backend.domain.chain.relay.ChainConnection;

/**
 * ANT-CHAIN-03 — 배포된 PredictToken 을 실체인에서 읽어 본다. 평소 빌드에서는 돌지 않는다.
 *
 * <pre>
 * CHAIN_RPC_URL=wss://ws.ssafy-blockchain.com CONTRACT_PREDICT_TOKEN=0x… TOKEN_OPERATOR=0x… \
 * ./gradlew test --tests '*PredictTokenE2ELiveTest*'
 * </pre>
 *
 * <p>읽기 전용이다 — tx 를 보내지 않으니 팀 공용 배포본에 흔적이 남지 않는다. 확인하는 것은 plan ①·⑤ 의
 * 되돌릴 수 없는 값들: decimals 0 · 심볼 ANT · 오퍼레이터에게 OPERATOR_ROLE 이 있고 MOVER_ROLE 은 없음.
 * 이름의 E2ELiveTest 는 팀 컨벤션(build.gradle) — CI 전체 빌드에서 제외된다.
 */
@EnabledIfEnvironmentVariable(named = "CHAIN_RPC_URL", matches = "wss?://.+")
@EnabledIfEnvironmentVariable(named = "CONTRACT_PREDICT_TOKEN", matches = "0x[0-9a-fA-F]{40}")
class PredictTokenE2ELiveTest {

    private static final String ZERO = "0x0000000000000000000000000000000000000000";

    private final String token = System.getenv("CONTRACT_PREDICT_TOKEN").toLowerCase();

    private Web3j web3j() {
        ChainProperties props =
                new ChainProperties(
                        31221L,
                        System.getenv("CHAIN_RPC_URL"),
                        new ChainProperties.Relayer(null),
                        new ChainProperties.Anchor("-", 60, new ChainProperties.Anchor.Retry(3, 10)),
                        new ChainProperties.Indexer("-", 0, 10_000));
        return new ChainConnection(props).web3j();
    }

    @Test
    @DisplayName("SSAFY 체인 — decimals 0 · ANT · 역할 분리 실측")
    void live_read() throws IOException {
        Web3j w = web3j();

        assertThat(callUint(w, new Function("decimals", List.of(), List.of(new TypeReference<Uint8>() {}))))
                .isEqualTo(BigInteger.ZERO);
        assertThat(callString(w, "symbol")).isEqualTo("ANT");
        assertThat(callString(w, "name")).isEqualTo("Antenna");
        assertThat(callUint(w, new Function("PLATFORM_SHARE_BPS", List.of(), List.of(new TypeReference<Uint256>() {}))))
                .isEqualTo(BigInteger.valueOf(3000));

        String treasury = callAddress(w, "treasury");
        System.out.println("treasury = " + treasury);
        assertThat(treasury).isNotEqualTo(ZERO);

        String operator = System.getenv("TOKEN_OPERATOR");
        if (operator != null && operator.matches("0x[0-9a-fA-F]{40}")) {
            byte[] operatorRole = Hash.sha3("OPERATOR_ROLE".getBytes());
            byte[] moverRole = Hash.sha3("MOVER_ROLE".getBytes());
            assertThat(hasRole(w, operatorRole, operator)).as("오퍼레이터에게 OPERATOR_ROLE").isTrue();
            assertThat(hasRole(w, moverRole, operator)).as("오퍼레이터에게 MOVER_ROLE 은 없어야 한다").isFalse();
            assertThat(hasRole(w, moverRole, treasury)).as("수납 주소에 MOVER_ROLE 은 없어야 한다").isFalse();
        }
    }

    // ── eth_call 헬퍼 ────────────────────────────────────────────────────

    private List<Type> call(Web3j w, Function fn) throws IOException {
        EthCall res =
                w.ethCall(
                                Transaction.createEthCallTransaction(ZERO, token, FunctionEncoder.encode(fn)),
                                DefaultBlockParameterName.LATEST)
                        .send();
        assertThat(res.isReverted()).as("eth_call reverted: " + res.getRevertReason()).isFalse();
        return FunctionReturnDecoder.decode(res.getValue(), fn.getOutputParameters());
    }

    private BigInteger callUint(Web3j w, Function fn) throws IOException {
        return (BigInteger) call(w, fn).get(0).getValue();
    }

    private String callString(Web3j w, String name) throws IOException {
        Function fn = new Function(name, List.of(), List.of(new TypeReference<Utf8String>() {}));
        return (String) call(w, fn).get(0).getValue();
    }

    private String callAddress(Web3j w, String name) throws IOException {
        Function fn = new Function(name, List.of(), List.of(new TypeReference<Address>() {}));
        return ((String) call(w, fn).get(0).getValue()).toLowerCase();
    }

    private boolean hasRole(Web3j w, byte[] role, String account) throws IOException {
        Function fn =
                new Function(
                        "hasRole",
                        List.of(new Bytes32(role), new Address(account)),
                        List.of(new TypeReference<Bool>() {}));
        return (Boolean) call(w, fn).get(0).getValue();
    }
}
