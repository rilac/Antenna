package ssafy.a507.backend.domain.chain.relay;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigInteger;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.web3j.abi.FunctionEncoder;
import org.web3j.utils.Numeric;

/**
 * ANT-CHAIN-10 — 토큰 릴레이어가 손으로 인코딩하는 셀렉터·인자가 컨트랙트와 같은지.
 *
 * <p>ABI 코드젠을 안 쓰므로 여기서 어긋나면 첫 mint 가 "함수 없음" 으로 revert 한다. 기대값은 Solidity 규칙
 * (keccak256("이름(타입,…)") 앞 4바이트)으로 직접 계산한 값이고, 함수·이벤트의 존재는 {@code PredictTokenConfigTest} 가
 * ABI 리소스로 본다.
 */
class Web3jTokenRelayerEncodingTest {

    private static final String A = "0x00000000000000000000000000000000000000aa";
    private static final String B = "0x00000000000000000000000000000000000000bb";

    @Test
    @DisplayName("mint(address,uint256,bytes32) — 셀렉터 + 인자 3개 = 100바이트")
    void mint_calldata() {
        String data = FunctionEncoder.encode(Web3jTokenRelayer.mintFunction(A, BigInteger.valueOf(1000), TokenReason.SIGNUP_BONUS));

        assertThat(data).startsWith(Web3jAnchorRelayer.selector("mint(address,uint256,bytes32)"));
        assertThat(data).hasSize(2 + 100 * 2);
        assertThat(data.substring(10, 74)).endsWith("aa"); // to
        assertThat(new BigInteger(data.substring(74, 138), 16)).isEqualTo(1000); // amount — 정수 ANT 그대로
        // reason: "SIGNUP_BONUS" ASCII 왼쪽 정렬 + 0 패딩
        assertThat(data.substring(138)).isEqualTo("5349474e55505f424f4e5553" + "00".repeat(20));
    }

    @Test
    @DisplayName("burn(address,uint256,bytes32) · subscribe(address,address,uint256) · balanceOf(address) 셀렉터")
    void selectors() {
        assertThat(FunctionEncoder.encode(Web3jTokenRelayer.burnFunction(A, BigInteger.TWO, TokenReason.SLOT_OVER)))
                .startsWith(Web3jAnchorRelayer.selector("burn(address,uint256,bytes32)"));
        String sub = FunctionEncoder.encode(Web3jTokenRelayer.subscribeFunction(A, B, BigInteger.valueOf(30_000)));
        assertThat(sub).startsWith(Web3jAnchorRelayer.selector("subscribe(address,address,uint256)"));
        assertThat(sub).hasSize(2 + 100 * 2);
        assertThat(sub.substring(74, 138)).endsWith("bb"); // creator
        assertThat(FunctionEncoder.encode(Web3jTokenRelayer.balanceOfFunction(A)))
                .startsWith(Web3jAnchorRelayer.selector("balanceOf(address)"))
                .hasSize(2 + 36 * 2);
    }

    @Test
    @DisplayName("bytes32 reason — 7개 전부 32바이트 안이고, 이벤트에서 되돌리면 같은 값이다")
    void reason_roundtrip() {
        for (TokenReason r : TokenReason.values()) {
            byte[] raw = r.toBytes32().getValue();
            assertThat(raw).hasSize(32);
            assertThat(TokenReason.fromBytes32(raw)).contains(r);
        }
        assertThat(Numeric.toHexString(TokenReason.SLOT_OVER.toBytes32().getValue()))
                .isEqualTo("0x534c4f545f4f564552" + "00".repeat(23));
        assertThat(TokenReason.fromBytes32(new byte[32])).isEmpty();
        assertThat(TokenReason.fromBytes32(Numeric.hexStringToByteArray("0x4d4947524154494f4e" + "00".repeat(23)))).isEmpty(); // MIGRATION — 이관 스크립트용, enum 에 없음
    }

    @Test
    @DisplayName("ledger 전용 사유(SUBSCRIBE·SUBSCRIBE_INCOME)는 mint/burn 에 못 싣는다")
    void ledger_only_reason_rejected() {
        assertThat(TokenReason.SUBSCRIBE.isOnChain()).isFalse();
        assertThat(TokenReason.SUBSCRIBE_INCOME.isOnChain()).isFalse();
        assertThatThrownBy(() -> Web3jTokenRelayer.burnFunction(A, BigInteger.ONE, TokenReason.SUBSCRIBE))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("금액 0·음수·uint256 초과와 주소 형식 오류는 인코딩 전에 IllegalArgumentException")
    void argument_validation() {
        assertThatThrownBy(() -> Web3jTokenRelayer.amount(BigInteger.ZERO)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> Web3jTokenRelayer.amount(BigInteger.valueOf(-1))).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> Web3jTokenRelayer.amount(BigInteger.TWO.pow(256))).isInstanceOf(IllegalArgumentException.class);
        assertThat(Web3jTokenRelayer.amount(BigInteger.TWO.pow(256).subtract(BigInteger.ONE)).getValue()).isEqualTo(BigInteger.TWO.pow(256).subtract(BigInteger.ONE));
        assertThatThrownBy(() -> Web3jTokenRelayer.address("0xabc")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> Web3jTokenRelayer.address(null)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("custom error 셀렉터 7종 — OZ 둘 + PredictToken 다섯")
    void error_selectors() {
        assertThat(Web3jTokenRelayer.decodeErrorName(
                        Web3jAnchorRelayer.selector("ERC20InsufficientBalance(address,uint256,uint256)") + "00".repeat(96)))
                .isEqualTo("ERC20InsufficientBalance");
        assertThat(Web3jTokenRelayer.decodeErrorName(
                        Web3jAnchorRelayer.selector("AccessControlUnauthorizedAccount(address,bytes32)") + "00".repeat(64)))
                .isEqualTo("AccessControlUnauthorizedAccount");
        // SSAFY 실측 09-10: transfer 시도가 0xa24e573d 로 revert 했다 — TransferDisabled() 의 셀렉터
        assertThat(Web3jAnchorRelayer.selector("TransferDisabled()")).isEqualTo("0xa24e573d");
        assertThat(Web3jTokenRelayer.decodeErrorName("0xa24e573d")).isEqualTo("TransferDisabled");
        assertThat(Web3jTokenRelayer.decodeErrorName(Web3jAnchorRelayer.selector("SelfSubscribe()"))).isEqualTo("SelfSubscribe");
        assertThat(Web3jTokenRelayer.decodeErrorName(Web3jAnchorRelayer.selector("ZeroAmount()"))).isEqualTo("ZeroAmount");
        assertThat(Web3jTokenRelayer.decodeErrorName(Web3jAnchorRelayer.selector("ZeroAddress()"))).isEqualTo("ZeroAddress");
        assertThat(Web3jTokenRelayer.decodeErrorName(Web3jAnchorRelayer.selector("SameAddress()"))).isEqualTo("SameAddress");
        assertThat(Web3jTokenRelayer.decodeErrorName("0xdeadbeef")).isEqualTo("Unknown(0xdeadbeef)");
        // Besu 가 error.data 를 따옴표 포함 문자열로 주는 함정 — 앵커에서 실측
        assertThat(Web3jTokenRelayer.decodeErrorName("\"" + Web3jAnchorRelayer.selector("ZeroAmount()") + "\"")).isEqualTo("ZeroAmount");
    }
}
