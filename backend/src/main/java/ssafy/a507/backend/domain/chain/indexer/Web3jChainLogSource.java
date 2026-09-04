package ssafy.a507.backend.domain.chain.indexer;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.web3j.protocol.Web3j;
import org.web3j.protocol.core.DefaultBlockParameter;
import org.web3j.protocol.core.methods.request.EthFilter;
import org.web3j.protocol.core.methods.response.EthBlock;
import org.web3j.protocol.core.methods.response.EthBlockNumber;
import org.web3j.protocol.core.methods.response.EthLog;
import org.web3j.protocol.core.methods.response.Log;
import ssafy.a507.backend.common.error.BusinessException;
import ssafy.a507.backend.common.error.ErrorCode;
import ssafy.a507.backend.domain.chain.config.CommitAnchorProperties;
import ssafy.a507.backend.domain.chain.relay.ChainConnection;

/**
 * web3j 로 {@code eth_getLogs} · {@code eth_blockNumber} · {@code eth_getBlockByNumber} 를 부르는 로그 소스 (ANT-CHAIN-04).
 *
 * <p>릴레이어와 같은 {@link ChainConnection}(wss 하나)을 쓴다. 폴링은 실패해도 5초 뒤 다음 회차가 있으므로
 * 재연결 정책은 릴레이어와 같은 "IO 예외면 소켓을 버리고 한 번 더" 로 충분하다 — 상시 구독(eth_subscribe)을
 * 안 쓰기로 한 이유가 바로 이것이다(결정 C1).
 */
@Slf4j
@Component
public class Web3jChainLogSource implements ChainLogSource {

    private final ChainConnection connection;
    private final CommitAnchorProperties contract;

    public Web3jChainLogSource(ChainConnection connection, CommitAnchorProperties contract) {
        this.connection = connection;
        this.contract = contract;
    }

    @Override
    public long latestBlock() {
        return withReconnect(
                () -> {
                    EthBlockNumber res = connection.web3j().ethBlockNumber().send();
                    if (res.hasError() || res.getBlockNumber() == null) {
                        throw new BusinessException(ErrorCode.CHAIN_UNAVAILABLE);
                    }
                    return res.getBlockNumber().longValueExact();
                });
    }

    @Override
    public List<AnchoredLog> anchoredLogs(long fromBlock, long toBlock) {
        return withReconnect(
                () -> {
                    EthFilter filter =
                            new EthFilter(
                                    DefaultBlockParameter.valueOf(BigInteger.valueOf(fromBlock)),
                                    DefaultBlockParameter.valueOf(BigInteger.valueOf(toBlock)),
                                    contract.normalizedAddress());
                    // topic0 = Anchored 시그니처. RoleGranted 같은 다른 이벤트는 노드가 걸러 준다.
                    filter.addSingleTopic(AnchoredLogDecoder.TOPIC);
                    EthLog res = connection.web3j().ethGetLogs(filter).send();
                    if (res.hasError()) {
                        log.warn("eth_getLogs [{}, {}] 실패: {}", fromBlock, toBlock, res.getError().getMessage());
                        throw new BusinessException(ErrorCode.CHAIN_UNAVAILABLE);
                    }
                    List<AnchoredLog> out = new ArrayList<>();
                    for (EthLog.LogResult<?> r : res.getLogs()) {
                        if (r.get() instanceof Log l) {
                            AnchoredLogDecoder.decode(l).ifPresent(out::add);
                        }
                    }
                    // 노드가 정렬해 주지만 규약이 아니다. 커서가 "마지막 이벤트 블록"이라 순서가 곧 정합성이다.
                    out.sort(
                            Comparator.comparingLong(AnchoredLog::blockNumber)
                                    .thenComparingInt(AnchoredLog::logIndex));
                    return out;
                });
    }

    @Override
    public Optional<String> blockHash(long blockNumber) {
        return withReconnect(
                () -> {
                    EthBlock res =
                            connection
                                    .web3j()
                                    .ethGetBlockByNumber(
                                            DefaultBlockParameter.valueOf(BigInteger.valueOf(blockNumber)), false)
                                    .send();
                    if (res.hasError()) {
                        throw new BusinessException(ErrorCode.CHAIN_UNAVAILABLE);
                    }
                    EthBlock.Block block = res.getBlock();
                    if (block == null || block.getHash() == null) {
                        return Optional.empty();
                    }
                    return Optional.of(block.getHash().toLowerCase(Locale.ROOT));
                });
    }

    /** {@code Web3jAnchorRelayer.withReconnect} 와 같은 규칙. 인덱서 쪽 예외 종류가 하나(CHAIN_UNAVAILABLE)라 더 짧다. */
    private <T> T withReconnect(IoSupplier<T> op) {
        try {
            return op.get();
        } catch (Exception first) {
            if (first instanceof BusinessException be) {
                throw be;
            }
            if (!connection.resetIfConnectionError(first)) {
                log.warn("체인 조회 실패: {}", first.toString());
                throw new BusinessException(ErrorCode.CHAIN_UNAVAILABLE);
            }
            try {
                return op.get();
            } catch (Exception second) {
                if (second instanceof BusinessException be) {
                    throw be;
                }
                log.warn("체인 재연결 후에도 조회 실패: {}", second.toString());
                connection.reset();
                throw new BusinessException(ErrorCode.CHAIN_UNAVAILABLE);
            }
        }
    }

    @FunctionalInterface
    private interface IoSupplier<T> {
        T get() throws Exception;
    }
}
