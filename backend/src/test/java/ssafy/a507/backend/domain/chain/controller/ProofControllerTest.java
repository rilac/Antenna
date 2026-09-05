package ssafy.a507.backend.domain.chain.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import jakarta.persistence.EntityManager;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import org.web3j.crypto.Hash;
import org.web3j.utils.Numeric;
import ssafy.a507.backend.domain.account.entity.User;
import ssafy.a507.backend.domain.chain.entity.AnchorBatch;
import ssafy.a507.backend.domain.chain.merkle.MerkleTree;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * ANT-CHAIN-06 — 3단계 검산 proof. H2.
 *
 * <p>예측·커밋·구독은 팩터리가 없어(PRED-01·02 몫) 네이티브 INSERT 로 심는다. 게이팅 두 겹(미판정 예측 / 근거 salt)과
 * proof 의 실제 유효성({@link MerkleTree#verify})을 본다. 체인은 부르지 않는다 — 루트 대조는 브라우저 몫이다.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
@DisplayName("3단계 검산 proof API")
class ProofControllerTest {

    private static final String CONTRACT = "0x07f8cfe2bc6174d62be8226e5e8699ffbf0d6d6a";
    private static final LocalDate DAY = LocalDate.of(2026, 9, 5);
    private static final String SALT = "5e" + "00".repeat(31);
    private static final JsonMapper JSON = JsonMapper.builder().build();

    @Autowired MockMvc mockMvc;
    @Autowired EntityManager em;

    private long authorId;
    private long otherId;

    @BeforeEach
    void setUp() {
        authorId = insertUser();
        otherId = insertUser();
        em.createNativeQuery("INSERT INTO stocks (code, name, listed) VALUES ('005930', '삼성전자', true)")
                .executeUpdate();
    }

    // ── 겹 ① 미판정 예측 게이팅 ──────────────────────────────────────

    @Nested
    @DisplayName("미판정(OPEN) 예측")
    class Unsettled {

        @Test
        @DisplayName("작성자 본인은 200 — 앵커 전이면 anchor 는 null, anchorStatus 는 WAITING")
        void 작성자_앵커_전() throws Exception {
            long pid = insertPrediction(authorId, "OPEN");
            insertCommit(pid, "seed", null);

            mockMvc.perform(get("/api/v1/predictions/{id}/proof", pid).with(user(str(authorId))))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.predictionId").value(pid))
                    .andExpect(jsonPath("$.commitHash").value(hashOf("seed")))
                    .andExpect(jsonPath("$.signerAddress").value("0x" + "11".repeat(20)))
                    .andExpect(jsonPath("$.payload.stockCode").value("005930"))
                    .andExpect(jsonPath("$.payload.direction").value("UP"))
                    .andExpect(jsonPath("$.payload.targetPrice").value(80000.0))
                    .andExpect(jsonPath("$.payload.horizon").value(30))
                    .andExpect(jsonPath("$.payload.noteHash").value((Object) null))
                    .andExpect(jsonPath("$.anchor").value((Object) null))
                    .andExpect(jsonPath("$.anchorStatus").value("WAITING"))
                    .andExpect(jsonPath("$.salt").value((Object) null))
                    .andExpect(jsonPath("$.settle").value((Object) null));
        }

        @Test
        @DisplayName("구독하지 않은 남은 403 PREDICTION_FORBIDDEN — 404 로 숨기지 않는다")
        void 비구독자_403() throws Exception {
            long pid = insertPrediction(authorId, "OPEN");
            insertCommit(pid, "seed", null);

            mockMvc.perform(get("/api/v1/predictions/{id}/proof", pid).with(user(str(otherId))))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.code").value("PREDICTION_FORBIDDEN"));
        }

        @Test
        @DisplayName("유효 구독자(ACTIVE + 기간 안)는 200")
        void 유효_구독자_200() throws Exception {
            long pid = insertPrediction(authorId, "OPEN");
            insertCommit(pid, "seed", null);
            insertSubscription(otherId, authorId, "ACTIVE", Instant.now().plusSeconds(86_400));

            mockMvc.perform(get("/api/v1/predictions/{id}/proof", pid).with(user(str(otherId))))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.commitHash").value(hashOf("seed")));
        }

        @Test
        @DisplayName("만료된 구독(ACTIVE 지만 expires_at 이 지남)은 403 — 상태가 아니라 기간이 근거다")
        void 만료_구독자_403() throws Exception {
            long pid = insertPrediction(authorId, "OPEN");
            insertCommit(pid, "seed", null);
            insertSubscription(otherId, authorId, "ACTIVE", Instant.now().minusSeconds(60));

            mockMvc.perform(get("/api/v1/predictions/{id}/proof", pid).with(user(str(otherId))))
                    .andExpect(status().isForbidden());
        }

        @Test
        @DisplayName("결제 대기(PENDING) 구독은 아직 열람 권한이 아니다 — 403")
        void 대기_구독자_403() throws Exception {
            long pid = insertPrediction(authorId, "OPEN");
            insertCommit(pid, "seed", null);
            insertSubscription(otherId, authorId, "PENDING", null);

            mockMvc.perform(get("/api/v1/predictions/{id}/proof", pid).with(user(str(otherId))))
                    .andExpect(status().isForbidden());
        }
    }

    @Nested
    @DisplayName("판정(HIT) 뒤")
    class Settled {

        @Test
        @DisplayName("구독 없는 남도 200 — settle 이 채워지고 sourceUrl 은 만기일·종목으로 조립된다")
        void 타인_200_settle() throws Exception {
            long pid = insertPrediction(authorId, "HIT");
            insertCommit(pid, "seed", null);

            mockMvc.perform(get("/api/v1/predictions/{id}/proof", pid).with(user(str(otherId))))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.settle.status").value("HIT"))
                    .andExpect(jsonPath("$.settle.settleDate").value("2026-10-05"))
                    .andExpect(jsonPath("$.settle.settlePrice").value(82000.0))
                    .andExpect(jsonPath("$.settle.basePrice").value(79000.0))
                    .andExpect(jsonPath("$.settle.sourceUrl").value(
                            org.hamcrest.Matchers.allOf(
                                    org.hamcrest.Matchers.containsString("basDt=20261005"),
                                    org.hamcrest.Matchers.containsString("likeSrtnCd=005930"),
                                    org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("serviceKey")))));
        }

        @Test
        @DisplayName("리빌 전에는 salt 가 없고, 리빌 뒤에는 남에게도 salt 가 간다 — 비구독자도 ①단계를 검산해야 한다")
        void 리빌_전후_salt() throws Exception {
            long before = insertPrediction(authorId, "HIT");
            insertCommit(before, "b", null);
            long after = insertPrediction(authorId, "HIT");
            insertCommit(after, "a", Instant.parse("2026-10-05T04:31:00Z"));

            mockMvc.perform(get("/api/v1/predictions/{id}/proof", before).with(user(str(otherId))))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.salt").value((Object) null))
                    .andExpect(jsonPath("$.revealedAt").value((Object) null));
            mockMvc.perform(get("/api/v1/predictions/{id}/proof", after).with(user(str(otherId))))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.salt").value(SALT))
                    .andExpect(jsonPath("$.revealedAt").value("2026-10-05T04:31:00Z"));
        }

        @Test
        @DisplayName("noteSalt 자리는 있으나 PRED-02 전까지 작성자에게도 null 이다")
        void noteSalt_는_아직_null() throws Exception {
            long pid = insertPrediction(authorId, "HIT");
            insertCommit(pid, "a", Instant.parse("2026-10-05T04:31:00Z"));

            String body = mockMvc.perform(get("/api/v1/predictions/{id}/proof", pid).with(user(str(authorId))))
                    .andExpect(status().isOk())
                    .andReturn()
                    .getResponse()
                    .getContentAsString();

            JsonNode json = JSON.readTree(body);
            assertThat(json.has("noteSalt")).isTrue();
            assertThat(json.get("noteSalt").isNull()).isTrue();
        }
    }

    // ── 앵커 · proof ──────────────────────────────────────────────────

    @Test
    @DisplayName("확정 배치의 커밋 — merkleProof 로 접은 루트가 merkleRoot 와 같고 leafIndex 는 리프 순서상 자리다")
    void proof_가_실제로_검증된다() throws Exception {
        List<Long> pids = new ArrayList<>();
        List<String> seeds = List.of("l0", "l1", "l2", "l3", "l4");
        for (String s : seeds) {
            long pid = insertPrediction(authorId, "OPEN");
            insertCommit(pid, s, null);
            pids.add(pid);
        }
        AnchorBatch batch = confirmedBatch(seeds, pids);
        long target = pids.get(2);

        String body = mockMvc.perform(get("/api/v1/predictions/{id}/proof", target).with(user(str(authorId))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.anchorStatus").value("CONFIRMED"))
                .andExpect(jsonPath("$.anchor.batchId").value(batch.getId()))
                .andExpect(jsonPath("$.anchor.merkleRoot").value(batch.getMerkleRoot()))
                .andExpect(jsonPath("$.anchor.leafIndex").value(2))
                .andExpect(jsonPath("$.anchor.leafCount").value(5))
                .andExpect(jsonPath("$.anchor.status").value("CONFIRMED"))
                .andExpect(jsonPath("$.anchor.txHash").value(batch.getTxHash()))
                .andExpect(jsonPath("$.anchor.blockNumber").value(batch.getBlockNumber()))
                .andExpect(jsonPath("$.anchor.contractAddress").value(CONTRACT))
                .andExpect(jsonPath("$.anchor.chainId").value(31337))
                .andReturn()
                .getResponse()
                .getContentAsString();

        JsonNode json = JSON.readTree(body);
        List<byte[]> proof = new ArrayList<>();
        json.get("anchor").get("merkleProof").forEach(n -> proof.add(Numeric.hexStringToByteArray(n.asString())));
        byte[] commitHash = Numeric.hexStringToByteArray(json.get("commitHash").asString());
        byte[] root = Numeric.hexStringToByteArray(json.get("anchor").get("merkleRoot").asString());

        assertThat(MerkleTree.verify(commitHash, proof, root)).isTrue();
        // 남의 커밋 해시로는 같은 proof 가 통하지 않는다.
        assertThat(MerkleTree.verify(Numeric.hexStringToByteArray(hashOf("l3")), proof, root)).isFalse();
    }

    @Test
    @DisplayName("배치가 아직 PENDING 이면 proof 는 내리되 anchorStatus 가 PENDING 이다 — 트리는 DB 만으로 정해진다")
    void 미확정_배치는_PENDING() throws Exception {
        long pid = insertPrediction(authorId, "OPEN");
        insertCommit(pid, "only", null);
        String root = Numeric.toHexString(MerkleTree.build(List.of(Hash.sha3("only".getBytes()))).root());
        AnchorBatch batch = AnchorBatch.open(DAY, root, 1, CONTRACT, 31337L);
        em.persist(batch);
        em.flush();
        assign(batch.getId(), pid);

        mockMvc.perform(get("/api/v1/predictions/{id}/proof", pid).with(user(str(authorId))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.anchorStatus").value("PENDING"))
                .andExpect(jsonPath("$.anchor.status").value("PENDING"))
                .andExpect(jsonPath("$.anchor.merkleProof").isEmpty())
                .andExpect(jsonPath("$.anchor.txHash").value((Object) null));
    }

    @Test
    @DisplayName("리프로 접은 루트가 저장된 루트와 다르면 500 — 틀린 proof 를 내리지 않는다")
    void 루트_불일치_500() throws Exception {
        long pid = insertPrediction(authorId, "OPEN");
        insertCommit(pid, "x", null);
        AnchorBatch batch = AnchorBatch.open(DAY, hashOf("not-the-root"), 1, CONTRACT, 31337L);
        em.persist(batch);
        em.flush();
        assign(batch.getId(), pid);

        mockMvc.perform(get("/api/v1/predictions/{id}/proof", pid).with(user(str(authorId))))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.code").value("INTERNAL_ERROR"));
    }

    // ── 기타 ──────────────────────────────────────────────────────────

    @Test
    @DisplayName("없는 예측은 404 PREDICTION_NOT_FOUND")
    void 없는_예측_404() throws Exception {
        mockMvc.perform(get("/api/v1/predictions/{id}/proof", 999_999).with(user(str(authorId))))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("PREDICTION_NOT_FOUND"));
    }

    @Test
    @DisplayName("인증 없이는 401")
    void 인증_없으면_401() throws Exception {
        mockMvc.perform(get("/api/v1/predictions/{id}/proof", 1)).andExpect(status().isUnauthorized());
    }

    // ── 픽스처 ──────────────────────────────────────────────────────

    private static String str(long id) {
        return String.valueOf(id);
    }

    private static String hashOf(String seed) {
        return Numeric.toHexString(Hash.sha3(seed.getBytes()));
    }

    private long insertUser() {
        User user = User.create();
        em.persist(user);
        em.flush();
        return user.getId();
    }

    /** HIT 이면 판정 컬럼(기준가·만기·종가·오차)까지 채운다 — PRED-03 이 채울 값의 모양이다. */
    private long insertPrediction(long userId, String status) {
        boolean settled = status.equals("HIT") || status.equals("MISS");
        em.createNativeQuery(
                        """
                        INSERT INTO predictions
                          (user_id, track, stock_code, direction, target_price, ref_close, horizon, status,
                           base_date, base_price, settle_date, settle_price, error_rate, created_at, updated_at)
                        VALUES (?, 'REAL', '005930', 'UP', 80000.00, 79500.00, 30, ?,
                                ?, ?, ?, ?, ?, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                        """)
                .setParameter(1, userId)
                .setParameter(2, status)
                .setParameter(3, settled ? LocalDate.of(2026, 9, 5) : null)
                .setParameter(4, settled ? 79000.00 : null)
                .setParameter(5, settled ? LocalDate.of(2026, 10, 5) : null)
                .setParameter(6, settled ? 82000.00 : null)
                .setParameter(7, settled ? 2.5 : null)
                .executeUpdate();
        return ((Number) em.createNativeQuery("SELECT MAX(id) FROM predictions").getSingleResult()).longValue();
    }

    private void insertCommit(long pid, String seed, Instant revealedAt) {
        em.createNativeQuery(
                        """
                        INSERT INTO prediction_commits
                          (prediction_id, commit_hash, salt, signature, signer_address, revealed_at)
                        VALUES (?, ?, ?, ?, ?, ?)
                        """)
                .setParameter(1, pid)
                .setParameter(2, hashOf(seed))
                .setParameter(3, SALT)
                .setParameter(4, "0x" + "ab".repeat(65))
                .setParameter(5, "0x" + "11".repeat(20))
                .setParameter(6, revealedAt)
                .executeUpdate();
        em.flush();
        em.clear();
    }

    private void insertSubscription(long subscriberId, long publisherId, String status, Instant expiresAt) {
        em.createNativeQuery(
                        """
                        INSERT INTO subscriptions
                          (subscriber_id, publisher_id, fee, status, started_at, expires_at, auto_renew, created_at, updated_at)
                        VALUES (?, ?, 1000, ?, CURRENT_TIMESTAMP, ?, false, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                        """)
                .setParameter(1, subscriberId)
                .setParameter(2, publisherId)
                .setParameter(3, status)
                .setParameter(4, expiresAt)
                .executeUpdate();
        em.flush();
        em.clear();
    }

    private void assign(long batchId, long pid) {
        em.createNativeQuery("UPDATE prediction_commits SET anchor_batch_id = ? WHERE prediction_id = ?")
                .setParameter(1, batchId)
                .setParameter(2, pid)
                .executeUpdate();
        em.flush();
        em.clear();
    }

    /** seeds 순서 = pids 순서 = 리프 순서(prediction id 오름차순)로 루트를 만들고 CONFIRMED 배치에 소속시킨다. */
    private AnchorBatch confirmedBatch(List<String> seeds, List<Long> pids) {
        List<byte[]> leaves = seeds.stream().map(s -> Hash.sha3(s.getBytes())).toList();
        String root = Numeric.toHexString(MerkleTree.build(leaves).root());
        AnchorBatch batch = AnchorBatch.open(DAY, root, seeds.size(), CONTRACT, 31337L);
        batch.markSending();
        batch.markConfirmed("0x" + "cd".repeat(32), 11_187_694L, Instant.parse("2026-09-05T15:05:10Z"));
        em.persist(batch);
        em.flush();
        for (long pid : pids) {
            assign(batch.getId(), pid);
        }
        return batch;
    }
}
