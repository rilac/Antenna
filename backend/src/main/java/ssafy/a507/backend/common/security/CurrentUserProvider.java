package ssafy.a507.backend.common.security;

/**
 * 현재 요청을 보낸 사용자의 id.
 * ANT-AUTH(자체 JWT)가 붙기 전까지는 SecurityContext 의 principal 이름을 그대로 쓴다.
 *
 * <p>인증 없이 부르면 401({@code UNAUTHENTICATED})로 던진다 — null 을 돌려주지 않으므로
 * 호출부에서 널 검사를 하지 않는다. 임시 구현의 한계는
 * {@link SecurityContextCurrentUserProvider} 주석에 적어 두었다.
 */
public interface CurrentUserProvider {

    Long currentUserId();
}
