package ssafy.a507.backend.domain.chain.anchor;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import ssafy.a507.backend.common.error.BusinessException;
import ssafy.a507.backend.common.error.ErrorCode;
import ssafy.a507.backend.domain.chain.relay.AnchorRelayer;
import ssafy.a507.backend.domain.chain.relay.AnchorResult;
import ssafy.a507.backend.domain.chain.relay.AnchorRevertException;

/**
 * 체인 없이 앵커 배치의 상태 전이를 검증하기 위한 가짜 릴레이어.
 *
 * <p>{@code anchor()} 호출마다 미리 심어 둔 대본(script)을 하나씩 꺼내 실행한다 — 확정·이미 앵커됨·미확정·
 * revert·RPC 장애를 순서대로 흉내 낼 수 있다. 대본이 비면 CONFIRMED. {@code rootOf()} 는 배치별로 심은 값.
 */
public class FakeAnchorRelayer implements AnchorRelayer {

    @TestConfiguration
    public static class Config {
        @Bean
        @Primary
        FakeAnchorRelayer fakeAnchorRelayer() {
            return new FakeAnchorRelayer();
        }
    }

    public record Call(long batchId, byte[] root, List<byte[]> leaves) {}

    private boolean enabled = true;
    private final Deque<Supplier<AnchorResult>> script = new ArrayDeque<>();
    private final Map<Long, byte[]> roots = new HashMap<>();
    private final List<Call> calls = new ArrayList<>();
    private int rootOfCalls = 0;
    private boolean rootOfUnavailable = false;
    private int txSeq = 0;

    public void reset() {
        enabled = true;
        script.clear();
        roots.clear();
        calls.clear();
        rootOfCalls = 0;
        rootOfUnavailable = false;
        txSeq = 0;
    }

    public void disable() {
        enabled = false;
    }

    public FakeAnchorRelayer thenConfirmed() {
        script.add(() -> AnchorResult.confirmed(nextTx(), 11_000_000L + txSeq));
        return this;
    }

    public FakeAnchorRelayer thenAlreadyAnchored() {
        script.add(AnchorResult::alreadyAnchored);
        return this;
    }

    public FakeAnchorRelayer thenUnconfirmed() {
        script.add(() -> AnchorResult.sentUnconfirmed(nextTx()));
        return this;
    }

    public FakeAnchorRelayer thenRevert(String errorName) {
        script.add(
                () -> {
                    throw new AnchorRevertException(errorName);
                });
        return this;
    }

    public FakeAnchorRelayer thenUnavailable() {
        script.add(
                () -> {
                    throw new BusinessException(ErrorCode.CHAIN_UNAVAILABLE);
                });
        return this;
    }

    public void setRoot(long batchId, byte[] root) {
        roots.put(batchId, root);
    }

    public void rootOfUnavailable() {
        rootOfUnavailable = true;
    }

    public List<Call> calls() {
        return calls;
    }

    public int rootOfCalls() {
        return rootOfCalls;
    }

    private String nextTx() {
        txSeq++;
        return "0x" + String.format("%064x", txSeq);
    }

    @Override
    public boolean isEnabled() {
        return enabled;
    }

    @Override
    public AnchorResult anchor(long batchId, byte[] merkleRoot, List<byte[]> commitHashes) {
        calls.add(new Call(batchId, merkleRoot, commitHashes));
        Supplier<AnchorResult> next = script.poll();
        return next == null ? AnchorResult.confirmed(nextTx(), 11_000_000L + txSeq) : next.get();
    }

    @Override
    public byte[] rootOf(long batchId) {
        rootOfCalls++;
        if (rootOfUnavailable) {
            throw new BusinessException(ErrorCode.CHAIN_UNAVAILABLE);
        }
        return roots.getOrDefault(batchId, new byte[32]);
    }
}
