package ssafy.a507.backend.domain.prediction.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import ssafy.a507.backend.domain.prediction.entity.Prediction;

public interface PredictionRepository extends JpaRepository<Prediction, Long> {}
