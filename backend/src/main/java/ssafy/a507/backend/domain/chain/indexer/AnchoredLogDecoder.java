package ssafy.a507.backend.domain.chain.indexer;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import org.web3j.abi.EventEncoder;
import org.web3j.abi.EventValues;
import org.web3j.abi.TypeReference;
import org.web3j.abi.datatypes.DynamicArray;
import org.web3j.abi.datatypes.Event;
import org.web3j.abi.datatypes.generated.Bytes32;
import org.web3j.abi.datatypes.generated.Uint256;
import org.web3j.protocol.core.methods.response.Log;
import org.web3j.tx.Contract;
import org.web3j.utils.Numeric;

/**
 * {@code Anchored} 로그를 {@link AnchoredLog} 로 푼다 (ANT-CHAIN-04).
 *
 * <p>ABI 코드젠을 쓰지 않는다(CHAIN-01·05 판단 유지). 이벤트 정의를 여기 한 번 손으로 적고, 시그니처가 배포된
 * 컨트랙트와 같은지는 {@code CommitAnchorConfigTest} 가 ABI 리소스로, 실제 로그와 맞는지는
 * {@code AnchoredLogDecoderTest} 가 SSAFY 체인에서 뜬 원본 로그로 고정한다.
 */
public final class AnchoredLogDecoder {

    /** 컨트랙트와 같은 순서·타입. batchId 만 indexed — topics[1] 로 온다. */
    public static final Event ANCHORED =
            new Event(
                    "Anchored",
                    List.of(
                            new TypeReference<Uint256>(true) {},
                            new TypeReference<Bytes32>(false) {},
                            new TypeReference<DynamicArray<Bytes32>>(false) {}));

    /** topics[0]. getLogs 필터에 넣는다. */
    public static final String TOPIC = EventEncoder.encode(ANCHORED);

    private AnchoredLogDecoder() {}

    /**
     * @return topics[0] 이 Anchored 가 아니면 empty — 같은 컨트랙트의 다른 이벤트(RoleGranted 등)는 여기서 걸러진다.
     *     getLogs 에 topic 필터를 걸어 두므로 실제로는 오지 않지만, 필터를 믿고 예외를 내는 것보다 조용히 거르는 편이 낫다
     */
    public static Optional<AnchoredLog> decode(Log log) {
        EventValues values = Contract.staticExtractEventParameters(ANCHORED, log);
        if (values == null) {
            return Optional.empty();
        }
        long batchId = ((Uint256) values.getIndexedValues().get(0)).getValue().longValueExact();
        byte[] root = ((Bytes32) values.getNonIndexedValues().get(0)).getValue();
        @SuppressWarnings("unchecked")
        List<Bytes32> leaves = ((DynamicArray<Bytes32>) values.getNonIndexedValues().get(1)).getValue();
        List<String> commitHashes = new ArrayList<>(leaves.size());
        for (Bytes32 leaf : leaves) {
            commitHashes.add(hex(leaf.getValue()));
        }
        return Optional.of(
                new AnchoredLog(
                        lower(log.getTransactionHash()),
                        log.getLogIndex().intValueExact(),
                        log.getBlockNumber().longValueExact(),
                        lower(log.getBlockHash()),
                        lower(log.getAddress()),
                        batchId,
                        hex(root),
                        List.copyOf(commitHashes)));
    }

    /** 0x + 소문자 hex. anchor_batches.merkle_root(Numeric.toHexString)와 같은 형식이라 문자열 비교가 된다. */
    static String hex(byte[] bytes) {
        return Numeric.toHexString(bytes).toLowerCase(Locale.ROOT);
    }

    static String lower(String s) {
        return s == null ? null : s.toLowerCase(Locale.ROOT);
    }
}
