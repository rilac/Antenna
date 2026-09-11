package ssafy.a507.backend.domain.chain.anchor;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.persistence.EntityManager;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.transaction.annotation.Transactional;
import org.web3j.crypto.Hash;
import org.web3j.utils.Numeric;
import ssafy.a507.backend.domain.account.entity.User;
import ssafy.a507.backend.domain.chain.entity.AnchorBatch;
import ssafy.a507.backend.domain.chain.merkle.MerkleTree;
import ssafy.a507.backend.domain.chain.repository.AnchorBatchRepository;
import ssafy.a507.backend.domain.prediction.repository.PredictionCommitRepository;

/**
 * ANT-CHAIN-02 — 앵커 배치의 상태 전이. 체인은 {@link FakeAnchorRelayer} 가 대신한다.
 *
 * <p>H2 다. 스키마 검증(컬럼 추가)은 컨텍스트 로딩이 대신하고, 실제 PostgreSQL 은 수동 체크리스트로.
 * 커밋은 네이티브 INSERT 로 심는다(PostControllerTest 와 같은 방식).
 *
 * <p>v3(ANT-CHAIN-13): 체인 칸의 키가 머클루트다. 재시도·재전송은 루트로 확인하고, v2 의 batchId 충돌 판정은 사라졌다.
 */
