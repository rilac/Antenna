package ssafy.a507.backend.domain.prediction;

import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import ssafy.a507.backend.domain.research.ResearchPoint;

/** 예측에 붙인 리서치 포인트. 봉인 후에는 추가·삭제할 수 없다. */
@Entity
@Table(
        name = "prediction_evidences",
        uniqueConstraints =
                @UniqueConstraint(
                        name = "uq_prediction_evidences_prediction_point",
                        columnNames = {"prediction_id", "point_id"}))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PredictionEvidence {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "prediction_id", nullable = false)
    private Prediction prediction;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "point_id", nullable = false)
    private ResearchPoint point;
}
