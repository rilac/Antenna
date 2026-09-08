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
import java.util.List;
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
import org.springframework.transaction.annotation.Transactional;

/**
 * GET /me · POST /orders · GET /trades 의 AC 검증(ANT-SEASON-03).
 *
 * <p>체결가는 내 진행일 종가다. 이 파일의 시세: A 는 D+1 70,000 · D+2 80,000, B 는 D+1 10,000.
 * 예수금 30,000,000 에서 시작한다.
 *
 * <p>Redis 는 띄우지 않는다. StringRedisTemplate 을 맵으로 대신한다(SeasonJoinControllerTest 와 같다).
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class SeasonPlayControllerTest {

    private static final String KEY = "Idempotency-Key";

    @Autowired MockMvc mockMvc;
    @Autowired EntityManager em;

    @MockitoBean StringRedisTemplate redis;
    private final Map<String, String> redisStore = new HashMap<>();

    private String me;
    private Long seasonId;
    private Long participantId;
    private Long tickerA;
    private Long tickerB;
    private Long otherSeasonTicker;

    @BeforeEach
    void setUp() {
        stubRedis();
        me = String.valueOf(insertUser("나", "USER"));
        seasonId = insertSeason("PRACTICE", "급락과 반등", 5);
        insertStock("A00010");
        insertStock("B00020");
        tickerA = insertTicker(seasonId, "종목A", "A00010");
        tickerB = insertTicker(seasonId, "종목B", "B00020");
        insertPrice(tickerA, 0, 65000);
        insertPrice(tickerA, 1, 70000);
        insertPrice(tickerA, 2, 80000);
        insertPrice(tickerB, 1, 10000);
        participantId = insertParticipant(seasonId, Long.valueOf(me), 1, 1);

        Long other = insertSeason("PRACTICE", "다른 시즌", 5);
        otherSeasonTicker = insertTicker(other, "남의 종목", "A00010");
        em.flush();
        em.clear();
    }

    // ── GET /me ──────────────────────────────────────────────

    @Test
    @DisplayName("참가 직후 현황 — 예수금이 곧 총자산, 손익 0, 보유 없음")
    void 참가_직후_현황() throws Exception {
        mockMvc.perform(get(url("/me")).with(user(me)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.cash").value(30000000))
                .andExpect(jsonPath("$.totalAsset").value(30000000))
                .andExpect(jsonPath("$.stockValue").value(0))
                .andExpect(jsonPath("$.pnl").value(0))
                .andExpect(jsonPath("$.pnlRate").value(0.0))
                .andExpect(jsonPath("$.currentDay").value(1))
                .andExpect(jsonPath("$.positions").isEmpty());
    }

    @Test
    @DisplayName("보유 종목은 내 진행일 종가로 평가한다 — 진행일이 오르면 같은 포지션의 평가금액이 바뀐다")
    void 현황은_진행일_종가로_평가한다() throws Exception {
        order("k-1", tickerA, "BUY", 10).andExpect(status().isCreated());
        setCurrentDay(2);

        mockMvc.perform(get(url("/me")).with(user(me)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.cash").value(29300000))
                .andExpect(jsonPath("$.stockValue").value(800000))
                .andExpect(jsonPath("$.totalAsset").value(30100000))
                .andExpect(jsonPath("$.pnl").value(100000))
                .andExpect(jsonPath("$.pnlRate").value(0.33))
                .andExpect(jsonPath("$.currentDay").value(2))
                .andExpect(jsonPath("$.positions[0].tickerId").value(tickerA))
                .andExpect(jsonPath("$.positions[0].displayName").value("종목A"))
                .andExpect(jsonPath("$.positions[0].qty").value(10))
                .andExpect(jsonPath("$.positions[0].avgPrice").value(70000))
                .andExpect(jsonPath("$.positions[0].value").value(800000))
                .andExpect(jsonPath("$.positions[0].pnl").value(100000))
                .andExpect(jsonPath("$.positions[0].weight").value(2.66));
    }

    // ── POST /orders ─────────────────────────────────────────

    @Test
    @DisplayName("매수는 진행일 종가로 체결되고 예수금이 줄고 포지션이 생긴다")
    void 매수_체결() throws Exception {
        order("k-1", tickerA, "BUY", 10)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.tradeId").isNumber())
                .andExpect(jsonPath("$.price").value(70000))
                .andExpect(jsonPath("$.gameDay").value(1));

        assertThat(cash()).isEqualByComparingTo("29300000");
        List<Object[]> positions = positions();
        assertThat(positions).hasSize(1);
        assertThat(((Number) positions.get(0)[1]).intValue()).isEqualTo(10);
        assertThat((BigDecimal) positions.get(0)[2]).isEqualByComparingTo("70000");
    }

    @Test
    @DisplayName("추가 매수는 평단을 수량 가중으로 다시 구한다 — 10주@70,000 + 10주@80,000 = 75,000")
    void 추가_매수_평단() throws Exception {
        order("k-1", tickerA, "BUY", 10).andExpect(status().isCreated());
        setCurrentDay(2);
        order("k-2", tickerA, "BUY", 10)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.price").value(80000))
                .andExpect(jsonPath("$.gameDay").value(2));

        List<Object[]> positions = positions();
        assertThat(positions).hasSize(1);
        assertThat(((Number) positions.get(0)[1]).intValue()).isEqualTo(20);
        assertThat((BigDecimal) positions.get(0)[2]).isEqualByComparingTo("75000");
        assertThat(cash()).isEqualByComparingTo("28500000");
    }

    @Test
    @DisplayName("예수금을 넘는 매수는 409 INSUFFICIENT_BALANCE — 아무것도 바뀌지 않는다")
    void 예수금_부족_409() throws Exception {
        order("k-1", tickerA, "BUY", 500)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("INSUFFICIENT_BALANCE"));

        assertThat(cash()).isEqualByComparingTo("30000000");
        assertThat(positions()).isEmpty();
        assertThat(trades()).isEmpty();
    }

    @Test
    @DisplayName("매도는 실현손익을 남기고 예수금을 늘린다 · 전량 매도면 포지션 행이 사라진다")
    void 매도_체결_실현손익() throws Exception {
        order("k-1", tickerA, "BUY", 10).andExpect(status().isCreated());
        setCurrentDay(2);
        order("k-2", tickerA, "SELL", 10)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.price").value(80000));

        assertThat(cash()).isEqualByComparingTo("30100000");
        assertThat(positions()).isEmpty();
        List<Object[]> trades = trades();
        assertThat(trades).hasSize(2);
        // 최근 체결(SELL)이 먼저
        assertThat(trades.get(0)[0]).isEqualTo("SELL");
        assertThat((BigDecimal) trades.get(0)[1]).isEqualByComparingTo("100000");
        assertThat(trades.get(1)[0]).isEqualTo("BUY");
        assertThat(trades.get(1)[1]).isNull();
    }

    @Test
    @DisplayName("일부 매도는 수량만 줄고 평단은 그대로다")
    void 일부_매도() throws Exception {
        order("k-1", tickerA, "BUY", 10).andExpect(status().isCreated());
        order("k-2", tickerA, "SELL", 4).andExpect(status().isCreated());

        List<Object[]> positions = positions();
        assertThat(((Number) positions.get(0)[1]).intValue()).isEqualTo(6);
        assertThat((BigDecimal) positions.get(0)[2]).isEqualByComparingTo("70000");
    }

    @Test
    @DisplayName("보유보다 많이 팔거나 없는 종목을 팔면 409 SEASON_INSUFFICIENT_QTY")
    void 수량_부족_409() throws Exception {
        order("k-0", tickerA, "SELL", 1)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("SEASON_INSUFFICIENT_QTY"));

        order("k-1", tickerA, "BUY", 10).andExpect(status().isCreated());
        order("k-2", tickerA, "SELL", 11)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("SEASON_INSUFFICIENT_QTY"));
        assertThat(cash()).isEqualByComparingTo("29300000");
    }

    @Test
    @DisplayName("같은 Idempotency-Key 재요청은 체결을 더 만들지 않고 첫 응답을 돌려준다")
    void 같은_키는_한_번만_체결된다() throws Exception {
        order("same", tickerA, "BUY", 10).andExpect(status().isCreated());
        order("same", tickerA, "BUY", 10)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.price").value(70000));

        assertThat(trades()).hasSize(1);
        assertThat(cash()).isEqualByComparingTo("29300000");
    }

    @Test
    @DisplayName("Idempotency-Key 가 없으면 400 · 수량 0 은 400 INVALID_REQUEST")
    void 키_없음과_검증_400() throws Exception {
        mockMvc.perform(post(url("/orders")).with(user(me))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(tickerA, "BUY", 10)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("IDEMPOTENCY_KEY_REQUIRED"));

        order("k-1", tickerA, "BUY", 0)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"))
                .andExpect(jsonPath("$.field").value("qty"));
    }

    @Test
    @DisplayName("남의 시즌 종목 id 는 404 SEASON_TICKER_NOT_FOUND")
    void 남의_시즌_종목_404() throws Exception {
        order("k-1", otherSeasonTicker, "BUY", 1)
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("SEASON_TICKER_NOT_FOUND"));
    }

    @Test
    @DisplayName("참가하지 않은 시즌은 현황·주문·체결 내역 모두 409 SEASON_NOT_JOINED")
    void 미참가_409() throws Exception {
        String other = String.valueOf(insertUser("남", "USER"));
        em.flush();
        em.clear();

        mockMvc.perform(get(url("/me")).with(user(other)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("SEASON_NOT_JOINED"));
        mockMvc.perform(post(url("/orders")).with(user(other)).header(KEY, "k-1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(tickerA, "BUY", 1)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("SEASON_NOT_JOINED"));
        mockMvc.perform(get(url("/trades")).with(user(other)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("SEASON_NOT_JOINED"));
    }

    // ── GET /trades ──────────────────────────────────────────

    @Test
    @DisplayName("체결 내역은 최근이 먼저 · 금액 = 체결가 × 수량 · 매수의 realizedPnl 은 null")
    void 체결_내역() throws Exception {
        order("k-1", tickerA, "BUY", 10).andExpect(status().isCreated());
        order("k-2", tickerB, "BUY", 5).andExpect(status().isCreated());
        order("k-3", tickerA, "SELL", 3).andExpect(status().isCreated());

        mockMvc.perform(get(url("/trades")).with(user(me)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(3))
                .andExpect(jsonPath("$.hasNext").value(false))
                .andExpect(jsonPath("$.nextCursor").doesNotExist())
                .andExpect(jsonPath("$.items[0].side").value("SELL"))
                .andExpect(jsonPath("$.items[0].tickerName").value("종목A"))
                .andExpect(jsonPath("$.items[0].qty").value(3))
                .andExpect(jsonPath("$.items[0].amount").value(210000))
                .andExpect(jsonPath("$.items[0].realizedPnl").value(0))
                .andExpect(jsonPath("$.items[1].tickerName").value("종목B"))
                .andExpect(jsonPath("$.items[1].amount").value(50000))
                .andExpect(jsonPath("$.items[1].realizedPnl").doesNotExist())
                .andExpect(jsonPath("$.items[2].side").value("BUY"))
                .andExpect(jsonPath("$.items[2].gameDay").value(1));
    }

    @Test
    @DisplayName("커서 페이징 — size 로 자르고 nextCursor 로 이어 받으면 겹치지 않는다")
    void 체결_내역_커서() throws Exception {
        order("k-1", tickerA, "BUY", 1).andExpect(status().isCreated());
        order("k-2", tickerA, "BUY", 2).andExpect(status().isCreated());
        order("k-3", tickerA, "BUY", 3).andExpect(status().isCreated());

        String first = mockMvc.perform(get(url("/trades")).param("size", "2").with(user(me)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(2))
                .andExpect(jsonPath("$.items[0].qty").value(3))
                .andExpect(jsonPath("$.items[1].qty").value(2))
                .andExpect(jsonPath("$.hasNext").value(true))
                .andReturn().getResponse().getContentAsString();
        String cursor = first.replaceAll(".*\"nextCursor\":(\\d+).*", "$1");

        mockMvc.perform(get(url("/trades")).param("size", "2").param("cursor", cursor).with(user(me)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(1))
                .andExpect(jsonPath("$.items[0].qty").value(1))
                .andExpect(jsonPath("$.hasNext").value(false));
    }

    @Test
    @DisplayName("종목·방향 필터")
    void 체결_내역_필터() throws Exception {
        order("k-1", tickerA, "BUY", 10).andExpect(status().isCreated());
        order("k-2", tickerB, "BUY", 5).andExpect(status().isCreated());
        order("k-3", tickerA, "SELL", 3).andExpect(status().isCreated());

        mockMvc.perform(get(url("/trades")).param("tickerId", String.valueOf(tickerB)).with(user(me)))
                .andExpect(jsonPath("$.items.length()").value(1))
                .andExpect(jsonPath("$.items[0].tickerName").value("종목B"));
        mockMvc.perform(get(url("/trades")).param("side", "SELL").with(user(me)))
                .andExpect(jsonPath("$.items.length()").value(1))
                .andExpect(jsonPath("$.items[0].qty").value(3));
    }

    // ── helpers ──────────────────────────────────────────────

    private String url(String tail) {
        return "/api/v1/seasons/" + seasonId + tail;
    }

    private org.springframework.test.web.servlet.ResultActions order(
            String key, Long tickerId, String side, int qty) throws Exception {
        return mockMvc.perform(post(url("/orders")).with(user(me)).header(KEY, key)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body(tickerId, side, qty)));
    }

    private static String body(Long tickerId, String side, int qty) {
        return "{\"tickerId\":" + tickerId + ",\"side\":\"" + side + "\",\"qty\":" + qty + "}";
    }

    private void setCurrentDay(int day) {
        em.createNativeQuery("UPDATE season_participants SET current_day = ? WHERE id = ?")
                .setParameter(1, day)
                .setParameter(2, participantId)
                .executeUpdate();
        em.flush();
        em.clear();
    }

    /** 서비스가 더티체킹으로 바꾼 값은 flush 해야 보인다 — clear 만 하면 버려진다. */
    private BigDecimal cash() {
        em.flush();
        em.clear();
        return (BigDecimal) em.createNativeQuery(
                        "SELECT cash FROM season_participants WHERE id = ?")
                .setParameter(1, participantId)
                .getSingleResult();
    }

    /** (ticker_id, qty, avg_price). */
    @SuppressWarnings("unchecked")
    private List<Object[]> positions() {
        em.flush();
        em.clear();
        return em.createNativeQuery(
                        "SELECT ticker_id, qty, avg_price FROM season_positions"
                                + " WHERE participant_id = ? ORDER BY id")
                .setParameter(1, participantId)
                .getResultList();
    }

    /** (side, realized_pnl) 최근 순. */
    @SuppressWarnings("unchecked")
    private List<Object[]> trades() {
        em.flush();
        em.clear();
        return em.createNativeQuery(
                        "SELECT side, realized_pnl FROM season_trades"
                                + " WHERE participant_id = ? ORDER BY id DESC")
                .setParameter(1, participantId)
                .getResultList();
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
                        """
                        INSERT INTO stocks (code, name, market, listed) VALUES (?, ?, 'KOSPI', true)
                        """)
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

    private Long insertParticipant(Long seasonId, Long userId, int attemptNo, int currentDay) {
        em.createNativeQuery(
                        """
                        INSERT INTO season_participants
                          (season_id, user_id, attempt_no, cash, current_day, created_at)
                        VALUES (?, ?, ?, 30000000, ?, CURRENT_TIMESTAMP)
                        """)
                .setParameter(1, seasonId)
                .setParameter(2, userId)
                .setParameter(3, attemptNo)
                .setParameter(4, currentDay)
                .executeUpdate();
        return ((Number) em.createNativeQuery(
                                "SELECT id FROM season_participants WHERE season_id = ? AND user_id = ?")
                        .setParameter(1, seasonId)
                        .setParameter(2, userId)
                        .getSingleResult())
                .longValue();
    }
}
