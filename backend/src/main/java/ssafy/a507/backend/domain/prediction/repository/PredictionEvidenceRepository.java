package ssafy.a507.backend.domain.prediction.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import ssafy.a507.backend.domain.prediction.entity.PredictionEvidence;

public interface PredictionEvidenceRepository extends JpaRepository<PredictionEvidence, Long> {}
