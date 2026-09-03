package ssafy.a507.backend.domain.market.controller;

import static org.hamcrest.Matchers.nullValue;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import jakarta.persistence.EntityManager;
import java.math.BigDecimal;
import java.time.LocalDate;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

/**
 * ANT-DATA-06 — GET/POST /api/v1/watchlist · DELETE /api/v1/watchlist/{stockCode} 의 AC 검증.
 *
 * <p>읽고 쓰는 것이 전부 단순 행이라 H2 로 충분하다. 인증 주체의 이름이 곧 users.id 다.
 *
 * <p>기준 데이터 — 삼성전자(8/27 70,000 → 8/28 71,500) · 카카오(8/28 41,250) · 시세 없는 종목 900000.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class WatchlistControllerTest {

    private static final String URL = "/api/v1/watchlist";
    private static final String SAMSUNG = "005930";
    private static final String KAKAO = "035720";
    private static final String NO_QUOTES = "900000";
    private static final LocalDate LATEST = LocalDate.of(2026, 8, 28);

    @Autowired MockMvc mockMvc;
    @Autowired EntityManager em;

    private String me;
    private String other;

    @BeforeEach
    void setUp() {
        me = String.valueOf(insertUser("나"));
        other = String.valueOf(insertUser("남"));
        insertStock(SAMSUNG, "삼성전자");
        insertStock(KAKAO, "카카오");
        insertStock(NO_QUOTES, "시세없음");
        insertQuote(SAMSUNG, LATEST.minusDays(1), "70000");
        insertQuote(SAMSUNG, LATEST, "71500");
        insertQuote(KAKAO, LATEST, "41250");
        em.flush();
        em.clear();
    }

    @Test
    @DisplayName("아무것도 담지 않았으면 빈 목록이다")
    void 빈_목록() throws Exception {
        mockMvc.perform(get(URL).with(user(me)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(0));
    }

    @Test
    @DisplayName("담으면 목록에 전일 종가·전일 대비·오래된 순 시계열이 실린다")
    void 담으면_목록에_시세가_실린다() throws Exception {
        add(me, SAMSUNG).andExpect(status().isCreated())
                .andExpect(jsonPath("$.stockCode").value(SAMSUNG));

        mockMvc.perform(get(URL).with(user(me)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(1))
                .andExpect(jsonPath("$.items[0].stockCode").value(SAMSUNG))
                .andExpect(jsonPath("$.items[0].name").value("삼성전자"))
                .andExpect(jsonPath("$.items[0].prevClose").value(71500))
                // (71500 - 70000) / 70000 = 2.142…% → 소수 둘째 자리
                .andExpect(jsonPath("$.items[0].changeRate").value(2.14))
                .andExpect(jsonPath("$.items[0].series.length()").value(2))
                .andExpect(jsonPath("$.items[0].series[0]").value(70000))
                .andExpect(jsonPath("$.items[0].series[1]").value(71500));
    }

    @Test
    @DisplayName("최근 담은 것이 먼저다")
    void 최근_담은_순() throws Exception {
        add(me, SAMSUNG).andExpect(status().isCreated());
        add(me, KAKAO).andExpect(status().isCreated());

        mockMvc.perform(get(URL).with(user(me)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].stockCode").value(KAKAO))
                .andExpect(jsonPath("$.items[1].stockCode").value(SAMSUNG));
    }

    @Test
    @DisplayName("시계열은 최근 30 영업일 점까지만이다")
    void 시계열은_30점까지() throws Exception {
        // 8/28 이전으로 영업일 40개를 더 깐다 — 달력이 아니라 점 수로 자르는지 본다.
        LocalDate date = LATEST.minusDays(2);
        for (int i = 0; i < 40; i++) {
            insertQuote(KAKAO, date, String.valueOf(40000 - i));
            date = date.minusDays(1);
        }
        em.flush();
        em.clear();
        add(me, KAKAO).andExpect(status().isCreated());

        mockMvc.perform(get(URL).with(user(me)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].series.length()").value(30))
                .andExpect(jsonPath("$.items[0].series[29]").value(41250))
                .andExpect(jsonPath("$.items[0].prevClose").value(41250));
    }

    @Test
    @DisplayName("시세가 없는 종목은 종가가 비고 시계열이 빈다 — 값을 지어내지 않는다")
    void 시세_없는_종목은_비어_있다() throws Exception {
        add(me, NO_QUOTES).andExpect(status().isCreated());

        mockMvc.perform(get(URL).with(user(me)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].prevClose").value(nullValue()))
                .andExpect(jsonPath("$.items[0].changeRate").value(0.0))
                .andExpect(jsonPath("$.items[0].series.length()").value(0));
    }

    @Test
    @DisplayName("같은 종목을 다시 담으면 409 DUPLICATE_WATCHLIST_ITEM")
    void 중복_등록은_409() throws Exception {
        add(me, SAMSUNG).andExpect(status().isCreated());

        add(me, SAMSUNG)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("DUPLICATE_WATCHLIST_ITEM"))
                .andExpect(jsonPath("$.field").value("stockCode"));
    }

    @Test
    @DisplayName("없는 종목은 404 STOCK_NOT_FOUND")
    void 없는_종목은_404() throws Exception {
        add(me, "999999")
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("STOCK_NOT_FOUND"))
                .andExpect(jsonPath("$.field").value("stockCode"));
    }

    @Test
    @DisplayName("종목코드 형식이 틀리면 400 INVALID_REQUEST")
    void 형식_오류는_400() throws Exception {
        add(me, "12")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"))
                .andExpect(jsonPath("$.field").value("stockCode"));
    }

    @Test
    @DisplayName("빼면 목록에서 사라지고, 없는 것을 다시 빼도 204 — 취소는 멱등이다")
    void 삭제는_멱등이다() throws Exception {
        add(me, SAMSUNG).andExpect(status().isCreated());

        mockMvc.perform(delete(URL + "/" + SAMSUNG).with(user(me))).andExpect(status().isNoContent());
        mockMvc.perform(get(URL).with(user(me)))
                .andExpect(jsonPath("$.items.length()").value(0));
        mockMvc.perform(delete(URL + "/" + SAMSUNG).with(user(me))).andExpect(status().isNoContent());
    }

    @Test
    @DisplayName("남이 담은 종목은 내 목록에 없고, 남이 지워도 내 것은 남는다")
    void 관심종목은_사용자별이다() throws Exception {
        add(me, SAMSUNG).andExpect(status().isCreated());

        mockMvc.perform(get(URL).with(user(other)))
                .andExpect(jsonPath("$.items.length()").value(0));
        mockMvc.perform(delete(URL + "/" + SAMSUNG).with(user(other))).andExpect(status().isNoContent());
        mockMvc.perform(get(URL).with(user(me)))
                .andExpect(jsonPath("$.items.length()").value(1));
    }

    @Test
    @DisplayName("로그인하지 않으면 401")
    void 비로그인은_401() throws Exception {
        mockMvc.perform(get(URL)).andExpect(status().isUnauthorized());
    }

    private org.springframework.test.web.servlet.ResultActions add(String userId, String code)
            throws Exception {
        return mockMvc.perform(post(URL)
                .with(user(userId))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"stockCode\":\"" + code + "\"}"));
    }

    private Long insertUser(String nickname) {
        em.createNativeQuery(
                        """
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

    private void insertStock(String code, String name) {
        em.createNativeQuery(
                        """
                        INSERT INTO stocks (code, name, market, listed) VALUES (?, ?, 'KOSPI', true)
                        """)
                .setParameter(1, code)
                .setParameter(2, name)
                .executeUpdate();
    }

    private void insertQuote(String code, LocalDate tradeDate, String close) {
        em.createNativeQuery(
                        """
                        INSERT INTO daily_quotes (stock_code, trade_date, close, collected_at)
                        VALUES (?, ?, ?, CURRENT_TIMESTAMP)
                        """)
                .setParameter(1, code)
                .setParameter(2, tradeDate)
                .setParameter(3, new BigDecimal(close))
                .executeUpdate();
    }
}
