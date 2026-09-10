package ssafy.a507.backend.domain.chain.indexer;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import ssafy.a507.backend.common.error.BusinessException;
import ssafy.a507.backend.common.error.ErrorCode;

/**
 * 체인 없이 인덱서의 커서·멱등·충돌·정지를 검증하기 위한 가짜 로그 소스.
 *
 * <p>로그를 미리 심어 두면 {@code anchoredLogs(from, to)} 가 구간에 드는 것만 돌려준다. 물어본 구간은 전부 기록해
 * 커서가 어디서 시작했는지 검증한다. 블록 해시는 심은 로그의 것을 돌려주되, 덮어쓰면 reorg 를 흉내 낼 수 있다.
 */
public class FakeChainLogSource implements ChainLogSource {

    @TestConfiguration
    public static class Config {
        @Bean
        @Primary
        FakeChainLogSource fakeChainLogSource() {
            return new FakeChainLogSource();
        }
    }

    public record Range(long from, long to) {}

    private final List<AnchoredLog> logs = new ArrayList<>();
    /** 토큰 인덱서 ②(ANT-CHAIN-11) 용. 앵커 로그와 별도 목록 — 컨트랙트가 다르다. */
    private final List<TokenLog> tokenLogs = new ArrayList<>();
    /** tx 해시 → receipt status. 없으면 "아직 채굴 안 됨". */
    private final Map<String, Boolean> receipts = new HashMap<>();
    private final Map<Long, String> blockHashOverrides = new HashMap<>();
    private final List<Range> ranges = new ArrayList<>();
    private final List<Range> tokenRanges = new ArrayList<>();
    private long head = 0;
    private boolean unavailable = false;
    private int latestBlockCalls = 0;
    private int receiptCalls = 0;

    public void reset() {
        logs.clear();
        tokenLogs.clear();
        receipts.clear();
        blockHashOverrides.clear();
        ranges.clear();
        tokenRanges.clear();
        head = 0;
        unavailable = false;
        latestBlockCalls = 0;
        receiptCalls = 0;
    }

    public FakeChainLogSource add(TokenLog log) {
        tokenLogs.add(log);
        if (log.blockNumber() > head) {
            head = log.blockNumber();
        }
        return this;
    }

    /** receipt 를 심는다. true = 성공, false = revert. 안 심으면 "아직 채굴 안 됨". */
    public FakeChainLogSource receipt(String txHash, boolean ok) {
        receipts.put(txHash, ok);
        return this;
    }

    public List<Range> tokenRanges() {
        return tokenRanges;
    }

    public int receiptCalls() {
        return receiptCalls;
    }

    @Override
    public List<TokenLog> tokenLogs(long fromBlock, long toBlock) {
        failIfUnavailable();
        tokenRanges.add(new Range(fromBlock, toBlock));
        List<TokenLog> out = new ArrayList<>();
        for (TokenLog l : tokenLogs) {
            if (l.blockNumber() >= fromBlock && l.blockNumber() <= toBlock) {
                out.add(l);
            }
        }
        out.sort(java.util.Comparator.comparingLong(TokenLog::blockNumber).thenComparingInt(TokenLog::logIndex));
        return out;
    }

    @Override
    public Optional<Boolean> receiptStatus(String txHash) {
        failIfUnavailable();
        receiptCalls++;
        return Optional.ofNullable(receipts.get(txHash));
    }

    public FakeChainLogSource head(long head) {
        this.head = head;
        return this;
    }

    public FakeChainLogSource add(AnchoredLog log) {
        logs.add(log);
        if (log.blockNumber() > head) {
            head = log.blockNumber();
        }
        return this;
    }

    /** 체인의 그 블록 해시를 바꾼다 — 기록과 다르면 인덱서가 정지해야 한다. */
    public void overrideBlockHash(long blockNumber, String hash) {
        blockHashOverrides.put(blockNumber, hash);
    }

    public void unavailable(boolean value) {
        this.unavailable = value;
    }

    public List<Range> ranges() {
        return ranges;
    }

    public int latestBlockCalls() {
        return latestBlockCalls;
    }

    @Override
    public long latestBlock() {
        failIfUnavailable();
        latestBlockCalls++;
        return head;
    }

    @Override
    public List<AnchoredLog> anchoredLogs(long fromBlock, long toBlock) {
        failIfUnavailable();
        ranges.add(new Range(fromBlock, toBlock));
        List<AnchoredLog> out = new ArrayList<>();
        for (AnchoredLog l : logs) {
            if (l.blockNumber() >= fromBlock && l.blockNumber() <= toBlock) {
                out.add(l);
            }
        }
        out.sort(
                java.util.Comparator.comparingLong(AnchoredLog::blockNumber)
                        .thenComparingInt(AnchoredLog::logIndex));
        return out;
    }

    @Override
    public Optional<String> blockHash(long blockNumber) {
        failIfUnavailable();
        if (blockHashOverrides.containsKey(blockNumber)) {
            return Optional.ofNullable(blockHashOverrides.get(blockNumber));
        }
        for (AnchoredLog l : logs) {
            if (l.blockNumber() == blockNumber) {
                return Optional.of(l.blockHash());
            }
        }
        for (TokenLog l : tokenLogs) {
            if (l.blockNumber() == blockNumber) {
                return Optional.of(l.blockHash());
            }
        }
        return blockNumber <= head ? Optional.of(FakeLogs.blockHashOf(blockNumber)) : Optional.empty();
    }

    private void failIfUnavailable() {
        if (unavailable) {
            throw new BusinessException(ErrorCode.CHAIN_UNAVAILABLE);
        }
    }
}
