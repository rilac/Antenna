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
import org.web3j.utils.Numeric;
import ssafy.a507.backend.common.error.BusinessException;
import ssafy.a507.backend.common.error.ErrorCode;
import ssafy.a507.backend.domain.chain.relay.AnchorRelayer;
import ssafy.a507.backend.domain.chain.relay.AnchorResult;
import ssafy.a507.backend.domain.chain.relay.AnchorRevertException;

/**
 * 체인 없이 앵커 배치의 상태 전이를 검증하기 위한 가짜 릴레이어.
 *
 * <p>{@code anchor()} 호출마다 미리 심어 둔 대본(script)을 하나씩 꺼내 실행한다 — 확정·이미 앵커됨·미확정·
 * revert·RPC 장애를 순서대로 흉내 낼 수 있다. 대본이 비면 CONFIRMED. {@code anchoredAt()} 은 루트별로 심은 블록 번호.
 * v3(ANT-CHAIN-13)라 칸의 키가 루트다 — 가짜의 장부도 루트로 찾는다.
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

    public record Call(byte[] root, List<byte[]> leaves) {}

    private boolean enabled = true;
    private final Deque<Supplier<AnchorResult>> script = new ArrayDeque<>();
    /** 0x 소문자 루트 → 박힌 블록. */
    private final Map<String, Long> anchored = new HashMap<>();
    private final List<Call> calls = new ArrayList<>();
    private int anchoredAtCalls = 0;
    private boolean anchoredAtUnavailable = false;
    private int txSeq = 0;

    public void reset() {
        enabled = true;
        script.clear();
        anchored.clear();
        calls.clear();
        anchoredAtCalls = 0;
        anchoredAtUnavailable = false;
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

    /** 릴레이어가 revert·RPC 장애로 분류하지 못한 예외(라이브러리 내부 오류 등). */
    public FakeAnchorRelayer thenThrow(RuntimeException e) {
        script.add(
                () -> {
                    throw e;
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

    /** "체인에 이 루트가 이 블록에 박혀 있다". */
    public void setAnchored(byte[] root, long blockNumber) {
        anchored.put(key(root), blockNumber);
    }

    public void anchoredAtUnavailable() {
        anchoredAtUnavailable = true;
    }

    public List<Call> calls() {
        return calls;
    }

    public int anchoredAtCalls() {
        return anchoredAtCalls;
    }

    private String nextTx() {
        txSeq++;
        return "0x" + String.format("%064x", txSeq);
    }

    private static String key(byte[] root) {
        return Numeric.toHexString(root).toLowerCase();
    }

    @Override
    public boolean isEnabled() {
        return enabled;
    }

    @Override
    public AnchorResult anchor(byte[] merkleRoot, List<byte[]> commitHashes) {
        calls.add(new Call(merkleRoot, commitHashes));
        Supplier<AnchorResult> next = script.poll();
        return next == null ? AnchorResult.confirmed(nextTx(), 11_000_000L + txSeq) : next.get();
    }

    @Override
    public long anchoredAt(byte[] merkleRoot) {
        anchoredAtCalls++;
        if (anchoredAtUnavailable) {
            throw new BusinessException(ErrorCode.CHAIN_UNAVAILABLE);
        }
        return anchored.getOrDefault(key(merkleRoot), 0L);
    }
}
