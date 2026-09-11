package ssafy.a507.backend.domain.prediction.controller;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import jakarta.persistence.EntityManager;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

/**
 * ANT-PRED-05 — 채널 예측 목록(E-02)과 예측 상세(C-03)의 필드 단위 공개 규칙 (2026-09-11 개정).
 *
 * <pre>
 *   필드                 판정 전          판정 후
 *   종목                 전체             전체
 *   방향·목표가·근거포인트  작성자·구독자     전체
 *   근거 본문             작성자·구독자     작성자·구독자
 * </pre>
 * 403 은 없다. 보는 사람 셋(작성자 · 유효 구독자 · 비구독자) × 판정 전·후를 덮는다.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class PredictionViewControllerTest {

    private static final String SAMSUNG = "005930";
    private static final String CHANNEL = "/api/v1/channels/{userId}/predictions";
    private static final String DETAIL = "/api/v1/predictions/{id}";

    @Autowired
    MockMvc mockMvc;

    @Autowired
    EntityManager em;

    private Long authorId;
    private Long viewerId;
    private Long subscriberId;

    @BeforeEach
    void setUp() {
        authorId = insertUser("채널주인");
        viewerId = insertUser("비구독자");
        subscriberId = insertUser("구독자");
        insertStock(SAMSUNG, "삼성전자");
        insertSubscription(subscriberId, authorId, Instant.now().plus(10, ChronoUnit.DAYS));
    }

    // ── 채널 예측 목록 ─────────────────────────────────────

    @Test
    @DisplayName("채널 — 비구독자에게 판정 전 건은 잠금 카드(종목만), 판정 후 건은 전부 열린다")
    void 채널_비구독자() throws Exception {
        Long hit = insertPrediction("HIT", "1.500");
        Long open = insertPrediction("OPEN", null);

        mockMvc.perform(get(CHANNEL, authorId).with(user(String.valueOf(viewerId))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(2))
                // 최신순 — 나중에 넣은 OPEN 이 먼저다. 잠겨도 목록에서 빠지지 않는다.
                .andExpect(jsonPath("$.items[0].id").value(open))
                .andExpect(jsonPath("$.items[0].locked").value(true))
                .andExpect(jsonPath("$.items[0].stockCode").value(SAMSUNG))
                .andExpect(jsonPath("$.items[0].stockName").value("삼성전자"))
                .andExpect(jsonPath("$.items[0].direction").doesNotExist())
                .andExpect(jsonPath("$.items[0].targetPrice").doesNotExist())
                .andExpect(jsonPath("$.items[1].id").value(hit))
                .andExpect(jsonPath("$.items[1].locked").value(false))
                .andExpect(jsonPath("$.items[1].direction").value("UP"))
                .andExpect(jsonPath("$.items[1].targetPrice").value(70000))
                .andExpect(jsonPath("$.items[1].errorRate").value(1.5));
    }

    @Test
    @DisplayName("채널 — 유효 구독자와 본인에게는 판정 전 건도 열린다")
    void 채널_구독자_본인() throws Exception {
        insertPrediction("OPEN", null);

        for (Long reader : new Long[] {subscriberId, authorId}) {
            mockMvc.perform(get(CHANNEL, authorId).with(user(String.valueOf(reader))))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.items[0].locked").value(false))
                    .andExpect(jsonPath("$.items[0].direction").value("UP"))
                    .andExpect(jsonPath("$.items[0].targetPrice").value(70000));
        }
    }

    @Test
    @DisplayName("채널 — 만료된 구독은 열어 주지 않는다(상태가 ACTIVE 여도 기간이 근거)")
    void 채널_만료_구독() throws Exception {
        Long expired = insertUser("만료구독자");
        insertSubscription(expired, authorId, Instant.now().minus(1, ChronoUnit.DAYS));
        insertPrediction("OPEN", null);

        mockMvc.perform(get(CHANNEL, authorId).with(user(String.valueOf(expired))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].locked").value(true));
    }

    @Test
    @DisplayName("채널 — status 필터는 /predictions/me 와 같은 어휘, 어휘 밖은 400")
    void 채널_필터() throws Exception {
        insertPrediction("HIT", "1.000");
        insertPrediction("OPEN", null);
        insertPrediction("BASE", null);

        mockMvc.perform(get(CHANNEL, authorId).param("status", "JUDGED").with(user(String.valueOf(viewerId))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(1))
                .andExpect(jsonPath("$.items[0].status").value("HIT"));
        mockMvc.perform(get(CHANNEL, authorId).param("status", "PENDING").with(user(String.valueOf(viewerId))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(2));
        mockMvc.perform(get(CHANNEL, authorId).param("status", "WAITING").with(user(String.valueOf(viewerId))))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("채널 — size 를 넘으면 커서로 다음 장을 준다")
    void 채널_커서() throws Exception {
        insertPrediction("BASE", null);
        Long second = insertPrediction("BASE", null);
        insertPrediction("BASE", null);

        mockMvc.perform(get(CHANNEL, authorId).param("size", "2").with(user(String.valueOf(viewerId))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(2))
                .andExpect(jsonPath("$.hasNext").value(true))
                .andExpect(jsonPath("$.nextCursor").value(second));
    }

    @Test
    @DisplayName("채널 — 없는 회원은 404 USER_NOT_FOUND")
    void 채널_없는_회원() throws Exception {
        mockMvc.perform(get(CHANNEL, 999_999).with(user(String.valueOf(viewerId))))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("USER_NOT_FOUND"));
    }

    // ── 예측 상세 ─────────────────────────────────────────

    @Test
    @DisplayName("상세 — 비구독자·판정 전: 403 이 아니라 200. 방향·목표가·근거 포인트·본문이 잠기고 proof 는 온다")
    void 상세_비구독자_판정전() throws Exception {
        Long id = insertFullPrediction("OPEN", null);
        insertQuote(LocalDate.now().minusDays(1), "71000");

        mockMvc.perform(get(DETAIL, id).with(user(String.valueOf(viewerId))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.locked").value(true))
                .andExpect(jsonPath("$.stockCode").value(SAMSUNG))
                .andExpect(jsonPath("$.stockName").value("삼성전자"))
                .andExpect(jsonPath("$.author.nickname").value("채널주인"))
                .andExpect(jsonPath("$.direction").doesNotExist())
                .andExpect(jsonPath("$.targetPrice").doesNotExist())
                .andExpect(jsonPath("$.evidences.length()").value(0))
                .andExpect(jsonPath("$.noteLocked").value(true))
                .andExpect(jsonPath("$.note").doesNotExist())
                // 조작 불가 근거는 잠금과 무관하다.
                .andExpect(jsonPath("$.proof.commitHash").value("0x" + "bb".repeat(32)))
                .andExpect(jsonPath("$.proof.anchorStatus").value("WAITING"))
                .andExpect(jsonPath("$.proof.revealed").value(false))
                .andExpect(jsonPath("$.lastClose.close").value(71000));
    }

    @Test
    @DisplayName("상세 — 비구독자·판정 후: 방향·목표가·근거 포인트가 열리고 본문은 계속 잠긴다(D6)")
    void 상세_비구독자_판정후() throws Exception {
        Long id = insertFullPrediction("HIT", "2.000");

        mockMvc.perform(get(DETAIL, id).with(user(String.valueOf(viewerId))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.locked").value(false))
                .andExpect(jsonPath("$.direction").value("UP"))
                .andExpect(jsonPath("$.targetPrice").value(70000))
                .andExpect(jsonPath("$.errorRate").value(2.0))
                .andExpect(jsonPath("$.evidences.length()").value(1))
                .andExpect(jsonPath("$.evidences[0].kind").value("POSITIVE"))
                .andExpect(jsonPath("$.noteLocked").value(true))
                .andExpect(jsonPath("$.note").doesNotExist());
    }

    @Test
    @DisplayName("상세 — 구독자·본인은 판정 전에도 전부 본다(근거 본문 포함)")
    void 상세_구독자_본인() throws Exception {
        Long id = insertFullPrediction("BASE", null);

        for (Long reader : new Long[] {subscriberId, authorId}) {
            mockMvc.perform(get(DETAIL, id).with(user(String.valueOf(reader))))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.locked").value(false))
                    .andExpect(jsonPath("$.direction").value("UP"))
                    .andExpect(jsonPath("$.noteLocked").value(false))
                    .andExpect(jsonPath("$.note").value("반도체 업황 반등"))
                    .andExpect(jsonPath("$.evidences[0].body").value("실적 개선"));
        }
    }

    @Test
    @DisplayName("상세 — 없는 예측은 404, /me·/slots 같은 고정 경로는 상세로 잡히지 않는다")
    void 상세_없음과_경로() throws Exception {
        mockMvc.perform(get(DETAIL, 999_999).with(user(String.valueOf(viewerId))))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("PREDICTION_NOT_FOUND"));
        mockMvc.perform(get("/api/v1/predictions/me").with(user(String.valueOf(viewerId))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items").exists());
    }

    private Long insertFullPrediction(String status, String errorRate) {
        Long id = insertPrediction(status, errorRate);
        em.createNativeQuery("INSERT INTO prediction_notes (prediction_id, body) VALUES (?, ?)")
                .setParameter(1, id)
                .setParameter(2, "반도체 업황 반등")
                .executeUpdate();
        em.createNativeQuery("""
                        INSERT INTO research_points (stock_code, target_date, kind, body, created_at)
                        VALUES (?, CURRENT_DATE, 'POSITIVE', '실적 개선', CURRENT_TIMESTAMP)
                        """)
                .setParameter(1, SAMSUNG)
                .executeUpdate();
        long pointId = ((Number) em.createNativeQuery("SELECT max(id) FROM research_points").getSingleResult())
                .longValue();
        em.createNativeQuery("INSERT INTO prediction_evidences (prediction_id, point_id) VALUES (?, ?)")
                .setParameter(1, id)
                .setParameter(2, pointId)
                .executeUpdate();
        em.createNativeQuery("INSERT INTO prediction_commits (prediction_id, commit_hash, salt) VALUES (?, ?, ?)")
                .setParameter(1, id)
                .setParameter(2, "0x" + "bb".repeat(32))
                .setParameter(3, "0123456789abcdef".repeat(4))
                .executeUpdate();
        return id;
    }

    private Long insertPrediction(String status, String errorRate) {
        em.createNativeQuery("""
                        INSERT INTO predictions
                          (user_id, track, stock_code, direction, target_price, horizon,
                           status, error_rate, created_at, updated_at)
                        VALUES (?, 'REAL', ?, 'UP', 70000, 7, ?, ?, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                        """)
                .setParameter(1, authorId)
                .setParameter(2, SAMSUNG)
                .setParameter(3, status)
                .setParameter(4, errorRate == null ? null : new BigDecimal(errorRate))
                .executeUpdate();
        return ((Number) em.createNativeQuery("SELECT max(id) FROM predictions").getSingleResult()).longValue();
    }

    private void insertSubscription(Long subscriber, Long publisher, Instant expiresAt) {
        em.createNativeQuery("""
                        INSERT INTO subscriptions
                          (subscriber_id, publisher_id, fee, status, started_at, expires_at, auto_renew,
                           created_at, updated_at)
                        VALUES (?, ?, 1000, 'ACTIVE', CURRENT_TIMESTAMP, ?, false, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                        """)
                .setParameter(1, subscriber)
                .setParameter(2, publisher)
                .setParameter(3, expiresAt)
                .executeUpdate();
    }

    private void insertQuote(LocalDate tradeDate, String close) {
        em.createNativeQuery("""
                        INSERT INTO daily_quotes (stock_code, trade_date, close, collected_at)
                        VALUES (?, ?, ?, CURRENT_TIMESTAMP)
                        """)
                .setParameter(1, SAMSUNG)
                .setParameter(2, tradeDate)
                .setParameter(3, new BigDecimal(close))
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
