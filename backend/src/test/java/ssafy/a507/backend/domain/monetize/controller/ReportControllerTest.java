package ssafy.a507.backend.domain.monetize.controller;

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
 * ANT-COMMUNITY-01 — 리포트 발행·열람의 AC 검증.
 *
 * <p>요청마다 {@code user(...)} 로 인증 주체를 붙인다. 주체의 이름이 곧 users.id 다
 * ({@code SecurityContextCurrentUserProvider}).
 *
 * <p>Redis 는 띄우지 않는다 — 이 프로젝트 테스트는 컨테이너 없이 도는 것이 전제다.
 * StringRedisTemplate 을 맵 하나로 대신해 멱등성 동작까지 검증한다.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class ReportControllerTest {

    private static final String URL = "/api/v1/reports";

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
        authorId = insertUser("발행자");
        viewerId = insertUser("열람자");
        flush();
    }

    /** Redis 대신 맵. get·set(TTL) 두 연산만 쓰므로 그 둘만 흉내낸다. */
    private void stubRedis() {
        @SuppressWarnings("unchecked")
        ValueOperations<String, String> ops = mock(ValueOperations.class);
        given(redis.opsForValue()).willReturn(ops);
        given(ops.get(anyString()))
                .willAnswer(call -> redisStore.get(call.getArgument(0, String.class)));
        willAnswer(call -> {
                    redisStore.put(call.getArgument(0), call.getArgument(1));
                    return null;
                })
                .given(ops)
                .set(anyString(), anyString(), any(Duration.class));
    }

    // ── POST /reports ───────────────────────────────────────

    @Test
    @DisplayName("리포트를 발행하면 201 과 id 를 받는다")
    void 발행_성공() throws Exception {
        mockMvc.perform(post(URL)
                        .with(user(String.valueOf(authorId)))
                        .with(csrf())
                        .header("Idempotency-Key", "key-1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"title":"반도체 리포트","body":"본문","visibility":true}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").isNumber());
    }

    @Test
    @DisplayName("같은 Idempotency-Key 재요청은 리포트를 하나만 만든다")
    void 발행_멱등() throws Exception {
        String body = """
                {"title":"멱등 리포트","body":"본문","visibility":true}
                """;

        String first = mockMvc.perform(post(URL)
                        .with(user(String.valueOf(authorId)))
                        .with(csrf())
                        .header("Idempotency-Key", "key-same")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString();

        String second = mockMvc.perform(post(URL)
                        .with(user(String.valueOf(authorId)))
                        .with(csrf())
                        .header("Idempotency-Key", "key-same")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString();

        assertThat(second).isEqualTo(first);
        assertThat(countReports("멱등 리포트")).isEqualTo(1);
    }

    @Test
    @DisplayName("Idempotency-Key 가 없으면 400 IDEMPOTENCY_KEY_REQUIRED")
    void 발행_키_누락() throws Exception {
        mockMvc.perform(post(URL)
                        .with(user(String.valueOf(authorId)))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"title":"제목","body":"본문","visibility":true}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("IDEMPOTENCY_KEY_REQUIRED"));
    }

    @Test
    @DisplayName("visibility 를 빼면 400 — 공개 여부를 기본값으로 정하지 않는다")
    void 발행_공개여부_누락() throws Exception {
        mockMvc.perform(post(URL)
                        .with(user(String.valueOf(authorId)))
                        .with(csrf())
                        .header("Idempotency-Key", "key-2")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"title":"제목","body":"본문"}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"))
                .andExpect(jsonPath("$.field").value("visibility"));
    }

    @Test
    @DisplayName("제목이 100자를 넘으면 400")
    void 발행_제목_초과() throws Exception {
        mockMvc.perform(post(URL)
                        .with(user(String.valueOf(authorId)))
                        .with(csrf())
                        .header("Idempotency-Key", "key-3")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"" + "가".repeat(101)
                                + "\",\"body\":\"본문\",\"visibility\":true}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"))
                .andExpect(jsonPath("$.field").value("title"));
    }

    @Test
    @DisplayName("발행하면 ACTIVE 구독자에게 REPORT_PUBLISHED 알림이 쌓인다")
    void 발행_알림() throws Exception {
        insertSubscription(viewerId, authorId, "ACTIVE");
        flush();

        mockMvc.perform(post(URL)
                        .with(user(String.valueOf(authorId)))
                        .with(csrf())
                        .header("Idempotency-Key", "key-noti")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"title":"알림 리포트","body":"본문","visibility":false}
                                """))
                .andExpect(status().isCreated());
        flush();

        assertThat(countNotifications(viewerId, "REPORT_PUBLISHED")).isEqualTo(1);
    }

    @Test
    @DisplayName("PENDING 구독자에게는 발행 알림을 보내지 않는다 — 아직 열람 권한이 아니다")
    void 발행_알림_PENDING_제외() throws Exception {
        insertSubscription(viewerId, authorId, "PENDING");
        flush();

        mockMvc.perform(post(URL)
                        .with(user(String.valueOf(authorId)))
                        .with(csrf())
                        .header("Idempotency-Key", "key-noti-2")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"title":"알림 리포트2","body":"본문","visibility":false}
                                """))
                .andExpect(status().isCreated());
        flush();

        assertThat(countNotifications(viewerId, "REPORT_PUBLISHED")).isZero();
    }

    // ── GET /reports (통합 피드) ──────────────────────────────

    @Test
    @DisplayName("통합 피드는 최신순이고 제목·작성자·열람 수를 공개한다")
    void 피드_최신순() throws Exception {
        insertReport(authorId, "먼저 쓴 것", false, 0);
        insertReport(authorId, "나중에 쓴 것", true, 7);
        flush();

        mockMvc.perform(get(URL).with(user(String.valueOf(viewerId))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].title").value("나중에 쓴 것"))
                .andExpect(jsonPath("$.items[0].author.nickname").value("발행자"))
                .andExpect(jsonPath("$.items[0].viewCount").value(7))
                .andExpect(jsonPath("$.items[1].title").value("먼저 쓴 것"))
                .andExpect(jsonPath("$.hasNext").value(false));
    }

    @Test
    @DisplayName("비공개 리포트는 미구독자에게 locked=true 로 내려간다 — 제목은 그대로 공개")
    void 피드_잠금() throws Exception {
        insertReport(authorId, "구독자 전용", false, 0);
        flush();

        mockMvc.perform(get(URL).with(user(String.valueOf(viewerId))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].locked").value(true))
                .andExpect(jsonPath("$.items[0].title").value("구독자 전용"));
    }

    @Test
    @DisplayName("ACTIVE 구독자에게는 비공개 리포트도 잠기지 않는다")
    void 피드_구독자는_열린다() throws Exception {
        insertReport(authorId, "구독자 전용", false, 0);
        insertSubscription(viewerId, authorId, "ACTIVE");
        flush();

        mockMvc.perform(get(URL).with(user(String.valueOf(viewerId))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].locked").value(false));
    }

    @Test
    @DisplayName("본인 리포트는 비공개여도 잠기지 않는다")
    void 피드_본인은_열린다() throws Exception {
        insertReport(authorId, "내 비공개", false, 0);
        flush();

        mockMvc.perform(get(URL).with(user(String.valueOf(authorId))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].locked").value(false));
    }

    @Test
    @DisplayName("scope=SUBSCRIBED 는 구독 중인 채널의 리포트만 돌려준다")
    void 피드_구독_탭() throws Exception {
        Long other = insertUser("남");
        insertReport(authorId, "구독한 채널 글", true, 0);
        insertReport(other, "안 구독한 채널 글", true, 0);
        insertSubscription(viewerId, authorId, "ACTIVE");
        flush();

        mockMvc.perform(get(URL).param("scope", "SUBSCRIBED").with(user(String.valueOf(viewerId))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(1))
                .andExpect(jsonPath("$.items[0].title").value("구독한 채널 글"));
    }

    @Test
    @DisplayName("sort=POPULAR 는 열람 수 내림차순이고 커서가 (viewCount:id) 복합값이다")
    void 피드_인기순_커서() throws Exception {
        insertReport(authorId, "1등", true, 30);
        insertReport(authorId, "2등", true, 20);
        insertReport(authorId, "3등", true, 10);
        flush();

        String cursor = mockMvc.perform(get(URL)
                        .param("sort", "POPULAR")
                        .param("size", "2")
                        .with(user(String.valueOf(viewerId))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].title").value("1등"))
                .andExpect(jsonPath("$.items[1].title").value("2등"))
                .andExpect(jsonPath("$.hasNext").value(true))
                .andReturn()
                .getResponse()
                .getContentAsString()
                .replaceAll(".*\"nextCursor\":\"([^\"]+)\".*", "$1");

        assertThat(cursor).contains(":");

        mockMvc.perform(get(URL)
                        .param("sort", "POPULAR")
                        .param("cursor", cursor)
                        .with(user(String.valueOf(viewerId))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(1))
                .andExpect(jsonPath("$.items[0].title").value("3등"));
    }

    @Test
    @DisplayName("최신순 커서는 다음 페이지를 겹치지 않게 이어준다")
    void 피드_최신순_커서() throws Exception {
        insertReport(authorId, "첫째", true, 0);
        insertReport(authorId, "둘째", true, 0);
        insertReport(authorId, "셋째", true, 0);
        flush();

        String cursor = mockMvc.perform(get(URL)
                        .param("size", "2")
                        .with(user(String.valueOf(viewerId))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].title").value("셋째"))
                .andExpect(jsonPath("$.hasNext").value(true))
                .andReturn()
                .getResponse()
                .getContentAsString()
                .replaceAll(".*\"nextCursor\":\"([^\"]+)\".*", "$1");

        mockMvc.perform(get(URL).param("cursor", cursor).with(user(String.valueOf(viewerId))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(1))
                .andExpect(jsonPath("$.items[0].title").value("첫째"));
    }

    @Test
    @DisplayName("sector 필터는 아직 구현되지 않아 400 으로 거절한다 — 조용히 무시하지 않는다")
    void 피드_sector_거절() throws Exception {
        mockMvc.perform(get(URL).param("sector", "반도체").with(user(String.valueOf(viewerId))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"))
                .andExpect(jsonPath("$.field").value("sector"));
    }

    @Test
    @DisplayName("어휘에 없는 sort 는 500 이 아니라 400 이다")
    void 피드_잘못된_sort() throws Exception {
        mockMvc.perform(get(URL).param("sort", "HOT").with(user(String.valueOf(viewerId))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"))
                .andExpect(jsonPath("$.field").value("sort"));
    }

    @Test
    @DisplayName("숫자가 아닌 커서는 400 이다")
    void 피드_잘못된_커서() throws Exception {
        mockMvc.perform(get(URL).param("cursor", "abc").with(user(String.valueOf(viewerId))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"))
                .andExpect(jsonPath("$.field").value("cursor"));
    }

    // ── GET /reports/{id} ───────────────────────────────────

    @Test
    @DisplayName("공개 리포트는 전문이 내려간다")
    void 열람_전문() throws Exception {
        Long reportId = insertReportWithBody(authorId, "공개 리포트", true, "1줄\n2줄\n3줄\n4줄");
        flush();

        mockMvc.perform(get(URL + "/" + reportId).with(user(String.valueOf(viewerId))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.locked").value(false))
                .andExpect(jsonPath("$.body").value("1줄\n2줄\n3줄\n4줄"))
                .andExpect(jsonPath("$.preview").doesNotExist());
    }

    @Test
    @DisplayName("비공개 리포트를 미구독자가 열면 앞 3줄만 내려간다 — AC 핵심")
    void 열람_미리보기_잠금() throws Exception {
        Long reportId = insertReportWithBody(authorId, "구독자 전용", false, "1줄\n2줄\n3줄\n4줄\n5줄");
        flush();

        mockMvc.perform(get(URL + "/" + reportId).with(user(String.valueOf(viewerId))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.locked").value(true))
                .andExpect(jsonPath("$.preview").value("1줄\n2줄\n3줄"))
                .andExpect(jsonPath("$.body").doesNotExist())
                // 제목은 항상 공개다 — 구독 유인이다
                .andExpect(jsonPath("$.title").value("구독자 전용"));
    }

    @Test
    @DisplayName("개행 없는 본문의 미리보기는 300자에서 끊는다 — 1줄=전문이 되는 구멍을 막는다")
    void 열람_미리보기_글자수_상한() throws Exception {
        Long reportId = insertReportWithBody(authorId, "한 줄 리포트", false, "가".repeat(1000));
        flush();

        mockMvc.perform(get(URL + "/" + reportId).with(user(String.valueOf(viewerId))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.locked").value(true))
                // JsonPath 의 length() 는 문자열에 쓰면 null 이라 값 자체로 단정한다
                .andExpect(jsonPath("$.preview").value("가".repeat(300)));
    }

    @Test
    @DisplayName("ACTIVE 구독자는 비공개 리포트 전문을 본다")
    void 열람_구독자_전문() throws Exception {
        Long reportId = insertReportWithBody(authorId, "구독자 전용", false, "1줄\n2줄\n3줄\n4줄");
        insertSubscription(viewerId, authorId, "ACTIVE");
        flush();

        mockMvc.perform(get(URL + "/" + reportId).with(user(String.valueOf(viewerId))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.locked").value(false))
                .andExpect(jsonPath("$.body").value("1줄\n2줄\n3줄\n4줄"));
    }

    @Test
    @DisplayName("EXPIRED 구독은 열람 권한이 아니다")
    void 열람_만료구독_잠금() throws Exception {
        Long reportId = insertReportWithBody(authorId, "구독자 전용", false, "1줄\n2줄\n3줄\n4줄");
        insertSubscription(viewerId, authorId, "EXPIRED");
        flush();

        mockMvc.perform(get(URL + "/" + reportId).with(user(String.valueOf(viewerId))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.locked").value(true));
    }

    @Test
    @DisplayName("열람하면 viewCount 가 1 늘고 응답에도 반영된다")
    void 열람_카운트_증가() throws Exception {
        Long reportId = insertReport(authorId, "카운트", true, 5);
        flush();

        mockMvc.perform(get(URL + "/" + reportId).with(user(String.valueOf(viewerId))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.viewCount").value(6));
        flush();

        assertThat(viewCountOf(reportId)).isEqualTo(6);
    }

    @Test
    @DisplayName("작성자 본인의 열람은 세지 않는다 — 인기순 정렬 키를 자기 새로고침으로 올릴 수 없다")
    void 열람_본인은_카운트_제외() throws Exception {
        Long reportId = insertReport(authorId, "카운트", true, 5);
        flush();

        mockMvc.perform(get(URL + "/" + reportId).with(user(String.valueOf(authorId))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.viewCount").value(5));
        flush();

        assertThat(viewCountOf(reportId)).isEqualTo(5);
    }

    @Test
    @DisplayName("없는 리포트는 404 REPORT_NOT_FOUND")
    void 열람_없음() throws Exception {
        mockMvc.perform(get(URL + "/999999").with(user(String.valueOf(viewerId))))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("REPORT_NOT_FOUND"));
    }

    // ── GET /channels/{userId}/reports ───────────────────────

    @Test
    @DisplayName("채널 목록은 미구독자에게도 제목·발행일·공개 여부를 준다")
    void 채널목록_미구독자_공개() throws Exception {
        insertReport(authorId, "비공개 글", false, 3);
        flush();

        mockMvc.perform(get("/api/v1/channels/" + authorId + "/reports")
                        .with(user(String.valueOf(viewerId))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].title").value("비공개 글"))
                .andExpect(jsonPath("$.items[0].visibility").value(false))
                .andExpect(jsonPath("$.items[0].locked").value(true))
                .andExpect(jsonPath("$.items[0].publishedAt").exists());
    }

    @Test
    @DisplayName("채널 목록은 그 채널의 리포트만 담는다")
    void 채널목록_채널_한정() throws Exception {
        Long other = insertUser("다른 발행자");
        insertReport(authorId, "내 채널 글", true, 0);
        insertReport(other, "남의 채널 글", true, 0);
        flush();

        mockMvc.perform(get("/api/v1/channels/" + authorId + "/reports")
                        .with(user(String.valueOf(viewerId))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(1))
                .andExpect(jsonPath("$.items[0].title").value("내 채널 글"));
    }

    @Test
    @DisplayName("구독자에게는 visibility=false 라도 locked=false 다 — 두 값은 다른 것을 말한다")
    void 채널목록_구독자() throws Exception {
        insertReport(authorId, "비공개 글", false, 0);
        insertSubscription(viewerId, authorId, "ACTIVE");
        flush();

        mockMvc.perform(get("/api/v1/channels/" + authorId + "/reports")
                        .with(user(String.valueOf(viewerId))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].visibility").value(false))
                .andExpect(jsonPath("$.items[0].locked").value(false));
    }

    @Test
    @DisplayName("없는 채널은 404 USER_NOT_FOUND — 빈 목록으로 속이지 않는다")
    void 채널목록_없는_유저() throws Exception {
        mockMvc.perform(get("/api/v1/channels/999999/reports")
                        .with(user(String.valueOf(viewerId))))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("USER_NOT_FOUND"));
    }

    // ── 픽스처 ──────────────────────────────────────────────

    private void flush() {
        em.flush();
        em.clear();
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

    private Long insertReport(Long userId, String title, boolean isPublic, int viewCount) {
        return insertReportWithBody(userId, title, isPublic, "본문", viewCount);
    }

    private Long insertReportWithBody(Long userId, String title, boolean isPublic, String body) {
        return insertReportWithBody(userId, title, isPublic, body, 0);
    }

    private Long insertReportWithBody(
            Long userId, String title, boolean isPublic, String body, int viewCount) {
        em.createNativeQuery("""
                        INSERT INTO reports (user_id, title, body, is_public, view_count, created_at)
                        VALUES (?, ?, ?, ?, ?, CURRENT_TIMESTAMP)
                        """)
                .setParameter(1, userId)
                .setParameter(2, title)
                .setParameter(3, body)
                .setParameter(4, isPublic)
                .setParameter(5, viewCount)
                .executeUpdate();
        return ((Number) em.createNativeQuery("SELECT id FROM reports WHERE title = ?")
                        .setParameter(1, title)
                        .getSingleResult())
                .longValue();
    }

    /** 구독은 ANT-MONETIZE 담당 엔티티다. NOT NULL 컬럼만 채운 최소 행을 넣는다. */
    private void insertSubscription(Long subscriberId, Long publisherId, String status) {
        em.createNativeQuery("""
                        INSERT INTO subscriptions
                          (subscriber_id, publisher_id, fee, status, auto_renew, created_at, updated_at)
                        VALUES (?, ?, 0, ?, false, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                        """)
                .setParameter(1, subscriberId)
                .setParameter(2, publisherId)
                .setParameter(3, status)
                .executeUpdate();
    }

    private int viewCountOf(Long reportId) {
        return ((Number) em.createNativeQuery("SELECT view_count FROM reports WHERE id = ?")
                        .setParameter(1, reportId)
                        .getSingleResult())
                .intValue();
    }

    private long countReports(String title) {
        return ((Number) em.createNativeQuery("SELECT count(*) FROM reports WHERE title = ?")
                        .setParameter(1, title)
                        .getSingleResult())
                .longValue();
    }

    private long countNotifications(Long userId, String type) {
        return ((Number) em.createNativeQuery("""
                                SELECT count(*) FROM notifications WHERE user_id = ? AND type = ?
                                """)
                        .setParameter(1, userId)
                        .setParameter(2, type)
                        .getSingleResult())
                .longValue();
    }
}
