package ssafy.a507.backend.domain.chain.relay;

import java.io.IOException;
import java.math.BigInteger;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Pattern;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.web3j.abi.FunctionEncoder;
import org.web3j.abi.FunctionReturnDecoder;
import org.web3j.abi.TypeReference;
import org.web3j.abi.datatypes.Address;
import org.web3j.abi.datatypes.Function;
import org.web3j.abi.datatypes.Type;
import org.web3j.abi.datatypes.generated.Uint256;
import org.web3j.protocol.core.DefaultBlockParameterName;
import org.web3j.protocol.core.methods.request.Transaction;
import org.web3j.protocol.core.methods.response.EthCall;
import org.web3j.protocol.core.methods.response.EthEstimateGas;
import ssafy.a507.backend.common.error.BusinessException;
import ssafy.a507.backend.common.error.ErrorCode;
import ssafy.a507.backend.domain.chain.config.PredictTokenProperties;

/**
 * web3j 로 PredictToken 을 부르는 릴레이어 (ANT-CHAIN-10).
 *
 * <p>흐름은 {@link Web3jAnchorRelayer} 와 같다: ① {@code eth_call} 시뮬레이션으로 revert 이름을 먼저 읽는다
 * ② 통과하면 {@link TxSender}(키 락) 로 raw tx 전송 ③ <b>receipt 는 기다리지 않는다</b> — 여기가 앵커와 다르다.
 * 요청 스레드에서 블록 하나(10초)를 잡을 수 없고, 확정은 인덱서 ②(ANT-CHAIN-11)가 이벤트로 본다.
 *
 * <p>시뮬레이션이 막는 것: 잔액 부족은 409 로, 키 권한은 503 으로, 나머지 거부는 서버 버그로 가른다.
 * 시뮬레이션은 {@code latest} 상태를 보므로 "시뮬 통과 후 채굴 전에 잔액이 바뀐" 경우(같은 지갑 소각 두 건이
 * 수 초 안에 겹침)는 못 막는다 — 그건 채굴에서 revert 되고 이벤트가 없다. CHAIN-11 이 receipt 로 닫는다.
 *
 * <p>ABI 코드젠을 쓰지 않는다(CHAIN-01·05 판단 유지). 함수 넷·에러 일곱의 인코딩은 여기서 손으로 하고,
 * 시그니처가 컨트랙트와 같은지는 {@code PredictTokenConfigTest} 가 ABI 리소스로, 셀렉터 값은
 * {@code Web3jTokenRelayerEncodingTest} 가 고정한다.
 */
@Slf4j
@Component
public class Web3jTokenRelayer implements TokenRelayer {

    /** estimateGas 가 안 될 때의 상한. mint ≈ 55k · subscribe ≈ 80k 라 넉넉하다. 앵커의 10M 은 리프 수 비례라 컸다. */
    static final BigInteger GAS_LIMIT_FALLBACK = BigInteger.valueOf(300_000L);
    private static final BigInteger UINT256_MAX = BigInteger.TWO.pow(256).subtract(BigInteger.ONE);
    private static final Pattern ADDRESS = Pattern.compile("0x[0-9a-fA-F]{40}");

    /** custom error 셀렉터 → 이름. OZ 의 것 둘 + PredictToken 의 것 다섯. */
    static final Map<String, String> ERROR_SELECTORS =
            Map.of(
                    Web3jAnchorRelayer.selector("ERC20InsufficientBalance(address,uint256,uint256)"),
                            "ERC20InsufficientBalance",
                    Web3jAnchorRelayer.selector("AccessControlUnauthorizedAccount(address,bytes32)"),
                            "AccessControlUnauthorizedAccount",
                    Web3jAnchorRelayer.selector("TransferDisabled()"), "TransferDisabled",
                    Web3jAnchorRelayer.selector("SelfSubscribe()"), "SelfSubscribe",
                    Web3jAnchorRelayer.selector("ZeroAmount()"), "ZeroAmount",
                    Web3jAnchorRelayer.selector("ZeroAddress()"), "ZeroAddress",
                    Web3jAnchorRelayer.selector("SameAddress()"), "SameAddress");

    private final PredictTokenProperties token;
    private final ChainConnection connection;
    private final TxSender txSender;

