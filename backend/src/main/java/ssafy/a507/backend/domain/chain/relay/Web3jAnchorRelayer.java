package ssafy.a507.backend.domain.chain.relay;

import java.io.IOException;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.web3j.abi.FunctionEncoder;
import org.web3j.abi.FunctionReturnDecoder;
import org.web3j.abi.TypeReference;
import org.web3j.abi.datatypes.DynamicArray;
import org.web3j.abi.datatypes.Function;
import org.web3j.abi.datatypes.Type;
import org.web3j.abi.datatypes.generated.Bytes32;
import org.web3j.abi.datatypes.generated.Uint256;
import org.web3j.crypto.Credentials;
import org.web3j.crypto.Hash;
import org.web3j.protocol.Web3j;
import org.web3j.protocol.core.DefaultBlockParameterName;
import org.web3j.protocol.core.methods.request.Transaction;
import org.web3j.protocol.core.methods.response.EthCall;
import org.web3j.protocol.core.methods.response.EthEstimateGas;
import org.web3j.protocol.core.methods.response.TransactionReceipt;
import org.web3j.utils.Numeric;
import ssafy.a507.backend.common.error.BusinessException;
import ssafy.a507.backend.common.error.ErrorCode;
import ssafy.a507.backend.domain.chain.config.ChainProperties;
import ssafy.a507.backend.domain.chain.config.CommitAnchorProperties;

/**
 * web3j 로 CommitAnchor 를 부르는 릴레이어 (ANT-CHAIN-05).
 *
 * <p>흐름: ① {@code eth_call} 로 시뮬레이션 → revert 면 셀렉터로 에러 이름을 읽는다
 * ② 통과하면 서버 키로 raw tx 서명·전송 (gasPrice 0 — 이 체인은 가스가 공짜다)
 * ③ receipt 를 2초 간격으로 기다린다. 제한 시간을 넘기면 "보냈지만 미확정" 으로 돌려준다.
 *
 * <p>시뮬레이션을 먼저 하는 이유: Besu 의 receipt 에는 revert 데이터가 없다. 그냥 보내면
 * status=0 만 남고 <i>왜</i> 실패했는지는 trace 를 떠야 안다. eth_call 은 revert 데이터를
 * 그대로 돌려주므로 {@code AlreadyAnchored}(이미 성공) 와 {@code RootMismatch}(버그) 를
 * 보내기 전에 가른다. 왕복 하나가 늘지만 실패 tx 를 체인에 안 남긴다.
 *
 * <p>ABI 코드젠을 쓰지 않는다(CHAIN-01 판단 유지). 함수 둘·에러 다섯의 인코딩은 여기서 손으로 하고,
 * 시그니처가 컨트랙트와 같은지는 {@code CommitAnchorConfigTest} 가 ABI 리소스로 고정한다.
 *
 * <p>v3(ANT-CHAIN-13): 칸의 키가 머클루트다. {@code anchor(root, leaves)} · {@code anchoredAt(root)} — DB 배치 id 는 체인에 가지 않는다.
 */
@Slf4j
@Component
public class Web3jAnchorRelayer implements AnchorRelayer {

    private static final long RECEIPT_POLL_MILLIS = 2_000;
    /** estimateGas 가 안 될 때의 상한. 500 리프 로컬 실측 0.9M 의 10배 — 블록 한도가 사실상 무제한이라 넉넉해도 된다. */
    private static final BigInteger GAS_LIMIT_FALLBACK = BigInteger.valueOf(10_000_000L);

    /** custom error 셀렉터 → 이름. 시그니처 문자열의 keccak 앞 4바이트. */
    private static final Map<String, String> ERROR_SELECTORS =
            Map.of(
                    selector("AlreadyAnchored(bytes32)"), "AlreadyAnchored",
                    selector("EmptyRoot()"), "EmptyRoot",
                    selector("EmptyCommitCount()"), "EmptyCommitCount",
                    selector("RootMismatch(bytes32,bytes32)"), "RootMismatch",
                    selector("AccessControlUnauthorizedAccount(address,bytes32)"),
                            "AccessControlUnauthorizedAccount");

    /** Hardhat 이 {@code error.data} 를 객체로 싸서 줄 때 안쪽 revert 데이터. {@link #decodeErrorName} 참고. */
    private static final Pattern NESTED_REVERT_DATA = Pattern.compile("\"data\"\\s*:\\s*\"(0x[0-9a-fA-F]*)\"");

    private final ChainProperties props;
    private final CommitAnchorProperties contract;
    private final ChainConnection connection;
    /** 전송은 토큰 릴레이어와 같은 키를 쓰므로 {@link TxSender} 의 락을 거친다(ANT-CHAIN-10). nonce 가 겹치지 않게. */
    private final TxSender txSender;
    private final Credentials credentials;

