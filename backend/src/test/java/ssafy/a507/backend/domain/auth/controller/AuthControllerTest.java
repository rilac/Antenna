package ssafy.a507.backend.domain.auth.controller;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
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

    /**
     * 어휘에 없는 provider 는 "아직 안 붙였다"(501)가 아니라 잘못된 요청(400)이다.
     * 프론트 오타와 미구현을 같은 코드로 묶으면 어느 쪽인지 가릴 수 없다.
     */
    @Test
    @DisplayName("어휘에 없는 provider 로 로그인하면 400 과 INVALID_REQUEST 를 돌려준다")
    void loginWithUnknownProvider() throws Exception {
        mockMvc.perform(
                        post("/api/v1/auth/login/kakao")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"code\":\"dummy\",\"redirectUri\":\"http://localhost:5173\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
    }

    /**
     * 자격증명이 없으면 SSAFY 로그인은 501 이다. 500 이 아니라는 것이 요점 —
     * ResponseStatusException 을 쓰면 마지막 그물에 걸려 원인이 지워진다.
     */
    @Test
    @DisplayName("SSAFY 자격증명이 없으면 501 과 PROVIDER_NOT_SUPPORTED 를 돌려준다")
    void loginWithUnconfiguredSsafy() throws Exception {
        mockMvc.perform(
                        post("/api/v1/auth/login/ssafy")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"code\":\"dummy\",\"redirectUri\":\"http://localhost:5173\"}"))
                .andExpect(status().isNotImplemented())
                .andExpect(jsonPath("$.code").value("PROVIDER_NOT_SUPPORTED"));
    }
}
