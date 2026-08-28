package ssafy.a507.backend.domain.chain.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import ssafy.a507.backend.domain.chain.entity.AnchorBatch;

public interface AnchorBatchRepository extends JpaRepository<AnchorBatch, Long> {}
