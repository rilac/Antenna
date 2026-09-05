package ssafy.a507.backend.domain.chain.indexer;

import java.util.List;
import java.util.Optional;
import ssafy.a507.backend.common.error.BusinessException;

/**
 * 인덱서가 체인에서 읽는 것 전부 (ANT-CHAIN-04). 세 가지뿐이다.
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
}
