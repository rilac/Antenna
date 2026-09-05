package ssafy.a507.backend.domain.chain.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import jakarta.persistence.EntityManager;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
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

/**
 * ANT-CHAIN-06 — 커밋 원장 목록·상세. H2.
 *
 * <p>커밋은 PRED-02 가 팩터리를 만들기 전이라 네이티브 INSERT 로 심는다(AnchorRunnerTest 와 같은 방식).
 * SecurityConfig 가 아직 없어 기본 체인이 살아 있다 — 요청마다 {@code user("<id>")} 를 붙인다.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
@DisplayName("커밋 원장 API")
class AnchorQueryControllerTest {

    private static final String CONTRACT = "0x07f8cfe2bc6174d62be8226e5e8699ffbf0d6d6a";
    private static final LocalDate DAY = LocalDate.of(2026, 9, 5);

    @Autowired MockMvc mockMvc;
    @Autowired EntityManager em;

    private String viewer;

    @BeforeEach
    void setUp() {
        User user = User.create();
        em.persist(user);
        viewer = String.valueOf(user.getId());
        em.createNativeQuery("INSERT INTO stocks (code, name, listed) VALUES ('005930', '삼성전자', true)")
                .executeUpdate();
    }

    // ── 목록 ──────────────────────────────────────────────────────────

    @Test
    @DisplayName("배치가 없으면 빈 목록 — 프론트 EmptyState 경로")
    void 빈_목록() throws Exception {
        mockMvc.perform(get("/api/v1/anchors").with(user(viewer)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items").isEmpty())
                .andExpect(jsonPath("$.nextCursor").value((Object) null))
                .andExpect(jsonPath("$.hasNext").value(false));
    }

    @Test
    @DisplayName("최신 배치부터 size 개씩, 커서는 마지막 id — 세 페이지를 이어 받으면 전부 한 번씩 나온다")
    void 커서_페이징() throws Exception {
        long b1 = confirmedBatch(List.of("a1", "a2")).getId();
        long b2 = confirmedBatch(List.of("b1")).getId();
        long b3 = confirmedBatch(List.of("c1", "c2", "c3")).getId();

        mockMvc.perform(get("/api/v1/anchors").param("size", "2").with(user(viewer)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(2))
                .andExpect(jsonPath("$.items[0].id").value(b3))
                .andExpect(jsonPath("$.items[0].commitCount").value(3))
                .andExpect(jsonPath("$.items[0].status").value("CONFIRMED"))
                .andExpect(jsonPath("$.items[0].contractAddress").value(CONTRACT))
                .andExpect(jsonPath("$.items[0].chainId").value(31337))
                .andExpect(jsonPath("$.items[0].businessDate").value(DAY.toString()))
                .andExpect(jsonPath("$.items[1].id").value(b2))
                .andExpect(jsonPath("$.hasNext").value(true))
                .andExpect(jsonPath("$.nextCursor").value(b2));

        mockMvc.perform(get("/api/v1/anchors").param("size", "2").param("cursor", String.valueOf(b2)).with(user(viewer)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(1))
                .andExpect(jsonPath("$.items[0].id").value(b1))
                .andExpect(jsonPath("$.hasNext").value(false))
                .andExpect(jsonPath("$.nextCursor").value((Object) null));
    }

    @Test
    @DisplayName("PENDING·FAILED 배치도 목록에 나온다 — 상태가 곧 앵커 배지다")
    void 미확정_배치도_나온다() throws Exception {
        AnchorBatch pending = AnchorBatch.open(DAY, hashOf("pending-root"), 1, CONTRACT, 31337L);
        em.persist(pending);
        AnchorBatch failed = AnchorBatch.open(DAY, hashOf("failed-root"), 1, CONTRACT, 31337L);
        failed.markFailed("BATCH_ID_COLLISION: onchain root 0x… != ours");
        em.persist(failed);
        em.flush();

        mockMvc.perform(get("/api/v1/anchors").with(user(viewer)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(2))
                .andExpect(jsonPath("$.items[0].status").value("FAILED"))
                .andExpect(jsonPath("$.items[0].txHash").value((Object) null))
                .andExpect(jsonPath("$.items[1].status").value("PENDING"));
    }

    @Test
    @DisplayName("인증 없이는 401")
    void 인증_없으면_401() throws Exception {
        mockMvc.perform(get("/api/v1/anchors")).andExpect(status().isUnauthorized());
    }

    // ── 상세 ──────────────────────────────────────────────────────────

    @Test
    @DisplayName("상세의 커밋 해시는 리프 순서(prediction id 오름차순)이고, 그 순서로 접은 루트가 merkleRoot 와 같다")
    void 상세_리프_순서와_루트() throws Exception {
        List<String> seeds = List.of("z-first", "m-second", "a-third", "k-fourth", "q-fifth");
        AnchorBatch batch = confirmedBatch(seeds);
        List<String> expected = seeds.stream().map(AnchorQueryControllerTest::hashOf).toList();

        var result = mockMvc.perform(get("/api/v1/anchors/{id}", batch.getId()).with(user(viewer)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(batch.getId()))
                .andExpect(jsonPath("$.merkleRoot").value(batch.getMerkleRoot()))
                .andExpect(jsonPath("$.commitCount").value(5))
                .andExpect(jsonPath("$.txHash").value(batch.getTxHash()))
                .andExpect(jsonPath("$.blockNumber").value(batch.getBlockNumber()))
                .andExpect(jsonPath("$.attempts").value(1))
                .andExpect(jsonPath("$.commitHashes.length()").value(5))
                .andExpect(jsonPath("$.commitHashes[0]").value(expected.get(0)))
                .andExpect(jsonPath("$.commitHashes[4]").value(expected.get(4)))
                .andReturn();

        // 응답 목록을 그대로 접으면 저장된 루트가 나와야 한다 — 순서가 하나라도 어긋나면 다른 루트다.
        List<byte[]> leaves = expected.stream().map(Numeric::hexStringToByteArray).toList();
        assertThat(Numeric.toHexString(MerkleTree.build(leaves).root())).isEqualTo(batch.getMerkleRoot());
        assertThat(result.getResponse().getContentAsString()).doesNotContain("predictionId");
    }

    @Test
    @DisplayName("없는 batchId 는 404 ANCHOR_NOT_FOUND")
    void 없는_배치_404() throws Exception {
        mockMvc.perform(get("/api/v1/anchors/{id}", 999_999).with(user(viewer)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("ANCHOR_NOT_FOUND"));
    }

    // ── 픽스처 ──────────────────────────────────────────────────────

    private static String hashOf(String seed) {
        return Numeric.toHexString(Hash.sha3(seed.getBytes()));
    }

    /** 커밋들을 심고 그 리프로 만든 루트의 CONFIRMED 배치에 소속시킨다. seeds 순서 = 삽입 순서 = 리프 순서. */
    private AnchorBatch confirmedBatch(List<String> seeds) {
        List<byte[]> leaves = seeds.stream().map(s -> Hash.sha3(s.getBytes())).toList();
        String root = Numeric.toHexString(MerkleTree.build(leaves).root());
        AnchorBatch batch = AnchorBatch.open(DAY, root, seeds.size(), CONTRACT, 31337L);
        batch.markSending();
        batch.markConfirmed("0x" + "ab".repeat(32), 11_187_694L, Instant.parse("2026-09-05T15:05:10Z"));
        em.persist(batch);
        em.flush();
        for (String seed : seeds) {
            long pid = insertCommit(seed);
            em.createNativeQuery("UPDATE prediction_commits SET anchor_batch_id = ? WHERE prediction_id = ?")
                    .setParameter(1, batch.getId())
                    .setParameter(2, pid)
                    .executeUpdate();
        }
        em.flush();
        em.clear();
        return batch;
    }

    private long insertCommit(String seed) {
        em.createNativeQuery(
                        """
                        INSERT INTO predictions
                          (user_id, track, stock_code, direction, target_price, horizon, status, created_at, updated_at)
                        VALUES (?, 'REAL', '005930', 'UP', 80000.00, 30, 'OPEN', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                        """)
                .setParameter(1, Long.valueOf(viewer))
                .executeUpdate();
        long pid = ((Number) em.createNativeQuery("SELECT MAX(id) FROM predictions").getSingleResult()).longValue();
        em.createNativeQuery("INSERT INTO prediction_commits (prediction_id, commit_hash, salt) VALUES (?, ?, ?)")
                .setParameter(1, pid)
                .setParameter(2, hashOf(seed))
                .setParameter(3, "00".repeat(32))
                .executeUpdate();
        return pid;
    }
}
