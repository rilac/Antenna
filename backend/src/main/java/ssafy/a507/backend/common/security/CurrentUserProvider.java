package ssafy.a507.backend.common.security;

import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import ssafy.a507.backend.common.error.BusinessException;
import ssafy.a507.backend.common.error.ErrorCode;

/**
 * 현재 로그인 사용자. 서비스에서 SecurityContextHolder를 직접 뒤지지 않는다.
 * 인증 주체의 이름을 users.id로 읽는다 — ANT-AUTH-03이 자체 JWT를 붙일 때
 * sub 클레임이 그 자리에 오므로 이 어댑터는 그대로 동작한다.
 */
@Component
public class CurrentUserProvider {

    public Long currentUserId() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null
                || !authentication.isAuthenticated()
                || authentication instanceof AnonymousAuthenticationToken) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED);
        }
        try {
            return Long.parseLong(authentication.getName());
        } catch (NumberFormatException e) {
            // 인증은 됐는데 주체 이름이 users.id가 아니다 — 인증 설정이 계약을 어긴 것이다.
            throw new BusinessException(ErrorCode.UNAUTHORIZED);
        }
    }
}
