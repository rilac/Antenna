package ssafy.a507.backend.domain.season.controller;

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
 * GET /api/v1/seasons · /me · /{id} 의 AC 검증.
 *
 * <p>가장 중요한 검증은 <b>응답에 시기가 없다</b> 는 것이다. 연도가 새면 참가자가 결과를
 * 아는 상태로 시작해 예측이 아니라 복기가 된다(API 명세 v0.24 · 설계서 §3 G).
 *
 * <p>두 번째는 시연 시즌이 일반 사용자에게 보이지 않는다는 것이다. 화면에서 걸러도 응답에
 * 실려 오면 개발자도구로 다 보이므로 서버가 걸러야 한다.
 *
 * <p>기준 데이터 — 연습 시즌 하나, 대회 시즌 하나, 시연 시즌 하나. 연습 시즌에만 종목 둘이고
 * A사에 워밍업 두 봉(game_day -1·0)과 플레이 다섯 봉(1~5)이 있다.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class SeasonControllerTest {

    private static final String URL = "/api/v1/seasons";

    @Autowired MockMvc mockMvc;
    @Autowired EntityManager em;

    private String me;
    private String admin;
    private Long practiceId;
    private Long demoId;
    private Long tickerA;

    @BeforeEach
    void setUp() {
        me = String.valueOf(insertUser("나", "USER"));
        admin = String.valueOf(insertUser("관리자", "ADMIN"));

        practiceId = insertSeason("PRACTICE", "전기전자 업종", "한 줄 설명", "전기·전자", 60);
        insertSeason("COMPETITION", "화학 업종", null, "화학", 120);
        demoId = insertSeason("DEMO", "시연용", null, "유통", 5);

        insertStock("A0001");
        insertStock("A0002");
        tickerA = insertTicker(practiceId, "A사", "A0001");
        insertTicker(practiceId, "B사", "A0002");
        // 워밍업 두 봉(game_day -1·0)과 플레이 다섯 봉. 진행일이 3 이면 워밍업 2 + 플레이 3 이다.
        insertPrice(tickerA, -1, 900);
        insertPrice(tickerA, 0, 950);
        for (int day = 1; day <= 5; day++) {
            insertPrice(tickerA, day, 1000 * day);
        }
        em.flush();
        em.clear();
    }

    private String pricesUrl() {
        return URL + "/" + practiceId + "/tickers/" + tickerA + "/prices";
    }

    @Test
    @DisplayName("응답에 시기가 없다 — baseDate·연도 필드가 어디에도 없다")
    void 시기를_내보내지_않는다() throws Exception {
        mockMvc.perform(get(URL + "/" + practiceId).with(user(me)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.baseDate").doesNotExist())
                .andExpect(jsonPath("$.startDate").doesNotExist())
                .andExpect(jsonPath("$.year").doesNotExist());

        mockMvc.perform(get(URL).with(user(me)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].baseDate").doesNotExist());
    }

    @Test
    @DisplayName("일반 사용자 목록에 시연 시즌이 없다")
    void 시연은_일반_사용자에게_숨긴다() throws Exception {
        mockMvc.perform(get(URL).with(user(me)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(2))
                .andExpect(jsonPath("$.items[?(@.mode == 'DEMO')]").isEmpty());
    }

    @Test
    @DisplayName("관리자 목록에는 시연 시즌이 있다")
    void 관리자는_시연을_본다() throws Exception {
        mockMvc.perform(get(URL).with(user(admin)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(3));
    }

    @Test
    @DisplayName("시연 시즌 상세는 일반 사용자에게 404 — 403 이면 있다는 사실이 샌다")
    void 시연_상세는_없는_것처럼_답한다() throws Exception {
        mockMvc.perform(get(URL + "/" + demoId).with(user(me)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("SEASON_NOT_FOUND"));

        mockMvc.perform(get(URL + "/" + demoId).with(user(admin))).andExpect(status().isOk());
    }

    @Test
    @DisplayName("mode 로 걸러진다 — 연습하기 화면이 쓰는 조건이다")
    void 모드로_걸러_준다() throws Exception {
        mockMvc.perform(get(URL).param("mode", "PRACTICE").with(user(me)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(1))
                .andExpect(jsonPath("$.items[0].mode").value("PRACTICE"))
                .andExpect(jsonPath("$.items[0].title").value("전기전자 업종"));
    }

    @Test
    @DisplayName("일반 사용자가 mode=DEMO 를 물으면 빈 목록이다")
    void 볼_수_없는_모드는_빈_목록() throws Exception {
        mockMvc.perform(get(URL).param("mode", "DEMO").with(user(me)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(0));
    }

    @Test
    @DisplayName("종목 수는 세어서 담고, 참가 안 한 시즌은 joined 가 false 다")
    void 상세가_담는_값() throws Exception {
        mockMvc.perform(get(URL + "/" + practiceId).with(user(me)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tickerCount").value(2))
                .andExpect(jsonPath("$.sector").value("전기·전자"))
                .andExpect(jsonPath("$.lengthDays").value(60))
                .andExpect(jsonPath("$.joined").value(false))
                .andExpect(jsonPath("$.currentDay").doesNotExist());
    }

    @Test
    @DisplayName("없는 시즌은 404 SEASON_NOT_FOUND")
    void 없는_시즌은_404() throws Exception {
        mockMvc.perform(get(URL + "/999999").with(user(me)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("SEASON_NOT_FOUND"));
    }

    @Test
    @DisplayName("참가한 시즌은 joined 와 개인 진행일이 온다")
    void 참가하면_이어하기_값이_온다() throws Exception {
        insertParticipant(practiceId, Long.valueOf(me), 1, 12);
        em.flush();
        em.clear();

        mockMvc.perform(get(URL + "/" + practiceId).with(user(me)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.joined").value(true))
                .andExpect(jsonPath("$.currentDay").value(12));
    }

    @Test
    @DisplayName("이어하기 목록은 내 진행일로 진행 중·완료를 가른다")
    void 내_진행일로_가른다() throws Exception {
        insertParticipant(practiceId, Long.valueOf(me), 1, 60);
        insertParticipant(practiceId, Long.valueOf(me), 2, 12);
        em.flush();
        em.clear();

        mockMvc.perform(get(URL + "/me").param("status", "ONGOING").with(user(me)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(1))
                .andExpect(jsonPath("$.items[0].currentDay").value(12))
                .andExpect(jsonPath("$.items[0].progress").value(20));

        mockMvc.perform(get(URL + "/me").param("status", "DONE").with(user(me)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(1))
                .andExpect(jsonPath("$.items[0].progress").value(100));
    }

    @Test
    @DisplayName("참가한 적 없으면 이어하기 목록이 비어 있다")
    void 참가_없으면_빈_목록() throws Exception {
        mockMvc.perform(get(URL + "/me").with(user(me)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(0));
    }

    @Test
    @DisplayName("종목 목록은 표시 이름과 섹터만 준다 — 원본 종목코드는 응답에 없다")
    void 종목은_블라인드로_나간다() throws Exception {
        mockMvc.perform(get(URL + "/" + practiceId + "/tickers").with(user(me)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(2))
                .andExpect(jsonPath("$.items[0].displayName").value("A사"))
                .andExpect(jsonPath("$.items[0].sector").value("전기·전자"))
                .andExpect(jsonPath("$.items[0].realStockCode").doesNotExist())
                .andExpect(jsonPath("$.items[0].stockCode").doesNotExist())
                .andExpect(jsonPath("$.items[0].name").doesNotExist());
    }

    @Test
    @DisplayName("참가하지 않았으면 워밍업까지만 온다 — 플레이 구간은 한 봉도 없다")
    void 미참가는_워밍업까지다() throws Exception {
        mockMvc.perform(get(pricesUrl()).with(user(me)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(2))
                .andExpect(jsonPath("$.items[0].gameDay").value(-1))
                .andExpect(jsonPath("$.items[1].gameDay").value(0))
                .andExpect(jsonPath("$.items[?(@.gameDay > 0)]").isEmpty());
    }

    @Test
    @DisplayName("내 진행일까지만 온다 — 그 뒤 종가는 응답에 실리지 않는다")
    void 진행일까지만_준다() throws Exception {
        insertParticipant(practiceId, Long.valueOf(me), 1, 3);
        em.flush();
        em.clear();

        // 워밍업 2 + 플레이 3. 심어 둔 4·5일치는 오지 않는다.
        mockMvc.perform(get(pricesUrl()).with(user(me)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(5))
                .andExpect(jsonPath("$.items[0].gameDay").value(-1))
                .andExpect(jsonPath("$.items[4].gameDay").value(3))
                .andExpect(jsonPath("$.items[2].close").value(1000.00))
                .andExpect(jsonPath("$.items[2].volume").value(1000))
                .andExpect(jsonPath("$.items[?(@.gameDay > 3)]").isEmpty());
    }

    @Test
    @DisplayName("uptoDay 를 크게 넣어도 진행일에서 잘린다 — 요청으로 상한을 넘길 수 없다")
    void uptoDay_로_커닝할_수_없다() throws Exception {
        insertParticipant(practiceId, Long.valueOf(me), 1, 3);
        em.flush();
        em.clear();

        mockMvc.perform(get(pricesUrl()).param("uptoDay", "60").with(user(me)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(5))
                .andExpect(jsonPath("$.items[?(@.gameDay > 3)]").isEmpty());

        // 낮추는 쪽으로는 듣는다 — 차트에서 구간을 좁혀 볼 때 쓴다
        mockMvc.perform(get(pricesUrl()).param("uptoDay", "1").with(user(me)))
                .andExpect(jsonPath("$.items.length()").value(3))
                .andExpect(jsonPath("$.items[2].gameDay").value(1));
    }

    @Test
    @DisplayName("남의 시즌 종목 id 로는 가격을 못 본다")
    void 다른_시즌_종목은_404() throws Exception {
        insertParticipant(practiceId, Long.valueOf(me), 1, 3);
        Long otherTicker = insertTicker(demoId, "A사", "A0001");
        em.flush();
        em.clear();

        mockMvc.perform(get(URL + "/" + practiceId + "/tickers/" + otherTicker + "/prices")
                        .with(user(me)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("SEASON_TICKER_NOT_FOUND"));
    }

    @Test
    @DisplayName("시연 시즌 종목·가격도 일반 사용자에게 404")
    void 시연_종목도_숨긴다() throws Exception {
        mockMvc.perform(get(URL + "/" + demoId + "/tickers").with(user(me)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("SEASON_NOT_FOUND"));
    }

    @Test
    @DisplayName("대회는 공용 진행일까지 온다 — 참가 여부와 무관하다")
    void 대회는_공용_진행일이다() throws Exception {
        // 기준 데이터의 대회 시즌과 섹터가 겹치면 UQ(mode, theme, seed, base_date) 에 걸린다
        Long contestId = insertSeason("COMPETITION", "대회 시즌", null, "운송장비·부품", 60);
        Long ticker = insertTicker(contestId, "A사", "A0002");
        for (int day = 1; day <= 5; day++) {
            insertPrice(ticker, day, 2000 + day);
        }
        em.createNativeQuery("UPDATE seasons SET current_day = 2 WHERE id = ?")
                .setParameter(1, contestId)
                .executeUpdate();
        em.flush();
        em.clear();

        mockMvc.perform(get(URL + "/" + contestId + "/tickers/" + ticker + "/prices")
                        .with(user(me)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(2));
    }

    @Test
    @DisplayName("로그인하지 않으면 401")
    void 비로그인은_401() throws Exception {
        mockMvc.perform(get(URL)).andExpect(status().isUnauthorized());
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

    private Long insertSeason(String mode, String title, String note, String theme, int lengthDays) {
        em.createNativeQuery(
                        """
                        INSERT INTO seasons
                          (mode, status, title, note, theme, base_date, length_days,
                           initial_cash, seed, current_day)
                        VALUES (?, 'RUNNING', ?, ?, ?, ?, ?, ?, ?, 0)
                        """)
                .setParameter(1, mode)
                .setParameter(2, title)
                .setParameter(3, note)
                .setParameter(4, theme)
                .setParameter(5, LocalDate.of(2021, 3, 2))
                .setParameter(6, lengthDays)
                .setParameter(7, new BigDecimal("30000000"))
                .setParameter(8, 1001L)
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
                .setParameter(2, code + "종목")
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

    private void insertParticipant(Long seasonId, Long userId, int attemptNo, int currentDay) {
        em.createNativeQuery(
                        """
                        INSERT INTO season_participants
                          (season_id, user_id, attempt_no, cash, current_day, created_at)
                        VALUES (?, ?, ?, ?, ?, CURRENT_TIMESTAMP)
                        """)
                .setParameter(1, seasonId)
                .setParameter(2, userId)
                .setParameter(3, attemptNo)
                .setParameter(4, new BigDecimal("30000000"))
                .setParameter(5, currentDay)
                .executeUpdate();
    }
}
