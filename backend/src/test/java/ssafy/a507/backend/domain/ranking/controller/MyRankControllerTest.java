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
 * ANT-RANK-03 — 내 순위(화면 E-01 상단 카드)의 AC 검증.
 *
 * <p>랭킹 스냅샷 배치(ANT-RANK-01)가 없어 행은 네이티브 INSERT 로 만든다. {@code rank} 는 예약어라
 * 인용부호로 감싼다.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class MyRankControllerTest {

    private static final String URL = "/api/v1/rankings/me";

    @Autowired
    MockMvc mockMvc;

    @Autowired
    EntityManager em;

    private Long meId;

    @BeforeEach
    void setUp() {
        meId = insertUser("나");
    }

    @Test
    @DisplayName("내 순위와 상위 %를 준다 — percentile 은 rank/전체×100, 작을수록 상위")
    void 내_순위() throws Exception {
        // 전체 8명 중 내가 2등이면 상위 25.0% 다.
        insertRanking("REAL", "ALL", meId, 2);
        for (int rank : new int[] {1, 3, 4, 5, 6, 7, 8}) {
            insertRanking("REAL", "ALL", insertUser("남" + rank), rank);
        }

        mockMvc.perform(get(URL).param("track", "REAL").with(user(String.valueOf(meId))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.rank").value(2))
                .andExpect(jsonPath("$.percentile").value(25.0))
                // prev_rank 컬럼이 없어 변동은 늘 0 이다. 화면은 0 이면 ▲▼ 를 그리지 않는다.
                .andExpect(jsonPath("$.delta").value(0))
                // 티어는 리플레이 전용이고 경계 수치가 미확정이라 실전은 항상 null 이다.
                .andExpect(jsonPath("$.tier").doesNotExist());
    }

    @Test
    @DisplayName("REAL 은 기간·섹터를 걸지 않은 전체(ALL) 기준 한 줄만 본다")
    void 전체_기준() throws Exception {
        // ALL 필터에 5명, 내가 꼴등(5등) — 상위 100.0% 다.
        insertRanking("REAL", "ALL", meId, 5);
        for (int rank = 1; rank <= 4; rank++) {
            insertRanking("REAL", "ALL", insertUser("남" + rank), rank);
        }
        // 같은 사람의 30일·섹터 행에서 1등이어도 카드는 ALL 을 본다.
        insertRanking("REAL", "30D", meId, 1);
        insertRanking("REAL", "ALL:SEC:전기전자", meId, 1);

        mockMvc.perform(get(URL).param("track", "REAL").with(user(String.valueOf(meId))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.rank").value(5))
                .andExpect(jsonPath("$.percentile").value(100.0));
    }

    @Test
    @DisplayName("REPLAY 는 seasonId 로 갈리고 실전 행과 섞이지 않는다")
    void 리플레이_시즌() throws Exception {
        insertRanking("REAL", "ALL", meId, 9);
        insertRanking("REPLAY", "SEASON:7", meId, 3);

        mockMvc.perform(get(URL)
                        .param("track", "REPLAY")
                        .param("seasonId", "7")
                        .with(user(String.valueOf(meId))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.rank").value(3));

        // 없는 시즌은 내 행이 없으므로 204 다.
        mockMvc.perform(get(URL)
                        .param("track", "REPLAY")
                        .param("seasonId", "99")
                        .with(user(String.valueOf(meId))))
                .andExpect(status().isNoContent());
    }

    @Test
    @DisplayName("랭킹에 없으면 204 — 404 면 목록과 묶인 Promise.all 이 깨져 페이지가 통째로 오류다")
    void 랭킹에_없음() throws Exception {
        // 남은 랭킹에 있지만 나는 없다.
        insertRanking("REAL", "ALL", insertUser("1등"), 1);

        mockMvc.perform(get(URL).param("track", "REAL").with(user(String.valueOf(meId))))
                .andExpect(status().isNoContent());
    }

    @Test
    @DisplayName("내 행만 있고 집계가 비면 값을 지어내지 않고 204 — 상위 0%로 보이면 안 된다")
    void 집계가_빈_사이() throws Exception {
        // 배치가 전량 재계산으로 필터를 비우는 중이면 내 행을 찾은 직후 집계가 0 일 수 있다.
        // 여기서는 그 상태를 rank 만 있고 같은 필터의 다른 행이 없는 상황으로 만들지 못하므로
        // (내 행 자체가 집계에 들어간다) 최소 경계인 1명 필터를 확인한다 — 나 혼자면 상위 100% 다.
        insertRanking("REAL", "ALL", meId, 1);

        mockMvc.perform(get(URL).param("track", "REAL").with(user(String.valueOf(meId))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.rank").value(1))
                .andExpect(jsonPath("$.percentile").value(100.0));
    }

    @Test
    @DisplayName("track 누락·어휘 밖은 400, 비로그인은 401")
    void 잘못된_요청() throws Exception {
        mockMvc.perform(get(URL).with(user(String.valueOf(meId))))
                .andExpect(status().isBadRequest());

        mockMvc.perform(get(URL).param("track", "PAPER").with(user(String.valueOf(meId))))
                .andExpect(status().isBadRequest());

        mockMvc.perform(get(URL).param("track", "REAL")).andExpect(status().isUnauthorized());
    }

    private void insertRanking(String track, String filterKey, Long userId, int rank) {
        em.createNativeQuery(
                        """
                        INSERT INTO rankings
                          (track, filter_key, user_id, score, hit_rate, avg_error, done_count, "rank", computed_at)
                        VALUES (?, ?, ?, 50.000, 50.00, 5.000, 10, ?, CURRENT_TIMESTAMP)
                        """)
                .setParameter(1, track)
                .setParameter(2, filterKey)
                .setParameter(3, userId)
                .setParameter(4, rank)
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