    public Web3jAnchorRelayer(
            ChainProperties props, CommitAnchorProperties contract, ChainConnection connection, TxSender txSender) {
        this.props = props;
        this.contract = contract;
        this.connection = connection;
        this.txSender = txSender;
        // 키가 없으면 null — isEnabled() 가 false 라 여기까지 오는 호출이 없다.
        this.credentials =
                props.relayerEnabled() ? Credentials.create(props.relayer().privateKey()) : null;
        if (credentials != null) {
            log.info("앵커 릴레이어 준비: 주소 {}, 컨트랙트 {}", credentials.getAddress(), contract.address());
        } else {
            log.info("앵커 릴레이어 꺼짐 — CHAIN_RPC_URL · RELAYER_PRIVATE_KEY · CONTRACT_COMMIT_ANCHOR 를 확인하라");
        }
    }

    @Override
    public boolean isEnabled() {
        return credentials != null && contract.isDeployed();
    }

    @Override
    public AnchorResult anchor(byte[] merkleRoot, List<byte[]> commitHashes) {
        requireEnabled();
        String data = FunctionEncoder.encode(anchorFunction(merkleRoot, commitHashes));

        // ① 시뮬레이션
        Optional<String> revert = withReconnect(() -> simulate(data));
        if (revert.isPresent()) {
            String name = revert.get();
            if ("AlreadyAnchored".equals(name)) {
                return AnchorResult.alreadyAnchored();
            }
            throw new AnchorRevertException(name);
        }

        // ② 전송
        String txHash = withReconnect(() -> send(data));

        // ③ receipt
        return waitForReceipt(txHash);
    }

    @Override
    public long anchoredAt(byte[] merkleRoot) {
        requireEnabled();
        Function fn =
                new Function(
                        "anchoredAt",
                        List.of(new Bytes32(merkleRoot)),
                        List.of(new TypeReference<Uint256>() {}));
        String data = FunctionEncoder.encode(fn);
        return withReconnect(
                () -> {
                    EthCall call = ethCall(data);
                    if (call.isReverted() || call.getValue() == null) {
                        // anchoredAt 은 revert 하지 않는 함수다. 여기 오면 주소가 틀렸거나 노드가 이상하다.
                        throw new BusinessException(ErrorCode.CHAIN_UNAVAILABLE);
                    }
                    List<Type> out = FunctionReturnDecoder.decode(call.getValue(), fn.getOutputParameters());
                    // 코드가 없는 주소(v2 주소를 잘못 넣은 경우 등)는 빈 응답이 온다 — 0(미앵커)으로 읽힌다.
                    return out.isEmpty() ? 0L : ((Uint256) out.get(0)).getValue().longValueExact();
                });
    }

    // ── 내부 ────────────────────────────────────────────────────────────

    /** @return revert 면 에러 이름, 아니면 empty */
    private Optional<String> simulate(String data) throws IOException {
        EthCall call = ethCall(data);
        if (!call.isReverted() && call.getError() == null) {
            return Optional.empty();
        }
        // Besu 는 revert 데이터를 error.data 에, 일부 노드는 result 에 준다. 둘 다 본다.
        String revertData = null;
        if (call.getError() != null && call.getError().getData() != null) {
            revertData = call.getError().getData();
        } else if (call.getValue() != null) {
            revertData = call.getValue();
        }
        return Optional.of(decodeErrorName(revertData));
    }

    private EthCall ethCall(String data) throws IOException {
        Web3j web3j = connection.web3j();
        Transaction tx =
                Transaction.createEthCallTransaction(
                        credentials.getAddress(), contract.normalizedAddress(), data);
        return web3j.ethCall(tx, DefaultBlockParameterName.LATEST).send();
    }

    private String send(String data) throws IOException {
        // nonce 조회 → 서명(gasPrice 0) → 전송은 TxSender 의 키 락 안에서. receipt 대기는 락 밖(아래 waitForReceipt).
        String txHash = txSender.send(contract.normalizedAddress(), data, gasLimit(data));
        log.info("앵커 tx 전송: {}", txHash);
        return txHash;
    }

    private BigInteger gasLimit(String data) throws IOException {
        Transaction tx =
                Transaction.createFunctionCallTransaction(
                        credentials.getAddress(), null, BigInteger.ZERO, null, contract.normalizedAddress(), data);
        EthEstimateGas est = connection.web3j().ethEstimateGas(tx).send();
        if (est.hasError() || est.getAmountUsed() == null) {
            return GAS_LIMIT_FALLBACK;
        }
        // 추정치 + 20%. 리프 수에 비례하는 함수라 추정이 정확하지만 여유를 둔다.
        return est.getAmountUsed().multiply(BigInteger.valueOf(120)).divide(BigInteger.valueOf(100));
    }

