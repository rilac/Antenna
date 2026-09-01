package ssafy.a507.backend.domain.community.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** POST /api/v1/posts/{postId}/comments 요청 본문. 대댓글이 없어 부모 id 를 받지 않는다. */
public record CommentCreateRequest(

        @NotBlank(message = "댓글을 입력해야 합니다.")
        @Size(max = 500, message = "댓글은 500자를 넘을 수 없습니다.")
        String body
) {
}
