package ssafy.a507.backend.domain.auth.service;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ssafy.a507.backend.common.error.BusinessException;
import ssafy.a507.backend.common.error.ErrorCode;
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

    /** 프로바이더 → 구현체. 스프링이 등록한 OAuthClient 빈으로 채운다. */
    private final Map<UserOauth.Provider, OAuthClient> oauthClients;
    private final RefreshTokenStore refreshTokenStore;
    private final JwtProvider jwtProvider;
    private final UserRepository users;
    private final UserOauthRepository userOauths;

    public AuthService(
            List<OAuthClient> oauthClients,
            RefreshTokenStore refreshTokenStore,
            JwtProvider jwtProvider,
            UserRepository users,
            UserOauthRepository userOauths) {
        this.oauthClients =
                oauthClients.stream()
                        .collect(
                                Collectors.toUnmodifiableMap(
                                        OAuthClient::provider, Function.identity()));
        this.refreshTokenStore = refreshTokenStore;
        this.jwtProvider = jwtProvider;
        this.users = users;
        this.userOauths = userOauths;
    }

    @Transactional
    public LoginResult login(String provider, String code, String redirectUri) {
        OAuthClient client = clientFor(provider);
        OAuthClient.OAuthAccount account = client.exchange(code, redirectUri);

        UserOauth oauth =
                userOauths
                        .findByProviderAndProviderUserId(
                                client.provider(), account.providerUserId())
                        .orElse(null);

        User user = oauth == null ? register(client.provider(), account) : oauth.getUser();

        if (user.getStatus() == User.Status.BANNED) {
            throw new BusinessException(ErrorCode.ACCOUNT_BANNED);
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
    private User register(UserOauth.Provider provider, OAuthClient.OAuthAccount account) {
        User user = users.save(User.create());
        userOauths.save(
                UserOauth.link(user, provider, account.providerUserId(), account.email()));
        return user;
    }

    /**
     * 경로변수 provider 로 구현체를 고른다.
     *
     * <p>어휘에 없는 값(400)과 어휘에는 있지만 아직 구현체가 없는 값(501)을 나눈다 — 프론트
     * 오타와 "아직 안 붙였다"는 서로 다른 상황이라 같은 코드로 묶으면 원인을 못 가린다.
     */
    private OAuthClient clientFor(String provider) {
        UserOauth.Provider key;
        try {
            key = UserOauth.Provider.valueOf(provider.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST);
        }
        OAuthClient client = oauthClients.get(key);
        if (client == null) {
            throw new BusinessException(ErrorCode.PROVIDER_NOT_SUPPORTED);
        }
        return client;
    }

    public RefreshTokenStore.Rotated refresh(String refreshToken) {
        return refreshTokenStore.rotate(refreshToken);
    }

    public String issueAccessToken(Long userId) {
        User user =
                users.findById(userId)
                        .orElseThrow(() -> new BusinessException(ErrorCode.UNAUTHENTICATED));
        if (user.getStatus() == User.Status.BANNED) {
            refreshTokenStore.revokeAll(userId);
            throw new BusinessException(ErrorCode.ACCOUNT_BANNED);
        }
        return jwtProvider.issueAccessToken(user.getId(), user.getRole().name());
    }

    public void logout(String refreshToken) {
        if (refreshToken != null) {
            refreshTokenStore.revoke(refreshToken);
        }
    }
}
