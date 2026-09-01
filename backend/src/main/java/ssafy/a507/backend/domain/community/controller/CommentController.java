package ssafy.a507.backend.domain.community.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import ssafy.a507.backend.domain.community.dto.CommentCreateRequest;
import ssafy.a507.backend.domain.community.dto.CommentCreateResponse;
import ssafy.a507.backend.domain.community.dto.CommentListResponse;
import ssafy.a507.backend.domain.community.service.CommentService;

/**
 * 댓글과 좋아요 — ANT-COMMUNITY-03.
 *
 * <p>클래스 접두사가 {@code /api/v1} 까지인 이유는 경로가 두 갈래이기 때문이다 —
 * 댓글은 글에 딸리지만({@code /posts/{postId}/comments}) 댓글 좋아요는 글을 거치지 않는다
 * ({@code /comments/{commentId}/like}). 명세 §리포트·글이 그렇게 나눠 놨고, 화면도 댓글 id
 * 하나만 들고 좋아요를 누른다. 접두사는 직접 적는다(자동 접두사는 걷어냈다 —
 * {@code ApiPathContractTest} 주석 참고).
 */
@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class CommentController {

    private final CommentService commentService;

    @GetMapping("/posts/{postId}/comments")
    public CommentListResponse list(
            @PathVariable Long postId,
            @RequestParam(required = false) Long cursor,
            @RequestParam(required = false) Integer size) {
        return commentService.list(postId, cursor, size);
    }

    /**
     * 댓글 작성. 멱등성 키를 받지 않는다 — 명세 §1 의 필수 대상이 아니고, 재시도로 같은
     * 댓글이 두 건 남는 것은 글과 달리 대화 맥락에서 눈에 보이는 실수다.
     */
    @PostMapping("/posts/{postId}/comments")
    public ResponseEntity<CommentCreateResponse> create(
            @PathVariable Long postId, @Valid @RequestBody CommentCreateRequest request) {
        Long id = commentService.create(postId, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(new CommentCreateResponse(id));
    }

    @PostMapping("/posts/{postId}/like")
    public ResponseEntity<Void> likePost(@PathVariable Long postId) {
        commentService.likePost(postId);
        return ResponseEntity.status(HttpStatus.CREATED).build();
    }

    @DeleteMapping("/posts/{postId}/like")
    public ResponseEntity<Void> unlikePost(@PathVariable Long postId) {
        commentService.unlikePost(postId);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/comments/{commentId}/like")
    public ResponseEntity<Void> likeComment(@PathVariable Long commentId) {
        commentService.likeComment(commentId);
        return ResponseEntity.status(HttpStatus.CREATED).build();
    }

    @DeleteMapping("/comments/{commentId}/like")
    public ResponseEntity<Void> unlikeComment(@PathVariable Long commentId) {
        commentService.unlikeComment(commentId);
        return ResponseEntity.noContent().build();
    }
}
