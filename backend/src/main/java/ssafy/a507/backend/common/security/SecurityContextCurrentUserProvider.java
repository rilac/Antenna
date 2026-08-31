package ssafy.a507.backend.common.security;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import ssafy.a507.backend.common.error.BusinessException;
import ssafy.a507.backend.common.error.ErrorCode;

/**
 * principal 이름을 users.id 로 읽는다.
 *
 * <p><b>지금 이 경로는 테스트에서만 성립한다.</b> 프로젝트에 SecurityConfig 가 아직 없어서
 * principal 이름에 users.id 를 채워 넣는 주체가 없다. 실요청에는 starter-security 기본값
 * (HTTP Basic, 자동 생성 사용자 {@code user})이 걸리고, 그러면 {@code auth.getName()} 이
 * {@code "user"} 라서 아래 NumberFormatException 분기로 떨어져 401 이 된다.
 * 통과하는 것은 {@code SecurityMockMvcRequestPostProcessors.user("1")} 로 principal 이름을
 * 직접 주입하는 테스트뿐이다 — 실클라이언트로 성공하는 API 는 아직 하나도 없다.
 *
 * <p>ponytail: ANT-AUTH(S15P21A507-20) 가 SecurityConfig 와 JWT 필터를 붙이면서
 * "principal 이름 = users.id" 규약을 채운다. 그때 <b>이 클래스를 지우지 않는다</b> —
 * {@link #currentUserId()} 본문만 JWT subject 를 읽도록 바꾸고, 인터페이스와 주입 지점은
 * 전부 그대로 둔다. 이 클래스가 유일한 사용자 식별 경로라 바뀌는 파일은 여기 하나다.
 *
 * <p>NumberFormatException 을 401 로 뭉개는 것은 임시 조치다. JWT subject 는 항상 숫자이므로
 * ANT-AUTH 이후 이 분기는 죽는다 — 그때 제거하면 된다.
 *
 * <p>헤더로 사용자를 지정하는 우회는 두지 않았다 — 인증 없이 남을 사칭할 구멍이 되기 때문이다.
 * 로컬에서 API 를 직접 찔러 보려면 헤더 우회를 만드는 대신 SecurityConfig 를 붙여야 한다.
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
