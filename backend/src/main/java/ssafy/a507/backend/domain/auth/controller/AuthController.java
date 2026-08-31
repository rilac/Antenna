package ssafy.a507.backend.domain.auth.controller;

import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import ssafy.a507.backend.common.error.BusinessException;
import ssafy.a507.backend.common.error.ErrorCode;
import ssafy.a507.backend.domain.auth.dto.AuthDtos.AccessTokenResponse;
import ssafy.a507.backend.domain.auth.dto.AuthDtos.LoginRequest;
import ssafy.a507.backend.domain.auth.dto.AuthDtos.LoginResponse;
import ssafy.a507.backend.domain.auth.service.AuthService;
import ssafy.a507.backend.domain.auth.service.RefreshTokenStore;

@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

    private static final String REFRESH_COOKIE = "refreshToken";

    private final AuthService authService;
    private final RefreshTokenStore refreshTokenStore;
    private final boolean cookieSecure;

    public AuthController(
            AuthService authService,
            RefreshTokenStore refreshTokenStore,
            @Value("${app.auth.cookie-secure}") boolean cookieSecure) {
        this.authService = authService;
        this.refreshTokenStore = refreshTokenStore;
        this.cookieSecure = cookieSecure;
    }

    @PostMapping("/login/{provider}")
    public ResponseEntity<LoginResponse> login(
            @PathVariable String provider, @Valid @RequestBody LoginRequest request) {
        AuthService.LoginResult result =
                authService.login(provider, request.code(), request.redirectUri());
        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, refreshCookie(result.refreshToken()).toString())
                .body(result.body());
    }

    @PostMapping("/refresh")
    public ResponseEntity<AccessTokenResponse> refresh(
            @CookieValue(name = REFRESH_COOKIE, required = false) String refreshToken) {
        if (refreshToken == null) {
            // ResponseStatusException 을 쓰면 GlobalExceptionHandler 의 마지막 그물
            // (@ExceptionHandler(Exception.class))에 걸려 401 이 500 으로 바뀐다.
            // 오류 계약대로 BusinessException 을 던져 code 까지 함께 내려보낸다.
            throw new BusinessException(ErrorCode.UNAUTHENTICATED);
        }
        RefreshTokenStore.Rotated rotated;
        try {
            rotated = authService.refresh(refreshToken);
        } catch (RefreshTokenStore.InvalidRefreshTokenException e) {
            // 재사용 탐지 시 서비스가 이미 해당 유저 토큰을 전부 폐기했다. 쿠키도 함께 지운다.
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .header(HttpHeaders.SET_COOKIE, expiredCookie().toString())
                    .build();
        }
        String accessToken = authService.issueAccessToken(rotated.userId());
        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, refreshCookie(rotated.token()).toString())
                .body(new AccessTokenResponse(accessToken));
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout(
            @CookieValue(name = REFRESH_COOKIE, required = false) String refreshToken) {
        authService.logout(refreshToken);
        return ResponseEntity.noContent()
                .header(HttpHeaders.SET_COOKIE, expiredCookie().toString())
                .build();
    }

    private ResponseCookie refreshCookie(String token) {
        return baseCookie(token).maxAge(refreshTokenStore.ttl()).build();
    }

    private ResponseCookie expiredCookie() {
        return baseCookie("").maxAge(0).build();
    }

    private ResponseCookie.ResponseCookieBuilder baseCookie(String value) {
        return ResponseCookie.from(REFRESH_COOKIE, value)
                .httpOnly(true)
                .secure(cookieSecure)
                .sameSite("Lax")
                .path("/api/v1/auth");
    }
}
