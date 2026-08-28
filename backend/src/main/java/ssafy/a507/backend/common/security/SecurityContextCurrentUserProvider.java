package ssafy.a507.backend.common.security;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import ssafy.a507.backend.common.error.BusinessException;
import ssafy.a507.backend.common.error.ErrorCode;

/**
 * principal 이름을 users.id 로 읽는다.
 *
 * ponytail: ANT-AUTH(S15P21A507-20) 가 붙으면 JWT subject 를 읽는 구현으로 교체한다.
 * 그때까지 이 클래스가 유일한 사용자 식별 경로이므로, 바뀌는 파일은 여기 하나다.
 * 헤더로 사용자를 지정하는 우회는 두지 않았다 — 인증 없이 남을 사칭할 구멍이 되기 때문이다.
 */
@Component
public class SecurityContextCurrentUserProvider implements CurrentUserProvider {

    @Override
    public Long currentUserId() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated()) {
            throw new BusinessException(ErrorCode.UNAUTHENTICATED);
        }
        try {
            return Long.valueOf(auth.getName());
        } catch (NumberFormatException e) {
            throw new BusinessException(ErrorCode.UNAUTHENTICATED);
        }
    }
}
