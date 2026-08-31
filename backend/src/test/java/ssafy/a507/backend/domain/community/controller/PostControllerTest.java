package ssafy.a507.backend.domain.community.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willAnswer;
import static org.mockito.Mockito.mock;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import jakarta.persistence.EntityManager;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

/**
 * ANT-COMMUNITY-02 — GET·POST /api/v1/posts, GET /api/v1/posts/{postId} 의 AC 검증.
 *
 * <p>SecurityConfig 가 아직 없어 기본 체인이 살아 있다. 그래서 요청마다 user(...) 로 인증
 * 주체를, csrf() 로 토큰을 붙인다. 인증 주체의 이름이 곧 users.id 다.
 *
 * <p>Redis 는 띄우지 않는다 — 이 프로젝트 테스트는 컨테이너 없이 도는 것이 전제다.
 * StringRedisTemplate 을 맵 하나로 대신해 멱등성 동작까지 검증한다.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class PostControllerTest {

    private static final String URL = "/api/v1/posts";

    @Autowired
    MockMvc mockMvc;

    @Autowired
    EntityManager em;

    @MockitoBean
    StringRedisTemplate redis;

    private final Map<String, String> redisStore = new HashMap<>();

    private Long authorId;
    private Long viewerId;

    @BeforeEach
    void setUp() {
        stubRedis();
        authorId = insertUser("작성자");
        viewerId = insertUser("열람자");
        flush();
    }

    /** Redis 대신 맵. get·set(TTL) 두 연산만 쓰므로 그 둘만 흉내낸다. */
    private void stubRedis() {
        @SuppressWarnings("unchecked")
        ValueOperations<String, String> ops = mock(ValueOperations.class);
        given(redis.opsForValue()).willReturn(ops);
        given(ops.get(anyString())).willAnswer(call -> redisStore.get(call.getArgument(0, String.class)));
        willAnswer(call -> {
                    redisStore.put(call.getArgument(0), call.getArgument(1));
                    return null;
                })
                .given(ops)
                .set(anyString(), anyString(), any(Duration.class));
    }

    // ── POST /posts ─────────────────────────────────────────

    @Test
    @DisplayName("글을 쓰면 201 과 id 를 받는다")
    void 글_작성_성공() throws Exception {
        mockMvc.perform(post(URL)
                        .with(user(String.valueOf(authorId))).with(csrf())
                        .header("Idempotency-Key", "key-1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createBody("첫 글입니다", null, null)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").exists());
    }

    @Test
    @DisplayName("본문이 5000자를 넘으면 400 INVALID_REQUEST")
    void 본문_상한_초과는_400() throws Exception {
        mockMvc.perform(post(URL)
                        .with(user(String.valueOf(authorId))).with(csrf())
                        .header("Idempotency-Key", "key-long")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createBody("가".repeat(5001), null, null)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"))
                .andExpect(jsonPath("$.field").value("body"));
    }

    @Test
    @DisplayName("본문이 비면 400 INVALID_REQUEST")
    void 본문_누락은_400() throws Exception {
        mockMvc.perform(post(URL)
                        .with(user(String.valueOf(authorId))).with(csrf())
                        .header("Idempotency-Key", "key-blank")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{ \"body\": \"  \" }"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"))
                .andExpect(jsonPath("$.field").value("body"));
    }

    @Test
    @DisplayName("로그인하지 않으면 401 — 기본 Security 체인이 막는다")
    void 미인증은_401() throws Exception {
        mockMvc.perform(post(URL).with(csrf())
                        .header("Idempotency-Key", "key-anon")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createBody("몰래 쓴 글", null, null)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("없는 리포트를 첨부하면 404 REPORT_NOT_FOUND")
    void 없는_리포트_첨부는_404() throws Exception {
        mockMvc.perform(post(URL)
                        .with(user(String.valueOf(authorId))).with(csrf())
                        .header("Idempotency-Key", "key-noreport")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createBody("리포스팅", 999_999L, null)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("REPORT_NOT_FOUND"))
                .andExpect(jsonPath("$.field").value("reportId"));
    }

    // ── 멱등성 ──────────────────────────────────────────────

    @Test
    @DisplayName("Idempotency-Key 가 없으면 400 IDEMPOTENCY_KEY_REQUIRED")
    void 멱등키_누락은_400() throws Exception {
        mockMvc.perform(post(URL)
                        .with(user(String.valueOf(authorId))).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createBody("키 없는 글", null, null)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("IDEMPOTENCY_KEY_REQUIRED"));
    }

    @Test
    @DisplayName("같은 키로 같은 본문을 다시 보내면 같은 id 를 받고 글은 한 건만 남는다")
    void 멱등키_재요청은_같은_응답() throws Exception {
        String body = createBody("한 번만 저장돼야 한다", null, null);

        String first = mockMvc.perform(post(URL)
                        .with(user(String.valueOf(authorId))).with(csrf())
                        .header("Idempotency-Key", "key-retry")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        mockMvc.perform(post(URL)
                        .with(user(String.valueOf(authorId))).with(csrf())
                        .header("Idempotency-Key", "key-retry")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated())
                .andExpect(content().string(first));

        assertPostCount("한 번만 저장돼야 한다", 1);
    }

    @Test
    @DisplayName("같은 키로 다른 본문을 보내면 409 IDEMPOTENCY_KEY_REUSED")
    void 멱등키_재사용은_409() throws Exception {
        mockMvc.perform(post(URL)
                        .with(user(String.valueOf(authorId))).with(csrf())
                        .header("Idempotency-Key", "key-reuse")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createBody("처음 본문", null, null)))
                .andExpect(status().isCreated());

        mockMvc.perform(post(URL)
                        .with(user(String.valueOf(authorId))).with(csrf())
                        .header("Idempotency-Key", "key-reuse")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createBody("다른 본문", null, null)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("IDEMPOTENCY_KEY_REUSED"));

        assertPostCount("다른 본문", 0);
    }

    @Test
    @DisplayName("다른 사용자가 같은 키를 써도 서로 간섭하지 않는다")
    void 멱등키는_사용자별로_갈린다() throws Exception {
        String body = createBody("공용 키 충돌 확인", null, null);

        mockMvc.perform(post(URL)
                        .with(user(String.valueOf(authorId))).with(csrf())
                        .header("Idempotency-Key", "shared-key")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated());

        mockMvc.perform(post(URL)
                        .with(user(String.valueOf(viewerId))).with(csrf())
                        .header("Idempotency-Key", "shared-key")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated());

        assertPostCount("공용 키 충돌 확인", 2);
    }

    // ── GET /posts ──────────────────────────────────────────

    @Test
    @DisplayName("목록은 최신순이고 BLOCKED 글은 빠진다")
    void 목록은_최신순이고_블라인드_제외() throws Exception {
        Long oldPost = insertFeedPost(authorId, "오래된 글", "VISIBLE");
        insertFeedPost(authorId, "가려진 글", "BLOCKED");
        Long newPost = insertFeedPost(authorId, "새 글", "VISIBLE");
        flush();

        mockMvc.perform(get(URL).with(user(String.valueOf(viewerId))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(2))
                .andExpect(jsonPath("$.items[0].id").value(newPost))
                .andExpect(jsonPath("$.items[1].id").value(oldPost))
                .andExpect(jsonPath("$.hasNext").value(false))
                .andExpect(jsonPath("$.nextCursor").doesNotExist());
    }

    @Test
    @DisplayName("size 보다 글이 많으면 hasNext 와 nextCursor 가 채워지고, 그 커서로 다음 페이지가 이어진다")
    void 커서_페이징_경계() throws Exception {
        Long first = insertFeedPost(authorId, "글 1", "VISIBLE");
        Long second = insertFeedPost(authorId, "글 2", "VISIBLE");
        flush();

        mockMvc.perform(get(URL).param("size", "1").with(user(String.valueOf(viewerId))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(1))
                .andExpect(jsonPath("$.items[0].id").value(second))
                .andExpect(jsonPath("$.hasNext").value(true))
                .andExpect(jsonPath("$.nextCursor").value(second));

        mockMvc.perform(get(URL).param("size", "1").param("cursor", String.valueOf(second))
                        .with(user(String.valueOf(viewerId))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(1))
                .andExpect(jsonPath("$.items[0].id").value(first))
                .andExpect(jsonPath("$.hasNext").value(false));
    }

    @Test
    @DisplayName("공감·댓글이 없는 글은 개수가 0으로 내려간다")
    void 집계가_없으면_0() throws Exception {
        insertFeedPost(authorId, "조용한 글", "VISIBLE");
        flush();

        mockMvc.perform(get(URL).with(user(String.valueOf(viewerId))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].likeCount").value(0))
                .andExpect(jsonPath("$.items[0].commentCount").value(0));
    }

    @Test
    @DisplayName("비공개 리포트를 리포스팅한 글은 미구독자에게 카드가 잠기고 제목은 보인다")
    void 비공개_리포트_카드는_미구독자에게_잠긴다() throws Exception {
        Long reportId = insertReport(authorId, "구독자 전용 리포트", false);
        insertFeedPostWithReport(authorId, "리포스팅한 글", reportId);
        flush();

        mockMvc.perform(get(URL).with(user(String.valueOf(viewerId))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].reportCard.locked").value(true))
                .andExpect(jsonPath("$.items[0].reportCard.title").value("구독자 전용 리포트"));
    }

    @Test
    @DisplayName("비공개 리포트라도 작성자 본인에게는 잠기지 않는다")
    void 비공개_리포트라도_본인은_열린다() throws Exception {
        Long reportId = insertReport(authorId, "내 리포트", false);
        insertFeedPostWithReport(authorId, "내 리포스팅", reportId);
        flush();

        mockMvc.perform(get(URL).with(user(String.valueOf(authorId))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].reportCard.locked").value(false));
    }

    @Test
    @DisplayName("공개 리포트 카드는 누구에게도 잠기지 않는다")
    void 공개_리포트_카드는_열린다() throws Exception {
        Long reportId = insertReport(authorId, "공개 리포트", true);
        insertFeedPostWithReport(authorId, "공개 리포스팅", reportId);
        flush();

        mockMvc.perform(get(URL).with(user(String.valueOf(viewerId))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].reportCard.locked").value(false));
    }

    // ── 예측 카드 잠금 ──────────────────────────────────────
    // 명세에 없는 조합을 구현에서 정한 부분이다(PredictionCardResponse 주석). 규칙이
    // 바뀌면 여기서 걸리게 두려고 네 경우를 다 덮는다.

    @Test
    @DisplayName("없는 예측을 첨부하면 404 PREDICTION_NOT_FOUND")
    void 없는_예측_첨부는_404() throws Exception {
        mockMvc.perform(post(URL)
                        .with(user(String.valueOf(authorId))).with(csrf())
                        .header("Idempotency-Key", "key-noprediction")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createBody("예측 카드", null, 999_999L)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("PREDICTION_NOT_FOUND"))
                .andExpect(jsonPath("$.field").value("predictionId"));
    }

    @Test
    @DisplayName("미판정(OPEN) 예측은 미구독자에게 잠기고 targetPrice 가 빠진다 — 종목·방향만 남는다")
    void 미판정_예측은_미구독자에게_잠긴다() throws Exception {
        insertStock("000660", "SK하이닉스");
        Long predictionId = insertPrediction(authorId, "000660", "UP", "OPEN");
        insertFeedPostWithPrediction(authorId, "미판정 예측 첨부", predictionId);
        flush();

        mockMvc.perform(get(URL).with(user(String.valueOf(viewerId))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].predictionCard.locked").value(true))
                .andExpect(jsonPath("$.items[0].predictionCard.stockCode").value("000660"))
                .andExpect(jsonPath("$.items[0].predictionCard.stockName").value("SK하이닉스"))
                .andExpect(jsonPath("$.items[0].predictionCard.direction").value("UP"))
                .andExpect(jsonPath("$.items[0].predictionCard.targetPrice").doesNotExist());
    }

    @Test
    @DisplayName("미판정 예측이라도 작성자 본인에게는 열리고 targetPrice 가 실린다")
    void 미판정_예측도_본인은_열린다() throws Exception {
        insertStock("000660", "SK하이닉스");
        Long predictionId = insertPrediction(authorId, "000660", "UP", "BASE");
        insertFeedPostWithPrediction(authorId, "내 예측 첨부", predictionId);
        flush();

        mockMvc.perform(get(URL).with(user(String.valueOf(authorId))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].predictionCard.locked").value(false))
                .andExpect(jsonPath("$.items[0].predictionCard.targetPrice").exists());
    }

    @Test
    @DisplayName("판정 완료(HIT) 예측은 미구독자에게도 열린다 — 과거 예측은 전체 공개다")
    void 판정_완료_예측은_누구에게나_열린다() throws Exception {
        insertStock("005930", "삼성전자");
        Long predictionId = insertPrediction(authorId, "005930", "DOWN", "HIT");
        insertFeedPostWithPrediction(authorId, "판정된 예측 첨부", predictionId);
        flush();

        mockMvc.perform(get(URL).with(user(String.valueOf(viewerId))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].predictionCard.locked").value(false))
                .andExpect(jsonPath("$.items[0].predictionCard.targetPrice").exists());
    }

    @Test
    @DisplayName("리포트와 예측을 함께 붙인 글은 카드 두 개가 각각 판정된다")
    void 리포트와_예측을_함께_붙일_수_있다() throws Exception {
        insertStock("000660", "SK하이닉스");
        Long reportId = insertReport(authorId, "공개 리포트", true);
        Long predictionId = insertPrediction(authorId, "000660", "UP", "OPEN");
        insertFeedPostWithBoth(authorId, "둘 다 붙인 글", reportId, predictionId);
        flush();

        mockMvc.perform(get(URL).with(user(String.valueOf(viewerId))))
                .andExpect(status().isOk())
                // 공개 리포트라 열리고, 미판정 예측이라 잠긴다 — 서로 독립적으로 판정된다
                .andExpect(jsonPath("$.items[0].reportCard.locked").value(false))
                .andExpect(jsonPath("$.items[0].predictionCard.locked").value(true));
    }

    @Test
    @DisplayName("첨부가 없는 글은 카드 키 자체가 응답에서 빠진다")
    void 첨부_없는_글은_카드가_없다() throws Exception {
        insertFeedPost(authorId, "본문만 있는 글", "VISIBLE");
        flush();

        mockMvc.perform(get(URL).with(user(String.valueOf(viewerId))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].reportCard").doesNotExist())
                .andExpect(jsonPath("$.items[0].predictionCard").doesNotExist());
    }

    // ── GET /posts/{postId} ─────────────────────────────────

    @Test
    @DisplayName("상세는 댓글 미리보기 3건까지만 주고 commentCount 는 전체를 준다")
    void 상세는_댓글_미리보기와_전체_개수() throws Exception {
        Long postId = insertFeedPost(authorId, "댓글 많은 글", "VISIBLE");
        for (int i = 1; i <= 5; i++) {
            insertPostComment(postId, viewerId, "댓글 " + i, "VISIBLE");
        }
        flush();

        mockMvc.perform(get(URL + "/" + postId).with(user(String.valueOf(viewerId))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(postId))
                .andExpect(jsonPath("$.comments.length()").value(3))
                .andExpect(jsonPath("$.comments[0].body").value("댓글 1"))
                .andExpect(jsonPath("$.commentCount").value(5));
    }

    @Test
    @DisplayName("BLOCKED 댓글은 미리보기와 개수 양쪽에서 빠진다")
    void 블라인드_댓글은_개수에도_안_센다() throws Exception {
        Long postId = insertFeedPost(authorId, "댓글 하나 가려진 글", "VISIBLE");
        insertPostComment(postId, viewerId, "보이는 댓글", "VISIBLE");
        insertPostComment(postId, viewerId, "가려진 댓글", "BLOCKED");
        flush();

        mockMvc.perform(get(URL + "/" + postId).with(user(String.valueOf(viewerId))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.comments.length()").value(1))
                .andExpect(jsonPath("$.commentCount").value(1));
    }

    @Test
    @DisplayName("BLOCKED 글의 상세는 404 — 403 은 가려진 글의 존재를 알려준다")
    void 블라인드_글_상세는_404() throws Exception {
        Long postId = insertFeedPost(authorId, "가려진 글", "BLOCKED");
        flush();

        mockMvc.perform(get(URL + "/" + postId).with(user(String.valueOf(viewerId))))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("POST_NOT_FOUND"));
    }

    @Test
    @DisplayName("없는 글의 상세는 404 POST_NOT_FOUND")
    void 없는_글_상세는_404() throws Exception {
        mockMvc.perform(get(URL + "/999999").with(user(String.valueOf(viewerId))))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("POST_NOT_FOUND"));
    }

    // ── 픽스처 ──────────────────────────────────────────────
    // User·Report 는 다른 담당 엔티티라 팩터리를 추가하지 않고 네이티브 INSERT 로 넣는다.

    private Long insertUser(String nickname) {
        em.createNativeQuery("""
                INSERT INTO users (nickname, role, status, created_at, updated_at)
                VALUES (?, 'USER', 'ACTIVE', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                """).setParameter(1, nickname).executeUpdate();
        return ((Number) em.createNativeQuery("SELECT id FROM users WHERE nickname = ?")
                .setParameter(1, nickname).getSingleResult()).longValue();
    }

    private Long insertReport(Long userId, String title, boolean isPublic) {
        em.createNativeQuery("""
                INSERT INTO reports (user_id, title, body, is_public, created_at)
                VALUES (?, ?, '본문', ?, CURRENT_TIMESTAMP)
                """).setParameter(1, userId).setParameter(2, title)
                .setParameter(3, isPublic).executeUpdate();
        return ((Number) em.createNativeQuery("SELECT id FROM reports WHERE title = ?")
                .setParameter(1, title).getSingleResult()).longValue();
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

    private void insertStock(String code, String name) {
        em.createNativeQuery("""
                INSERT INTO stocks (code, name, listed) VALUES (?, ?, true)
                """).setParameter(1, code).setParameter(2, name).executeUpdate();
    }

    /** 예측은 ANT-PRED 담당 엔티티다. NOT NULL 컬럼만 채운 최소 행을 넣는다. */
    private Long insertPrediction(Long userId, String stockCode, String direction, String status) {
        em.createNativeQuery("""
                INSERT INTO predictions
                  (user_id, track, stock_code, direction, target_price, horizon, status,
                   created_at, updated_at)
                VALUES (?, 'REAL', ?, ?, 240000.00, 30, ?, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                """).setParameter(1, userId).setParameter(2, stockCode)
                .setParameter(3, direction).setParameter(4, status).executeUpdate();
        return ((Number) em.createNativeQuery("""
                SELECT id FROM predictions WHERE user_id = ? AND status = ?
                """).setParameter(1, userId).setParameter(2, status).getSingleResult()).longValue();
    }

    private void insertFeedPostWithPrediction(Long userId, String body, Long predictionId) {
        em.createNativeQuery("""
                INSERT INTO feed_posts (user_id, body, prediction_id, status, created_at)
                VALUES (?, ?, ?, 'VISIBLE', CURRENT_TIMESTAMP)
                """).setParameter(1, userId).setParameter(2, body)
                .setParameter(3, predictionId).executeUpdate();
    }

    private void insertFeedPostWithBoth(Long userId, String body, Long reportId, Long predictionId) {
        em.createNativeQuery("""
                INSERT INTO feed_posts (user_id, body, report_id, prediction_id, status, created_at)
                VALUES (?, ?, ?, ?, 'VISIBLE', CURRENT_TIMESTAMP)
                """).setParameter(1, userId).setParameter(2, body)
                .setParameter(3, reportId).setParameter(4, predictionId).executeUpdate();
    }

    private void insertFeedPostWithReport(Long userId, String body, Long reportId) {
        em.createNativeQuery("""
                INSERT INTO feed_posts (user_id, body, report_id, status, created_at)
                VALUES (?, ?, ?, 'VISIBLE', CURRENT_TIMESTAMP)
                """).setParameter(1, userId).setParameter(2, body)
                .setParameter(3, reportId).executeUpdate();
    }

    private void insertPostComment(Long postId, Long userId, String body, String status) {
        em.createNativeQuery("""
                INSERT INTO post_comments (post_id, user_id, body, status, created_at)
                VALUES (?, ?, ?, ?, CURRENT_TIMESTAMP)
                """).setParameter(1, postId).setParameter(2, userId)
                .setParameter(3, body).setParameter(4, status).executeUpdate();
    }

    private void assertPostCount(String body, int expected) {
        flush();
        Number count = (Number) em.createNativeQuery(
                        "SELECT count(*) FROM feed_posts WHERE body = ?")
                .setParameter(1, body).getSingleResult();
        assertThat(count.intValue()).isEqualTo(expected);
    }

    /** MockMvc 요청이 같은 트랜잭션에 참여하므로 flush 만 하면 픽스처가 보인다. */
    private void flush() {
        em.flush();
        em.clear();
    }

    private String createBody(String body, Long reportId, Long predictionId) {
        return """
                { "body": "%s", "reportId": %s, "predictionId": %s }
                """.formatted(body, String.valueOf(reportId), String.valueOf(predictionId));
    }
}
