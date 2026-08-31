package ssafy.a507.backend.domain.auth.controller;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;

/**
 * 회귀 방지 — GlobalExceptionHandler 의 마지막 그물(@ExceptionHandler(Exception.class))이
 * ResponseStatusException 을 삼켜 401 이 500 으로 나갔다. 두 브랜치가 각각의 테스트를
 * 통과하고 합쳐진 뒤에야 드러난 문제라, 상태 코드와 code 를 같이 고정해 둔다.
 */
@SpringBootTest
@AutoConfigureMockMvc
class AuthControllerTest {

    @Autowired
    MockMvc mockMvc;

    @Test
    @DisplayName("refresh 쿠키가 없으면 401 과 UNAUTHENTICATED 를 돌려준다")
    void refreshWithoutCookie() throws Exception {
        mockMvc.perform(post("/api/v1/auth/refresh"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));
    }
}
