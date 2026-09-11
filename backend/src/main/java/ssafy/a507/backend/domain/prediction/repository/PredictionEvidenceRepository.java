package ssafy.a507.backend.domain.prediction.repository;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import ssafy.a507.backend.domain.prediction.entity.PredictionEvidence;

public interface PredictionEvidenceRepository extends JpaRepository<PredictionEvidence, Long> {

    /** 예측 상세(ANT-PRED-05)의 근거 포인트. 포인트 본문을 실어야 해서 같이 끌어온다. 등록 순서 그대로. */
    @Query("select e from PredictionEvidence e join fetch e.point where e.prediction.id = :predictionId order by e.id")
    List<PredictionEvidence> findWithPointByPredictionId(@Param("predictionId") Long predictionId);
}
