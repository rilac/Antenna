package ssafy.a507.backend.domain.season.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willAnswer;
import static org.mockito.Mockito.mock;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
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
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

/**
 * POST /api/v1/seasons/{id}/join 의 AC 검증(ANT-SEASON-03).
 *
 * <p>회차 규칙이 핵심이다 — 진행 중 회차가 있으면 409, 끝난 회차만 있으면 다음 회차.
 * 그리고 멱등성 — 같은 키로 두 번 보내도 회차는 하나다.
 *
 * <p>Redis 는 띄우지 않는다. StringRedisTemplate 을 맵으로 대신한다(PostControllerTest 와 같은 방식).
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class SeasonJoinControllerTest {

    private static final String KEY = "Idempotency-Key";

    @Autowired MockMvc mockMvc;
    @Autowired EntityManager em;

    @MockitoBean StringRedisTemplate redis;
    private final Map<String, String> redisStore = new HashMap<>();

    private String me;
    private Long practiceId;
    private Long competitionId;
    private Long demoId;

    @BeforeEach
    void setUp() {
        stubRedis();
        me = String.valueOf(insertUser("나", "USER"));
        practiceId = insertSeason("PRACTICE", "급락과 반등", 5, "RUNNING");
        competitionId = insertSeason("COMPETITION", "대회", 120, "RUNNING");
        demoId = insertSeason("DEMO", "시연", 5, "RUNNING");
        em.flush();
        em.clear();
    }

    @Test
    @DisplayName("연습 시즌은 즉시 201 — 1회차 · 진행일 1 · 예수금은 시즌 출발선")
    void 연습은_즉시_참가된다() throws Exception {
        mockMvc.perform(post(url(practiceId)).header(KEY, "k-1").with(user(me)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.attemptNo").value(1))
                .andExpect(jsonPath("$.currentDay").value(1));

        List<Object[]> rows = participants(practiceId);
        assertThat(rows).hasSize(1);
        assertThat(((Number) rows.get(0)[0]).intValue()).isEqualTo(1);
        assertThat(((BigDecimal) rows.get(0)[1]).compareTo(new BigDecimal("30000000"))).isZero();
        assertThat(((Number) rows.get(0)[2]).intValue()).isEqualTo(1);
    }

    @Test
    @DisplayName("진행 중인 회차가 있으면 409 — 이어하기지 참가가 아니다")
    void 진행_중이면_409() throws Exception {
        mockMvc.perform(post(url(practiceId)).header(KEY, "k-1").with(user(me)))
                .andExpect(status().isCreated());

        mockMvc.perform(post(url(practiceId)).header(KEY, "k-2").with(user(me)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("SEASON_ALREADY_JOINED"));
        assertThat(participants(practiceId)).hasSize(1);
    }

    @Test
    @DisplayName("끝난(DONE) 회차만 있으면 다음 회차로 다시 참가한다")
    void 끝났으면_다음_회차다() throws Exception {
        insertParticipant(practiceId, Long.valueOf(me), 1, 5, "DONE");
        em.flush();
        em.clear();

        mockMvc.perform(post(url(practiceId)).header(KEY, "k-1").with(user(me)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.attemptNo").value(2))
                .andExpect(jsonPath("$.currentDay").value(1));
        assertThat(participants(practiceId)).hasSize(2);
    }

    @Test
    @DisplayName("마지막 게임일이어도 종료 전(ONGOING)이면 참가는 409 다 — 끝남은 진행일이 아니라 상태다")
    void 마지막_날이어도_종료_전이면_409() throws Exception {
        insertParticipant(practiceId, Long.valueOf(me), 1, 5, "ONGOING");
        em.flush();
        em.clear();

        mockMvc.perform(post(url(practiceId)).header(KEY, "k-1").with(user(me)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("SEASON_ALREADY_JOINED"));
    }

    @Test
    @DisplayName("restart 면 진행 중 회차를 버리고(ABANDONED) 새 회차로 시작한다 — 보유는 지우고 체결은 남긴다")
    void 초기화하면_새_회차다() throws Exception {
        mockMvc.perform(post(url(practiceId)).header(KEY, "k-1").with(user(me)))
                .andExpect(status().isCreated());
        Long first = ((Number) em.createNativeQuery(
                        "SELECT id FROM season_participants WHERE season_id = ? AND attempt_no = 1")
                .setParameter(1, practiceId).getSingleResult()).longValue();
        insertStockTickerPositionTrade(practiceId, first);
        em.flush();
        em.clear();

        mockMvc.perform(post(url(practiceId)).header(KEY, "k-2").with(user(me))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"restart\":true}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.attemptNo").value(2))
                .andExpect(jsonPath("$.currentDay").value(1));

        List<Object[]> rows = participantsWithStatus(practiceId);
        assertThat(rows).hasSize(2);
        assertThat(rows.get(0)[1]).isEqualTo("ABANDONED");
        assertThat(rows.get(0)[2]).isNotNull();
        assertThat(rows.get(1)[1]).isEqualTo("ONGOING");
        assertThat(count("season_positions", first)).isZero();
        assertThat(count("season_trades", first)).isEqualTo(1);
    }

    @Test
    @DisplayName("진행 중 회차가 없을 때의 restart 는 그냥 참가다")
    void 진행_중이_없으면_restart_는_참가다() throws Exception {
        mockMvc.perform(post(url(practiceId)).header(KEY, "k-1").with(user(me))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"restart\":true}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.attemptNo").value(1));
        assertThat(participants(practiceId)).hasSize(1);
    }

    @Test
    @DisplayName("같은 Idempotency-Key 재요청은 회차를 더 만들지 않고 첫 응답을 돌려준다")
    void 같은_키는_한_번만_처리된다() throws Exception {
        mockMvc.perform(post(url(practiceId)).header(KEY, "same").with(user(me)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.attemptNo").value(1));
        mockMvc.perform(post(url(practiceId)).header(KEY, "same").with(user(me)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.attemptNo").value(1));

        assertThat(participants(practiceId)).hasSize(1);
    }

    @Test
    @DisplayName("Idempotency-Key 가 없으면 400")
    void 키가_없으면_400() throws Exception {
        mockMvc.perform(post(url(practiceId)).with(user(me)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("IDEMPOTENCY_KEY_REQUIRED"));
    }

    @Test
    @DisplayName("대회는 참가비 소각 서명이 붙어 아직 501")
    void 대회는_아직_501() throws Exception {
        mockMvc.perform(post(url(competitionId)).header(KEY, "k-1").with(user(me)))
                .andExpect(status().isNotImplemented())
                .andExpect(jsonPath("$.code").value("SEASON_JOIN_NOT_SUPPORTED"));
    }

    @Test
    @DisplayName("일반 사용자에게 시연 시즌은 없는 시즌이다 — 404")
    void 시연은_일반_사용자에게_404() throws Exception {
        mockMvc.perform(post(url(demoId)).header(KEY, "k-1").with(user(me)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("SEASON_NOT_FOUND"));
    }

    @Test
    @DisplayName("RUNNING 이 아닌 시즌에는 참가할 수 없다 — 409")
    void 닫힌_시즌은_409() throws Exception {
        Long closed = insertSeason("PRACTICE", "닫힌 시즌", 5, "CLOSED");
        em.flush();
        em.clear();

        mockMvc.perform(post(url(closed)).header(KEY, "k-1").with(user(me)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("SEASON_NOT_RUNNING"));
    }

    private static String url(Long seasonId) {
        return "/api/v1/seasons/" + seasonId + "/join";
    }

    /** (attempt_no, status, ended_at) 회차 순. 더티체킹 변경은 flush 해야 보인다. */
    @SuppressWarnings("unchecked")
    private List<Object[]> participantsWithStatus(Long seasonId) {
        em.flush();
        em.clear();
        return em.createNativeQuery(
                        "SELECT attempt_no, status, ended_at FROM season_participants"
                                + " WHERE season_id = ? ORDER BY attempt_no")
                .setParameter(1, seasonId)
                .getResultList();
    }

    private long count(String table, Long participantId) {
        em.flush();
        em.clear();
        return ((Number) em.createNativeQuery(
                        "SELECT COUNT(*) FROM " + table + " WHERE participant_id = ?")
                .setParameter(1, participantId)
                .getSingleResult())
                .longValue();
    }

    /** 초기화 검증용 — 종목 하나, 보유 한 줄, 체결 한 건을 회차에 붙인다. */
    private void insertStockTickerPositionTrade(Long seasonId, Long participantId) {
        em.createNativeQuery(
                        "INSERT INTO stocks (code, name, market, listed) VALUES ('A00010', '가', 'KOSPI', true)")
                .executeUpdate();
        em.createNativeQuery(
                        "INSERT INTO season_tickers (season_id, display_name, real_stock_code, sector)"
                                + " VALUES (?, '가', 'A00010', '전기·전자')")
                .setParameter(1, seasonId)
                .executeUpdate();
        Long tickerId = ((Number) em.createNativeQuery(
                        "SELECT id FROM season_tickers WHERE season_id = ?")
                .setParameter(1, seasonId).getSingleResult()).longValue();
        em.createNativeQuery(
                        "INSERT INTO season_positions (participant_id, ticker_id, qty, avg_price)"
                                + " VALUES (?, ?, 10, 1000)")
                .setParameter(1, participantId).setParameter(2, tickerId).executeUpdate();
        em.createNativeQuery(
                        "INSERT INTO season_trades (participant_id, ticker_id, side, qty, price, game_day, created_at)"
                                + " VALUES (?, ?, 'BUY', 10, 1000, 1, CURRENT_TIMESTAMP)")
                .setParameter(1, participantId).setParameter(2, tickerId).executeUpdate();
    }

    /** (attempt_no, cash, current_day) 회차 순. */
    @SuppressWarnings("unchecked")
    private List<Object[]> participants(Long seasonId) {
        return em.createNativeQuery(
                        "SELECT attempt_no, cash, current_day FROM season_participants"
                                + " WHERE season_id = ? ORDER BY attempt_no")
                .setParameter(1, seasonId)
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

    private Long insertSeason(String mode, String title, int lengthDays, String status) {
        em.createNativeQuery(
                        """
                        INSERT INTO seasons
                          (mode, status, title, theme, base_date, length_days, initial_cash, seed, current_day)
                        VALUES (?, ?, ?, '전기·전자', ?, ?, 30000000, 1, 0)
                        """)
                .setParameter(1, mode)
                .setParameter(2, status)
                .setParameter(3, title)
                .setParameter(4, LocalDate.of(2021, 3, 2))
                .setParameter(5, lengthDays)
                .executeUpdate();
        return ((Number) em.createNativeQuery("SELECT id FROM seasons WHERE title = ?")
                        .setParameter(1, title)
                        .getSingleResult())
                .longValue();
    }

    private void insertParticipant(
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
    }
}
