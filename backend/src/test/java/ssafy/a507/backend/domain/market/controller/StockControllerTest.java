package ssafy.a507.backend.domain.market.controller;

import static org.hamcrest.Matchers.nullValue;
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
 * ANT-DATA-03 — GET /api/v1/stocks, GET /api/v1/stocks/{code}/prices 의 AC 검증.
 *
 * <p>읽기만 하는 API 라 H2 로 충분하다. PostgreSQL 이 필요한 것은 ON CONFLICT 를 쓰는 적재
 * 경로뿐이고 그쪽은 MarketUpsertRepositoryTest 가 진짜 DB 로 본다.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class StockControllerTest {

    private static final String URL = "/api/v1/stocks";
    private static final LocalDate LATEST = LocalDate.of(2026, 8, 28);
    private static final LocalDate PREVIOUS = LocalDate.of(2026, 8, 27);

    @Autowired MockMvc mockMvc;
    @Autowired EntityManager em;

    @BeforeEach
    void setUp() {
        insertStock("005930", "삼성전자", "반도체", "KOSPI", true);
        insertStock("035720", "카카오", "인터넷", "KOSDAQ", true);
        // 그날 거래가 정지돼 마지막 영업일 시세가 없는 종목.
        insertStock("900000", "거래정지", null, "KOSDAQ", true);
        // 상장폐지. 행은 남지만 탐색 목록에는 나오지 않아야 한다.
        insertStock("111111", "폐지종목", null, "KOSPI", false);

        insertQuote("005930", PREVIOUS, "70000");
        insertQuote("005930", LATEST, "71500");
        insertQuote("035720", LATEST, "41250");
        insertQuote("900000", PREVIOUS, "1200");
        insertQuote("111111", LATEST, "500");
        em.flush();
        em.clear();
    }

    // ── GET /stocks ─────────────────────────────────────────

    @Test
    @DisplayName("목록은 직전 영업일 종가와 그 기준일을 함께 내려준다")
    void 목록에_전일_종가가_실린다() throws Exception {
        mockMvc.perform(get(URL).with(user("1")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.baseDate").value("2026-08-28"))
                .andExpect(jsonPath("$.items[0].code").value("005930"))
                .andExpect(jsonPath("$.items[0].name").value("삼성전자"))
                .andExpect(jsonPath("$.items[0].sector").value("반도체"))
                .andExpect(jsonPath("$.items[0].market").value("KOSPI"))
                .andExpect(jsonPath("$.items[0].prevClose").value(71500));
    }

    @Test
    @DisplayName("상장폐지 종목은 목록에 나오지 않는다 — 행은 그대로 남아 있다")
    void 폐지_종목은_목록에서_빠진다() throws Exception {
        mockMvc.perform(get(URL).with(user("1")))
                .andExpect(status().isOk())
                // 005930 · 035720 · 900000 셋뿐. 111111 은 listed=false 다.
                .andExpect(jsonPath("$.items.length()").value(3))
                .andExpect(jsonPath("$.items[?(@.code == '111111')]").isEmpty());
    }

    @Test
    @DisplayName("기준일에 거래가 없던 종목은 종가가 빈 칸이다")
    void 거래정지_종목은_종가가_비어_있다() throws Exception {
        // 목록은 종목코드 오름차순이라 900000 이 마지막이다.
        mockMvc.perform(get(URL).with(user("1")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[2].code").value("900000"))
                .andExpect(jsonPath("$.items[2].prevClose").value(nullValue()));
    }

    @Test
    @DisplayName("커서로 다음 페이지를 이어 받는다")
    void 커서_페이징이_이어진다() throws Exception {
        mockMvc.perform(get(URL).param("size", "2").with(user("1")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(2))
                .andExpect(jsonPath("$.hasNext").value(true))
                .andExpect(jsonPath("$.nextCursor").value("035720"));

        mockMvc.perform(get(URL).param("size", "2").param("cursor", "035720").with(user("1")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(1))
                .andExpect(jsonPath("$.items[0].code").value("900000"))
                .andExpect(jsonPath("$.hasNext").value(false))
                .andExpect(jsonPath("$.nextCursor").doesNotExist());
    }

    @Test
    @DisplayName("로그인하지 않으면 401 — 실전 시세도 인증 뒤에 있다")
    void 비로그인은_401() throws Exception {
        mockMvc.perform(get(URL)).andExpect(status().isUnauthorized());
    }

    // ── GET /stocks/{code}/prices ───────────────────────────

    @Test
    @DisplayName("구간 시세를 날짜 오름차순 종가로 내려준다")
    void 구간_시세를_준다() throws Exception {
        mockMvc.perform(get(URL + "/005930/prices")
                        .param("from", "2026-08-27")
                        .param("to", "2026-08-28")
                        .with(user("1")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(2))
                .andExpect(jsonPath("$.items[0].tradeDate").value("2026-08-27"))
                .andExpect(jsonPath("$.items[0].close").value(70000))
                .andExpect(jsonPath("$.items[1].close").value(71500));
    }

    @Test
    @DisplayName("구간을 주지 않으면 마지막 영업일까지의 최근 30일이다")
    void 구간을_생략하면_최근_30일이다() throws Exception {
        mockMvc.perform(get(URL + "/005930/prices").with(user("1")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(2));
    }

    @Test
    @DisplayName("없는 종목은 404 STOCK_NOT_FOUND — 시세 없는 구간(200)과 구분한다")
    void 없는_종목은_404() throws Exception {
        mockMvc.perform(get(URL + "/999999/prices").with(user("1")))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("STOCK_NOT_FOUND"));
    }

    @Test
    @DisplayName("시세가 없는 구간은 200 에 빈 목록이다")
    void 시세_없는_구간은_빈_목록() throws Exception {
        mockMvc.perform(get(URL + "/005930/prices")
                        .param("from", "2026-01-01")
                        .param("to", "2026-01-31")
                        .with(user("1")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(0));
    }

    @Test
    @DisplayName("from 이 to 보다 뒤면 400 INVALID_REQUEST")
    void 뒤집힌_구간은_400() throws Exception {
        mockMvc.perform(get(URL + "/005930/prices")
                        .param("from", "2026-08-28")
                        .param("to", "2026-08-27")
                        .with(user("1")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"))
                .andExpect(jsonPath("$.field").value("from"));
    }

    private void insertStock(String code, String name, String sector, String market, boolean listed) {
        em.createNativeQuery(
                        """
                        INSERT INTO stocks (code, name, sector, market, listed)
                        VALUES (?, ?, ?, ?, ?)
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
                .setParameter(3, new java.math.BigDecimal(close))
                .executeUpdate();
    }
}
