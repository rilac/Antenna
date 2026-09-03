package ssafy.a507.backend.domain.market.controller;

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
 * ANT-DATA-04 — GET /api/v1/market/indices 의 AC 검증. 홈 시장 Overview 카드가 그대로 쓴다.
 *
 * <p>읽기만 하는 API 라 H2 로 충분하다. 적재 경로는 IndexQuoteIngestIntegrationTest 가 진짜
 * PostgreSQL 로 본다.
 *
 * <p>기준 데이터 — 코스피 4일 · 코스닥 2일 · 환율 1일(9/2 하루뿐이라 전일 대비를 낼 수 없다).
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class MarketControllerTest {

    private static final String URL = "/api/v1/market/indices";

    @Autowired MockMvc mockMvc;
    @Autowired EntityManager em;

    @BeforeEach
    void setUp() {
        insert("KOSPI", LocalDate.of(2026, 8, 28), "6800");
        insert("KOSPI", LocalDate.of(2026, 8, 31), "6820");
        insert("KOSPI", LocalDate.of(2026, 9, 1), "6835.8");
        insert("KOSPI", LocalDate.of(2026, 9, 2), "6850.1");
        insert("KOSDAQ", LocalDate.of(2026, 9, 1), "821.25");
        insert("KOSDAQ", LocalDate.of(2026, 9, 2), "830.5");
        insert("USDKRW", LocalDate.of(2026, 9, 2), "1385.14");
        em.flush();
        em.clear();
    }

    @Test
    @DisplayName("지수 3종을 KOSPI · KOSDAQ · USDKRW 순으로, 최신 종가·전일 대비 등락률·오래된 순 시계열과 함께 준다")
    void 세_지수의_종가와_등락률과_시계열을_준다() throws Exception {
        mockMvc.perform(get(URL).with(user("1")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(3))
                .andExpect(jsonPath("$.items[0].code").value("KOSPI"))
                .andExpect(jsonPath("$.items[0].close").value(6850.1))
                // (6850.1 - 6835.8) / 6835.8 = 0.2092% → 소수 둘째 자리
                .andExpect(jsonPath("$.items[0].changeRate").value(0.21))
                .andExpect(jsonPath("$.items[0].series.length()").value(4))
                .andExpect(jsonPath("$.items[0].series[0]").value(6800))
                .andExpect(jsonPath("$.items[0].series[3]").value(6850.1))
                .andExpect(jsonPath("$.items[1].code").value("KOSDAQ"))
                .andExpect(jsonPath("$.items[1].changeRate").value(1.13))
                .andExpect(jsonPath("$.items[2].code").value("USDKRW"))
                .andExpect(jsonPath("$.items[2].close").value(1385.14));
    }

    @Test
    @DisplayName("점이 하나뿐이면 전일 대비는 0 이다 — 없는 값을 지어내지 않는다")
    void 점이_하나면_등락률은_0() throws Exception {
        mockMvc.perform(get(URL).with(user("1")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[2].changeRate").value(0.0))
                .andExpect(jsonPath("$.items[2].series.length()").value(1));
    }

    @Test
    @DisplayName("days 는 시계열 점 수다 — 영업일 기준이라 달력 날짜가 아니다")
    void days_로_시계열_길이를_자른다() throws Exception {
        mockMvc.perform(get(URL).param("days", "2").with(user("1")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].series.length()").value(2))
                .andExpect(jsonPath("$.items[0].series[0]").value(6835.8))
                .andExpect(jsonPath("$.items[0].series[1]").value(6850.1))
                // 등락률은 창 크기와 무관하게 마지막 두 점으로 낸다
                .andExpect(jsonPath("$.items[0].changeRate").value(0.21));
    }

    @Test
    @DisplayName("아직 한 점도 없는 지수는 목록에서 빠진다 — 화면이 빈 값을 0 으로 읽지 않게")
    void 값이_없는_지수는_빠진다() throws Exception {
        em.createNativeQuery("DELETE FROM index_quotes WHERE index_code = 'USDKRW'").executeUpdate();
        em.flush();
        em.clear();

        mockMvc.perform(get(URL).with(user("1")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(2))
                .andExpect(jsonPath("$.items[?(@.code == 'USDKRW')]").isEmpty());
    }

    @Test
    @DisplayName("days 가 0 이하면 400 INVALID_REQUEST")
    void days_가_0_이하면_400() throws Exception {
        mockMvc.perform(get(URL).param("days", "0").with(user("1")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"))
                .andExpect(jsonPath("$.field").value("days"));
    }

    @Test
    @DisplayName("로그인하지 않으면 401")
    void 비로그인은_401() throws Exception {
        mockMvc.perform(get(URL)).andExpect(status().isUnauthorized());
    }

    private void insert(String code, LocalDate tradeDate, String close) {
        em.createNativeQuery(
                        """
                        INSERT INTO index_quotes (index_code, trade_date, close)
                        VALUES (?, ?, ?)
                        """)
                .setParameter(1, code)
                .setParameter(2, tradeDate)
                .setParameter(3, new BigDecimal(close))
                .executeUpdate();
    }
}
