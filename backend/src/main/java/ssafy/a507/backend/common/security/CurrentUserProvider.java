package ssafy.a507.backend.common.security;

/**
 * 현재 요청을 보낸 사용자의 id.
 * ANT-AUTH(자체 JWT)가 붙기 전까지는 SecurityContext 의 principal 이름을 그대로 쓴다.
 */
public interface CurrentUserProvider {

    Long currentUserId();
}
