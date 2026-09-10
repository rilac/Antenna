package ssafy.a507.backend.domain.chain.indexer;

import java.math.BigInteger;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import org.web3j.abi.EventEncoder;
import org.web3j.abi.EventValues;
import org.web3j.abi.TypeReference;
import org.web3j.abi.datatypes.Address;
import org.web3j.abi.datatypes.Event;
import org.web3j.abi.datatypes.generated.Bytes32;
import org.web3j.abi.datatypes.generated.Uint256;
import org.web3j.protocol.core.methods.response.Log;
import org.web3j.tx.Contract;
import org.web3j.utils.Numeric;

/**
 * PredictToken 로그를 {@link TokenLog} 로 푼다 (ANT-CHAIN-11).
 *
 * <p>ABI 코드젠을 쓰지 않는다(CHAIN-04 판단 유지). 이벤트 셋의 정의를 여기 한 번 손으로 적고, 배포된 컨트랙트와
 * 같은지는 {@code PredictTokenConfigTest} 가 ABI 리소스로, 실제 바이트는 {@code TokenLogDecoderTest} 가 SSAFY 실로그로 고정한다.
 *
 * <p>ERC-20 {@code Transfer} 는 읽지 않는다 — 사유가 없고, {@code subscribe} 한 번이 Transfer 두 건이라 원장이 겹친다.
 */
public final class TokenLogDecoder {

    /** {@code Minted(address indexed to, uint256 amount, bytes32 indexed reason)} */
    public static final Event MINTED =
            new Event(
                    "Minted",
                    List.of(
                            new TypeReference<Address>(true) {},
                            new TypeReference<Uint256>(false) {},
                            new TypeReference<Bytes32>(true) {}));

    /** {@code Burned(address indexed from, uint256 amount, bytes32 indexed reason)} */
    public static final Event BURNED =
            new Event(
                    "Burned",
                    List.of(
                            new TypeReference<Address>(true) {},
                            new TypeReference<Uint256>(false) {},
                            new TypeReference<Bytes32>(true) {}));

    /** {@code Subscribed(address indexed subscriber, address indexed creator, uint256 amount, uint256 creatorShare, uint256 platformShare)} */
    public static final Event SUBSCRIBED =
            new Event(
                    "Subscribed",
                    List.of(
                            new TypeReference<Address>(true) {},
                            new TypeReference<Address>(true) {},
                            new TypeReference<Uint256>(false) {},
                            new TypeReference<Uint256>(false) {},
                            new TypeReference<Uint256>(false) {}));

    public static final String TOPIC_MINTED = EventEncoder.encode(MINTED);
    public static final String TOPIC_BURNED = EventEncoder.encode(BURNED);
    public static final String TOPIC_SUBSCRIBED = EventEncoder.encode(SUBSCRIBED);

    /** getLogs 의 topic0 필터(셋 중 하나). {@code RoleGranted}·{@code Transfer} 는 노드가 걸러 준다. */
    public static final List<String> TOPICS = List.of(TOPIC_MINTED, TOPIC_BURNED, TOPIC_SUBSCRIBED);

    private TokenLogDecoder() {}

    /** @return topics[0] 이 셋 중 하나가 아니면 empty — 필터를 믿고 예외를 내는 것보다 조용히 거르는 편이 낫다. */
    public static Optional<TokenLog> decode(Log log) {
        List<String> topics = log.getTopics();
        if (topics == null || topics.isEmpty()) {
            return Optional.empty();
        }
        String topic0 = lower(topics.get(0));
        if (topic0.equals(TOPIC_MINTED)) {
            EventValues v = Contract.staticExtractEventParameters(MINTED, log);
            return Optional.of(
                    build(log, TokenLog.Kind.MINTED, null, address(v, 0), amount(v, 0), null, null, bytes32(v, 1)));
        }
        if (topic0.equals(TOPIC_BURNED)) {
            EventValues v = Contract.staticExtractEventParameters(BURNED, log);
            return Optional.of(
                    build(log, TokenLog.Kind.BURNED, address(v, 0), null, amount(v, 0), null, null, bytes32(v, 1)));
        }
        if (topic0.equals(TOPIC_SUBSCRIBED)) {
            EventValues v = Contract.staticExtractEventParameters(SUBSCRIBED, log);
            return Optional.of(
                    build(
                            log,
                            TokenLog.Kind.SUBSCRIBED,
                            address(v, 0),
                            address(v, 1),
                            amount(v, 0),
                            amount(v, 1),
                            amount(v, 2),
                            null));
        }
        return Optional.empty();
    }

    private static TokenLog build(
            Log log,
            TokenLog.Kind kind,
            String from,
            String to,
            BigInteger amount,
            BigInteger creatorShare,
            BigInteger platformShare,
            String reasonHex) {
        return new TokenLog(
                lower(log.getTransactionHash()),
                log.getLogIndex().intValueExact(),
                log.getBlockNumber().longValueExact(),
                lower(log.getBlockHash()),
                lower(log.getAddress()),
                kind,
                from,
                to,
                amount,
                creatorShare,
                platformShare,
                reasonHex);
    }

    private static String address(EventValues v, int indexedPos) {
        return lower(((Address) v.getIndexedValues().get(indexedPos)).getValue());
    }

    private static BigInteger amount(EventValues v, int nonIndexedPos) {
        return ((Uint256) v.getNonIndexedValues().get(nonIndexedPos)).getValue();
    }

    private static String bytes32(EventValues v, int indexedPos) {
        return lower(Numeric.toHexString(((Bytes32) v.getIndexedValues().get(indexedPos)).getValue()));
    }

    static String lower(String s) {
        return s == null ? null : s.toLowerCase(Locale.ROOT);
    }
}
