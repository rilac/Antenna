package ssafy.a507.backend.domain.chain.repository;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import ssafy.a507.backend.domain.chain.entity.ChainEvent;

public interface ChainEventRepository extends JpaRepository<ChainEvent, Long> {

    /** 멱등 INSERT 의 사전 검사. UQ(tx_hash, log_index)가 마지막 방어선이고 이건 정상 경로다 — 재훑기·재시작에서 늘 걸린다. */
    boolean existsByTxHashAndLogIndex(String txHash, int logIndex);

    /** 저장은 됐는데 도메인 반영이 안 된 행(ANT-CHAIN-04 결정 C3). 시작 시 커서보다 먼저 처리한다. */
    List<ChainEvent> findByProcessedAtIsNullOrderByBlockNumberAscLogIndexAsc();

    /** 재시작 커서 — 컨트랙트별 max(block_number). 이벤트가 없으면 empty. */
    @Query("select max(e.blockNumber) from ChainEvent e where e.contractAddress = :address")
    Optional<Long> findMaxBlockNumber(@Param("address") String address);

    /** 마지막으로 적재한 이벤트. reorg 검사(블록 해시 대조)의 기준점이다. */
    Optional<ChainEvent> findTopByContractAddressOrderByBlockNumberDescLogIndexDesc(String address);
}
