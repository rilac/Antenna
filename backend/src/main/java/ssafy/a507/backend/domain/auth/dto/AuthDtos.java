package ssafy.a507.backend.domain.auth.dto;

import jakarta.validation.constraints.NotBlank;

public final class AuthDtos {

    private AuthDtos() {}

    public record LoginRequest(@NotBlank String code, @NotBlank String redirectUri) {}

    /** 닉네임 미설정 상태로 가입되므로 프론트는 isNew 로 온보딩 화면을 띄운다. */
    public record UserSummary(Long id, String nickname, boolean walletLinked, boolean isNew) {}

    public record LoginResponse(String accessToken, UserSummary user) {}

    public record AccessTokenResponse(String accessToken) {}
}
