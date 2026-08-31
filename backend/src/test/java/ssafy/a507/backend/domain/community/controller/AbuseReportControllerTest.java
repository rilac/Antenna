package ssafy.a507.backend.domain.community.controller;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import jakarta.persistence.EntityManager;
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
 * POST /api/abuse-reports — ANT-COMMUNITY-04 의 AC 검증.
 *
 * SecurityConfig 가 아직 없어 기본 체인이 살아 있다. 그래서 요청마다
 * user(...) 로 인증 주체를, csrf() 로 토큰을 붙인다. 인증 주체의 이름이
 * 곧 users.id 이며 SecurityContextCurrentUserProvider 가 그것을 읽는다.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class AbuseReportControllerTest {

    private static final String URL = "/api/abuse-reports";

    @Autowired
    MockMvc mockMvc;

    @Autowired
    EntityManager em;

    private Long reporterId;
    private Long authorId;
    private Long postId;
    private Long commentId;

    @BeforeEach
    void setUp() {
        reporterId = insertUser("신고자");
        authorId = insertUser("작성자");
        postId = insertFeedPost(authorId, "문제되는 글");
        commentId = insertPostComment(postId, authorId, "문제되는 댓글");
        flush();
    }

    @Test
    @DisplayName("남의 글을 신고하면 201 과 id 를 받는다")
    void 글_신고_성공() throws Exception {
        mockMvc.perform(post(URL)
                        .with(user(String.valueOf(reporterId))).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body("POST", postId, "SPAM", "광고만 올립니다")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").exists());
    }

    @Test
    @DisplayName("남의 댓글도 신고할 수 있다")
    void 댓글_신고_성공() throws Exception {
        mockMvc.perform(post(URL)
                        .with(user(String.valueOf(reporterId))).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body("COMMENT", commentId, "ABUSE", null)))
                .andExpect(status().isCreated());
    }

    @Test
    @DisplayName("유저 자체도 신고할 수 있다")
    void 유저_신고_성공() throws Exception {
        mockMvc.perform(post(URL)
                        .with(user(String.valueOf(reporterId))).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body("USER", authorId, "FRAUD", null)))
                .andExpect(status().isCreated());
    }

    @Test
    @DisplayName("내 글을 신고하면 400 SELF_REPORT")
    void 자기_글_신고는_거부() throws Exception {
        mockMvc.perform(post(URL)
                        .with(user(String.valueOf(authorId))).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body("POST", postId, "SPAM", null)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("SELF_REPORT"))
                .andExpect(jsonPath("$.field").value("targetId"));
    }

    @Test
    @DisplayName("나 자신을 유저 신고하면 400 SELF_REPORT")
    void 자기_자신_신고는_거부() throws Exception {
        mockMvc.perform(post(URL)
                        .with(user(String.valueOf(reporterId))).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body("USER", reporterId, "ETC", null)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("SELF_REPORT"));
    }

    @Test
    @DisplayName("같은 대상을 다시 신고하면 409 DUPLICATE_REPORT")
    void 중복_신고는_거부() throws Exception {
        mockMvc.perform(post(URL)
                        .with(user(String.valueOf(reporterId))).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body("POST", postId, "SPAM", null)))
                .andExpect(status().isCreated());

        mockMvc.perform(post(URL)
                        .with(user(String.valueOf(reporterId))).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body("POST", postId, "ABUSE", null)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("DUPLICATE_REPORT"));
    }

    @Test
    @DisplayName("없는 글을 신고하면 404 TARGET_NOT_FOUND")
    void 없는_대상_신고는_404() throws Exception {
        mockMvc.perform(post(URL)
                        .with(user(String.valueOf(reporterId))).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body("POST", 999_999L, "SPAM", null)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("TARGET_NOT_FOUND"));
    }

    @Test
    @DisplayName("상세 사유가 300자를 넘으면 400 INVALID_REQUEST")
    void 상세사유_길이_초과는_400() throws Exception {
        String tooLong = "가".repeat(301);
        mockMvc.perform(post(URL)
                        .with(user(String.valueOf(reporterId))).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body("POST", postId, "SPAM", tooLong)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"))
                .andExpect(jsonPath("$.field").value("detail"));
    }

    @Test
    @DisplayName("신고 사유가 없으면 400 INVALID_REQUEST")
    void 사유_누락은_400() throws Exception {
        String noReason = """
                { "targetType": "POST", "targetId": %d }
                """.formatted(postId);
        mockMvc.perform(post(URL)
                        .with(user(String.valueOf(reporterId))).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(noReason))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"))
                .andExpect(jsonPath("$.field").value("reason"));
    }

    @Test
    @DisplayName("enum 에 없는 targetType 이면 400 INVALID_REQUEST — code 가 반드시 실린다")
    void 잘못된_enum_은_400() throws Exception {
        mockMvc.perform(post(URL)
                        .with(user(String.valueOf(reporterId))).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body("GROUP", postId, "SPAM", null)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
    }

    @Test
    @DisplayName("깨진 JSON 이면 400 INVALID_REQUEST — 본문이 비지 않는다")
    void 깨진_JSON_은_400() throws Exception {
        mockMvc.perform(post(URL)
                        .with(user(String.valueOf(reporterId))).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{ broken"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
    }

    @Test
    @DisplayName("로그인하지 않으면 401 — 기본 Security 체인이 막는다")
    void 미인증은_401() throws Exception {
        mockMvc.perform(post(URL).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body("POST", postId, "SPAM", null)))
                .andExpect(status().isUnauthorized());
    }

    // ── 픽스처 ──────────────────────────────────────────────
    // User 는 ANT-AUTH 담당 엔티티라 생성 팩터리를 추가하지 않고 네이티브 INSERT 로 넣는다.

    private Long insertUser(String nickname) {
        em.createNativeQuery("""
                INSERT INTO users (nickname, role, status, created_at, updated_at)
                VALUES (?, 'USER', 'ACTIVE', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                """).setParameter(1, nickname).executeUpdate();
        return ((Number) em.createNativeQuery(
                        "SELECT id FROM users WHERE nickname = ?")
                .setParameter(1, nickname).getSingleResult()).longValue();
    }

    private Long insertFeedPost(Long userId, String body) {
        em.createNativeQuery("""
                INSERT INTO feed_posts (user_id, body, status, created_at)
                VALUES (?, ?, 'VISIBLE', CURRENT_TIMESTAMP)
                """).setParameter(1, userId).setParameter(2, body).executeUpdate();
        return ((Number) em.createNativeQuery(
                        "SELECT id FROM feed_posts WHERE body = ?")
                .setParameter(1, body).getSingleResult()).longValue();
    }

    private Long insertPostComment(Long postId, Long userId, String body) {
        em.createNativeQuery("""
                INSERT INTO post_comments (post_id, user_id, body, status, created_at)
                VALUES (?, ?, ?, 'VISIBLE', CURRENT_TIMESTAMP)
                """).setParameter(1, postId).setParameter(2, userId)
                .setParameter(3, body).executeUpdate();
        return ((Number) em.createNativeQuery(
                        "SELECT id FROM post_comments WHERE body = ?")
                .setParameter(1, body).getSingleResult()).longValue();
    }

    /** MockMvc 요청이 같은 트랜잭션에 참여하므로 flush 만 하면 픽스처가 보인다. */
    private void flush() {
        em.flush();
        em.clear();
    }

    private String body(String targetType, Long targetId, String reason, String detail) {
        String detailJson = detail == null ? "null" : "\"" + detail + "\"";
        return """
                { "targetType": "%s", "targetId": %d, "reason": "%s", "detail": %s }
                """.formatted(targetType, targetId, reason, detailJson);
    }
}
