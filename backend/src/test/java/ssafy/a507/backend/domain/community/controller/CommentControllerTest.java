package ssafy.a507.backend.domain.community.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

/**
 * ANT-COMMUNITY-03 — 댓글 작성·조회, 글·댓글 좋아요의 AC 검증.
 *
 * <p>PostControllerTest 와 같은 전제다: SecurityConfig 가 없어 기본 체인이 살아 있으므로
 * 요청마다 user(...)·csrf() 를 붙이고, 인증 주체의 이름이 곧 users.id 다. Redis 는 띄우지
 * 않는다 — 이 스토리는 멱등성 저장소를 쓰지 않지만 컨텍스트에 빈이 필요해 목으로 채운다.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class CommentControllerTest {

    @Autowired
    MockMvc mockMvc;

    @Autowired
    EntityManager em;

    @MockitoBean
    StringRedisTemplate redis;

    private Long authorId;
    private Long viewerId;
    private Long postId;

    @BeforeEach
    void setUp() {
        authorId = insertUser("작성자");
        viewerId = insertUser("열람자");
        postId = insertFeedPost(authorId, "댓글 달릴 글", "VISIBLE");
        flush();
    }

    // ── POST /posts/{postId}/comments ───────────────────────

    @Test
    @DisplayName("댓글을 쓰면 201 과 id 를 받는다")
    void 댓글_작성_성공() throws Exception {
        mockMvc.perform(post(commentsUrl(postId))
                        .with(user(String.valueOf(viewerId))).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{ \"body\": \"첫 댓글\" }"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").exists());

        assertCommentCount("첫 댓글", 1);
    }

    @Test
    @DisplayName("댓글이 500자를 넘으면 400 INVALID_REQUEST")
    void 댓글_상한_초과는_400() throws Exception {
        mockMvc.perform(post(commentsUrl(postId))
                        .with(user(String.valueOf(viewerId))).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{ \"body\": \"" + "가".repeat(501) + "\" }"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"))
                .andExpect(jsonPath("$.field").value("body"));
    }

    @Test
    @DisplayName("댓글이 비면 400 INVALID_REQUEST")
    void 댓글_누락은_400() throws Exception {
        mockMvc.perform(post(commentsUrl(postId))
                        .with(user(String.valueOf(viewerId))).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{ \"body\": \"   \" }"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"))
                .andExpect(jsonPath("$.field").value("body"));
    }

    @Test
    @DisplayName("로그인하지 않으면 401")
    void 미인증_댓글은_401() throws Exception {
        mockMvc.perform(post(commentsUrl(postId)).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{ \"body\": \"몰래 쓴 댓글\" }"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("없는 글에 댓글을 쓰면 404 POST_NOT_FOUND")
    void 없는_글에_댓글은_404() throws Exception {
        mockMvc.perform(post(commentsUrl(999_999L))
                        .with(user(String.valueOf(viewerId))).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{ \"body\": \"허공에 쓴 댓글\" }"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("POST_NOT_FOUND"));
    }

    @Test
    @DisplayName("BLOCKED 글에 댓글을 쓰면 404 — 가려진 글의 존재를 알려주지 않는다")
    void 블라인드_글에_댓글은_404() throws Exception {
        Long blocked = insertFeedPost(authorId, "가려진 글", "BLOCKED");
        flush();

        mockMvc.perform(post(commentsUrl(blocked))
                        .with(user(String.valueOf(viewerId))).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{ \"body\": \"가려진 글의 댓글\" }"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("POST_NOT_FOUND"));

        assertCommentCount("가려진 글의 댓글", 0);
    }

    // ── GET /posts/{postId}/comments ─────────────────────────

    @Test
    @DisplayName("목록은 오래된 순이고 BLOCKED 댓글은 빠진다")
    void 목록은_오래된_순이고_블라인드_제외() throws Exception {
        Long first = insertComment(postId, viewerId, "먼저 쓴 댓글", "VISIBLE");
        insertComment(postId, viewerId, "가려진 댓글", "BLOCKED");
        Long last = insertComment(postId, authorId, "나중 댓글", "VISIBLE");
        flush();

        mockMvc.perform(get(commentsUrl(postId)).with(user(String.valueOf(viewerId))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(2))
                .andExpect(jsonPath("$.items[0].id").value(first))
                .andExpect(jsonPath("$.items[1].id").value(last))
                .andExpect(jsonPath("$.items[0].author.nickname").value("열람자"))
                .andExpect(jsonPath("$.hasNext").value(false))
                .andExpect(jsonPath("$.nextCursor").doesNotExist());
    }

    @Test
    @DisplayName("size 보다 댓글이 많으면 nextCursor 로 다음 페이지가 이어진다")
    void 커서_페이징_경계() throws Exception {
        Long first = insertComment(postId, viewerId, "댓글 1", "VISIBLE");
        Long second = insertComment(postId, viewerId, "댓글 2", "VISIBLE");
        flush();

        mockMvc.perform(get(commentsUrl(postId)).param("size", "1")
                        .with(user(String.valueOf(viewerId))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(1))
                .andExpect(jsonPath("$.items[0].id").value(first))
                .andExpect(jsonPath("$.hasNext").value(true))
                .andExpect(jsonPath("$.nextCursor").value(first));

        mockMvc.perform(get(commentsUrl(postId)).param("size", "1")
                        .param("cursor", String.valueOf(first))
                        .with(user(String.valueOf(viewerId))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(1))
                .andExpect(jsonPath("$.items[0].id").value(second))
                .andExpect(jsonPath("$.hasNext").value(false));
    }

    @Test
    @DisplayName("댓글 목록은 좋아요 수와 내가 눌렀는지를 함께 준다")
    void 목록에_좋아요_수와_내_상태() throws Exception {
        Long liked = insertComment(postId, authorId, "내가 누른 댓글", "VISIBLE");
        Long other = insertComment(postId, authorId, "안 누른 댓글", "VISIBLE");
        insertCommentLike(liked, viewerId);
        insertCommentLike(liked, authorId);
        flush();

        mockMvc.perform(get(commentsUrl(postId)).with(user(String.valueOf(viewerId))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].id").value(liked))
                .andExpect(jsonPath("$.items[0].likeCount").value(2))
                .andExpect(jsonPath("$.items[0].liked").value(true))
                .andExpect(jsonPath("$.items[1].id").value(other))
                .andExpect(jsonPath("$.items[1].likeCount").value(0))
                .andExpect(jsonPath("$.items[1].liked").value(false));
    }

    @Test
    @DisplayName("없는 글의 댓글 목록은 404")
    void 없는_글의_목록은_404() throws Exception {
        mockMvc.perform(get(commentsUrl(999_999L)).with(user(String.valueOf(viewerId))))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("POST_NOT_FOUND"));
    }

    // ── 글 좋아요 ───────────────────────────────────────────

    @Test
    @DisplayName("글 공감은 201 과 갱신된 likeCount, 같은 사람이 다시 누르면 409 DUPLICATE_LIKE")
    void 글_공감과_중복() throws Exception {
        mockMvc.perform(post(postLikeUrl(postId))
                        .with(user(String.valueOf(viewerId))).with(csrf()))
                .andExpect(status().isCreated())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.likeCount").value(1));

        mockMvc.perform(post(postLikeUrl(postId))
                        .with(user(String.valueOf(viewerId))).with(csrf()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("DUPLICATE_LIKE"));

        assertPostLikeCount(postId, 1);
    }

    @Test
    @DisplayName("다른 사람의 공감은 서로 막지 않는다 — UQ 는 (post_id, user_id) 다")
    void 사람마다_따로_공감() throws Exception {
        mockMvc.perform(post(postLikeUrl(postId))
                        .with(user(String.valueOf(viewerId))).with(csrf()))
                .andExpect(status().isCreated());
        mockMvc.perform(post(postLikeUrl(postId))
                        .with(user(String.valueOf(authorId))).with(csrf()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.likeCount").value(2));

        assertPostLikeCount(postId, 2);
    }

    @Test
    @DisplayName("공감 취소는 204, 없던 공감을 취소해도 204 다(멱등)")
    void 글_공감_취소는_멱등() throws Exception {
        mockMvc.perform(post(postLikeUrl(postId))
                        .with(user(String.valueOf(viewerId))).with(csrf()))
                .andExpect(status().isCreated());

        mockMvc.perform(delete(postLikeUrl(postId))
                        .with(user(String.valueOf(viewerId))).with(csrf()))
                .andExpect(status().isNoContent());
        mockMvc.perform(delete(postLikeUrl(postId))
                        .with(user(String.valueOf(viewerId))).with(csrf()))
                .andExpect(status().isNoContent());

        assertPostLikeCount(postId, 0);
    }

    @Test
    @DisplayName("취소한 뒤에는 다시 공감할 수 있다")
    void 취소_후_재공감() throws Exception {
        mockMvc.perform(post(postLikeUrl(postId))
                        .with(user(String.valueOf(viewerId))).with(csrf()))
                .andExpect(status().isCreated());
        mockMvc.perform(delete(postLikeUrl(postId))
                        .with(user(String.valueOf(viewerId))).with(csrf()))
                .andExpect(status().isNoContent());
        mockMvc.perform(post(postLikeUrl(postId))
                        .with(user(String.valueOf(viewerId))).with(csrf()))
                .andExpect(status().isCreated());

        assertPostLikeCount(postId, 1);
    }

    @Test
    @DisplayName("BLOCKED 글에는 공감할 수 없다 — 404")
    void 블라인드_글_공감은_404() throws Exception {
        Long blocked = insertFeedPost(authorId, "가려진 글", "BLOCKED");
        flush();

        mockMvc.perform(post(postLikeUrl(blocked))
                        .with(user(String.valueOf(viewerId))).with(csrf()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("POST_NOT_FOUND"));
    }

    @Test
    @DisplayName("공감은 로그인이 필요하다 — 401")
    void 미인증_공감은_401() throws Exception {
        mockMvc.perform(post(postLikeUrl(postId)).with(csrf()))
                .andExpect(status().isUnauthorized());
    }

    // ── 댓글 좋아요 ─────────────────────────────────────────

    @Test
    @DisplayName("댓글 좋아요는 201, 중복은 409 DUPLICATE_LIKE")
    void 댓글_좋아요와_중복() throws Exception {
        Long commentId = insertComment(postId, authorId, "좋아요 대상 댓글", "VISIBLE");
        flush();

        mockMvc.perform(post(commentLikeUrl(commentId))
                        .with(user(String.valueOf(viewerId))).with(csrf()))
                .andExpect(status().isCreated())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.likeCount").value(1));

        mockMvc.perform(post(commentLikeUrl(commentId))
                        .with(user(String.valueOf(viewerId))).with(csrf()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("DUPLICATE_LIKE"));

        assertCommentLikeCount(commentId, 1);
    }

    @Test
    @DisplayName("댓글 좋아요 취소는 204, 없던 것도 204")
    void 댓글_좋아요_취소는_멱등() throws Exception {
        Long commentId = insertComment(postId, authorId, "취소 대상 댓글", "VISIBLE");
        flush();

        mockMvc.perform(delete(commentLikeUrl(commentId))
                        .with(user(String.valueOf(viewerId))).with(csrf()))
                .andExpect(status().isNoContent());

        mockMvc.perform(post(commentLikeUrl(commentId))
                        .with(user(String.valueOf(viewerId))).with(csrf()))
                .andExpect(status().isCreated());
        mockMvc.perform(delete(commentLikeUrl(commentId))
                        .with(user(String.valueOf(viewerId))).with(csrf()))
                .andExpect(status().isNoContent());

        assertCommentLikeCount(commentId, 0);
    }

    @Test
    @DisplayName("없는 댓글에 좋아요는 404 COMMENT_NOT_FOUND")
    void 없는_댓글_좋아요는_404() throws Exception {
        mockMvc.perform(post(commentLikeUrl(999_999L))
                        .with(user(String.valueOf(viewerId))).with(csrf()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("COMMENT_NOT_FOUND"));
    }

    @Test
    @DisplayName("BLOCKED 댓글에 좋아요는 404 — 가려진 댓글의 존재를 알려주지 않는다")
    void 블라인드_댓글_좋아요는_404() throws Exception {
        Long blocked = insertComment(postId, authorId, "가려진 댓글", "BLOCKED");
        flush();

        mockMvc.perform(post(commentLikeUrl(blocked))
                        .with(user(String.valueOf(viewerId))).with(csrf()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("COMMENT_NOT_FOUND"));
    }

    // ── 픽스처 ──────────────────────────────────────────────
    // User 는 다른 담당 엔티티라 네이티브 INSERT 로 넣는다(PostControllerTest 와 같은 방식).

    private String commentsUrl(Long postId) {
        return "/api/v1/posts/" + postId + "/comments";
    }

    private String postLikeUrl(Long postId) {
        return "/api/v1/posts/" + postId + "/like";
    }

    private String commentLikeUrl(Long commentId) {
        return "/api/v1/comments/" + commentId + "/like";
    }

    private Long insertUser(String nickname) {
        em.createNativeQuery("""
                INSERT INTO users (nickname, role, status, created_at, updated_at)
                VALUES (?, 'USER', 'ACTIVE', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                """).setParameter(1, nickname).executeUpdate();
        return ((Number) em.createNativeQuery("SELECT id FROM users WHERE nickname = ?")
                .setParameter(1, nickname).getSingleResult()).longValue();
    }

    private Long insertFeedPost(Long userId, String body, String status) {
        em.createNativeQuery("""
                INSERT INTO feed_posts (user_id, body, status, created_at)
                VALUES (?, ?, ?, CURRENT_TIMESTAMP)
                """).setParameter(1, userId).setParameter(2, body)
                .setParameter(3, status).executeUpdate();
        return ((Number) em.createNativeQuery("SELECT id FROM feed_posts WHERE body = ?")
                .setParameter(1, body).getSingleResult()).longValue();
    }

    private Long insertComment(Long postId, Long userId, String body, String status) {
        em.createNativeQuery("""
                INSERT INTO post_comments (post_id, user_id, body, status, created_at)
                VALUES (?, ?, ?, ?, CURRENT_TIMESTAMP)
                """).setParameter(1, postId).setParameter(2, userId)
                .setParameter(3, body).setParameter(4, status).executeUpdate();
        return ((Number) em.createNativeQuery("SELECT id FROM post_comments WHERE body = ?")
                .setParameter(1, body).getSingleResult()).longValue();
    }

    private void insertCommentLike(Long commentId, Long userId) {
        em.createNativeQuery("""
                INSERT INTO comment_likes (comment_id, user_id, created_at)
                VALUES (?, ?, CURRENT_TIMESTAMP)
                """).setParameter(1, commentId).setParameter(2, userId).executeUpdate();
    }

    private void assertCommentCount(String body, int expected) {
        flush();
        Number count = (Number) em.createNativeQuery(
                        "SELECT count(*) FROM post_comments WHERE body = ?")
                .setParameter(1, body).getSingleResult();
        assertThat(count.intValue()).isEqualTo(expected);
    }

    private void assertPostLikeCount(Long postId, int expected) {
        flush();
        Number count = (Number) em.createNativeQuery(
                        "SELECT count(*) FROM post_likes WHERE post_id = ?")
                .setParameter(1, postId).getSingleResult();
        assertThat(count.intValue()).isEqualTo(expected);
    }

    private void assertCommentLikeCount(Long commentId, int expected) {
        flush();
        Number count = (Number) em.createNativeQuery(
                        "SELECT count(*) FROM comment_likes WHERE comment_id = ?")
                .setParameter(1, commentId).getSingleResult();
        assertThat(count.intValue()).isEqualTo(expected);
    }

    /** MockMvc 요청이 같은 트랜잭션에 참여하므로 flush 만 하면 픽스처가 보인다. */
    private void flush() {
        em.flush();
        em.clear();
    }
}
