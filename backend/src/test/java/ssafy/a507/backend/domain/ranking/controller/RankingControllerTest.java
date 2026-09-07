package ssafy.a507.backend.domain.ranking.controller;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

/**
 * ANT-RANK-02 — 랭킹 조회(화면 E-01 · B-01)의 AC 검증.
 *
 * <p>랭킹 스냅샷 배치(ANT-RANK-01)가 아직 없어서 행은 네이티브 INSERT 로 만든다. {@code rank} 는 예약어라
 * 엔티티와 마찬가지로 인용부호로 감싼다.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class RankingControllerTest {

    private static final String URL = "/api/v1/rankings";

    @Autowired
    MockMvc mockMvc;

    @Autowired
    EntityManager em;

    private Long viewerId;

    @BeforeEach
    void setUp() {
        viewerId = insertUser("보는사람");
    }

    @Test
    @DisplayName("트랙·필터가 맞는 행만 순위 오름차순으로 나오고 산출 시각이 붙는다")
    void 목록() throws Exception {
        Long first = insertUser("1등");
        Long second = insertUser("2등");
        insertRanking("REAL", "ALL", second, 2, "71.500", "62.00", "3.100", 40);
        insertRanking("REAL", "ALL", first, 1, "88.250", "74.00", "1.900", 52);
        // 같은 사람이라도 트랙·필터가 다르면 다른 줄이다 — 섞이면 안 된다.
        insertRanking("REPLAY", "ALL", first, 1, "99.000", "90.00", "0.500", 10);
        insertRanking("REAL", "30D", first, 1, "50.000", "50.00", "5.000", 5);

        mockMvc.perform(get(URL).param("track", "REAL").with(user(String.valueOf(viewerId))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(2))
                .andExpect(jsonPath("$.computedAt").exists())
                .andExpect(jsonPath("$.items[0].rank").value(1))
                .andExpect(jsonPath("$.items[0].userId").value(first))
                .andExpect(jsonPath("$.items[0].nickname").value("1등"))
                .andExpect(jsonPath("$.items[0].score").value(88.25))
                .andExpect(jsonPath("$.items[0].hitRate").value(74.0))
                .andExpect(jsonPath("$.items[0].avgError").value(1.9))
                .andExpect(jsonPath("$.items[0].doneCount").value(52))
                .andExpect(jsonPath("$.items[1].rank").value(2));
    }

    @Test
    @DisplayName("period·sector 는 다른 filter_key 를 본다")
    void 기간_섹터_필터() throws Exception {
        Long user = insertUser("반도체");
        insertRanking("REAL", "ALL", user, 1, "10.000", "10.00", "1.000", 1);
        insertRanking("REAL", "30D", user, 1, "20.000", "20.00", "2.000", 2);
        insertRanking("REAL", "ALL:SEC:전기전자", user, 1, "30.000", "30.00", "3.000", 3);

        // 프론트가 실제로 보내는 값은 D30 이다(api/rankings.ts PERIODS). 한글 라벨도 함께 받는다.
        for (String thirtyDays : new String[] {"D30", "30D", "30일"}) {
            mockMvc.perform(get(URL)
                            .param("track", "REAL")
                            .param("period", thirtyDays)
                            .with(user(String.valueOf(viewerId))))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.items[0].score").value(20.0));
        }

        mockMvc.perform(get(URL)
                        .param("track", "REAL")
                        .param("period", "ALL")
                        .with(user(String.valueOf(viewerId))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].score").value(10.0));

        mockMvc.perform(get(URL)
                        .param("track", "REAL")
                        .param("sector", "전기전자")
                        .with(user(String.valueOf(viewerId))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].score").value(30.0));
    }

    @Test
    @DisplayName("REPLAY 는 seasonId 로 갈리고, REAL 에 준 seasonId 는 무시한다")
    void 리플레이_시즌() throws Exception {
        Long user = insertUser("시즌참가자");
        insertRanking("REPLAY", "SEASON:7", user, 1, "44.000", "44.00", "4.000", 4);
        insertRanking("REAL", "ALL", user, 1, "11.000", "11.00", "1.000", 1);

        mockMvc.perform(get(URL)
                        .param("track", "REPLAY")
                        .param("seasonId", "7")
                        .with(user(String.valueOf(viewerId))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].score").value(44.0));

        // REAL 에 seasonId 를 줘도 400 이 아니라 무시다.
        mockMvc.perform(get(URL)
                        .param("track", "REAL")
                        .param("seasonId", "7")
                        .with(user(String.valueOf(viewerId))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].score").value(11.0));
    }

    @Test
    @DisplayName("limit 으로 자르고 fromRank·cursor 로 같은 자리에서 이어 받는다")
    void 순위_직행과_커서() throws Exception {
        for (int rank = 1; rank <= 5; rank++) {
            insertRanking("REAL", "ALL", insertUser("선수" + rank), rank, "10.000", "10.00", "1.000", 1);
        }

        mockMvc.perform(get(URL)
                        .param("track", "REAL")
                        .param("limit", "2")
                        .with(user(String.valueOf(viewerId))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(2))
                .andExpect(jsonPath("$.items[0].rank").value(1));

        mockMvc.perform(get(URL)
                        .param("track", "REAL")
                        .param("limit", "2")
                        .param("cursor", "3")
                        .with(user(String.valueOf(viewerId))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].rank").value(3))
                .andExpect(jsonPath("$.items[1].rank").value(4));

        // fromRank 가 cursor 보다 우선한다 — 순위 직행이 더 구체적인 요청이다.
        mockMvc.perform(get(URL)
                        .param("track", "REAL")
                        .param("cursor", "2")
                        .param("fromRank", "5")
                        .with(user(String.valueOf(viewerId))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(1))
                .andExpect(jsonPath("$.items[0].rank").value(5));
    }

    @Test
    @DisplayName("같은 순위끼리도 순서가 고정이라 페이지가 흔들리지 않는다")
    void 동점_안정_정렬() throws Exception {
        Long a = insertUser("동점A");
        Long b = insertUser("동점B");
        insertRanking("REAL", "ALL", b, 2, "50.000", "50.00", "5.000", 9);
        insertRanking("REAL", "ALL", a, 2, "50.000", "50.00", "5.000", 9);
        insertRanking("REAL", "ALL", insertUser("1등"), 1, "90.000", "90.00", "1.000", 9);

        // 같은 요청을 두 번 해도 동점 두 줄의 앞뒤가 같아야 한다 — user.id 오름차순으로 고정된다.
        for (int attempt = 0; attempt < 2; attempt++) {
            mockMvc.perform(get(URL).param("track", "REAL").with(user(String.valueOf(viewerId))))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.items.length()").value(3))
                    .andExpect(jsonPath("$.items[1].userId").value(a))
                    .andExpect(jsonPath("$.items[2].userId").value(b));
        }
    }

    @Test
    @DisplayName("스냅샷이 없으면 computedAt null · 빈 목록 200 — 404 가 아니다")
    void 배치_전() throws Exception {
        mockMvc.perform(get(URL).param("track", "REAL").with(user(String.valueOf(viewerId))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(0))
                .andExpect(jsonPath("$.computedAt").doesNotExist());
    }

    @Test
    @DisplayName("어휘 밖 track·period 는 400, track 이 없어도 400")
    void 잘못된_파라미터() throws Exception {
        mockMvc.perform(get(URL).param("track", "PAPER").with(user(String.valueOf(viewerId))))
                .andExpect(status().isBadRequest());

        mockMvc.perform(get(URL)
                        .param("track", "REAL")
                        .param("period", "1년")
                        .with(user(String.valueOf(viewerId))))
                .andExpect(status().isBadRequest());

        mockMvc.perform(get(URL).with(user(String.valueOf(viewerId))))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("비로그인은 401")
    void 인증_필요() throws Exception {
        mockMvc.perform(get(URL).param("track", "REAL")).andExpect(status().isUnauthorized());
    }

    private void insertRanking(
            String track,
            String filterKey,
            Long userId,
            int rank,
            String score,
            String hitRate,
            String avgError,
            int doneCount) {
        em.createNativeQuery(
                        """
                        INSERT INTO rankings
                          (track, filter_key, user_id, score, hit_rate, avg_error, done_count, "rank", computed_at)
                        VALUES (?, ?, ?, ?, ?, ?, ?, ?, CURRENT_TIMESTAMP)
                        """)
                .setParameter(1, track)
                .setParameter(2, filterKey)
                .setParameter(3, userId)
                .setParameter(4, new java.math.BigDecimal(score))
                .setParameter(5, new java.math.BigDecimal(hitRate))
                .setParameter(6, new java.math.BigDecimal(avgError))
                .setParameter(7, doneCount)
                .setParameter(8, rank)
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
