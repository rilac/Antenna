package ssafy.a507.backend.domain.prediction.commit;

import java.util.Objects;
import org.springframework.stereotype.Component;
import ssafy.a507.backend.domain.prediction.entity.Prediction;
import ssafy.a507.backend.domain.prediction.entity.PredictionCommit;

/**
 * 예측 하나를 봉인해 {@link PredictionCommit} 을 만든다 (ANT-PRED-02). 등록 API(PRED-01)가 부른다.
 *
 * <pre>
 * noteHash   = keccak256(note ‖ noteSalt)          ← 근거 봉인. noteSalt 는 클라이언트가 만든 32바이트
 * commitHash = keccak256(CommitPayload.canonical()) ← 예측 봉인. 머클 리프의 원료
 * </pre>
 *
 * <p><b>저장은 하지 않는다.</b> 엔티티만 돌려주고 저장은 호출자 트랜잭션에서 예측·근거와 함께 한다.
 * 따로 커밋되면 "예측은 있는데 커밋이 없는" 반쪽 상태가 생기고, 그 예측은 영영 앵커되지 않는다.
 *
 * <p>서버가 만드는 난수·비밀은 없다. 클라이언트가 보낸 noteHash 는 받지 않고 여기서 <b>다시 계산</b>한다 —
 * 보내온 해시를 믿으면 "근거는 A, 해시는 B" 변조가 가능해진다(서명 검증과 같은 원칙).
 */
@Component
public class PredictionCommitFactory {

    /**
     * @param prediction 저장 대상 예측(영속 여부 무관 — 커밋은 {@code @MapsId} 로 같은 id 를 쓴다)
     * @param note 근거 본문 그대로. 빈 문자열이어도 된다 — {@code keccak(noteSalt)} 도 난수라 안전하다
     * @param noteSalt 클라이언트가 만든 64 hex. 형식·"전부 같은 바이트" 만 검사한다
     * @param signature 작성자 EIP-191 서명, {@code signerAddress} 복원 주소 — 검증은 PRED-01 이 {@code SignatureGuard} 로 끝내고 온다
     */
    public PredictionCommit seal(
            Prediction prediction, String note, String noteSalt, String signature, String signerAddress) {
        Objects.requireNonNull(prediction, "prediction");
        Objects.requireNonNull(note, "note — 근거가 없으면 빈 문자열을 넘긴다");
        String noteHash = CommitHashes.noteHash(note, noteSalt);
        String commitHash = CommitHashes.keccak256Hex(CommitPayload.of(prediction, noteHash).canonical());
        return PredictionCommit.seal(prediction, commitHash, noteHash, noteSalt, signature, signerAddress);
    }
}
