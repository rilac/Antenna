package ssafy.a507.backend.domain.community.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import ssafy.a507.backend.common.idempotency.IdempotencyStore;
import ssafy.a507.backend.common.security.CurrentUserProvider;
import ssafy.a507.backend.domain.community.dto.PostCreateRequest;
import ssafy.a507.backend.domain.community.dto.PostCreateResponse;
import ssafy.a507.backend.domain.community.dto.PostDetailResponse;
import ssafy.a507.backend.domain.community.dto.PostListResponse;
import ssafy.a507.backend.domain.community.service.PostService;

/** 피드 글 — ANT-COMMUNITY-02. 실제 경로에는 {@code /api/v1} 이 앞에 붙는다(WebMvcConfig). */
@RestController
@RequestMapping("/posts")
@RequiredArgsConstructor
public class PostController {

    private static final String CREATE_ENDPOINT = "POST /posts";

    private final PostService postService;
    private final IdempotencyStore idempotencyStore;
    private final CurrentUserProvider currentUserProvider;

    /**
     * 글 작성. 명세 §1 의 멱등성 필수 대상이라 Idempotency-Key 헤더를 받는다.
     *
     * <p>글에는 삭제 API 가 없다. 타임아웃 후 클라이언트가 한 번 재시도하면 같은 글이 두 건
     * 남고 지울 방법이 없어서, 재시도에는 최초에 만든 글의 id 를 그대로 돌려준다.
     *
     * <p>멱등 저장소에는 응답 JSON 이 아니라 <b>글 id 하나만</b> 넣는다. 응답이
     * {@code { id }} 뿐이라 id 만 있으면 같은 응답을 다시 만들 수 있고, 그러면 직렬화기를
     * 끌어들이지 않아도 된다. 응답 모양이 커지면 그때 저장 형식을 바꾼다.
     */
    @PostMapping
    public ResponseEntity<PostCreateResponse> create(
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
            @Valid @RequestBody PostCreateRequest request) {

        Long userId = currentUserProvider.currentUserId();
        String postId = idempotencyStore.execute(
                userId,
                CREATE_ENDPOINT,
                idempotencyKey,
                // record 의 toString 은 모든 구성요소를 순서대로 담아 같은 입력에 같은 문자열을 준다.
                IdempotencyStore.hash(request.toString()),
                () -> String.valueOf(postService.create(request)));

        return ResponseEntity.status(HttpStatus.CREATED)
                .body(new PostCreateResponse(Long.valueOf(postId)));
    }

    @GetMapping
    public PostListResponse list(
            @RequestParam(required = false) Long cursor,
            @RequestParam(required = false) Integer size) {
        return postService.list(cursor, size);
    }

    @GetMapping("/{postId}")
    public PostDetailResponse detail(@PathVariable Long postId) {
        return postService.detail(postId);
    }
}
