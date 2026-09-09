package ssafy.a507.backend.domain.prediction.controller;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import jakarta.persistence.EntityManager;
import java.time.LocalDate;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

/**
 * ANT-PRED-06 — 내 예측 목록(화면 C-02)의 AC 검증.
 *
 * <p>예측 등록 API(ANT-PRED-01)가 아직 없어서 행은 네이티브 INSERT 로 만든다. 요청마다 {@code user(...)} 로
 * 인증 주체를 붙이고, 주체의 이름이 곧 users.id 다({@code SecurityContextCurrentUserProvider}).
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class MyPredictionControllerTest {

    private static final String URL = "/api/v1/predictions/me";
    private static final String SAMSUNG = "005930";

    @Autowired
    MockMvc mockMvc;

    @Autowired
    EntityManager em;

    private Long userId;
    private Long otherId;

    @BeforeEach
    void setUp() {
        userId = insertUser("예측가");
        otherId = insertUser("남의계정");
        insertStock(SAMSUNG, "삼성전자");
    }

    @Test
    @DisplayName("내 예측만 최신순으로 나오고 종목명·D-day 가 붙는다")
    void 목록() throws Exception {
        insertPrediction(userId, "OPEN", LocalDate.now().plusDays(7), null);
        Long latest = insertPrediction(userId, "BASE", null, null);
        insertPrediction(otherId, "OPEN", LocalDate.now().plusDays(3), null);

        mockMvc.perform(get(URL).with(user(String.valueOf(userId))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(2))
                .andExpect(jsonPath("$.items[0].id").value(latest))
                .andExpect(jsonPath("$.items[0].stockCode").value(SAMSUNG))
                .andExpect(jsonPath("$.items[0].stockName").value("삼성전자"))
                // 만기일이 아직 없는 BASE 건은 D-day 가 null, 그 다음 OPEN 건은 7 이다.
                .andExpect(jsonPath("$.items[0].dday").doesNotExist())
                .andExpect(jsonPath("$.items[1].dday").value(7))
                .andExpect(jsonPath("$.hasNext").value(false));
    }

    @Test
    @DisplayName("status=PENDING 은 BASE·OPEN 을, JUDGED 는 HIT·MISS 를 묶고, 집계는 필터와 무관하게 전량 기준이다")
    void 상태_필터와_집계() throws Exception {
        insertPrediction(userId, "BASE", null, null);
        insertPrediction(userId, "OPEN", LocalDate.now().plusDays(5), null);
        insertPrediction(userId, "HIT", LocalDate.now().minusDays(1), "1.250");
        insertPrediction(userId, "MISS", LocalDate.now().minusDays(2), "9.900");

        mockMvc.perform(get(URL).param("status", "PENDING").with(user(String.valueOf(userId))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(2))
                .andExpect(jsonPath("$.total").value(4))
                .andExpect(jsonPath("$.pendingCount").value(2))
                .andExpect(jsonPath("$.judgedCount").value(2))
                .andExpect(jsonPath("$.hitRate").value(0.5));

        mockMvc.perform(get(URL).param("status", "HIT").with(user(String.valueOf(userId))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(1))
                .andExpect(jsonPath("$.items[0].status").value("HIT"))
                .andExpect(jsonPath("$.items[0].errorRate").value(1.25))
                // 판정된 건은 D-day 를 내리지 않는다.
                .andExpect(jsonPath("$.items[0].dday").doesNotExist());

        // JUDGED 한 번이 HIT·MISS 두 번을 대신한다 — 커서가 하나여야 프론트가 페이지를 이어 받는다.
        mockMvc.perform(get(URL).param("status", "JUDGED").with(user(String.valueOf(userId))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(2))
                // id 내림차순이라 나중에 넣은 MISS 가 먼저다.
                .andExpect(jsonPath("$.items[0].status").value("MISS"))
                .andExpect(jsonPath("$.items[1].status").value("HIT"))
                .andExpect(jsonPath("$.judgedCount").value(2));
    }

    @Test
    @DisplayName("판정 건이 없으면 hitRate 는 null 이다 — 0 이면 전부 틀린 것으로 읽힌다")
    void 판정_전_적중률() throws Exception {
        insertPrediction(userId, "BASE", null, null);

        mockMvc.perform(get(URL).with(user(String.valueOf(userId))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.judgedCount").value(0))
                .andExpect(jsonPath("$.hitRate").doesNotExist());
    }

    @Test
    @DisplayName("size 를 넘으면 커서로 다음 장을 준다")
    void 커서_페이징() throws Exception {
        insertPrediction(userId, "BASE", null, null);
        Long second = insertPrediction(userId, "BASE", null, null);
        Long third = insertPrediction(userId, "BASE", null, null);

        mockMvc.perform(get(URL).param("size", "2").with(user(String.valueOf(userId))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(2))
                .andExpect(jsonPath("$.hasNext").value(true))
                .andExpect(jsonPath("$.nextCursor").value(second));

        mockMvc.perform(get(URL)
                        .param("size", "2")
                        .param("cursor", String.valueOf(third))
                        .with(user(String.valueOf(userId))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(2))
                .andExpect(jsonPath("$.hasNext").value(false));
    }

    @Test
    @DisplayName("anchorStatus — 커밋이 없으면 WAITING, 확정 배치에 속하면 CONFIRMED (프론트 요청 09-09, 행마다 proof 를 부르지 않게)")
    void 앵커_상태() throws Exception {
        Long waiting = insertPrediction(userId, "BASE", null, null);
        Long confirmed = insertPrediction(userId, "OPEN", LocalDate.now().plusDays(7), null);
        Long batchId = insertConfirmedBatch();
        insertCommit(confirmed, batchId);

        mockMvc.perform(get(URL).with(user(String.valueOf(userId))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].id").value(confirmed))
                .andExpect(jsonPath("$.items[0].anchorStatus").value("CONFIRMED"))
                .andExpect(jsonPath("$.items[1].id").value(waiting))
                .andExpect(jsonPath("$.items[1].anchorStatus").value("WAITING"));
    }

    @Test
    @DisplayName("어휘 밖 status 는 400")
    void 잘못된_상태값() throws Exception {
        mockMvc.perform(get(URL).param("status", "WAITING").with(user(String.valueOf(userId))))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("비로그인은 401")
    void 인증_필요() throws Exception {
        mockMvc.perform(get(URL)).andExpect(status().isUnauthorized());
    }

    private Long insertPrediction(Long ownerId, String status, LocalDate settleDate, String errorRate) {
        em.createNativeQuery("""
                        INSERT INTO predictions
                          (user_id, track, stock_code, direction, target_price, horizon,
                           status, settle_date, error_rate, created_at, updated_at)
                        VALUES (?, 'REAL', ?, 'UP', 70000, 7, ?, ?, ?, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                        """)
                .setParameter(1, ownerId)
                .setParameter(2, SAMSUNG)
                .setParameter(3, status)
                .setParameter(4, settleDate)
                .setParameter(5, errorRate == null ? null : new java.math.BigDecimal(errorRate))
                .executeUpdate();
        return ((Number) em.createNativeQuery("SELECT max(id) FROM predictions").getSingleResult())
                .longValue();
    }

    private Long insertConfirmedBatch() {
        em.createNativeQuery("""
                        INSERT INTO anchor_batches
                          (business_date, merkle_root, commit_count, status, contract_address, chain_id,
                           tx_hash, block_number, confirmed_at, attempts, created_at)
                        VALUES (CURRENT_DATE, ?, 1, 'CONFIRMED', ?, 31337, ?, 11187694, CURRENT_TIMESTAMP, 1,
                                CURRENT_TIMESTAMP)
                        """)
                .setParameter(1, "0x" + "aa".repeat(32))
                .setParameter(2, "0x07f8cfe2bc6174d62be8226e5e8699ffbf0d6d6a")
                .setParameter(3, "0x" + "cd".repeat(32))
                .executeUpdate();
        return ((Number) em.createNativeQuery("SELECT max(id) FROM anchor_batches").getSingleResult()).longValue();
    }

    private void insertCommit(Long predictionId, Long batchId) {
        em.createNativeQuery("""
                        INSERT INTO prediction_commits (prediction_id, commit_hash, salt, anchor_batch_id)
                        VALUES (?, ?, ?, ?)
                        """)
                .setParameter(1, predictionId)
                .setParameter(2, "0x" + "bb".repeat(32))
                .setParameter(3, "0123456789abcdef".repeat(4))
                .setParameter(4, batchId)
                .executeUpdate();
    }

    private void insertStock(String code, String name) {
        em.createNativeQuery("INSERT INTO stocks (code, name, listed) VALUES (?, ?, true)")
                .setParameter(1, code)
                .setParameter(2, name)
                .executeUpdate();
    }

    private Long insertUser(String nickname) {
        em.createNativeQuery("""
                        INSERT INTO users (nickname, role, status, created_at, updated_at)
                        VALUES (?, 'USER', 'ACTIVE', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                        """)
                .setParameter(1, nickname)
                .executeUpdate();
        return ((Number) em.createNativeQuery("SELECT id FROM users WHERE nickname = ?")
                        .setParameter(1, nickname)
                        .getSingleResult())
                .longValue();
    }
}
