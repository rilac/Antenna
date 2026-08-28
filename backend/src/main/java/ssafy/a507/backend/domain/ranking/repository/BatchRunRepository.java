package ssafy.a507.backend.domain.ranking.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import ssafy.a507.backend.domain.ranking.entity.BatchRun;

public interface BatchRunRepository extends JpaRepository<BatchRun, Long> {}
