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
 *
 * <p>만드는 곳은 {@code PredictionCommitFactory}(ANT-PRED-02) 하나다. 규격은 거기 javadoc 과 {@code CommitPayload} 에 있다.
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

    /** keccak256(커밋 문자열). 머클 트리의 잎이다. 문자열 규격은 {@code CommitPayload}. */
    @Column(name = "commit_hash", nullable = false, unique = true, length = 66)
    private String commitHash;

    /**
     * 근거 salt — 그리고 이 시스템의 <b>유일한 난수</b>다. 클라이언트가 만든 32바이트(64 hex).
     *
     * <p>{@code noteHash = keccak256(note ‖ salt)} 가 이 난수성을 물려받고, 그 noteHash 가 커밋 문자열 안에 들어가므로
     * commitHash 도 리빌 전에 추측할 수 없다. 별도의 커밋 salt 는 두지 않는다(09-09 결정). <b>이 값을 없애거나 약하게 만들면
     * 커밋 전체가 다시 사전 공격에 뚫린다.</b>
     *
     * <p>리빌 뒤에도 <b>작성자·구독자만</b> 받는다(결정 D6) — 근거 본문이 구독자 전용이라, 비구독자가 짧은 근거를 후보 해시로
     * 맞추는 걸 막는다. 비구독자의 ①단계 검산에는 이 값이 필요 없다(noteHash 만 있으면 된다). 직렬화에서는 항상 뺀다.
     */
    @JsonIgnore
    @Column(nullable = false, length = 64)
    private String salt;

    /**
     * keccak256(근거 본문 ‖ salt). 커밋 문자열의 마지막 줄이자 바깥 해시의 salt 역할.
     * 리빌 뒤 회원 전원에게 공개된다 — 비구독자가 ①단계(commitHash 재계산)를 하려면 이 값이 있어야 한다.
     * nullable 인 이유는 ddl-auto 때문이다: 행이 있는 테이블에 NOT NULL 컬럼을 더하면 Hibernate 가 조용히 실패한다. 팩터리는 항상 채운다.
     */
    @Column(name = "note_hash", length = 66)
    private String noteHash;

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

    /**
     * 봉인 결과를 담는다. 값 계산은 {@code PredictionCommitFactory} 가 끝내고 온다 — 엔티티는 계산하지 않는다.
     * 저장은 호출자가 예측과 같은 트랜잭션에서 한다.
     */
    public static PredictionCommit seal(
            Prediction prediction,
            String commitHash,
            String noteHash,
            String salt,
            String signature,
            String signerAddress) {
        PredictionCommit c = new PredictionCommit();
        // predictionId 는 손대지 않는다 — @MapsId 가 persist 때 prediction 에서 가져온다. 직접 채우면 Spring Data save() 가
        // "이미 있는 행" 으로 보고 merge 를 타서 Hibernate 가 null identifier 로 넘어진다.
        c.prediction = prediction;
        c.commitHash = commitHash;
        c.noteHash = noteHash;
        c.salt = salt;
        c.signature = signature;
        c.signerAddress = signerAddress;
        return c;
    }

    /**
     * 앵커 배치에 넣는다 (ANT-CHAIN-02). 한 번 넣으면 바꾸지 않는다 — 배치가 FAILED 여도 같은 배치로
     * 재전송하지, 다른 배치로 옮기지 않는다(옮기면 이미 계산된 루트와 리프 목록이 어긋난다).
     */
    public void assignBatch(AnchorBatch batch) {
        if (this.anchorBatch != null) {
            throw new IllegalStateException("이미 배치에 속한 커밋이다: prediction " + predictionId);
        }
        this.anchorBatch = batch;
    }

    /** salt 를 공개한다. 판정(HIT/MISS) 뒤에만 부른다 — 호출자가 status 를 확인한다. 이미 공개됐으면 그대로 둔다. */
    public void reveal(Instant now) {
        if (this.revealedAt == null) {
            this.revealedAt = now;
        }
    }

    public boolean isRevealed() {
        return revealedAt != null;
    }
}