    private AnchorResult waitForReceipt(String txHash) {
        long deadline = System.currentTimeMillis() + props.anchor().receiptTimeoutSeconds() * 1000L;
        while (System.currentTimeMillis() < deadline) {
            Optional<TransactionReceipt> receipt =
                    withReconnect(() -> connection.web3j().ethGetTransactionReceipt(txHash).send().getTransactionReceipt());
            if (receipt.isPresent()) {
                TransactionReceipt r = receipt.get();
                if (r.isStatusOK()) {
                    return AnchorResult.confirmed(txHash, r.getBlockNumber().longValue());
                }
                // 시뮬레이션은 통과했는데 실제 tx 가 revert — 그 사이 같은 루트가 박혔거나 상태가 바뀐 것.
                // 이름을 모르니 호출자는 FAILED 로 두고, 다음 실행의 anchoredAt 확인이 진실을 가린다.
                throw new AnchorRevertException("Reverted(receipt status 0)");
            }
            try {
                Thread.sleep(RECEIPT_POLL_MILLIS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }
        log.warn("앵커 tx receipt 대기 초과({}초): {}", props.anchor().receiptTimeoutSeconds(), txHash);
        return AnchorResult.sentUnconfirmed(txHash);
    }

    /** IO 예외면 소켓을 리셋하고 한 번 더. 그래도 안 되면 CHAIN_UNAVAILABLE. */
    private <T> T withReconnect(IoSupplier<T> op) {
        try {
            return op.get();
        } catch (Exception first) {
            if (first instanceof BusinessException be) {
                throw be;
            }
            if (first instanceof AnchorRevertException are) {
                throw are;
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
                if (second instanceof AnchorRevertException are) {
                    throw are;
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

    static Function anchorFunction(byte[] merkleRoot, List<byte[]> commitHashes) {
        List<Bytes32> leaves = new ArrayList<>(commitHashes.size());
        for (byte[] c : commitHashes) {
            leaves.add(new Bytes32(c));
        }
        return new Function(
                "anchor",
                List.of(new Bytes32(merkleRoot), new DynamicArray<>(Bytes32.class, leaves)),
                List.of());
    }

    /**
     * revert 데이터의 앞 4바이트로 이름을 찾는다. 모르면 셀렉터를 그대로 보여 준다.
     *
     * <p>Besu 는 {@code error.data} 를 JSON 문자열로 주는데 web3j 가 그 값을 따옴표까지 포함한 문자열로
     * 넘긴다({@code "\"0x8e74…\""}). SSAFY 실측(2026-09-04)에서 그대로 잘라 {@code Unknown("0x8e740b5)} 가
     * 나왔다. 따옴표·공백을 벗기고 본다.
     *
     * <p>Hardhat 은 한 겹 더 싼다 — {@code error.data = {"message": "...", "data": "0x53ce4ece"}}. web3j 는 그 객체를 JSON
     * 문자열 그대로 넘기므로 안쪽 {@code data} 를 꺼낸다. 이게 없으면 로컬 Hardhat 에서 {@code AlreadyAnchored}(=성공)가
     * {@code Unknown(no data)} 로 읽혀 재전송 배치가 FAILED 가 된다(ANT-CHAIN-13 로컬 Live 테스트에서 발견).
     */
    static String decodeErrorName(String revertData) {
        if (revertData == null) {
            return "Unknown(no data)";
        }
        String cleaned = revertData.trim();
        if (cleaned.startsWith("{")) {
            Matcher inner = NESTED_REVERT_DATA.matcher(cleaned);
            cleaned = inner.find() ? inner.group(1) : "";
        }
        if (cleaned.startsWith("\"") && cleaned.endsWith("\"") && cleaned.length() >= 2) {
            cleaned = cleaned.substring(1, cleaned.length() - 1);
        }
        if (cleaned.length() < 10 || !cleaned.startsWith("0x")) {
            return "Unknown(no data)";
        }
        String sel = cleaned.substring(0, 10).toLowerCase();
        return ERROR_SELECTORS.getOrDefault(sel, "Unknown(" + sel + ")");
    }

    static String selector(String signature) {
        return Numeric.toHexString(Arrays.copyOf(Hash.sha3(signature.getBytes()), 4)).toLowerCase();
    }

    /** {@code Supplier} 는 checked 예외를 못 던진다. web3j 의 send() 는 IOException 을 던진다. */
    @FunctionalInterface
    interface IoSupplier<T> {
        T get() throws Exception;
    }
}
