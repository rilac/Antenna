package ssafy.a507.backend.domain.season.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willAnswer;
import static org.mockito.Mockito.mock;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import jakarta.persistence.EntityManager;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;

/**
 * POST /advance · POST /finish 의 AC 검증(ANT-SEASON-04).
 *
 * <p>시즌은 3게임일. 종가: A = D+1 70,000 · D+2 80,000 · D+3 60,000, B = 10,000 · 12,000 · 9,000.
 * 시나리오 — D+1 A 100주 매수, D+2 50주 매도(+500,000), D+3 50주 매도(−500,000):
 * 자산 곡선 30,000,000 → 31,000,000 → 30,000,000 이라 최대 낙폭 3.226%, 승률 50%, 손익비 1,
 * 평균 보유일 (1 + 2) / 2 = 1.5. 벤치마크(등가중) = (−14.286% + −10%) / 2 = −12.143%.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class SeasonProgressControllerTest {

    private static final String KEY = "Idempotency-Key";

    @Autowired MockMvc mockMvc;
    @Autowired EntityManager em;

    @MockitoBean StringRedisTemplate redis;
    private final Map<String, String> redisStore = new HashMap<>();

    private String me;
    private Long seasonId;
    private Long participantId;
    private Long tickerA;

    @BeforeEach
    void setUp() {
        stubRedis();
        me = String.valueOf(insertUser("나", "USER"));
        seasonId = insertSeason("PRACTICE", "급락과 반등", 3);
        insertStock("A00010");
        insertStock("B00020");
        tickerA = insertTicker(seasonId, "종목A", "A00010");
        Long tickerB = insertTicker(seasonId, "종목B", "B00020");
        insertPrice(tickerA, 1, 70000);
        insertPrice(tickerA, 2, 80000);
        insertPrice(tickerA, 3, 60000);
        insertPrice(tickerB, 1, 10000);
        insertPrice(tickerB, 2, 12000);
        insertPrice(tickerB, 3, 9000);
        participantId = insertParticipant(seasonId, Long.valueOf(me), 1, 1, "ONGOING");
        em.flush();
        em.clear();
    }

    // ── POST /advance ────────────────────────────────────────

    @Test
    @DisplayName("진행은 하루씩 간다 — 마지막 게임일에 닿으면 isLastDay")
    void 하루씩_간다() throws Exception {
        advance(1)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.currentDay").value(2))
                .andExpect(jsonPath("$.isLastDay").value(false));
        advance(2)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.currentDay").value(3))
                .andExpect(jsonPath("$.isLastDay").value(true));
        assertThat(currentDay()).isEqualTo(3);
    }

    @Test
    @DisplayName("expectedDay 가 서버와 다르면 409 DAY_MISMATCH — 두 번 눌러도 하루만 간다")
    void 낙관적_잠금() throws Exception {
        advance(1).andExpect(status().isOk());
        advance(1)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("DAY_MISMATCH"));
        assertThat(currentDay()).isEqualTo(2);
    }

    @Test
    @DisplayName("마지막 게임일에서는 더 가지 않는다 — 409 SEASON_LAST_DAY")
    void 마지막_날은_더_안_간다() throws Exception {
        setCurrentDay(3);
        advance(3)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("SEASON_LAST_DAY"));
    }

    @Test
    @DisplayName("대회는 수동 진행 불가 409 · 미참가 409 · expectedDay 없으면 400")
    void 진행_거절들() throws Exception {
        Long competition = insertSeason("COMPETITION", "대회", 3);
        insertParticipant(competition, Long.valueOf(me), 1, 1, "ONGOING");
        Long notJoined = insertSeason("PRACTICE", "안 한 시즌", 3);
        em.flush();
        em.clear();

        mockMvc.perform(post("/api/v1/seasons/" + competition + "/advance").with(user(me))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"expectedDay\":1}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("SEASON_ADVANCE_NOT_ALLOWED"));
        mockMvc.perform(post("/api/v1/seasons/" + notJoined + "/advance").with(user(me))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"expectedDay\":1}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("SEASON_NOT_JOINED"));
        mockMvc.perform(post(url("/advance")).with(user(me))
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"))
                .andExpect(jsonPath("$.field").value("expectedDay"));
    }

    // ── POST /finish ─────────────────────────────────────────

    @Test
    @DisplayName("마지막 게임일 전에는 종료할 수 없다 — 409 SEASON_NOT_LAST_DAY · 결과도 아직 404")
    void 끝까지_가야_종료다() throws Exception {
        mockMvc.perform(post(url("/finish")).with(user(me)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("SEASON_NOT_LAST_DAY"));
        assertThat(participantStatus()).isEqualTo("ONGOING");
        mockMvc.perform(get(url("/result/me")).with(user(me)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("SEASON_RESULT_NOT_FOUND"));
        mockMvc.perform(get("/api/v1/seasons/me").param("status", "ONGOING").with(user(me)))
                .andExpect(jsonPath("$.items[0].title").value("급락과 반등"))
                .andExpect(jsonPath("$.items[0].endedAt").doesNotExist())
                .andExpect(jsonPath("$.items[0].returnRate").doesNotExist());
    }

    @Test
    @DisplayName("종료하면 회차가 DONE 이 되고 성과 지표가 체결·가격에서 계산된다")
    void 종료와_성과_지표() throws Exception {
        order("k-1", tickerA, "BUY", 100).andExpect(status().isCreated());
        advance(1).andExpect(status().isOk());
        order("k-2", tickerA, "SELL", 50).andExpect(status().isCreated());
        advance(2).andExpect(status().isOk());
        order("k-3", tickerA, "SELL", 50).andExpect(status().isCreated());

        mockMvc.perform(post(url("/finish")).with(user(me)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.participantId").value(participantId))
                .andExpect(jsonPath("$.finalAsset").value(30000000))
                .andExpect(jsonPath("$.returnRate").value(0.0))
                .andExpect(jsonPath("$.benchmarkReturn").value(-12.143))
                .andExpect(jsonPath("$.maxDrawdown").value(3.226))
                .andExpect(jsonPath("$.winRate").value(50.0))
                .andExpect(jsonPath("$.profitFactor").value(1.0))
                .andExpect(jsonPath("$.avgHoldingDays").value(1.5));

        assertThat(participantStatus()).isEqualTo("DONE");
        assertThat(em.createNativeQuery("SELECT ended_at FROM season_participants WHERE id = ?")
                .setParameter(1, participantId).getSingleResult()).isNotNull();
        assertThat(((Number) em.createNativeQuery(
                        "SELECT COUNT(*) FROM season_results WHERE participant_id = ?")
                .setParameter(1, participantId).getSingleResult()).longValue()).isEqualTo(1);
    }

    @Test
    @DisplayName("체결이 없어도 종료된다 — 승률·손익비·보유일은 null, 낙폭 0")
    void 체결_없이_종료() throws Exception {
        setCurrentDay(3);
        mockMvc.perform(post(url("/finish")).with(user(me)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.finalAsset").value(30000000))
                .andExpect(jsonPath("$.returnRate").value(0.0))
                .andExpect(jsonPath("$.maxDrawdown").value(0.0))
                .andExpect(jsonPath("$.winRate").doesNotExist())
                .andExpect(jsonPath("$.profitFactor").doesNotExist())
                .andExpect(jsonPath("$.avgHoldingDays").doesNotExist());
    }

    @Test
    @DisplayName("끝난 회차 — 종료 재요청은 같은 결과, 주문·진행은 409, 목록은 DONE, 상세는 미참가, 참가는 새 회차")
    void 끝난_회차_이후() throws Exception {
        order("k-1", tickerA, "BUY", 10).andExpect(status().isCreated());
        setCurrentDay(3);
        mockMvc.perform(post(url("/finish")).with(user(me))).andExpect(status().isOk());

        mockMvc.perform(post(url("/finish")).with(user(me)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.participantId").value(participantId))
                .andExpect(jsonPath("$.finalAsset").value(29900000));
        order("k-2", tickerA, "SELL", 10)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("SEASON_ATTEMPT_ENDED"));
        advance(3)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("SEASON_ATTEMPT_ENDED"));

        mockMvc.perform(get("/api/v1/seasons/me").param("status", "DONE").with(user(me)))
                .andExpect(jsonPath("$.items.length()").value(1))
                .andExpect(jsonPath("$.items[0].seasonId").value(seasonId))
                .andExpect(jsonPath("$.items[0].title").value("급락과 반등"))
                .andExpect(jsonPath("$.items[0].endedAt").isString())
                .andExpect(jsonPath("$.items[0].returnRate").value(-0.333));
        mockMvc.perform(get(url("/result/me")).with(user(me)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.participantId").value(participantId))
                .andExpect(jsonPath("$.finalAsset").value(29900000))
                .andExpect(jsonPath("$.returnRate").value(-0.333))
                .andExpect(jsonPath("$.review").doesNotExist())
                .andExpect(jsonPath("$.closedAt").isString());
        mockMvc.perform(get("/api/v1/seasons/me").param("status", "ONGOING").with(user(me)))
                .andExpect(jsonPath("$.items.length()").value(0));
        mockMvc.perform(get("/api/v1/seasons/" + seasonId).with(user(me)))
                .andExpect(jsonPath("$.joined").value(false));

        mockMvc.perform(post(url("/join")).header(KEY, "j-1").with(user(me)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.attemptNo").value(2));
    }

    // ── helpers ──────────────────────────────────────────────

    private String url(String tail) {
        return "/api/v1/seasons/" + seasonId + tail;
    }

    private ResultActions advance(int expectedDay) throws Exception {
        return mockMvc.perform(post(url("/advance")).with(user(me))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"expectedDay\":" + expectedDay + "}"));
    }

    private ResultActions order(String key, Long tickerId, String side, int qty) throws Exception {
        return mockMvc.perform(post(url("/orders")).with(user(me)).header(KEY, key)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"tickerId\":" + tickerId + ",\"side\":\"" + side + "\",\"qty\":" + qty + "}"));
    }

    private void setCurrentDay(int day) {
        em.flush();
        em.createNativeQuery("UPDATE season_participants SET current_day = ? WHERE id = ?")
                .setParameter(1, day)
                .setParameter(2, participantId)
                .executeUpdate();
        em.clear();
    }

    private int currentDay() {
        em.flush();
        em.clear();
        return ((Number) em.createNativeQuery("SELECT current_day FROM season_participants WHERE id = ?")
                .setParameter(1, participantId).getSingleResult()).intValue();
    }

    private String participantStatus() {
        em.flush();
        em.clear();
        return (String) em.createNativeQuery("SELECT status FROM season_participants WHERE id = ?")
                .setParameter(1, participantId).getSingleResult();
    }

    private void stubRedis() {
        @SuppressWarnings("unchecked")
        ValueOperations<String, String> ops = mock(ValueOperations.class);
        given(redis.opsForValue()).willReturn(ops);
        given(ops.get(anyString())).willAnswer(call -> redisStore.get(call.getArgument(0, String.class)));
        willAnswer(call -> {
                    redisStore.put(call.getArgument(0), call.getArgument(1));
                    return null;
                })
                .given(ops)
                .set(anyString(), anyString(), any(Duration.class));
    }

    private Long insertUser(String nickname, String role) {
        em.createNativeQuery(
                        """
                        INSERT INTO users (nickname, role, status, created_at, updated_at)
                        VALUES (?, ?, 'ACTIVE', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                        """)
                .setParameter(1, nickname)
                .setParameter(2, role)
                .executeUpdate();
        return ((Number) em.createNativeQuery("SELECT id FROM users WHERE nickname = ?")
                        .setParameter(1, nickname)
                        .getSingleResult())
                .longValue();
    }

    private Long insertSeason(String mode, String title, int lengthDays) {
        em.createNativeQuery(
                        """
                        INSERT INTO seasons
                          (mode, status, title, theme, base_date, length_days, initial_cash, seed, current_day)
                        VALUES (?, 'RUNNING', ?, '전기·전자', ?, ?, 30000000, 1, 0)
                        """)
                .setParameter(1, mode)
                .setParameter(2, title)
                .setParameter(3, LocalDate.of(2021, 3, 2))
                .setParameter(4, lengthDays)
                .executeUpdate();
        return ((Number) em.createNativeQuery("SELECT id FROM seasons WHERE title = ?")
                        .setParameter(1, title)
                        .getSingleResult())
                .longValue();
    }

    private void insertStock(String code) {
        em.createNativeQuery(
                        "INSERT INTO stocks (code, name, market, listed) VALUES (?, ?, 'KOSPI', true)")
                .setParameter(1, code)
                .setParameter(2, "이름" + code)
                .executeUpdate();
    }

    private Long insertTicker(Long seasonId, String displayName, String code) {
        em.createNativeQuery(
                        """
                        INSERT INTO season_tickers (season_id, display_name, real_stock_code, sector)
                        VALUES (?, ?, ?, '전기·전자')
                        """)
                .setParameter(1, seasonId)
                .setParameter(2, displayName)
                .setParameter(3, code)
                .executeUpdate();
        return ((Number) em.createNativeQuery(
                                "SELECT id FROM season_tickers WHERE season_id = ? AND display_name = ?")
                        .setParameter(1, seasonId)
                        .setParameter(2, displayName)
                        .getSingleResult())
                .longValue();
    }

    private void insertPrice(Long tickerId, int gameDay, int close) {
        em.createNativeQuery(
                        """
                        INSERT INTO season_prices (ticker_id, game_day, open, high, low, close, volume)
                        VALUES (?, ?, ?, ?, ?, ?, 1000)
                        """)
                .setParameter(1, tickerId)
                .setParameter(2, gameDay)
                .setParameter(3, new BigDecimal(close))
                .setParameter(4, new BigDecimal(close + 10))
                .setParameter(5, new BigDecimal(close - 10))
                .setParameter(6, new BigDecimal(close))
                .executeUpdate();
    }

    private Long insertParticipant(
            Long seasonId, Long userId, int attemptNo, int currentDay, String status) {
        em.createNativeQuery(
                        """
                        INSERT INTO season_participants
                          (season_id, user_id, attempt_no, cash, current_day, status, created_at)
                        VALUES (?, ?, ?, 30000000, ?, ?, CURRENT_TIMESTAMP)
                        """)
                .setParameter(1, seasonId)
                .setParameter(2, userId)
                .setParameter(3, attemptNo)
                .setParameter(4, currentDay)
                .setParameter(5, status)
                .executeUpdate();
        return ((Number) em.createNativeQuery(
                                "SELECT id FROM season_participants WHERE season_id = ? AND user_id = ?")
                        .setParameter(1, seasonId)
                        .setParameter(2, userId)
                        .getSingleResult())
                .longValue();
    }
}
