package ssafy.a507.backend.domain.chain.indexer;

import java.util.List;
import java.util.Optional;
import ssafy.a507.backend.common.error.BusinessException;

/**
 * 인덱서가 체인에서 읽는 것 전부 (ANT-CHAIN-04 · 토큰 인덱서 ② 는 ANT-CHAIN-11). 다섯 가지다.
 *
 * <p>인터페이스로 뺀 이유는 릴레이어와 같다 — 커서·멱등·충돌·정지 같은 상태 전이는 체인 없이 검증돼야 하고,
 * 그러려면 "이 구간에 이런 로그가 있었다 / 노드가 죽었다 / 블록 해시가 바뀌었다" 를 마음대로 내는 가짜가 필요하다.
 * 모든 메서드는 RPC 에 닿지 못하면 {@link BusinessException} CHAIN_UNAVAILABLE 을 던진다.
 */
public interface ChainLogSource {

    /** 노드가 아는 최신 블록 번호. confirmations = 0 이라 이 블록까지가 곧 확정 구간이다(결정 C2, 실측 근거는 plan). */
    long latestBlock();

    /**
     * {@code [fromBlock, toBlock]} 구간의 {@code Anchored} 로그. 블록 번호·로그 순번 오름차순.
     * 컨트랙트 주소와 topic0 으로 걸러진 것만 온다.
     */
    List<AnchoredLog> anchoredLogs(long fromBlock, long toBlock);

    /** 블록 해시. 노드에 그 번호의 블록이 없으면 empty — 우리 기록보다 체인이 짧다는 뜻이라 reorg 로 본다. */
    Optional<String> blockHash(long blockNumber);

    /**
     * {@code [fromBlock, toBlock]} 구간의 PredictToken {@code Minted · Burned · Subscribed} 로그 (ANT-CHAIN-11).
     * 블록 번호·로그 순번 오름차순. 토큰 주소가 설정돼 있지 않으면 빈 목록.
     */
    List<TokenLog> tokenLogs(long fromBlock, long toBlock);

    /**
     * tx 의 receipt 상태 (ANT-CHAIN-11). {@code true} = 성공, {@code false} = revert, empty = 아직 채굴되지 않았거나 노드가 모른다.
     * 이벤트가 안 오는 실패(revert)를 닫는 유일한 재료다 — Besu 의 receipt 에는 revert 이유가 없으니 status 만 본다.
     */
    Optional<Boolean> receiptStatus(String txHash);
}
