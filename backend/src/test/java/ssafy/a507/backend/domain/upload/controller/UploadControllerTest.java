package ssafy.a507.backend.domain.upload.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willAnswer;
import static org.mockito.Mockito.mock;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
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
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.request.MockMultipartHttpServletRequestBuilder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;
import ssafy.a507.backend.domain.account.entity.User;
import ssafy.a507.backend.domain.upload.entity.UploadFile;
import ssafy.a507.backend.support.TestImages;

/**
 * POST · GET /api/v1/uploads — ANT-COMMUNITY-06 의 AC 검증.
 *
 * <p>Redis 는 띄우지 않는다. StringRedisTemplate 을 맵 하나로 대신해 멱등성까지 함께 본다
 * ({@code PostControllerTest} 와 같은 방식).
 */
@SpringBootTest(properties = "app.uploads.dir=build/test-uploads")
@AutoConfigureMockMvc
@Transactional
@DisplayName("이미지 업로드 API")
class UploadControllerTest {

    private static final String URL = "/api/v1/uploads";

    @Autowired MockMvc mockMvc;
    @Autowired EntityManager em;

    @MockitoBean StringRedisTemplate redis;

    private final Map<String, String> redisStore = new HashMap<>();
    private Long userId;

    @BeforeEach
    void setUp() {
        stubRedis();
        userId = insertUser("광고주");
    }

    @Test
    @DisplayName("PNG 를 올리면 201 과 fileId · 크기를 받는다")
    void 업로드_성공() throws Exception {
        mockMvc.perform(upload(TestImages.png(400, 100), "POST", "key-1"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.fileId").isNotEmpty())
                .andExpect(jsonPath("$.mime").value("image/png"))
                .andExpect(jsonPath("$.width").value(400))
                .andExpect(jsonPath("$.height").value(100))
                .andExpect(jsonPath("$.url").isNotEmpty());
    }

    @Test
    @DisplayName("행이 실제로 INSERT 된다 — url 을 나중에 채우면 여기서 NOT NULL 에 걸린다")
    void 실제_INSERT_까지_확인() throws Exception {
        String fileId =
                jsonValue(
                        mockMvc.perform(upload(TestImages.png(400, 100), "AD", "key-flush"))
                                .andReturn(),
                        "fileId");

        // 롤백으로 끝나는 테스트는 flush 가 없으면 INSERT 자체를 보지 않는다. 여기서 강제한다.
        em.flush();
        em.clear();

        assertThat(em.find(UploadFile.class, fileId).getUrl()).isNotBlank();
    }

    @Test
    @DisplayName("올린 이미지를 url 로 다시 받을 수 있다")
    void 업로드한_이미지_조회() throws Exception {
        byte[] content = TestImages.png(400, 100);
        MvcResult created =
                mockMvc.perform(upload(content, "AD", "key-read")).andReturn();
        String fileId = jsonValue(created, "fileId");

        byte[] fetched =
                mockMvc.perform(get(URL + "/" + fileId).with(user(String.valueOf(userId))))
                        .andExpect(status().isOk())
                        .andReturn()
                        .getResponse()
                        .getContentAsByteArray();

        assertThat(fetched).isEqualTo(content);
    }

    @Test
    @DisplayName("이미지가 아니면 400 UNSUPPORTED_IMAGE_TYPE — Content-Type 을 믿지 않는다")
    void 이미지가_아니면_400() throws Exception {
        MockMultipartFile file =
                new MockMultipartFile("file", "fake.png", "image/png", "not an image".getBytes());

        mockMvc.perform(multipart(URL)
                        .file(file)
                        .param("purpose", "POST")
                        .header("Idempotency-Key", "key-fake")
                        .with(user(String.valueOf(userId))).with(csrf()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("UNSUPPORTED_IMAGE_TYPE"));
    }

    @Test
    @DisplayName("배너 비율이 어긋나면 400 INVALID_IMAGE_RATIO — 다른 용도에서는 통과한다")
    void 배너_비율_검증() throws Exception {
        byte[] square = TestImages.png(300, 300);

        mockMvc.perform(upload(square, "AD", "key-ratio"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_IMAGE_RATIO"));

        mockMvc.perform(upload(square, "REPORT", "key-ratio-ok"))
                .andExpect(status().isCreated());
    }

    @Test
    @DisplayName("purpose 가 어휘에 없으면 400 INVALID_REQUEST")
    void 잘못된_purpose() throws Exception {
        mockMvc.perform(upload(TestImages.png(400, 100), "BANNER", "key-purpose"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"))
                .andExpect(jsonPath("$.field").value("purpose"));
    }

    @Test
    @DisplayName("Idempotency-Key 가 없으면 400 IDEMPOTENCY_KEY_REQUIRED")
    void 멱등키_필수() throws Exception {
        mockMvc.perform(multipart(URL)
                        .file(new MockMultipartFile(
                                "file", "b.png", "image/png", TestImages.png(400, 100)))
                        .param("purpose", "POST")
                        .with(user(String.valueOf(userId))).with(csrf()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("IDEMPOTENCY_KEY_REQUIRED"));
    }

    @Test
    @DisplayName("같은 키로 같은 파일을 다시 올리면 처음 fileId 를 그대로 돌려준다")
    void 멱등_재요청() throws Exception {
        byte[] content = TestImages.png(400, 100);

        String first = jsonValue(mockMvc.perform(upload(content, "AD", "key-same")).andReturn(), "fileId");
        String second = jsonValue(mockMvc.perform(upload(content, "AD", "key-same")).andReturn(), "fileId");

        assertThat(second).isEqualTo(first);
    }

    @Test
    @DisplayName("같은 키에 다른 파일이면 409 IDEMPOTENCY_KEY_REUSED")
    void 멱등키_재사용() throws Exception {
        mockMvc.perform(upload(TestImages.png(400, 100), "AD", "key-reuse"))
                .andExpect(status().isCreated());

        mockMvc.perform(upload(TestImages.png(800, 200), "AD", "key-reuse"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("IDEMPOTENCY_KEY_REUSED"));
    }

    // ── 픽스처 ──────────────────────────────────────────────

    private MockMultipartHttpServletRequestBuilder upload(byte[] content, String purpose, String key) {
        return multipart(URL)
                .file(new MockMultipartFile("file", "banner.png", "image/png", content))
                .param("purpose", purpose)
                .header("Idempotency-Key", key)
                .with(user(String.valueOf(userId)))
                .with(csrf());
    }

    private String jsonValue(MvcResult result, String field) throws Exception {
        String body = result.getResponse().getContentAsString();
        return com.jayway.jsonpath.JsonPath.read(body, "$." + field);
    }

    private Long insertUser(String nickname) {
        User user = User.create();
        user.changeNickname(nickname);
        em.persist(user);
        em.flush();
        return user.getId();
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
}
