package ssafy.a507.backend.domain.account.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import jakarta.persistence.EntityManager;
import java.util.Locale;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import org.web3j.crypto.Credentials;
import ssafy.a507.backend.common.security.SignatureNonceStore;
import ssafy.a507.backend.domain.account.dto.WalletLinkRequest;
import ssafy.a507.backend.support.TestNonceStoreConfig;
import ssafy.a507.backend.support.WalletSignatures;

/** SecurityConfig가 아직 없어 기본 체인이 살아 있다 — 요청마다 user()·csrf()를 붙인다. */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestNonceStoreConfig.class)
@Transactional
@DisplayName("지갑 연동 API")
class WalletControllerTest {

    private static final long CHAIN_ID = 31337L;
    private static final String PRIVATE_KEY =
            "0x4c0883a69102937d6231471b5dbb6204fe5129617082792ae468d01a3f362318";
    private static final String OTHER_PRIVATE_KEY =
            "0x8d5366123cb560bb606379f90a0bfd4769eecc0557f1b362dcae9012b548b1e5";
    private static final String DUMMY_SIGNATURE = "0x" + "0".repeat(130);

    @Autowired MockMvc mockMvc;
    @Autowired EntityManager em;
    @Autowired SignatureNonceStore nonceStore;

    private Credentials credentials;
    private Long userId;

    @BeforeEach
    void setUp() {
        credentials = Credentials.create(PRIVATE_KEY);
        userId = insertUser("예측가");
    }

    /**
     * User는 protected 기본 생성자만 있고 정적 팩터리가 없어 객체로 만들 수 없다.
     * 팀에 픽스처 경로가 생기기 전까지는 네이티브 INSERT로 넣는다.
     * updated_at은 @UpdateTimestamp인데도 스키마가 NOT NULL로 생성되므로 같이 넣는다.
     */
    private Long insertUser(String nickname) {
        em.createNativeQuery(
                        "INSERT INTO users (nickname, role, status, created_at, updated_at)"
                                + " VALUES (:nickname, 'USER', 'ACTIVE',"
                                + " CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)")
                .setParameter("nickname", nickname)
                .executeUpdate();
        return ((Number)
                        em.createNativeQuery("SELECT id FROM users WHERE nickname = :nickname")
                                .setParameter("nickname", nickname)
                                .getSingleResult())
                .longValue();
    }

    /**
     * 서버와 같은 규칙으로 payload를 만들어 서명한다. 프론트가 해야 할 일과 같다.
     * 본문은 손으로 조립한다 — Boot 4는 Jackson 3(tools.jackson)을 쓰고
     * com.fasterxml.jackson.databind.ObjectMapper 빈은 존재하지 않는다.
     */
    private String linkBody(String address, Credentials signer, String nonce) {
        String payload =
                new WalletLinkRequest(address, DUMMY_SIGNATURE).signingPayload(nonce, CHAIN_ID);
        return json(address, WalletSignatures.sign(payload, signer));
    }

    private String json(String address, String signature) {
        return "{\"address\":\"" + address + "\",\"signature\":\"" + signature + "\"}";
    }

