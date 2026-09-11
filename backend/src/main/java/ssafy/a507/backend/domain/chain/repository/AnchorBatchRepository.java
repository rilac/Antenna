package ssafy.a507.backend.domain.chain.repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Limit;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import ssafy.a507.backend.domain.chain.entity.AnchorBatch;

public interface AnchorBatchRepository extends JpaRepository<AnchorBatch, Long> {

    /** 재시도 대상 스캔용. 실행 순서를 id 로 고정해 로그를 읽기 쉽게 한다. */
    List<AnchorBatch> findByStatusInOrderByIdAsc(Collection<AnchorBatch.Status> statuses);

    /** 인덱서가 Anchored 이벤트의 루트로 배치를 찾는다(ANT-CHAIN-13). merkle_root 는 UNIQUE 이고 0x 소문자로 저장된다. */
    Optional<AnchorBatch> findByMerkleRoot(String merkleRoot);

    /** 커밋 원장 목록(ANT-CHAIN-06) — 최신 배치부터, 커서 id 미만. 리포트 목록과 같은 커서 규칙이다. */
    @Query("""
            select b from AnchorBatch b
             where (:cursorId is null or b.id < :cursorId)
             order by b.id desc
            """)
    List<AnchorBatch> findPage(@Param("cursorId") Long cursorId, Limit limit);
}
