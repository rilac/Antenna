package ssafy.a507.backend.domain.prediction.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import ssafy.a507.backend.domain.prediction.entity.PredictionNote;

public interface PredictionNoteRepository extends JpaRepository<PredictionNote, Long> {}
