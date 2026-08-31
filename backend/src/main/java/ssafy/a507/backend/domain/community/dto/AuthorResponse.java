package ssafy.a507.backend.domain.community.dto;

import ssafy.a507.backend.domain.account.entity.User;

/** 글·리포트·댓글 작성자. 닉네임까지만 내려간다 — 목록에서 프로필 전체가 필요한 화면이 없다. */
public record AuthorResponse(Long userId, String nickname) {

    public static AuthorResponse from(User user) {
        return new AuthorResponse(user.getId(), user.getNickname());
    }
}