    public Web3jTokenRelayer(PredictTokenProperties token, ChainConnection connection, TxSender txSender) {
        this.token = token;
        this.connection = connection;
        this.txSender = txSender;
        if (isEnabled()) {
            log.info("토큰 릴레이어 준비: 오퍼레이터 {}, PredictToken {}", txSender.senderAddress(), token.address());
        } else {
            log.info("토큰 릴레이어 꺼짐 — CHAIN_RPC_URL · RELAYER_PRIVATE_KEY · CONTRACT_PREDICT_TOKEN 를 확인하라");
        }
    }

    @Override
    public boolean isEnabled() {
        return txSender.isEnabled() && token.isDeployed();
    }

    @Override
    public String mint(String to, BigInteger amount, TokenReason reason) {
        return send("mint", mintFunction(to, amount, reason));
    }

    @Override
    public String burn(String from, BigInteger amount, TokenReason reason) {
        return send("burn", burnFunction(from, amount, reason));
    }

    @Override
    public String subscribe(String subscriber, String creator, BigInteger amount) {
        return send("subscribe", subscribeFunction(subscriber, creator, amount));
    }

    @Override
    public BigInteger balanceOf(String address) {
        requireEnabled();
        Function fn = balanceOfFunction(address);
        String data = FunctionEncoder.encode(fn);
        return withReconnect(
                () -> {
                    EthCall call = ethCall(data);
                    if (call.isReverted() || call.getValue() == null) {
                        // balanceOf 는 revert 하지 않는 함수다. 여기 오면 주소가 틀렸거나 노드가 이상하다.
                        throw new BusinessException(ErrorCode.CHAIN_UNAVAILABLE);
                    }
                    List<Type> out = FunctionReturnDecoder.decode(call.getValue(), fn.getOutputParameters());
                    return out.isEmpty() ? BigInteger.ZERO : ((Uint256) out.get(0)).getValue();
                });
    }

    // ── 내부 ────────────────────────────────────────────────────────────

    /** 시뮬 → 전송. 세 함수가 같은 길을 간다. */
    private String send(String what, Function fn) {
        requireEnabled();
        String data = FunctionEncoder.encode(fn);

        // ① 시뮬레이션 — Besu 의 receipt 에는 revert 데이터가 없어서, 보내기 전에 여기서 이름을 읽는다.
        Optional<String> revert = withReconnect(() -> simulate(data));
        if (revert.isPresent()) {
            throw mapRevert(what, revert.get());
        }

        // ② 전송 — receipt 는 안 기다린다.
        String txHash = withReconnect(() -> txSender.send(token.normalizedAddress(), data, gasLimit(data)));
        log.info("토큰 {} tx 전송: {}", what, txHash);
        return txHash;
    }

    /** revert 이름 → 예외. 사용자·운영자가 대응할 수 있는 둘만 BusinessException, 나머지는 서버 버그. */
    private RuntimeException mapRevert(String what, String name) {
        switch (name) {
            case "ERC20InsufficientBalance":
                return new BusinessException(ErrorCode.INSUFFICIENT_BALANCE);
            case "AccessControlUnauthorizedAccount":
                log.error(
                        "토큰 {} 거부 — 릴레이어 키 {} 에 OPERATOR_ROLE 이 없다. RELAYER_PRIVATE_KEY 와 배포본의 operator 를 대조하라",
                        what,
                        txSender.senderAddress());
                return new BusinessException(ErrorCode.CHAIN_UNAVAILABLE);
            default:
                log.error("토큰 {} 거부 — {} (호출자 검증을 뚫고 온 서버 버그)", what, name);
                return new TokenRevertException(name);
        }
    }

    /** @return revert 면 에러 이름, 아니면 empty */
    private Optional<String> simulate(String data) throws IOException {
        EthCall call = ethCall(data);
        if (!call.isReverted() && call.getError() == null) {
            return Optional.empty();
        }
        String revertData = null;
        if (call.getError() != null && call.getError().getData() != null) {
            revertData = call.getError().getData();
        } else if (call.getValue() != null) {
            revertData = call.getValue();
        }
        return Optional.of(decodeErrorName(revertData));
    }

    private EthCall ethCall(String data) throws IOException {
        Transaction tx =
                Transaction.createEthCallTransaction(txSender.senderAddress(), token.normalizedAddress(), data);
        return connection.web3j().ethCall(tx, DefaultBlockParameterName.LATEST).send();
    }

