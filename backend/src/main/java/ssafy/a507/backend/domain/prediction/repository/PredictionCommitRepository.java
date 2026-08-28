package ssafy.a507.backend.domain.prediction.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import ssafy.a507.backend.domain.prediction.entity.PredictionCommit;

public interface PredictionCommitRepository extends JpaRepository<PredictionCommit, Long> {}
