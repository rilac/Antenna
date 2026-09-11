package ssafy.a507.backend.domain.chain.relay;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.web3j.abi.FunctionEncoder;
import org.web3j.crypto.Hash;
import org.web3j.utils.Numeric;

/**
 * ANT-CHAIN-05 · v3 ANT-CHAIN-13 — 릴레이어가 손으로 인코딩하는 셀렉터가 컨트랙트와 같은지.
 *
 * <p>ABI 코드젠을 안 쓰므로 여기서 어긋나면 첫 tx 가 "함수 없음" 으로 revert 한다. 기대 셀렉터는
 * <b>ethers 가 따로 계산한 값</b>이다(2026-09-11, {@code Interface.getFunction(...).selector}) — 두 구현을 맞대 본다.
 */
class Web3jAnchorRelayerEncodingTest {

    @Test
    @DisplayName("anchor(bytes32,bytes32[]) 셀렉터와 인자 인코딩 — batchId 인자가 없다")
    void anchor_calldata() {
        byte[] root = Hash.sha3("root".getBytes());
        List<byte[]> leaves = List.of(Hash.sha3("a".getBytes()), Hash.sha3("b".getBytes()));

        String data = FunctionEncoder.encode(Web3jAnchorRelayer.anchorFunction(root, leaves));

        assertThat(Web3jAnchorRelayer.selector("anchor(bytes32,bytes32[])")).isEqualTo("0x897be064");
        assertThat(data).startsWith("0x897be064");
        // 셀렉터(4) + root(32) + 배열 오프셋(32) + 길이(32) + 리프 2×32 = 164바이트 = 328 hex + "0x"
        assertThat(data).hasSize(2 + 164 * 2);
        assertThat(data.substring(10, 74)).isEqualTo(Numeric.toHexStringNoPrefix(root)); // 첫 인자가 루트
    }

    @Test
    @DisplayName("anchoredAt(bytes32) 셀렉터는 ethers 값과 같다")
    void anchoredAt_selector() {
        assertThat(Web3jAnchorRelayer.selector("anchoredAt(bytes32)")).isEqualTo("0x9591a610");
    }

    @Test
    @DisplayName("custom error 셀렉터 5종을 이름으로 디코딩한다")
    void error_selectors() {
        assertThat(Web3jAnchorRelayer.selector("AlreadyAnchored(bytes32)")).isEqualTo("0x30d23813");
        assertThat(Web3jAnchorRelayer.decodeErrorName(Web3jAnchorRelayer.selector("AlreadyAnchored(bytes32)") + "00".repeat(32)))
                .isEqualTo("AlreadyAnchored");
        assertThat(Web3jAnchorRelayer.decodeErrorName(Web3jAnchorRelayer.selector("RootMismatch(bytes32,bytes32)") + "00".repeat(64)))
                .isEqualTo("RootMismatch");
        assertThat(Web3jAnchorRelayer.decodeErrorName(Web3jAnchorRelayer.selector("EmptyRoot()"))).isEqualTo("EmptyRoot");
        assertThat(Web3jAnchorRelayer.decodeErrorName(Web3jAnchorRelayer.selector("EmptyCommitCount()"))).isEqualTo("EmptyCommitCount");
        assertThat(Web3jAnchorRelayer.decodeErrorName(
                        Web3jAnchorRelayer.selector("AccessControlUnauthorizedAccount(address,bytes32)") + "00".repeat(64)))
                .isEqualTo("AccessControlUnauthorizedAccount");
        // v2 의 BatchAlreadyAnchored 는 이제 모르는 에러다 — v2 주소를 잘못 넣으면 여기로 온다
        assertThat(Web3jAnchorRelayer.decodeErrorName(Web3jAnchorRelayer.selector("BatchAlreadyAnchored(uint256)") + "00".repeat(32)))
                .startsWith("Unknown(");
        assertThat(Web3jAnchorRelayer.decodeErrorName("0xdeadbeef")).isEqualTo("Unknown(0xdeadbeef)");
        assertThat(Web3jAnchorRelayer.decodeErrorName(null)).isEqualTo("Unknown(no data)");
        // Besu 가 error.data 를 JSON 문자열로 줘서 web3j 가 따옴표까지 넘긴다 — 실측에서 잡힌 케이스
        assertThat(Web3jAnchorRelayer.decodeErrorName("\"" + Web3jAnchorRelayer.selector("AlreadyAnchored(bytes32)") + "00".repeat(32) + "\""))
                .isEqualTo("AlreadyAnchored");
        // Hardhat 은 error.data 를 객체로 한 겹 더 싼다 — 로컬 노드 실측(2026-09-11) 그대로
        assertThat(Web3jAnchorRelayer.decodeErrorName(
                        "{\"message\":\"Error: VM Exception while processing transaction: reverted with custom error 'EmptyRoot()'\","
                                + "\"data\":\"0x53ce4ece\"}"))
                .isEqualTo("EmptyRoot");
        assertThat(Web3jAnchorRelayer.decodeErrorName(
                        "{\"message\":\"x\",\"data\":\"" + Web3jAnchorRelayer.selector("AlreadyAnchored(bytes32)") + "00".repeat(32) + "\"}"))
                .isEqualTo("AlreadyAnchored");
        assertThat(Web3jAnchorRelayer.decodeErrorName("{\"message\":\"no inner data\"}")).isEqualTo("Unknown(no data)");
    }

    @Test
    @DisplayName("셀렉터는 시그니처 keccak 의 앞 4바이트다 — 알려진 값으로 고정")
    void selector_known_value() {
        // Error(string) 의 셀렉터 0x08c379a0 는 EVM 표준값이다. 계산 방식이 맞는지 이걸로 본다.
        assertThat(Web3jAnchorRelayer.selector("Error(string)")).isEqualTo("0x08c379a0");
    }
}
