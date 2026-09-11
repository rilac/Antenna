package ssafy.a507.backend.domain.prediction.controller;

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
import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.Duration;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
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
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;
import org.web3j.crypto.Credentials;
import ssafy.a507.backend.common.security.SignatureGuard;
import ssafy.a507.backend.common.security.SignatureNonceStore;
import ssafy.a507.backend.common.security.SignatureScope;
import ssafy.a507.backend.domain.account.entity.User;
import ssafy.a507.backend.domain.prediction.dto.PredictionCreateRequest;
import ssafy.a507.backend.domain.prediction.entity.Prediction;
import ssafy.a507.backend.support.TestNonceStoreConfig;
import ssafy.a507.backend.support.WalletSignatures;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * ANT-PRED-01 — 예측 등록·슬롯. H2.
 *
 * <p>지갑은 web3j {@code Credentials} 가 대신하고({@link WalletSignatures}), nonce 는 인메모리 저장소, Redis(멱등키)는 맵으로
 * 흉내낸다. 서명 문자열은 요청 DTO 의 {@code signingPayload} 를 그대로 써서 만든다 — 프론트가 이 문자열을 똑같이 만들어야 한다는
 * 계약을 테스트가 실행한다.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
@Import(TestNonceStoreConfig.class)
@DisplayName("예측 등록 API")
class PredictionControllerTest {

    private static final String URL = "/api/v1/predictions";
    private static final String PRIVATE_KEY = "0x4c0883a69102937d6231471b5dbb6204fe5129617082792ae468d01a3f362318";
    private static final String OTHER_PRIVATE_KEY = "0x8d5366123cb560bb606379f90a0bfd4769eecc0557f1b362dcae9012b548b1e5";
    private static final String SAMSUNG = "005930";
    private static final String HYNIX = "000660";
    private static final String NOTE_SALT = "0123456789abcdef".repeat(4);
    private static final ZoneId KST = ZoneId.of("Asia/Seoul");
    private static final JsonMapper JSON = JsonMapper.builder().build();

    @Autowired MockMvc mockMvc;
    @Autowired EntityManager em;
    @Autowired SignatureNonceStore nonceStore;
    @Autowired SignatureGuard signatureGuard;

    @MockitoBean StringRedisTemplate redis;
    private final Map<String, String> redisStore = new HashMap<>();

    private Credentials wallet;
    private long userId;
    private long pointId;

    @BeforeEach
    void setUp() {
        stubRedis();
        wallet = Credentials.create(PRIVATE_KEY);
        userId = insertUser(wallet.getAddress());
        insertStock(SAMSUNG, "삼성전자");
        insertStock(HYNIX, "SK하이닉스");
        // 직전 종가 80,000 — 방향·목표가 모순 검사의 기준
        insertQuote(SAMSUNG, LocalDate.now(KST).minusDays(1), "80000");
        insertQuote(HYNIX, LocalDate.now(KST).minusDays(1), "150000");
        pointId = insertPoint(SAMSUNG);
        em.flush();
    }

    // ── 정상 ──────────────────────────────────────────────────────

    @Test
    @DisplayName("서명한 예측을 올리면 201 — 예측·근거·리서치 포인트·커밋이 한 번에 저장되고 앵커 대기(WAITING)다")
    void 등록_성공() throws Exception {
        PredictionCreateRequest req = signed(request(SAMSUNG, "UP", "82000", 30, List.of(pointId)), wallet);

        String body = perform(req, UUID.randomUUID().toString())
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("BASE"))
                .andExpect(jsonPath("$.anchorStatus").value("WAITING"))
                .andExpect(jsonPath("$.commitHash").value(org.hamcrest.Matchers.matchesPattern("^0x[0-9a-f]{64}$")))
                .andReturn().getResponse().getContentAsString();
        JsonNode json = JSON.readTree(body);
        long id = json.get("id").asLong();

        // 기준일 = 다음 평일, 만기일 = 기준일 + 30
        LocalDate baseDate = LocalDate.parse(json.get("baseDate").asString());
        assertThat(baseDate).isAfter(LocalDate.now(KST));
        assertThat(baseDate.getDayOfWeek()).isNotIn(DayOfWeek.SATURDAY, DayOfWeek.SUNDAY);
        assertThat(LocalDate.parse(json.get("settleDate").asString())).isEqualTo(baseDate.plusDays(30));

