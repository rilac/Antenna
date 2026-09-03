package ssafy.a507.backend.domain.prediction.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.MapsId;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import java.time.Instant;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import ssafy.a507.backend.domain.chain.entity.AnchorBatch;

/**
 * 커밋. salt가 예측 본체에 실리지 않도록 테이블을 분리했다.
 * 리빌 전에는 salt를 어떤 응답에도 내보내지 않는다.
 */
@Entity
@Table(name = "prediction_commits")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PredictionCommit {

    @Id
    @Column(name = "prediction_id")
    private Long predictionId;

    @MapsId
    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "prediction_id")
    private Prediction prediction;

    /** hash(payload, salt). 머클 트리의 잎이다. */
    @Column(name = "commit_hash", nullable = false, unique = true, length = 66)
    private String commitHash;

    /** hex 난수. 리빌 전에는 어떤 응답에도 실리면 안 되므로 직렬화에서 뺀다. */
    @JsonIgnore
    @Column(nullable = false, length = 64)
    private String salt;

    /** EIP-191 personal_sign 서명. 작성자 부인방지용이다. 규격은 API 명세 v0.6 §1.2. */
    @Column(length = 255)
    private String signature;

    /** 서명 복원 주소. 연동 지갑과 다르면 401로 막는다. */
    @Column(name = "signer_address", length = 42)
    private String signerAddress;

    /** 소속 앵커 배치. NULL이면 앵커 대기 상태다. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "anchor_batch_id")
    private AnchorBatch anchorBatch;

    /** salt 공개 시각. NULL이면 아직 비공개다. */
    @Column(name = "revealed_at")
    private Instant revealedAt;
}