// 배치 행에 contract_address 가 NOT NULL 이라 "배포된" 주소가 있어야 한다. 값은 아무 42자 주소면 된다 —
// 체인 호출은 FakeAnchorRelayer 가 받으므로 실제로 이 주소로 나가는 tx 는 없다.
@SpringBootTest(properties = "app.chain.commit-anchor.address=0x07f8CfE2bc6174D62BE8226E5e8699ffBf0d6D6a")
@Import(FakeAnchorRelayer.Config.class)
@Transactional
@DisplayName("앵커 배치 실행")
class AnchorRunnerTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 9, 4);

    @Autowired EntityManager em;
    @Autowired AnchorRunner runner;
    @Autowired FakeAnchorRelayer relayer;
    @Autowired AnchorBatchRepository batches;
    @Autowired PredictionCommitRepository commits;

    private Long userId;

    @BeforeEach
    void setUp() {
        relayer.reset();
        User user = User.create();
        em.persist(user);
        userId = user.getId();
        em.createNativeQuery("INSERT INTO stocks (code, name, listed) VALUES ('005930', '삼성전자', true)")
                .executeUpdate();
    }

    // ── 정상 ──────────────────────────────────────────────────────────

    @Test
    @DisplayName("대기 커밋 3건 → 배치 하나 → CONFIRMED, 커밋은 id 순으로 소속되고 루트는 MerkleTree 와 같다")
    void 대기_커밋을_배치로_묶어_확정한다() {
        long p1 = insertCommit("c1", "BASE");
        long p2 = insertCommit("c2", "BASE");
        long p3 = insertCommit("c3", "OPEN");
        relayer.thenConfirmed();

        Optional<Long> created = runner.run(TODAY);

        assertThat(created).isPresent();
        AnchorBatch batch = batches.findById(created.get()).orElseThrow();
        assertThat(batch.getStatus()).isEqualTo(AnchorBatch.Status.CONFIRMED);
        assertThat(batch.getCommitCount()).isEqualTo(3);
        assertThat(batch.getBusinessDate()).isEqualTo(TODAY);
        assertThat(batch.getChainId()).isEqualTo(31337L);
        assertThat(batch.getContractAddress()).isEqualTo("0x07f8cfe2bc6174d62be8226e5e8699ffbf0d6d6a"); // 소문자 정규화
        assertThat(batch.getTxHash()).startsWith("0x");
        assertThat(batch.getBlockNumber()).isNotNull();
        assertThat(batch.getSentAt()).isNotNull();
        assertThat(batch.getConfirmedAt()).isNotNull();
        assertThat(batch.getAttempts()).isEqualTo(1);

        // 리프 순서 = prediction id 오름차순, 루트 = 서버 MerkleTree 와 동일. 체인에는 DB id 가 가지 않는다.
        FakeAnchorRelayer.Call call = relayer.calls().get(0);
        assertThat(call.leaves()).hasSize(3);
        assertThat(Numeric.toHexString(call.leaves().get(0))).isEqualTo(hashOf("c1"));
        assertThat(Numeric.toHexString(call.leaves().get(2))).isEqualTo(hashOf("c3"));
        assertThat(Numeric.toHexString(call.root())).isEqualTo(batch.getMerkleRoot());
        assertThat(Numeric.toHexString(MerkleTree.build(call.leaves()).root())).isEqualTo(batch.getMerkleRoot());

        for (long pid : List.of(p1, p2, p3)) {
            assertThat(commits.findById(pid).orElseThrow().getAnchorBatch().getId()).isEqualTo(batch.getId());
        }
    }

    @Test
    @DisplayName("대기 커밋이 0건이면 배치도 tx 도 없다")
    void 커밋이_없으면_아무것도_하지_않는다() {
        assertThat(runner.run(TODAY)).isEmpty();
        assertThat(batches.count()).isZero();
        assertThat(relayer.calls()).isEmpty();
    }

    @Test
    @DisplayName("두 번째 실행은 이미 소속된 커밋을 다시 묶지 않고, 새 커밋만 새 배치로 보낸다")
    void 새_커밋만_새_배치가_된다() {
        insertCommit("a", "BASE");
        Long first = runner.run(TODAY).orElseThrow();

        insertCommit("b", "BASE");
        Long second = runner.run(TODAY).orElseThrow();

        assertThat(second).isNotEqualTo(first);
        assertThat(batches.findById(second).orElseThrow().getCommitCount()).isEqualTo(1);
        assertThat(relayer.calls()).hasSize(2);
        assertThat(relayer.calls().get(1).leaves()).hasSize(1);
    }

    // ── 실패·재시도 ─────────────────────────────────────────────────

    @Test
    @DisplayName("RPC 장애는 즉시 3회 재시도 후 FAILED. 커밋은 그 배치에 그대로 남는다")
    void RPC_장애_3회면_FAILED() {
        long pid = insertCommit("x", "BASE");
        relayer.thenUnavailable().thenUnavailable().thenUnavailable();

        Long id = runner.run(TODAY).orElseThrow();

        AnchorBatch batch = batches.findById(id).orElseThrow();
        assertThat(batch.getStatus()).isEqualTo(AnchorBatch.Status.FAILED);
        assertThat(batch.getAttempts()).isEqualTo(3);
        assertThat(batch.getLastError()).isEqualTo("CHAIN_UNAVAILABLE");
        assertThat(batch.getSentAt()).isNull(); // 한 번도 나가지 못했다
        assertThat(relayer.calls()).hasSize(3);
        assertThat(commits.findById(pid).orElseThrow().getAnchorBatch().getId()).isEqualTo(id);
    }

    @Test
    @DisplayName("RPC 장애가 두 번이고 세 번째에 성공하면 같은 실행 안에서 CONFIRMED")
    void 재시도_중_성공() {
        insertCommit("x", "BASE");
        relayer.thenUnavailable().thenUnavailable().thenConfirmed();

        Long id = runner.run(TODAY).orElseThrow();

        AnchorBatch batch = batches.findById(id).orElseThrow();
        assertThat(batch.getStatus()).isEqualTo(AnchorBatch.Status.CONFIRMED);
        assertThat(batch.getAttempts()).isEqualTo(3);
        assertThat(batch.getLastError()).isNull();
    }

    @Test
    @DisplayName("FAILED 배치는 다음 실행이 같은 루트·같은 커밋으로 재전송한다 — 새 배치에 합치지 않는다")
    void 실패_배치는_같은_루트로_재전송() {
        insertCommit("x", "BASE");
        relayer.thenUnavailable().thenUnavailable().thenUnavailable();
        Long failed = runner.run(TODAY).orElseThrow();
        assertThat(batches.findById(failed).orElseThrow().getStatus()).isEqualTo(AnchorBatch.Status.FAILED);

        // 다음 날: 새 커밋도 하나 들어왔다
        insertCommit("y", "BASE");
        relayer.thenConfirmed().thenConfirmed();
        Long fresh = runner.run(TODAY.plusDays(1)).orElseThrow();

        AnchorBatch retried = batches.findById(failed).orElseThrow();
        assertThat(retried.getStatus()).isEqualTo(AnchorBatch.Status.CONFIRMED);
        assertThat(retried.getCommitCount()).isEqualTo(1);
        assertThat(retried.getAttempts()).isEqualTo(4);
        assertThat(fresh).isNotEqualTo(failed);
        // 재전송 호출의 루트가 원래 배치 그대로다 — v3 는 루트가 곧 체인 칸의 키다
        FakeAnchorRelayer.Call resend = relayer.calls().get(3);
        assertThat(Numeric.toHexString(resend.root())).isEqualTo(retried.getMerkleRoot());
        // 안 보낸 배치(sent_at NULL)라 anchoredAt 은 보지 않았다
        assertThat(relayer.anchoredAtCalls()).isZero();
    }

    @Test
    @DisplayName("보냈는데 receipt 를 못 받은 배치는 PENDING+sent_at 으로 남고, 다음 실행이 anchoredAt 으로 CONFIRMED 한다")
    void 미확정_배치는_anchoredAt으로_확인() {
        insertCommit("x", "BASE");
        relayer.thenUnconfirmed();
        Long id = runner.run(TODAY).orElseThrow();

        AnchorBatch pending = batches.findById(id).orElseThrow();
        assertThat(pending.getStatus()).isEqualTo(AnchorBatch.Status.PENDING);
        assertThat(pending.getSentAt()).isNotNull();
        assertThat(pending.getTxHash()).startsWith("0x");
        assertThat(pending.getBlockNumber()).isNull();

        // 체인에는 이미 박혀 있었다
        relayer.setAnchored(Numeric.hexStringToByteArray(pending.getMerkleRoot()), 11_000_500L);
        runner.run(TODAY.plusDays(1));

        AnchorBatch confirmed = batches.findById(id).orElseThrow();
        assertThat(confirmed.getStatus()).isEqualTo(AnchorBatch.Status.CONFIRMED);
        assertThat(confirmed.getTxHash()).isEqualTo(pending.getTxHash()); // 알던 해시는 유지
        assertThat(relayer.anchoredAtCalls()).isEqualTo(1);
        assertThat(relayer.calls()).hasSize(1); // 재전송하지 않았다
    }

    @Test
    @DisplayName("보냈는데 미확정이고 체인에도 없으면(anchoredAt=0) 같은 루트로 재전송한다")
    void 미확정이고_체인에_없으면_재전송() {
        insertCommit("x", "BASE");
        relayer.thenUnconfirmed();
        Long id = runner.run(TODAY).orElseThrow();

        relayer.thenConfirmed();
        runner.run(TODAY.plusDays(1));

        AnchorBatch batch = batches.findById(id).orElseThrow();
        assertThat(batch.getStatus()).isEqualTo(AnchorBatch.Status.CONFIRMED);
        assertThat(relayer.anchoredAtCalls()).isEqualTo(1);
        assertThat(relayer.calls()).hasSize(2);
        assertThat(Numeric.toHexString(relayer.calls().get(1).root())).isEqualTo(batch.getMerkleRoot());
    }

    @Test
    @DisplayName("AlreadyAnchored(같은 루트가 이미 박힘)면 곧 성공이다 — CONFIRMED, 체인을 다시 묻지 않는다")
    void 이미_앵커됨이면_성공() {
        // 같은 루트 = 같은 내용이라(ANT-CHAIN-13) 남의 것을 내 것으로 오판할 수 없다. v2 는 여기서 rootOf 를 다시 읽어 충돌을 가렸다.
        insertCommit("x", "BASE");
        relayer.thenAlreadyAnchored();

        Long id = runner.run(TODAY).orElseThrow();

        AnchorBatch batch = batches.findById(id).orElseThrow();
        assertThat(batch.getStatus()).isEqualTo(AnchorBatch.Status.CONFIRMED);
        assertThat(batch.getTxHash()).isNull(); // 이번엔 보내지 않았으니 해시를 모른다 — 인덱서가 채운다
        assertThat(relayer.anchoredAtCalls()).isZero();
    }

    @Test
    @DisplayName("같은 컨트랙트에 다른 DB 의 앵커가 먼저 박혀 있어도 우리 배치는 영향이 없다 — v2 의 batchId 충돌이 사라졌다")
    void 다른_DB의_앵커와_부딪히지_않는다() {
        // v2 에서는 다른 DB(데모·로컬·복원 전 운영)가 태운 번호를 우리 DB 가 다시 쓰면 BATCH_ID_COLLISION 이었다(contracts/README 함정 2).
        // v3 는 칸의 키가 루트라 내용이 다르면 칸이 다르다.
        relayer.setAnchored(Hash.sha3("someone-else".getBytes()), 11_000_000L);
        insertCommit("x", "BASE");
        relayer.thenConfirmed();

        Long id = runner.run(TODAY).orElseThrow();

        AnchorBatch batch = batches.findById(id).orElseThrow();
        assertThat(batch.getStatus()).isEqualTo(AnchorBatch.Status.CONFIRMED);
        assertThat(batch.getLastError()).isNull();
    }

    @Test
    @DisplayName("RootMismatch 같은 revert 는 즉시 FAILED 이고 재시도하지 않는다")
    void revert는_재시도하지_않는다() {
        insertCommit("x", "BASE");
        relayer.thenRevert("RootMismatch");

        Long id = runner.run(TODAY).orElseThrow();

        AnchorBatch batch = batches.findById(id).orElseThrow();
        assertThat(batch.getStatus()).isEqualTo(AnchorBatch.Status.FAILED);
        assertThat(batch.getLastError()).isEqualTo("RootMismatch");
        assertThat(batch.getAttempts()).isEqualTo(1);
        assertThat(relayer.calls()).hasSize(1);
    }

    @Test
    @DisplayName("재시도 단계에서 anchoredAt 조회가 실패하면 그 배치는 이번 실행을 건너뛴다(재전송 안 함)")
    void anchoredAt_장애면_건너뜀() {
        insertCommit("x", "BASE");
        relayer.thenUnconfirmed();
        Long id = runner.run(TODAY).orElseThrow();

        relayer.anchoredAtUnavailable();
        runner.run(TODAY.plusDays(1));

        assertThat(batches.findById(id).orElseThrow().getStatus()).isEqualTo(AnchorBatch.Status.PENDING);
        assertThat(relayer.calls()).hasSize(1);
    }

    // ── 릴레이어 꺼짐 · 리빌 ────────────────────────────────────────

    @Test
    @DisplayName("릴레이어가 꺼져 있으면 배치를 만들지 않지만 리빌은 돈다")
    void 릴레이어_꺼짐() {
        insertCommit("x", "BASE");
        long settled = insertCommit("y", "HIT");
        relayer.disable();

        assertThat(runner.run(TODAY)).isEmpty();
        assertThat(batches.count()).isZero();
        assertThat(commits.findById(settled).orElseThrow().isRevealed()).isTrue();
    }

    @Test
    @DisplayName("리빌은 HIT/MISS 만 — BASE/OPEN 은 salt 를 공개하지 않는다 (결정 A9: 판정 기준)")
    void 리빌은_판정된_것만() {
        long base = insertCommit("a", "BASE");
        long open = insertCommit("b", "OPEN");
        long hit = insertCommit("c", "HIT");
        long miss = insertCommit("d", "MISS");

        runner.run(TODAY);

        assertThat(commits.findById(base).orElseThrow().isRevealed()).isFalse();
        assertThat(commits.findById(open).orElseThrow().isRevealed()).isFalse();
        assertThat(commits.findById(hit).orElseThrow().isRevealed()).isTrue();
        assertThat(commits.findById(miss).orElseThrow().isRevealed()).isTrue();
    }

    @Test
    @DisplayName("이미 공개된 커밋은 다시 돌려도 revealed_at 이 바뀌지 않는다")
    void 리빌은_멱등() {
        long hit = insertCommit("c", "HIT");
        runner.run(TODAY);
        var first = commits.findById(hit).orElseThrow().getRevealedAt();

        runner.run(TODAY.plusDays(1));

        assertThat(commits.findById(hit).orElseThrow().getRevealedAt()).isEqualTo(first);
    }

    // ── 픽스처 ──────────────────────────────────────────────────────

    private static String hashOf(String seed) {
        return Numeric.toHexString(Hash.sha3(seed.getBytes()));
    }

    /** 예측 + 커밋을 심고 prediction id 를 돌려준다. 커밋 해시는 seed 의 keccak. */
    private long insertCommit(String seed, String status) {
        em.createNativeQuery(
                        """
                        INSERT INTO predictions
                          (user_id, track, stock_code, direction, target_price, horizon, status, created_at, updated_at)
                        VALUES (?, 'REAL', '005930', 'UP', 80000.00, 30, ?, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                        """)
                .setParameter(1, userId)
                .setParameter(2, status)
                .executeUpdate();
        long pid =
                ((Number) em.createNativeQuery("SELECT MAX(id) FROM predictions").getSingleResult()).longValue();
        em.createNativeQuery(
                        """
                        INSERT INTO prediction_commits (prediction_id, commit_hash, salt)
                        VALUES (?, ?, ?)
                        """)
                .setParameter(1, pid)
                .setParameter(2, hashOf(seed))
                .setParameter(3, "00".repeat(32))
                .executeUpdate();
        em.flush();
        em.clear();
        return pid;
    }
}