        em.flush(); // 서비스 트랜잭션이 테스트 트랜잭션에 얹혀 있어 아직 안 내려간 INSERT 가 있다 — clear 전에 내린다
        em.clear();
        Prediction saved = em.find(Prediction.class, id);
        assertThat(saved.getStatus()).isEqualTo(Prediction.Status.BASE);
        assertThat(saved.getRefClose()).isEqualByComparingTo("80000");
        assertThat(saved.getBasePrice()).isNull(); // 기준가는 배치(PRED-03)가 채운다
        assertThat(count("SELECT count(*) FROM prediction_notes WHERE prediction_id = " + id)).isEqualTo(1);
        assertThat(count("SELECT count(*) FROM prediction_evidences WHERE prediction_id = " + id)).isEqualTo(1);
        assertThat(count("SELECT count(*) FROM prediction_commits WHERE prediction_id = " + id
                        + " AND anchor_batch_id IS NULL AND note_hash IS NOT NULL AND salt = '" + NOTE_SALT + "'"))
                .isEqualTo(1);
    }

    @Test
    @DisplayName("같은 Idempotency-Key 로 재시도하면 새로 만들지 않고 같은 id 를 준다 — nonce 가 이미 사라졌어도 된다")
    void 멱등_재시도() throws Exception {
        PredictionCreateRequest req = signed(request(SAMSUNG, "UP", "82000", 30, List.of()), wallet);
        String key = UUID.randomUUID().toString();

        long first = idOf(perform(req, key).andExpect(status().isCreated()));
        long second = idOf(perform(req, key).andExpect(status().isCreated()));

        assertThat(second).isEqualTo(first);
        assertThat(count("SELECT count(*) FROM predictions")).isEqualTo(1);
    }

    @Test
    @DisplayName("같은 키로 다른 본문을 보내면 409 IDEMPOTENCY_KEY_REUSED · 키가 없으면 400")
    void 멱등키_오용() throws Exception {
        String key = UUID.randomUUID().toString();
        perform(signed(request(SAMSUNG, "UP", "82000", 30, List.of()), wallet), key).andExpect(status().isCreated());

        perform(signed(request(SAMSUNG, "UP", "83000", 30, List.of()), wallet), key)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("IDEMPOTENCY_KEY_REUSED"));
        perform(signed(request(SAMSUNG, "UP", "84000", 30, List.of()), wallet), null)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("IDEMPOTENCY_KEY_REQUIRED"));
    }

    // ── 슬롯 ──────────────────────────────────────────────────────

    @Test
    @DisplayName("하루 3건까지 — 4번째는 409 PREDICTION_SLOT_EXCEEDED 이고 slots 가 used/remaining 을 그대로 보여준다")
    void 슬롯_3건() throws Exception {
        mockMvc.perform(get(URL + "/slots").with(user(str(userId))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.freeLimit").value(3))
                .andExpect(jsonPath("$.used").value(0))
                .andExpect(jsonPath("$.remaining").value(3))
                .andExpect(jsonPath("$.overCost").value("1000")); // decimals 0 — 1,000 ANT 그대로 (금액표 ANT-TOKEN-08)

        for (int i = 0; i < 3; i++) {
            perform(signed(request(SAMSUNG, "UP", "8100" + i, 7, List.of()), wallet), UUID.randomUUID().toString())
                    .andExpect(status().isCreated());
        }
        perform(signed(request(SAMSUNG, "UP", "85000", 7, List.of()), wallet), UUID.randomUUID().toString())
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("PREDICTION_SLOT_EXCEEDED"));

        mockMvc.perform(get(URL + "/slots").with(user(str(userId))))
                .andExpect(jsonPath("$.used").value(3))
                .andExpect(jsonPath("$.remaining").value(0));
        // 슬롯 초과는 서명 검증 앞에서 끊긴다 — nonce 가 남아 있어야 한다.
        assertThat(nonceStore.consume(userId, SignatureScope.PREDICTION)).isNotNull();
    }

    // ── 서명 ──────────────────────────────────────────────────────

    @Nested
    @DisplayName("서명")
    class Signature {

        @Test
        @DisplayName("연동 지갑이 아닌 키로 서명하면 401 SIGNER_MISMATCH")
        void 다른_지갑_401() throws Exception {
            Credentials other = Credentials.create(OTHER_PRIVATE_KEY);
            perform(signed(request(SAMSUNG, "UP", "82000", 30, List.of()), other), UUID.randomUUID().toString())
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.code").value("SIGNER_MISMATCH"));
        }

        @Test
        @DisplayName("서명 뒤 본문(목표가)을 바꾸면 401 — 서버가 본문으로 문자열을 재조립하기 때문")
        void 본문_변조_401() throws Exception {
            PredictionCreateRequest signedReq = signed(request(SAMSUNG, "UP", "82000", 30, List.of()), wallet);
            PredictionCreateRequest tampered = new PredictionCreateRequest(
                    signedReq.stockCode(), signedReq.direction(), new BigDecimal("82001"), signedReq.horizon(),
                    signedReq.note(), signedReq.noteSalt(), signedReq.evidencePointIds(), signedReq.signature());
            perform(tampered, UUID.randomUUID().toString()).andExpect(status().isUnauthorized());
        }

        @Test
        @DisplayName("nonce 없이(발급 안 함) 보내면 400 NONCE_NOT_FOUND")
        void nonce_없음_400() throws Exception {
            nonceStore.consume(userId, SignatureScope.PREDICTION); // 다른 테스트가 남긴 nonce 가 있어도 비운다
            PredictionCreateRequest req = request(SAMSUNG, "UP", "82000", 30, List.of());
            String sig = WalletSignatures.sign(req.signingPayload("stale", signatureGuard.chainId()), wallet);
            perform(withSignature(req, sig), UUID.randomUUID().toString())
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("NONCE_NOT_FOUND"));
        }

        @Test
        @DisplayName("지갑 미연동 사용자는 400 WALLET_NOT_LINKED — nonce 를 태우기 전에 거른다")
        void 미연동_400() throws Exception {
            long noWallet = insertUser(null);
            em.flush();
            PredictionCreateRequest req = request(SAMSUNG, "UP", "82000", 30, List.of());
            String nonce = nonceStore.issue(noWallet, SignatureScope.PREDICTION);
            String sig = WalletSignatures.sign(req.signingPayload(nonce, signatureGuard.chainId()), wallet);
            mockMvc.perform(post(URL).with(user(str(noWallet))).with(csrf())
                            .header("Idempotency-Key", UUID.randomUUID().toString())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(JSON.writeValueAsString(withSignature(req, sig))))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("WALLET_NOT_LINKED"));
        }
    }

    // ── 검사 ──────────────────────────────────────────────────────

    @Nested
    @DisplayName("입력 검사 — 전부 nonce 소비 앞에서 끝난다")
    class Validation {

        @Test
        @DisplayName("UP 인데 직전 종가 이하면 400 targetPrice · DOWN 인데 이상이어도 400")
        void 방향_모순() throws Exception {
            perform(signed(request(SAMSUNG, "UP", "80000", 30, List.of()), wallet), UUID.randomUUID().toString())
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("INVALID_REQUEST"))
                    .andExpect(jsonPath("$.field").value("targetPrice"));
            perform(signed(request(SAMSUNG, "DOWN", "80000.01", 30, List.of()), wallet), UUID.randomUUID().toString())
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.field").value("targetPrice"));
            assertThat(nonceStore.consume(userId, SignatureScope.PREDICTION)).isNotNull();
        }

        @Test
        @DisplayName("다른 종목의 리서치 포인트를 근거로 달면 400 evidencePointIds")
        void 다른_종목_근거() throws Exception {
            long hynixPoint = insertPoint(HYNIX);
            em.flush();
            perform(signed(request(SAMSUNG, "UP", "82000", 30, List.of(hynixPoint)), wallet), UUID.randomUUID().toString())
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.field").value("evidencePointIds"));
        }

        @Test
        @DisplayName("없는 종목은 404 STOCK_NOT_FOUND · 시세가 없는 종목은 400 stockCode")
        void 종목() throws Exception {
            perform(signed(request("999999", "UP", "82000", 30, List.of()), wallet), UUID.randomUUID().toString())
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.code").value("STOCK_NOT_FOUND"));
            insertStock("035420", "NAVER");
            em.flush();
            perform(signed(request("035420", "UP", "82000", 30, List.of()), wallet), UUID.randomUUID().toString())
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.field").value("stockCode"));
        }

        @Test
        @DisplayName("horizon 이 7/14/30/90 밖이면 400 horizon · noteSalt 형식이 틀리면 400 noteSalt")
        void horizon_noteSalt() throws Exception {
            perform(signed(request(SAMSUNG, "UP", "82000", 5, List.of()), wallet), UUID.randomUUID().toString())
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.field").value("horizon"));
            PredictionCreateRequest bad = new PredictionCreateRequest(
                    SAMSUNG, Prediction.Direction.UP, new BigDecimal("82000"), (short) 30, "근거", "00".repeat(32),
                    List.of(), "0x" + "ab".repeat(65));
            perform(bad, UUID.randomUUID().toString())
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.field").value("noteSalt"));
        }
    }

    @Test
    @DisplayName("인증 없이는 401")
    void 인증_없으면_401() throws Exception {
        mockMvc.perform(get(URL + "/slots")).andExpect(status().isUnauthorized());
    }

    // ── 픽스처 ───────────────────────────────────────────────────

    private static String str(long id) {
        return String.valueOf(id);
    }

    private PredictionCreateRequest request(String code, String direction, String price, int horizon, List<Long> points) {
        return new PredictionCreateRequest(
                code, Prediction.Direction.valueOf(direction), new BigDecimal(price), (short) horizon,
                "3분기 실적 컨센서스 상회 전망", NOTE_SALT, points, "0x" + "00".repeat(65));
    }

    /** nonce 를 발급받고 DTO 의 signingPayload 로 서명한다 — 프론트가 할 일과 같다. */
    private PredictionCreateRequest signed(PredictionCreateRequest req, Credentials credentials) {
        String nonce = nonceStore.issue(userId, SignatureScope.PREDICTION);
        return withSignature(req, WalletSignatures.sign(req.signingPayload(nonce, signatureGuard.chainId()), credentials));
    }

    private static PredictionCreateRequest withSignature(PredictionCreateRequest r, String signature) {
        return new PredictionCreateRequest(
                r.stockCode(), r.direction(), r.targetPrice(), r.horizon(), r.note(), r.noteSalt(),
                r.evidencePointIds(), signature);
    }

    private ResultActions perform(PredictionCreateRequest req, String idempotencyKey) throws Exception {
        var builder = post(URL).with(user(str(userId))).with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(JSON.writeValueAsString(req));
        if (idempotencyKey != null) {
            builder.header("Idempotency-Key", idempotencyKey);
        }
        return mockMvc.perform(builder);
    }

    private static long idOf(ResultActions actions) throws Exception {
        return JSON.readTree(actions.andReturn().getResponse().getContentAsString()).get("id").asLong();
    }

    private long count(String sql) {
        return ((Number) em.createNativeQuery(sql).getSingleResult()).longValue();
    }

    private long insertUser(String walletAddress) {
        User user = User.create();
        if (walletAddress != null) {
            user.linkWallet(walletAddress);
        }
        em.persist(user);
        em.flush();
        return user.getId();
    }

    private void insertStock(String code, String name) {
        em.createNativeQuery("INSERT INTO stocks (code, name, listed) VALUES (?, ?, true)")
                .setParameter(1, code).setParameter(2, name).executeUpdate();
    }

    private void insertQuote(String code, LocalDate tradeDate, String close) {
        em.createNativeQuery(
                        "INSERT INTO daily_quotes (stock_code, trade_date, close, collected_at) VALUES (?, ?, ?, CURRENT_TIMESTAMP)")
                .setParameter(1, code).setParameter(2, tradeDate).setParameter(3, new BigDecimal(close))
                .executeUpdate();
    }

    private long insertPoint(String code) {
        em.createNativeQuery(
                        """
                        INSERT INTO research_points (stock_code, target_date, kind, body, created_at)
                        VALUES (?, CURRENT_DATE, 'POSITIVE', '실적 개선', CURRENT_TIMESTAMP)
                        """)
                .setParameter(1, code).executeUpdate();
        return count("SELECT max(id) FROM research_points");
    }

    /** Redis 대신 맵. IdempotencyStore 가 쓰는 get·set(TTL) 만 흉내낸다 — PostControllerTest 와 같다. */
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
