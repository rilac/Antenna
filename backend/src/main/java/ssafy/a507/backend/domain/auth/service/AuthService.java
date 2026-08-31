package ssafy.a507.backend.domain.auth.service;

import java.util.UUID;
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

        boolean isNew = oauth == null;
        User user = isNew ? register(account) : oauth.getUser();

        if (user.getStatus() == User.Status.BANNED) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "차단된 계정");
        }

        String accessToken = jwtProvider.issueAccessToken(user.getId(), user.getRole().name());
        String refreshToken = refreshTokenStore.issue(user.getId());
        UserSummary summary =
                new UserSummary(
                        user.getId(), user.getNickname(), user.getWalletAddress() != null, isNew);
        return new LoginResult(new LoginResponse(accessToken, summary), refreshToken);
    }

    /** 닉네임은 온보딩에서 정하므로 충돌하지 않는 임시값으로 가입시킨다. */
    private User register(GoogleOAuthClient.GoogleAccount account) {
        String placeholder = "user_" + UUID.randomUUID().toString().replace("-", "").substring(0, 12);
        User user = users.save(User.create(placeholder));
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
