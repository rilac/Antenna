package ssafy.a507.backend.domain.prediction;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.MapsId;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** 예측 근거. 게이팅 쿼리가 조인 하나로 끝나도록 본체에서 분리했다. 미구독자는 403이다. */
@Entity
@Table(name = "prediction_notes")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PredictionNote {

    @Id
    @Column(name = "prediction_id")
    private Long predictionId;

    @MapsId
    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "prediction_id")
    private Prediction prediction;

    /** 5000자 상한은 애플리케이션에서 검증한다. */
    @Column(columnDefinition = "text")
    private String body;
}
