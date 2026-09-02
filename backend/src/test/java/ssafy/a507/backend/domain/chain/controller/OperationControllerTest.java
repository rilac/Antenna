package ssafy.a507.backend.domain.chain.controller;

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
import ssafy.a507.backend.domain.account.entity.User;
import ssafy.a507.backend.domain.chain.entity.Operation;

/** GET /api/v1/operations/{operationId} — ANT-COMMUNITY-07 의 AC 검증. */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
@DisplayName("작업 리소스 폴링 API")
class OperationControllerTest {

    private static final String URL = "/api/v1/operations/";

    @Autowired MockMvc mockMvc;
    @Autowired EntityManager em;

    private Long ownerId;
    private Long strangerId;
    private Operation operation;

    @BeforeEach
    void setUp() {
        User owner = insertUser("요청자");
        User stranger = insertUser("남");
        ownerId = owner.getId();
        strangerId = stranger.getId();

        operation = Operation.accept(owner, Operation.Kind.AD, Operation.ResourceType.AD, 42L);
        em.persist(operation);
        em.flush();
    }

    @Test
    @DisplayName("접수 직후에는 PENDING 이고 리소스가 함께 실린다")
    void 본인_작업_조회() throws Exception {
        mockMvc.perform(get(URL + operation.getId()).with(user(String.valueOf(ownerId))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.operationId").value(operation.getId()))
                .andExpect(jsonPath("$.kind").value("AD"))
                .andExpect(jsonPath("$.status").value("PENDING"))
                .andExpect(jsonPath("$.resource.type").value("AD"))
                .andExpect(jsonPath("$.resource.id").value(42))
                .andExpect(jsonPath("$.txHash").doesNotExist())
                .andExpect(jsonPath("$.error").doesNotExist())
                .andExpect(jsonPath("$.settledAt").doesNotExist());
    }

    @Test
    @DisplayName("실패한 작업은 error.code 와 settledAt 이 실린다")
    void 실패_작업_조회() throws Exception {
        operation.markFailed("INSUFFICIENT_BALANCE", "잔액이 부족합니다.");
        em.flush();

        mockMvc.perform(get(URL + operation.getId()).with(user(String.valueOf(ownerId))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("FAILED"))
                .andExpect(jsonPath("$.error.code").value("INSUFFICIENT_BALANCE"))
                .andExpect(jsonPath("$.settledAt").isNotEmpty());
    }

    @Test
    @DisplayName("남의 작업을 조회하면 403 OPERATION_FORBIDDEN")
    void 남의_작업은_403() throws Exception {
        mockMvc.perform(get(URL + operation.getId()).with(user(String.valueOf(strangerId))))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("OPERATION_FORBIDDEN"));
    }

    @Test
    @DisplayName("없는 작업이면 404 OPERATION_NOT_FOUND — 정리된 작업과 구분하지 않는다")
    void 없는_작업은_404() throws Exception {
        mockMvc.perform(get(URL + "op_없는값").with(user(String.valueOf(ownerId))))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("OPERATION_NOT_FOUND"));
    }

    @Test
    @DisplayName("로그인하지 않으면 401")
    void 미인증은_401() throws Exception {
        mockMvc.perform(get(URL + operation.getId()))
                .andExpect(status().isUnauthorized());
    }

    private User insertUser(String nickname) {
        User user = User.create();
        user.changeNickname(nickname);
        em.persist(user);
        return user;
    }
}