    @Test
    @DisplayName("nonce 발급 성공")
    void nonce_발급_성공() throws Exception {
        mockMvc.perform(post("/api/wallet/nonce").with(user(String.valueOf(userId))).with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nonce").isNotEmpty());
    }

    @Test
    @DisplayName("지갑 연동 성공 — 체크섬 주소로 보내도 소문자로 저장된다")
    void 지갑_연동_성공() throws Exception {
        String nonce = nonceStore.issue(userId);
        String checksumAddress =
                credentials.getAddress().toUpperCase(Locale.ROOT).replace("0X", "0x");

        mockMvc.perform(
                        post("/api/wallet/link")
                                .with(user(String.valueOf(userId)))
                                .with(csrf())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(linkBody(checksumAddress, credentials, nonce)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.walletAddress").value(credentials.getAddress()));

        em.flush();
        em.clear();
        String stored =
                (String)
                        em.createNativeQuery("SELECT wallet_address FROM users WHERE id = :id")
                                .setParameter("id", userId)
                                .getSingleResult();
        assertThat(stored).isEqualTo(credentials.getAddress());
    }

    @Test
    @DisplayName("nonce는 발급받은 계정만 쓸 수 있다 — 남의 서명 본문을 복사해도 400 NONCE_NOT_FOUND")
    void 남의_nonce는_쓸_수_없다() throws Exception {
        String nonce = nonceStore.issue(userId);
        String body = linkBody(credentials.getAddress(), credentials, nonce);

        Long other = insertUser("가로채는사람");
        mockMvc.perform(
                        post("/api/wallet/link")
                                .with(user(String.valueOf(other)))
                                .with(csrf())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("NONCE_NOT_FOUND"));
    }

    @Test
    @DisplayName("같은 요청을 두 번 보내면 두 번째는 nonce가 소진돼 400")
    void 같은_요청_재전송_거절() throws Exception {
        String nonce = nonceStore.issue(userId);
        String body = linkBody(credentials.getAddress(), Credentials.create(OTHER_PRIVATE_KEY), nonce);

        // 서명자가 달라 401로 실패하지만, 그 과정에서 nonce는 이미 태워졌다.
        mockMvc.perform(
                        post("/api/wallet/link")
                                .with(user(String.valueOf(userId)))
                                .with(csrf())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(body))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(
                        post("/api/wallet/link")
                                .with(user(String.valueOf(userId)))
                                .with(csrf())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("NONCE_NOT_FOUND"));
    }

    @Test
    @DisplayName("다른 지갑으로 서명하면 401 SIGNER_MISMATCH")
    void 서명자_불일치_401() throws Exception {
        String nonce = nonceStore.issue(userId);

        mockMvc.perform(
                        post("/api/wallet/link")
                                .with(user(String.valueOf(userId)))
                                .with(csrf())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        linkBody(
                                                credentials.getAddress(),
                                                Credentials.create(OTHER_PRIVATE_KEY),
                                                nonce)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("SIGNER_MISMATCH"))
                .andExpect(jsonPath("$.field").value("signature"));
    }

    @Test
    @DisplayName("다른 계정이 쓰는 주소는 409 WALLET_ALREADY_LINKED")
    void 중복_주소_409() throws Exception {
        String nonce = nonceStore.issue(userId);
        mockMvc.perform(
                        post("/api/wallet/link")
                                .with(user(String.valueOf(userId)))
                                .with(csrf())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(linkBody(credentials.getAddress(), credentials, nonce)))
                .andExpect(status().isOk());
        em.flush();

        Long other = insertUser("따라하는사람");
        String otherNonce = nonceStore.issue(other);
        mockMvc.perform(
                        post("/api/wallet/link")
                                .with(user(String.valueOf(other)))
                                .with(csrf())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        linkBody(credentials.getAddress(), credentials, otherNonce)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("WALLET_ALREADY_LINKED"));
    }

    @Test
    @DisplayName("주소 형식이 틀리면 400 INVALID_REQUEST — DTO의 한국어 메시지가 그대로 내려간다")
    void 주소_형식_검증() throws Exception {
        mockMvc.perform(
                        post("/api/wallet/link")
                                .with(user(String.valueOf(userId)))
                                .with(csrf())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(json("0xnope", DUMMY_SIGNATURE)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"))
                .andExpect(jsonPath("$.field").value("address"))
                .andExpect(jsonPath("$.message").value("지갑 주소 형식이 올바르지 않습니다."));
    }

    @Test
    @DisplayName("미연동 계정은 linked=false, walletAddress는 null로 내려간다")
    void 연동_상태_조회_미연동() throws Exception {
        mockMvc.perform(get("/api/wallet").with(user(String.valueOf(userId))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.linked").value(false))
                .andExpect(jsonPath("$.walletAddress").isEmpty());
    }
}
