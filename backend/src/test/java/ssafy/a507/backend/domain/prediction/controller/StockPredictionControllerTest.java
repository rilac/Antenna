package ssafy.a507.backend.domain.prediction.controller;

import static org.assertj.core.api.Assertions.assertThat;
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
 * ANT-PRED-07 — 종목 상세의 예측 호가창(distribution)과 오늘 판정(settled-today).
 *
 * <p>전일 종가는 269,500원으로 고정한다. 5% 경계가 원 단위로 떨어지는 값이라(-15% = 229,075) 경계 바로 아래·위를
 * 정확히 찍어 볼 수 있다.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class StockPredictionControllerTest {

    private static final String SAMSUNG = "005930";
    private static final String NO_QUOTE = "000660";
    private static final String DIST = "/api/v1/stocks/{code}/predictions/distribution";
    private static final String TODAY = "/api/v1/stocks/{code}/predictions/settled-today";

    @Autowired
    MockMvc mockMvc;

    @Autowired
    EntityManager em;

    private Long authorId;
    private Long viewerId;

    @BeforeEach
    void setUp() {
        authorId = insertUser("호가작성자");
        viewerId = insertUser("구경꾼");
        insertStock(SAMSUNG, "삼성전자");
        insertStock(NO_QUOTE, "SK하이닉스");
        insertQuote(SAMSUNG, LocalDate.now().minusDays(1), "269500");
    }

    @Test
    @DisplayName("분포 — 판정 대기만 세고, 서버가 준 경계 가격으로 센다(경계값은 위 구간)")
    void 분포_경계() throws Exception {
        insertPrediction("215000", "OPEN", null, Instant.now());   // -20% 미만 → 맨 아래 열린 구간
        insertPrediction("215600", "BASE", null, Instant.now());   // -20% 경계 그 자체 → [-20,-15)
        insertPrediction("229074", "OPEN", null, Instant.now());   // -15% 경계(229,075) 바로 아래 → [-20,-15)
        insertPrediction("229075", "OPEN", null, Instant.now());   // 경계 그 자체 → [-15,-10)
        insertPrediction("269500", "OPEN", null, Instant.now());   // 전일 종가 = 0% → [0,5)
        insertPrediction("400000", "OPEN", null, Instant.now());   // +20% 이상 → 맨 위 열린 구간
        insertPrediction("269500", "HIT", "0.500", Instant.now()); // 판정 완료는 기록이라 세지 않는다

        mockMvc.perform(get(DIST, SAMSUNG).with(user(String.valueOf(viewerId))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.stockCode").value(SAMSUNG))
                .andExpect(jsonPath("$.basePrice").value(269500))
                .andExpect(jsonPath("$.asOf").value(LocalDate.now().minusDays(1).toString()))
                .andExpect(jsonPath("$.stepPct").value(5))
                .andExpect(jsonPath("$.total").value(6))
                .andExpect(jsonPath("$.buckets.length()").value(10))
                .andExpect(jsonPath("$.buckets[0].fromPct").doesNotExist())
                .andExpect(jsonPath("$.buckets[0].toPct").value(-20))
                .andExpect(jsonPath("$.buckets[0].toPrice").value(215600))
                .andExpect(jsonPath("$.buckets[0].count").value(1))
                .andExpect(jsonPath("$.buckets[1].fromPrice").value(215600))
                .andExpect(jsonPath("$.buckets[1].toPrice").value(229075))
                .andExpect(jsonPath("$.buckets[1].count").value(2))
                .andExpect(jsonPath("$.buckets[2].count").value(1))
                // 0% 경계는 전일 종가 그대로다 — 화면 머리의 "전일 종가" 와 어긋나지 않는다.
                .andExpect(jsonPath("$.buckets[5].fromPct").value(0))
                .andExpect(jsonPath("$.buckets[5].fromPrice").value(269500))
                .andExpect(jsonPath("$.buckets[5].count").value(1))
                .andExpect(jsonPath("$.buckets[9].fromPrice").value(323400))
                .andExpect(jsonPath("$.buckets[9].toPct").doesNotExist())
                .andExpect(jsonPath("$.buckets[9].count").value(1));
    }

    @Test
    @DisplayName("분포 — 응답에 개인이 없다(작성자·예측 id·목표가 원본)")
    void 분포_개인정보_없음() throws Exception {
        Long id = insertPrediction("229074", "OPEN", null, Instant.now());

        String body = mockMvc.perform(get(DIST, SAMSUNG).with(user(String.valueOf(viewerId))))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        assertThat(body).doesNotContain("호가작성자", "userId", "\"id\"", "229074");
        assertThat(id).isNotNull();
    }

    @Test
    @DisplayName("분포 — 전일 종가가 없는 종목은 구간을 지어내지 않는다(basePrice null · buckets 비움 · total 은 센 값)")
    void 분포_종가_없음() throws Exception {
        insertPrediction(NO_QUOTE, "100000", "OPEN", null, Instant.now());

        mockMvc.perform(get(DIST, NO_QUOTE).with(user(String.valueOf(viewerId))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.basePrice").doesNotExist())
                .andExpect(jsonPath("$.asOf").doesNotExist())
                .andExpect(jsonPath("$.buckets.length()").value(0))
                .andExpect(jsonPath("$.total").value(1));
    }

    @Test
    @DisplayName("오늘 판정 — 오늘(KST) HIT·MISS 로 바뀐 것만. 어제 판정·판정 대기는 빠진다")
    void 오늘_판정() throws Exception {
        Long hit = insertPrediction("280000", "HIT", "2.700", Instant.now());
        Long miss = insertPrediction("250000", "MISS", "9.100", Instant.now());
        insertPrediction("260000", "HIT", "1.000", Instant.now().minus(2, ChronoUnit.DAYS));
        insertPrediction("270000", "OPEN", null, Instant.now());

        mockMvc.perform(get(TODAY, SAMSUNG).with(user(String.valueOf(viewerId))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(2))
                .andExpect(jsonPath("$.items[0].id").value(hit))
                .andExpect(jsonPath("$.items[0].author.userId").value(authorId))
                .andExpect(jsonPath("$.items[0].author.nickname").value("호가작성자"))
                .andExpect(jsonPath("$.items[0].author.avatarUrl").doesNotExist())
                // 판정 후라 방향·목표가가 잠기지 않는다 — 비구독자가 봐도 온다.
                .andExpect(jsonPath("$.items[0].direction").value("UP"))
                .andExpect(jsonPath("$.items[0].targetPrice").value(280000))
                .andExpect(jsonPath("$.items[0].status").value("HIT"))
                .andExpect(jsonPath("$.items[0].errorRate").value(2.7))
                .andExpect(jsonPath("$.items[1].id").value(miss))
                .andExpect(jsonPath("$.items[1].status").value("MISS"));
    }

    @Test
    @DisplayName("오늘 판정 — 없으면 빈 목록")
    void 오늘_판정_없음() throws Exception {
        mockMvc.perform(get(TODAY, SAMSUNG).with(user(String.valueOf(viewerId))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(0));
    }

    @Test
    @DisplayName("없는 종목은 둘 다 404 STOCK_NOT_FOUND")
    void 없는_종목() throws Exception {
        mockMvc.perform(get(DIST, "999999").with(user(String.valueOf(viewerId))))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("STOCK_NOT_FOUND"));
        mockMvc.perform(get(TODAY, "999999").with(user(String.valueOf(viewerId))))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("STOCK_NOT_FOUND"));
    }

    @Test
    @DisplayName("비로그인은 401")
    void 인증_필요() throws Exception {
        mockMvc.perform(get(DIST, SAMSUNG)).andExpect(status().isUnauthorized());
    }

    private Long insertPrediction(String targetPrice, String status, String errorRate, Instant updatedAt) {
        return insertPrediction(SAMSUNG, targetPrice, status, errorRate, updatedAt);
    }

    private Long insertPrediction(
            String stockCode, String targetPrice, String status, String errorRate, Instant updatedAt) {
        em.createNativeQuery("""
                        INSERT INTO predictions
                          (user_id, track, stock_code, direction, target_price, horizon,
                           status, error_rate, created_at, updated_at)
                        VALUES (?, 'REAL', ?, 'UP', ?, 7, ?, ?, CURRENT_TIMESTAMP, ?)
                        """)
                .setParameter(1, authorId)
                .setParameter(2, stockCode)
                .setParameter(3, new BigDecimal(targetPrice))
                .setParameter(4, status)
                .setParameter(5, errorRate == null ? null : new BigDecimal(errorRate))
                .setParameter(6, updatedAt)
                .executeUpdate();
        return ((Number) em.createNativeQuery("SELECT max(id) FROM predictions").getSingleResult()).longValue();
    }

    private void insertQuote(String code, LocalDate tradeDate, String close) {
        em.createNativeQuery("""
                        INSERT INTO daily_quotes (stock_code, trade_date, close, collected_at)
                        VALUES (?, ?, ?, CURRENT_TIMESTAMP)
                        """)
                .setParameter(1, code)
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