    private BigInteger gasLimit(String data) throws IOException {
        Transaction tx =
                Transaction.createFunctionCallTransaction(
                        txSender.senderAddress(), null, BigInteger.ZERO, null, token.normalizedAddress(), data);
        EthEstimateGas est = connection.web3j().ethEstimateGas(tx).send();
        if (est.hasError() || est.getAmountUsed() == null) {
            return GAS_LIMIT_FALLBACK;
        }
        return est.getAmountUsed().multiply(BigInteger.valueOf(120)).divide(BigInteger.valueOf(100));
    }

    /** {@link Web3jAnchorRelayer} 의 것과 같은 규칙. 통과시키는 예외에 {@link TokenRevertException} 이 더해진다. */
    private <T> T withReconnect(IoSupplier<T> op) {
        try {
            return op.get();
        } catch (Exception first) {
            if (first instanceof BusinessException be) {
                throw be;
            }
            if (first instanceof TokenRevertException tre) {
                throw tre;
            }
            if (!connection.resetIfConnectionError(first)) {
                log.warn("체인 호출 실패: {}", first.toString());
                throw new BusinessException(ErrorCode.CHAIN_UNAVAILABLE);
            }
            try {
                return op.get();
            } catch (Exception second) {
                if (second instanceof BusinessException be) {
                    throw be;
                }
                if (second instanceof TokenRevertException tre) {
                    throw tre;
                }
                log.warn("체인 재연결 후에도 실패: {}", second.toString());
                connection.reset();
                throw new BusinessException(ErrorCode.CHAIN_UNAVAILABLE);
            }
        }
    }

    private void requireEnabled() {
        if (!isEnabled()) {
            throw new BusinessException(ErrorCode.CHAIN_UNAVAILABLE);
        }
    }

    // ── 인코딩 (테스트가 고정하는 표면) ─────────────────────────────────

    static Function mintFunction(String to, BigInteger amount, TokenReason reason) {
        return new Function(
                "mint",
                List.of(address(to), amount(amount), onChainReason(reason)),
                List.of());
    }

    static Function burnFunction(String from, BigInteger amount, TokenReason reason) {
        return new Function(
                "burn",
                List.of(address(from), amount(amount), onChainReason(reason)),
                List.of());
    }

    static Function subscribeFunction(String subscriber, String creator, BigInteger amount) {
        return new Function(
                "subscribe",
                List.of(address(subscriber), address(creator), amount(amount)),
                List.of());
    }

    static Function balanceOfFunction(String owner) {
        return new Function("balanceOf", List.of(address(owner)), List.of(new TypeReference<Uint256>() {}));
    }

    /** 0 과 uint256 초과는 인코딩 전에 거른다. 컨트랙트도 ZeroAmount 로 막지만 그건 RPC 왕복 뒤다. */
    static Uint256 amount(BigInteger amount) {
        if (amount == null || amount.signum() <= 0 || amount.compareTo(UINT256_MAX) > 0) {
            throw new IllegalArgumentException("금액은 1 이상 uint256 이하의 정수 ANT 여야 한다: " + amount);
        }
        return new Uint256(amount);
    }

    static Address address(String hex) {
        if (hex == null || !ADDRESS.matcher(hex).matches()) {
            throw new IllegalArgumentException("지갑 주소 형식이 아니다: " + hex);
        }
        return new Address(hex);
    }

    static org.web3j.abi.datatypes.generated.Bytes32 onChainReason(TokenReason reason) {
        if (reason == null || !reason.isOnChain()) {
            throw new IllegalArgumentException("mint/burn 에 실을 수 있는 사유가 아니다: " + reason);
        }
        return reason.toBytes32();
    }

    /**
     * revert 데이터의 앞 4바이트로 이름을 찾는다. Besu 가 {@code error.data} 를 따옴표 포함 JSON 문자열로 주는 함정은
     * {@link Web3jAnchorRelayer#decodeErrorName} 과 같다(SSAFY 실측 09-04).
     */
    static String decodeErrorName(String revertData) {
        if (revertData == null) {
            return "Unknown(no data)";
        }
        String cleaned = revertData.trim();
        if (cleaned.startsWith("\"") && cleaned.endsWith("\"") && cleaned.length() >= 2) {
            cleaned = cleaned.substring(1, cleaned.length() - 1);
        }
        if (cleaned.length() < 10 || !cleaned.startsWith("0x")) {
            return "Unknown(no data)";
        }
        String sel = cleaned.substring(0, 10).toLowerCase();
        return ERROR_SELECTORS.getOrDefault(sel, "Unknown(" + sel + ")");
    }

    @FunctionalInterface
    private interface IoSupplier<T> {
        T get() throws Exception;
    }
}
