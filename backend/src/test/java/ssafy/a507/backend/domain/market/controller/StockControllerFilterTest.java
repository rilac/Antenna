package ssafy.a507.backend.domain.market.controller;

import static org.hamcrest.Matchers.nullValue;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
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
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

/**
 * 종목 탐색 화면(B-02)이 GET /api/v1/stocks 에 기대하는 것 — 행별 등락률·관심 여부·집계 자리,
 * 섹터·시장·관심 필터, 그리고 GET /api/v1/stocks/sectors 요약 칩.
 *
 * <p>재료가 아직 없는 값(per · pbr · 예측 집계)은 키를 빼지 않고 null · 0 으로 내린다 — 화면이
 * {@code s.per === null} 로 분기하므로 키가 없으면 undefined.toFixed 로 죽는다. 같은 이유로
 * 재료 없는 필터·정렬은 400 이 아니라 무시다(명세 v0.15).
 *
 * <p>기준 데이터 — 삼성전자·SK하이닉스(반도체·KOSPI) · 카카오(인터넷·KOSDAQ) · 거래정지(섹터 없음·
 * KOSDAQ) · 폐지종목(listed=false). 종가는 8/27 → 8/28.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class StockControllerFilterTest {

    private static final String URL = "/api/v1/stocks";
    private static final LocalDate LATEST = LocalDate.of(2026, 8, 28);
    private static final LocalDate PREVIOUS = LocalDate.of(2026, 8, 27);

    @Autowired MockMvc mockMvc;
    @Autowired EntityManager em;

    private String me;
    private String other;

    @BeforeEach
    void setUp() {
        me = String.valueOf(insertUser("나"));
        other = String.valueOf(insertUser("남"));
        insertStock("005930", "삼성전자", "반도체", "KOSPI", true);
        insertStock("000660", "SK하이닉스", "반도체", "KOSPI", true);
        insertStock("035720", "카카오", "인터넷", "KOSDAQ", true);
        insertStock("900000", "거래정지", null, "KOSDAQ", true);
        insertStock("111111", "폐지종목", "반도체", "KOSPI", false);
        // 2020년에 잠깐 상위 300 에 들었다 빠진 종목 — 시세가 두 달 전에서 끊겼다.
        insertStock("800000", "옛종목", "반도체", "KOSPI", true);

        insertQuote("005930", PREVIOUS, "70000");
        insertQuote("005930", LATEST, "71500");
        insertQuote("000660", LATEST, "180000");
        insertQuote("035720", PREVIOUS, "40000");
        insertQuote("035720", LATEST, "41250");
        insertQuote("900000", PREVIOUS, "1200");
        insertQuote("111111", PREVIOUS, "100");
        insertQuote("111111", LATEST, "500");
        insertQuote("800000", LATEST.minusDays(60), "3000");
        // 13:00 회차가 써 둔 파생값. 목록은 계산하지 않고 읽기만 한다.
        em.createNativeQuery("UPDATE stocks SET per = 12.39, pbr = 1.07 WHERE code = '005930'")
                .executeUpdate();

        em.createNativeQuery(
                        """
                        INSERT INTO watchlist_items (user_id, stock_code, created_at)
                        VALUES (?, '005930', CURRENT_TIMESTAMP)
                        """)
                .setParameter(1, Long.valueOf(me))
                .executeUpdate();
        em.flush();
        em.clear();
    }

    @Test
    @DisplayName("행마다 등락률·관심 여부·PER·PBR 이 실리고, 재료 없는 값은 키를 남긴 채 null · 0 이다")
    void 행에_등락률과_관심_여부와_자리표시가_실린다() throws Exception {
        mockMvc.perform(get(URL).with(user(me)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].code").value("000660"))
                .andExpect(jsonPath("$.items[0].prevClose").value(180000))
                // 전날 종가가 없어 등락률을 낼 수 없다 — 0 이 아니라 null
                .andExpect(jsonPath("$.items[0].changeRate").value(nullValue()))
                .andExpect(jsonPath("$.items[0].watched").value(false))
                .andExpect(jsonPath("$.items[1].code").value("005930"))
                // (71500 - 70000) / 70000 = 2.142…%
                .andExpect(jsonPath("$.items[1].changeRate").value(2.14))
                .andExpect(jsonPath("$.items[1].watched").value(true))
                .andExpect(jsonPath("$.items[1].per").value(12.39))
                .andExpect(jsonPath("$.items[1].pbr").value(1.07))
                // 재무가 없는 종목은 키를 남긴 채 null
                .andExpect(jsonPath("$.items[0].per").value(nullValue()))
                .andExpect(jsonPath("$.items[0].pbr").value(nullValue()))
                .andExpect(jsonPath("$.items[1].predictionCount").value(0))
                .andExpect(jsonPath("$.items[1].upRatio").value(nullValue()))
                // 기준일에 거래가 없던 종목은 종가도 등락률도 비어 있다
                .andExpect(jsonPath("$.items[3].code").value("900000"))
                .andExpect(jsonPath("$.items[3].prevClose").value(nullValue()))
                .andExpect(jsonPath("$.items[3].changeRate").value(nullValue()));
    }

    @Test
    @DisplayName("market 필터")
    void 시장으로_거른다() throws Exception {
        mockMvc.perform(get(URL).param("market", "KOSDAQ").with(user(me)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(2))
                .andExpect(jsonPath("$.items[0].code").value("035720"))
                .andExpect(jsonPath("$.items[1].code").value("900000"));
    }

    @Test
    @DisplayName("sector 필터 — 폐지 종목은 섹터가 같아도 빠진다")
    void 섹터로_거른다() throws Exception {
        mockMvc.perform(get(URL).param("sector", "반도체").with(user(me)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(2))
                .andExpect(jsonPath("$.items[0].code").value("000660"))
                .andExpect(jsonPath("$.items[1].code").value("005930"));
    }

    @Test
    @DisplayName("watchedOnly 는 내 관심 종목만 — 남의 목록에는 아무것도 없다")
    void 관심_종목만_거른다() throws Exception {
        mockMvc.perform(get(URL).param("watchedOnly", "true").with(user(me)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(1))
                .andExpect(jsonPath("$.items[0].code").value("005930"))
                .andExpect(jsonPath("$.items[0].watched").value(true));

        mockMvc.perform(get(URL).param("watchedOnly", "true").with(user(other)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(0));
    }

    @Test
    @DisplayName("필터를 겹치면 전부 만족하는 행만 — 커서 페이징도 필터 안에서 이어진다")
    void 필터를_겹치고_페이징한다() throws Exception {
        mockMvc.perform(get(URL).param("sector", "반도체").param("size", "1").with(user(me)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(1))
                .andExpect(jsonPath("$.items[0].code").value("000660"))
                .andExpect(jsonPath("$.hasNext").value(true))
                .andExpect(jsonPath("$.nextCursor").value("000660"));

        mockMvc.perform(get(URL).param("sector", "반도체").param("size", "1")
                        .param("cursor", "000660").with(user(me)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].code").value("005930"))
                .andExpect(jsonPath("$.hasNext").value(false));
    }

    @Test
    @DisplayName("재료가 없는 필터·정렬(sentiment · PER · hasOpenPrediction · sort)은 400 이 아니라 무시다")
    void 재료_없는_필터는_무시한다() throws Exception {
        mockMvc.perform(get(URL)
                        .param("sentiment", "UP").param("perMin", "1").param("perMax", "20")
                        .param("hasOpenPrediction", "true").param("sort", "PER")
                        .with(user(me)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(4));
    }

    @Test
    @DisplayName("섹터 요약 — 전체 행이 먼저, 섹터는 종목 수 내림차순, 등락률은 낼 수 있는 종목의 평균")
    void 섹터_요약을_준다() throws Exception {
        mockMvc.perform(get(URL + "/sectors").with(user(me)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].sector").value(nullValue()))
                .andExpect(jsonPath("$.items[0].count").value(4))
                // (2.14 + 3.13) / 2 = 2.635 → 2.64. 등락률 없는 둘은 평균에서 뺀다
                .andExpect(jsonPath("$.items[0].changeRate").value(2.64))
                .andExpect(jsonPath("$.items[1].sector").value("반도체"))
                .andExpect(jsonPath("$.items[1].count").value(2))
                .andExpect(jsonPath("$.items[1].changeRate").value(2.14))
                .andExpect(jsonPath("$.items[2].sector").value("인터넷"))
                .andExpect(jsonPath("$.items[2].count").value(1))
                .andExpect(jsonPath("$.items[2].changeRate").value(3.13))
                // 섹터 없는 종목은 전체 수에는 들어가지만 칩으로는 나오지 않는다
                .andExpect(jsonPath("$.items.length()").value(3));
    }

    @Test
    @DisplayName("최근 7일 안에 시세가 없는 종목은 목록·요약에서 빠진다 — 수집 범위 밖으로 밀린 옛 종목")
    void 오래된_시세뿐인_종목은_빠진다() throws Exception {
        mockMvc.perform(get(URL).with(user(me)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(4))
                .andExpect(jsonPath("$.items[?(@.code == '800000')]").isEmpty());

        // 하루 거래정지(900000, 전날 시세만 있음)는 남는다 — 창 안이다.
        mockMvc.perform(get(URL).with(user(me)))
                .andExpect(jsonPath("$.items[?(@.code == '900000')]").isNotEmpty());

        mockMvc.perform(get(URL + "/sectors").with(user(me)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].count").value(4))
                .andExpect(jsonPath("$.items[1].sector").value("반도체"))
                .andExpect(jsonPath("$.items[1].count").value(2));
    }

    @Test
    @DisplayName("섹터 요약도 로그인 뒤에 있다")
    void 섹터_요약_비로그인은_401() throws Exception {
        mockMvc.perform(get(URL + "/sectors")).andExpect(status().isUnauthorized());
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

    private void insertStock(String code, String name, String sector, String market, boolean listed) {
        em.createNativeQuery(
                        """
                        INSERT INTO stocks (code, name, sector, market, listed) VALUES (?, ?, ?, ?, ?)
                        """)
                .setParameter(1, code)
                .setParameter(2, name)
                .setParameter(3, sector)
                .setParameter(4, market)
                .setParameter(5, listed)
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
