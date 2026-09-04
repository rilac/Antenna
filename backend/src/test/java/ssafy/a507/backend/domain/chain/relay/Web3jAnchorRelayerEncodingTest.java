package ssafy.a507.backend.domain.chain.relay;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.web3j.abi.FunctionEncoder;
import org.web3j.crypto.Hash;

/**
 * ANT-CHAIN-05 — 릴레이어가 손으로 인코딩하는 셀렉터가 컨트랙트와 같은지.
 *
 * <p>ABI 코드젠을 안 쓰므로 여기서 어긋나면 첫 tx 가 "함수 없음" 으로 revert 한다. 기대값은
 * Solidity 규칙(keccak256("이름(타입,…)") 앞 4바이트)으로 직접 계산한 값이다.
 */
class Web3jAnchorRelayerEncodingTest {

    @Test
    @DisplayName("anchor(uint256,bytes32,bytes32[]) 셀렉터와 인자 인코딩")
    void anchor_calldata() {
        byte[] root = Hash.sha3("root".getBytes());
        List<byte[]> leaves = List.of(Hash.sha3("a".getBytes()), Hash.sha3("b".getBytes()));

        String data = FunctionEncoder.encode(Web3jAnchorRelayer.anchorFunction(7, root, leaves));

        assertThat(data).startsWith(Web3jAnchorRelayer.selector("anchor(uint256,bytes32,bytes32[])"));
        // 셀렉터(4) + batchId(32) + root(32) + 배열 오프셋(32) + 길이(32) + 리프 2×32 = 196바이트 = 392 hex + "0x"
        assertThat(data).hasSize(2 + 196 * 2);
        assertThat(data.substring(10, 74)).endsWith("7"); // batchId = 7
    }

    @Test
    @DisplayName("custom error 셀렉터 6종을 이름으로 디코딩한다")
    void error_selectors() {
        assertThat(Web3jAnchorRelayer.decodeErrorName(Web3jAnchorRelayer.selector("BatchAlreadyAnchored(uint256)") + "00".repeat(32)))
                .isEqualTo("BatchAlreadyAnchored");
        assertThat(Web3jAnchorRelayer.decodeErrorName(Web3jAnchorRelayer.selector("RootMismatch(bytes32,bytes32)") + "00".repeat(64)))
                .isEqualTo("RootMismatch");
        assertThat(Web3jAnchorRelayer.decodeErrorName(Web3jAnchorRelayer.selector("InvalidBatchId()"))).isEqualTo("InvalidBatchId");
        assertThat(Web3jAnchorRelayer.decodeErrorName(Web3jAnchorRelayer.selector("EmptyRoot()"))).isEqualTo("EmptyRoot");
        assertThat(Web3jAnchorRelayer.decodeErrorName(Web3jAnchorRelayer.selector("EmptyCommitCount()"))).isEqualTo("EmptyCommitCount");
        assertThat(Web3jAnchorRelayer.decodeErrorName(
                        Web3jAnchorRelayer.selector("AccessControlUnauthorizedAccount(address,bytes32)") + "00".repeat(64)))
                .isEqualTo("AccessControlUnauthorizedAccount");
        assertThat(Web3jAnchorRelayer.decodeErrorName("0xdeadbeef")).isEqualTo("Unknown(0xdeadbeef)");
        assertThat(Web3jAnchorRelayer.decodeErrorName(null)).isEqualTo("Unknown(no data)");
        // Besu 가 error.data 를 JSON 문자열로 줘서 web3j 가 따옴표까지 넘긴다 — 실측에서 잡힌 케이스
        assertThat(Web3jAnchorRelayer.decodeErrorName("\"" + Web3jAnchorRelayer.selector("BatchAlreadyAnchored(uint256)") + "00".repeat(32) + "\""))
                .isEqualTo("BatchAlreadyAnchored");
    }

    @Test
    @DisplayName("셀렉터는 시그니처 keccak 의 앞 4바이트다 — 알려진 값으로 고정")
    void selector_known_value() {
        // Error(string) 의 셀렉터 0x08c379a0 는 EVM 표준값이다. 계산 방식이 맞는지 이걸로 본다.
        assertThat(Web3jAnchorRelayer.selector("Error(string)")).isEqualTo("0x08c379a0");
    }
}
