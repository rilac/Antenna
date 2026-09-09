package ssafy.a507.backend.domain.prediction.commit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;
import ssafy.a507.backend.common.error.BusinessException;
import ssafy.a507.backend.domain.account.entity.User;
import ssafy.a507.backend.domain.prediction.entity.Prediction;
import ssafy.a507.backend.domain.prediction.entity.PredictionCommit;
import ssafy.a507.backend.domain.prediction.repository.PredictionCommitRepository;

/**
 * PredictionCommitFactory — 예측 한 건을 봉인해 저장까지 (ANT-PRED-02). H2.
 *
 * <p>예측 행은 팩터리가 없어(PRED-01 몫) 네이티브 INSERT 로 심고 {@code em.find} 로 꺼내 넘긴다.
 * 여기서 보는 것: 저장된 값의 모양, 같은 입력이 noteSalt 만 다르면 다른 커밋이 되는 것,
 * 그리고 <b>salt 가 어떤 직렬화에도 실리지 않는 것</b>(AC "리빌 전 어떤 응답에도 미노출").
 */
@SpringBootTest
@Transactional
@DisplayName("PredictionCommitFactory — 예측 봉인")
class PredictionCommitFactoryTest {

    private static final String SALT = "0123456789abcdef".repeat(4);
    private static final String NOTE = "3분기 실적 컨센서스 상회 전망";
    private static final String SIGNATURE = "0x" + "ab".repeat(65);
    private static final String SIGNER = "0x" + "11".repeat(20);

    @Autowired PredictionCommitFactory factory;
    @Autowired PredictionCommitRepository commits;
    @Autowired EntityManager em;

    private long userId;

    @BeforeEach
    void setUp() {
        User user = User.create();
        em.persist(user);
        em.createNativeQuery("INSERT INTO stocks (code, name, listed) VALUES ('005930', '삼성전자', true)")
                .executeUpdate();
        em.flush();
        userId = user.getId();
    }

    @Test
    @DisplayName("봉인 → 저장 → 다시 읽으면 commitHash · noteHash · salt · 서명이 그대로고 앵커 대기(anchorBatch null) 상태다")
    void 봉인_저장_재조회() {
        Prediction prediction = insertPrediction();

        PredictionCommit sealed = factory.seal(prediction, NOTE, SALT, SIGNATURE, SIGNER);
        commits.save(sealed);
        em.flush();
        em.clear();

        PredictionCommit found = commits.findById(prediction.getId()).orElseThrow();
        String expectedNoteHash = CommitHashes.noteHash(NOTE, SALT);
        String expectedCommitHash = CommitHashes.keccak256Hex(
                new CommitPayload("005930", Prediction.Direction.UP, prediction.getTargetPrice(), (short) 30, expectedNoteHash)
                        .canonical());
        assertThat(found.getCommitHash()).isEqualTo(expectedCommitHash).matches("^0x[0-9a-f]{64}$");
        assertThat(found.getNoteHash()).isEqualTo(expectedNoteHash);
        assertThat(found.getSalt()).isEqualTo(SALT);
        assertThat(found.getSignature()).isEqualTo(SIGNATURE);
        assertThat(found.getSignerAddress()).isEqualTo(SIGNER);
        assertThat(found.getAnchorBatch()).isNull();
        assertThat(found.isRevealed()).isFalse();
        // 앵커 배치가 긁어가는 쿼리에 잡혀야 한다 — 이게 "매일 빈손" 을 끝내는 조건이다.
        assertThat(commits.findByAnchorBatchIsNullOrderByPredictionIdAsc()).extracting(PredictionCommit::getPredictionId)
                .contains(prediction.getId());
    }

    @Test
    @DisplayName("같은 예측·같은 근거라도 noteSalt 가 다르면 commitHash 가 다르다 — 난수 하나가 커밋 전체를 가린다")
    void noteSalt_만_달라도_다른_커밋() {
        Prediction p1 = insertPrediction();
        Prediction p2 = insertPrediction();
        PredictionCommit a = factory.seal(p1, NOTE, SALT, SIGNATURE, SIGNER);
        PredictionCommit b = factory.seal(p2, NOTE, "fedcba9876543210".repeat(4), SIGNATURE, SIGNER);
        assertThat(a.getNoteHash()).isNotEqualTo(b.getNoteHash());
        assertThat(a.getCommitHash()).isNotEqualTo(b.getCommitHash());
    }

    @Test
    @DisplayName("salt 는 엔티티 직렬화에서 빠진다(@JsonIgnore) — 리빌 전 어떤 응답에도 실리지 않는다는 AC 의 마지막 방어선")
    void salt_직렬화_차단() throws Exception {
        // 실제 응답(proof API)은 ProofControllerTest 가 본다. 여기서는 엔티티가 실수로 그대로 나가도 salt 는 안 새는지를 본다.
        assertThat(PredictionCommit.class.getDeclaredField("salt").isAnnotationPresent(JsonIgnore.class)).isTrue();
        // noteHash 는 공개 값이라 가리지 않는다 — 가리면 비구독자 ①단계 검산이 막힌다.
        assertThat(PredictionCommit.class.getDeclaredField("noteHash").isAnnotationPresent(JsonIgnore.class)).isFalse();
    }

    @Test
    @DisplayName("noteSalt 형식이 틀리면 봉인하지 않는다 — 000…0 도 거절")
    void noteSalt_형식_오류() {
        Prediction prediction = insertPrediction();
        assertThatThrownBy(() -> factory.seal(prediction, NOTE, "00".repeat(32), SIGNATURE, SIGNER))
                .isInstanceOf(BusinessException.class);
        assertThatThrownBy(() -> factory.seal(prediction, NOTE, "0x" + SALT.substring(2), SIGNATURE, SIGNER))
                .isInstanceOf(BusinessException.class);
    }

    private Prediction insertPrediction() {
        em.createNativeQuery(
                        """
                        INSERT INTO predictions
                          (user_id, track, stock_code, direction, target_price, horizon, status, created_at, updated_at)
                        VALUES (?, 'REAL', '005930', 'UP', 82000.00, 30, 'BASE', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                        """)
                .setParameter(1, userId)
                .executeUpdate();
        long id = ((Number) em.createNativeQuery("SELECT MAX(id) FROM predictions").getSingleResult()).longValue();
        em.clear();
        return em.find(Prediction.class, id);
    }
}
