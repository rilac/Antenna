package ssafy.a507.backend.domain.chain.repository;

import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import ssafy.a507.backend.domain.chain.entity.AnchorBatch;

public interface AnchorBatchRepository extends JpaRepository<AnchorBatch, Long> {

    /** 재시도 대상 스캔용. 실행 순서를 id 로 고정해 로그를 읽기 쉽게 한다. */
    List<AnchorBatch> findByStatusInOrderByIdAsc(Collection<AnchorBatch.Status> statuses);
}
