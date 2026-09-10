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
import java.math.BigInteger;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;
import org.web3j.crypto.Credentials;
import ssafy.a507.backend.common.security.SignatureNonceStore;
import ssafy.a507.backend.common.security.SignatureScope;
import ssafy.a507.backend.domain.account.entity.User;
import ssafy.a507.backend.domain.monetize.dto.AdCreateRequest;
import ssafy.a507.backend.domain.monetize.entity.AdBanner;
import ssafy.a507.backend.domain.upload.entity.UploadFile;
import ssafy.a507.backend.support.TestNonceStoreConfig;
import ssafy.a507.backend.support.WalletSignatures;

/**
 * POST /api/v1/ads · GET /api/v1/ads/active — ANT-COMMUNITY-05 의 AC 검증.
 *
 * <p>Redis 는 띄우지 않는다 — 멱등 저장소는 맵으로, nonce 저장소는 인메모리 구현으로 대신한다.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestNonceStoreConfig.class)
@Transactional
@DisplayName("스폰서드 광고 API")
class AdControllerTest {

    private static final String URL = "/api/v1/ads";
    private static final long CHAIN_ID = 31337L;
    private static final String PRIVATE_KEY =
            "0x4c0883a69102937d6231471b5dbb6204fe5129617082792ae468d01a3f362318";
    private static final String DUMMY_SIGNATURE = "0x" + "0".repeat(130);
    /** 기본 단가가 1 ANT/일이라 30일치를 덮고도 남는 잔액. ANT 는 decimals 0 이다. */
    private static final BigInteger RICH = BigInteger.valueOf(1_000);

    @Autowired MockMvc mockMvc;
    @Autowired EntityManager em;
    @Autowired SignatureNonceStore nonceStore;

    @MockitoBean StringRedisTemplate redis;

    private final Map<String, String> redisStore = new HashMap<>();

    private Credentials credentials;
    private User advertiser;
    private String imageFileId;

    @BeforeEach
    void setUp() {
        stubRedis();
        credentials = Credentials.create(PRIVATE_KEY);
        advertiser = insertUser("광고주");
        advertiser.linkWallet(credentials.getAddress());
        imageFileId = insertUploadFile(advertiser, UploadFile.Purpose.AD);
        em.flush();
    }

    @Test
    @DisplayName("신청하면 202 와 operationId · adId 를 받고 폴링 창구에서 PENDING 으로 보인다")
    void 신청_성공() throws Exception {
        credit(advertiser, RICH);

        MvcResult result = mockMvc.perform(create(imageFileId, "https://ad.example.com", 7, "key-1"))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.operationId").isNotEmpty())
                .andExpect(jsonPath("$.adId").isNumber())
                .andExpect(jsonPath("$.status").value("PENDING"))
                .andReturn();

        String operationId = jsonValue(result, "operationId");
        mockMvc.perform(get("/api/v1/operations/" + operationId)
                        .with(user(String.valueOf(advertiser.getId()))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.kind").value("AD"))
                .andExpect(jsonPath("$.resource.type").value("AD"));
    }

    @Test
    @DisplayName("접수된 배너는 확정 전이라 노출 목록에 들어가지 않는다")
    void 접수만으로는_노출되지_않는다() throws Exception {
        credit(advertiser, RICH);
        mockMvc.perform(create(imageFileId, "https://ad.example.com", 7, "key-pending"))
                .andExpect(status().isAccepted());

        mockMvc.perform(get(URL + "/active").with(user(String.valueOf(advertiser.getId()))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items").isEmpty());
    }

    @Test
    @DisplayName("같은 기간에 이미 자리가 찼으면 409 AD_SLOT_SOLD_OUT")
    void 슬롯_마감() throws Exception {
        credit(advertiser, RICH);
        insertBanner(AdBanner.Status.ACTIVE, Instant.now(), Instant.now().plus(30, ChronoUnit.DAYS));

        mockMvc.perform(create(imageFileId, "https://ad.example.com", 7, "key-slot"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("AD_SLOT_SOLD_OUT"));
    }

    @Test
    @DisplayName("확정을 기다리는 신청도 자리를 차지한다 — 확정되는 순간 배너가 둘이 되면 안 된다")
    void 최근_PENDING_은_슬롯을_막는다() throws Exception {
        credit(advertiser, RICH);
        insertBanner(AdBanner.Status.PENDING, Instant.now(), Instant.now().plus(30, ChronoUnit.DAYS));
        em.flush();

        mockMvc.perform(create(imageFileId, "https://ad.example.com", 7, "key-pending-blocks"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("AD_SLOT_SOLD_OUT"));
    }

    @Test
    @DisplayName("유예 창이 지난 PENDING 은 자리를 놓는다 — 접수는 토큰을 차감하지 않아 공짜 스쿼팅이 된다")
    void 오래된_PENDING_은_슬롯을_막지_않는다() throws Exception {
        credit(advertiser, RICH);
        AdBanner squatter =
                insertBanner(
                        AdBanner.Status.PENDING, Instant.now(), Instant.now().plus(30, ChronoUnit.DAYS));
        backdate(squatter, Instant.now().minus(1, ChronoUnit.HOURS));

        mockMvc.perform(create(imageFileId, "https://ad.example.com", 7, "key-squat"))
                .andExpect(status().isAccepted());
    }

    @Test
    @DisplayName("견적가를 배너에 박제한다 — 인덱서가 소각 tx 와 맞출 기대 금액이 필요하다")
    void 게재료를_박제한다() throws Exception {
        credit(advertiser, RICH);

        String adId = jsonValue(
                mockMvc.perform(create(imageFileId, "https://ad.example.com", 7, "key-price"))
                        .andReturn(),
                "adId");
        em.flush();
        em.clear();

        AdBanner saved = em.find(AdBanner.class, Long.valueOf(adId));
        assertThat(saved.getPriceWei()).isEqualTo(BigInteger.valueOf(7));
    }

    @Test
    @DisplayName("이미 끝난 광고는 슬롯을 막지 않는다 — 겹침 판정이 기간 양쪽을 본다")
    void 지난_광고는_슬롯을_막지_않는다() throws Exception {
        credit(advertiser, RICH);
        Instant now = Instant.now();
        insertBanner(
                AdBanner.Status.ACTIVE, now.minus(30, ChronoUnit.DAYS), now.minus(1, ChronoUnit.DAYS));
        em.flush();

        mockMvc.perform(create(imageFileId, "https://ad.example.com", 7, "key-past"))
                .andExpect(status().isAccepted());
    }

    @Test
    @DisplayName("링크가 컬럼 길이를 넘으면 400 — INSERT 가 터져 500 이 되게 두지 않는다")
    void 너무_긴_링크는_400() throws Exception {
        credit(advertiser, RICH);
        String tooLong = "https://ad.example.com/" + "a".repeat(500);

        mockMvc.perform(create(imageFileId, tooLong, 7, "key-longurl"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.field").value("linkUrl"));
    }

    @Test
    @DisplayName("잔액이 게재료보다 적으면 409 INSUFFICIENT_BALANCE")
    void 잔액_부족() throws Exception {
        credit(advertiser, BigInteger.ONE);

        mockMvc.perform(create(imageFileId, "https://ad.example.com", 7, "key-poor"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("INSUFFICIENT_BALANCE"));
    }

    @Test
    @DisplayName("남이 올린 이미지의 fileId 로는 등록할 수 없다 — 404 UPLOAD_FILE_NOT_FOUND")
    void 남의_이미지는_거부() throws Exception {
        credit(advertiser, RICH);
        User other = insertUser("남");
        String othersFile = insertUploadFile(other, UploadFile.Purpose.AD);
        em.flush();

        mockMvc.perform(create(othersFile, "https://ad.example.com", 7, "key-other"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("UPLOAD_FILE_NOT_FOUND"));
    }

    @Test
    @DisplayName("배너 용도로 올리지 않은 이미지는 400 — 비율 검증을 건너뛴 파일이다")
    void 다른_용도_이미지는_거부() throws Exception {
        credit(advertiser, RICH);
        String postImage = insertUploadFile(advertiser, UploadFile.Purpose.POST);
        em.flush();

        mockMvc.perform(create(postImage, "https://ad.example.com", 7, "key-purpose"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.field").value("imageFileId"));
    }

    @Test
    @DisplayName("http 링크는 400 — 배너는 클릭을 유도하는 자리라 피싱 대상이 된다")
    void http_링크는_거부() throws Exception {
        credit(advertiser, RICH);

        mockMvc.perform(create(imageFileId, "http://ad.example.com", 7, "key-http"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.field").value("linkUrl"));
    }

    @Test
    @DisplayName("최대 기간을 넘기면 400 — 한 명이 몇 년치를 선점하지 못한다")
    void 기간_상한() throws Exception {
        credit(advertiser, RICH);

        mockMvc.perform(create(imageFileId, "https://ad.example.com", 365, "key-long"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.field").value("days"));
    }

    @Test
    @DisplayName("같은 Idempotency-Key 재요청은 광고를 하나만 만든다 — 게재료는 되돌릴 수 없다")
    void 멱등_재요청() throws Exception {
        credit(advertiser, RICH);
        String body = signedBody(imageFileId, "https://ad.example.com", 7);

        String first = jsonValue(perform(body, "key-same"), "adId");
        String second = jsonValue(perform(body, "key-same"), "adId");

        assertThat(second).isEqualTo(first);
    }

    @Test
    @DisplayName("노출 목록은 ACTIVE 이면서 기간 안인 배너만 담는다")
    void 활성_목록() throws Exception {
        Instant now = Instant.now();
        insertBanner(AdBanner.Status.ACTIVE, now.minus(1, ChronoUnit.DAYS), now.plus(1, ChronoUnit.DAYS));
        // 끝난 광고. 상태를 ENDED 로 바꾸는 배치가 없어도 기간으로 걸러져야 한다.
        insertBanner(AdBanner.Status.ACTIVE, now.minus(10, ChronoUnit.DAYS), now.minus(1, ChronoUnit.DAYS));
        insertBanner(AdBanner.Status.PENDING, now.minus(1, ChronoUnit.DAYS), now.plus(1, ChronoUnit.DAYS));
        em.flush();

        mockMvc.perform(get(URL + "/active").with(user(String.valueOf(advertiser.getId()))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(1))
                .andExpect(jsonPath("$.items[0].imageUrl").isNotEmpty())
                .andExpect(jsonPath("$.items[0].linkUrl").isNotEmpty());
    }

    @Test
    @DisplayName("게재 조건은 app.ads 설정값을 그대로 내린다 — wei 는 문자열이다")
    void 게재_조건() throws Exception {
        mockMvc.perform(get(URL + "/pricing").with(user(String.valueOf(advertiser.getId()))))
                .andExpect(status().isOk())
                // 설정을 넣지 않았으므로 AdProperties 의 기본값 1 ANT/일이다. ANT 는 decimals 0
                // 이라 10^18 을 곱하지 않고, 금액 필드라 문자열로 내린다.
                .andExpect(jsonPath("$.pricePerDayWei").value("1"))
                // AdCreateRequest 의 @Min(1) 과 같은 값이어야 한다.
                .andExpect(jsonPath("$.minDays").value(1))
                .andExpect(jsonPath("$.maxDays").value(30))
                .andExpect(jsonPath("$.slotCount").value(1));
    }

    @Test
    @DisplayName("게재 조건도 로그인해야 받는다 — 401")
    void 게재_조건_미인증은_401() throws Exception {
        mockMvc.perform(get(URL + "/pricing")).andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("로그인하지 않으면 401")
    void 미인증은_401() throws Exception {
        mockMvc.perform(get(URL + "/active")).andExpect(status().isUnauthorized());
    }

    // ── 픽스처 ──────────────────────────────────────────────

    private org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder create(
            String fileId, String linkUrl, int days, String key) {
        return post(URL)
                .with(user(String.valueOf(advertiser.getId()))).with(csrf())
                .header("Idempotency-Key", key)
                .contentType(MediaType.APPLICATION_JSON)
                .content(signedBody(fileId, linkUrl, days));
    }

    private MvcResult perform(String body, String key) throws Exception {
        return mockMvc.perform(post(URL)
                        .with(user(String.valueOf(advertiser.getId()))).with(csrf())
                        .header("Idempotency-Key", key)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andReturn();
    }

    /** 서버와 같은 규칙으로 payload 를 조립해 서명한다. 프론트가 해야 할 일과 같다. */
    private String signedBody(String fileId, String linkUrl, int days) {
        String nonce = nonceStore.issue(advertiser.getId(), SignatureScope.AD);
        String payload =
                new AdCreateRequest(fileId, linkUrl, days, DUMMY_SIGNATURE)
                        .signingPayload(nonce, CHAIN_ID);
        return """
                {"imageFileId":"%s","linkUrl":"%s","days":%d,"signature":"%s"}
                """
                .formatted(fileId, linkUrl, days, WalletSignatures.sign(payload, credentials));
    }

    private String jsonValue(MvcResult result, String field) throws Exception {
        return String.valueOf(
                (Object) com.jayway.jsonpath.JsonPath.read(
                        result.getResponse().getContentAsString(), "$." + field));
    }

    private User insertUser(String nickname) {
        User user = User.create();
        user.changeNickname(nickname);
        em.persist(user);
        return user;
    }

    private String insertUploadFile(User owner, UploadFile.Purpose purpose) {
        UploadFile file = UploadFile.create(owner, purpose, "image/png", 400, 100, 1024);
        file.locateAt("/api/v1/uploads/" + file.getId());
        em.persist(file);
        return file.getId();
    }

    private AdBanner insertBanner(AdBanner.Status status, Instant startsAt, Instant endsAt) {
        AdBanner banner =
                AdBanner.request(
                        advertiser,
                        "/api/v1/uploads/x",
                        "https://ad.example.com",
                        startsAt,
                        endsAt,
                        BigInteger.valueOf(7));
        if (status == AdBanner.Status.ACTIVE) {
            banner.activate("0x" + "1".repeat(64));
        }
        em.persist(banner);
        return banner;
    }

    /** @CreationTimestamp 라 코드로는 못 바꾼다. 유예 창이 지난 신청을 만들려면 되돌려야 한다. */
    private void backdate(AdBanner banner, Instant createdAt) {
        em.flush();
        em.createNativeQuery("UPDATE ad_banners SET created_at = ? WHERE id = ?")
                .setParameter(1, java.sql.Timestamp.from(createdAt))
                .setParameter(2, banner.getId())
                .executeUpdate();
        em.clear();
    }

    /** 원장은 append-only 라 SUM(delta) 가 잔액이다. 엔티티에 팩터리가 없어 네이티브로 넣는다. */
    private void credit(User user, BigInteger amount) {
        em.createNativeQuery("""
                        INSERT INTO token_ledger (user_id, delta, reason, created_at)
                        VALUES (?, ?, 'TEST', CURRENT_TIMESTAMP)
                        """)
                .setParameter(1, user.getId())
                .setParameter(2, amount)
                .executeUpdate();
        em.flush();
    }

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
