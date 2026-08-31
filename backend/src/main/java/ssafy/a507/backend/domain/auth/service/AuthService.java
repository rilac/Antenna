package ssafy.a507.backend.domain.auth.service;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import ssafy.a507.backend.domain.account.entity.User;
import ssafy.a507.backend.domain.account.entity.UserOauth;
import ssafy.a507.backend.domain.account.repository.UserOauthRepository;
import ssafy.a507.backend.domain.account.repository.UserRepository;
import ssafy.a507.backend.domain.auth.dto.AuthDtos.LoginResponse;
import ssafy.a507.backend.domain.auth.dto.AuthDtos.UserSummary;
import ssafy.a507.backend.global.security.JwtProvider;

@Service
public class AuthService {

    /** access token 과 함께 쿠키로 내보낼 refresh 토큰. */
    public record LoginResult(LoginResponse body, String refreshToken) {}

    private final GoogleOAuthClient googleOAuthClient;
    private final RefreshTokenStore refreshTokenStore;
    private final JwtProvider jwtProvider;
    private final UserRepository users;
    private final UserOauthRepository userOauths;

    public AuthService(
            GoogleOAuthClient googleOAuthClient,
            RefreshTokenStore refreshTokenStore,
            JwtProvider jwtProvider,
            UserRepository users,
            UserOauthRepository userOauths) {
        this.googleOAuthClient = googleOAuthClient;
        this.refreshTokenStore = refreshTokenStore;
        this.jwtProvider = jwtProvider;
        this.users = users;
        this.userOauths = userOauths;
    }

    @Transactional
    public LoginResult login(String provider, String code, String redirectUri) {
        if (!"google".equalsIgnoreCase(provider)) {
            // SSAFY 는 개발자센터 승인 후 자격증명이 나오면 붙인다.
            throw new ResponseStatusException(
                    HttpStatus.NOT_IMPLEMENTED, "지원하지 않는 프로바이더: " + provider);
        }

        GoogleOAuthClient.GoogleAccount account = googleOAuthClient.exchange(code, redirectUri);

        UserOauth oauth =
                userOauths
                        .findByProviderAndProviderUserId(
                                UserOauth.Provider.GOOGLE, account.providerUserId())
                        .orElse(null);

        User user = oauth == null ? register(account) : oauth.getUser();

        if (user.getStatus() == User.Status.BANNED) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "차단된 계정");
        }

        String accessToken = jwtProvider.issueAccessToken(user.getId(), user.getRole().name());
        String refreshToken = refreshTokenStore.issue(user.getId());
        // 온보딩을 중간에 그만둔 회원도 다시 닉네임 화면으로 보내야 하므로,
        // "이번에 가입했는가"가 아니라 "닉네임이 아직 없는가"를 isNew 로 내려보낸다.
        UserSummary summary =
                new UserSummary(
                        user.getId(),
                        user.getNickname(),
                        user.getWalletAddress() != null,
                        !user.hasNickname());
        return new LoginResult(new LoginResponse(accessToken, summary), refreshToken);
    }

    /** 닉네임 없이 가입시킨다. 온보딩에서 확정할 때까지 nickname 은 NULL 이다. */
    private User register(GoogleOAuthClient.GoogleAccount account) {
        User user = users.save(User.create());
        userOauths.save(
                UserOauth.link(
                        user, UserOauth.Provider.GOOGLE, account.providerUserId(), account.email()));
        return user;
    }

    public RefreshTokenStore.Rotated refresh(String refreshToken) {
        return refreshTokenStore.rotate(refreshToken);
    }

    public String issueAccessToken(Long userId) {
        User user =
                users.findById(userId)
                        .orElseThrow(
                                () ->
                                        new ResponseStatusException(
                                                HttpStatus.UNAUTHORIZED, "존재하지 않는 회원"));
        if (user.getStatus() == User.Status.BANNED) {
            refreshTokenStore.revokeAll(userId);
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "차단된 계정");
        }
        return jwtProvider.issueAccessToken(user.getId(), user.getRole().name());
    }

    public void logout(String refreshToken) {
        if (refreshToken != null) {
            refreshTokenStore.revoke(refreshToken);
        }
    }
}
