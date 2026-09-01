package ssafy.a507.backend.domain.community.service;

import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Limit;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ssafy.a507.backend.common.error.BusinessException;
import ssafy.a507.backend.common.error.ErrorCode;
import ssafy.a507.backend.common.security.CurrentUserProvider;
import ssafy.a507.backend.domain.account.entity.User;
import ssafy.a507.backend.domain.account.repository.UserRepository;
import ssafy.a507.backend.domain.community.dto.AuthorResponse;
import ssafy.a507.backend.domain.community.dto.CommentCreateRequest;
import ssafy.a507.backend.domain.community.dto.CommentListItemResponse;
import ssafy.a507.backend.domain.community.dto.CommentListResponse;
import ssafy.a507.backend.domain.community.entity.CommentLike;
import ssafy.a507.backend.domain.community.entity.FeedPost;
import ssafy.a507.backend.domain.community.entity.PostComment;
import ssafy.a507.backend.domain.community.entity.PostLike;
import ssafy.a507.backend.domain.community.repository.CommentLikeRepository;
import ssafy.a507.backend.domain.community.repository.FeedPostRepository;
import ssafy.a507.backend.domain.community.repository.PostCommentRepository;
import ssafy.a507.backend.domain.community.repository.PostLikeRepository;

/**
 * 댓글 작성·조회와 글·댓글 좋아요 — ANT-COMMUNITY-03.
 *
 * <p>대댓글은 없다(단층). 좋아요 수는 별도 컬럼이 아니라 {@code count(*)} 로 센다 —
 * 카운터 컬럼을 두면 동시 증감이 유실되지 않게 잠금이 필요하고, 취소까지 있어서 값이
 * 실제 행 수와 어긋나면 되돌릴 근거가 없다.
 *
 * <p>멱등성 헤더는 받지 않는다. 명세 §1 의 필수 대상 8개에 댓글·좋아요가 없고, 둘 다
 * 되돌릴 수단이 있다 — 좋아요는 DELETE 가 있고 중복은 UQ 가 막는다.
 */
@Service
@RequiredArgsConstructor
public class CommentService {

    private static final int DEFAULT_PAGE_SIZE = 20;
    private static final int MAX_PAGE_SIZE = 50;

    private final FeedPostRepository feedPostRepository;
    private final PostCommentRepository postCommentRepository;
    private final PostLikeRepository postLikeRepository;
    private final CommentLikeRepository commentLikeRepository;
    private final UserRepository userRepository;
    private final CurrentUserProvider currentUserProvider;

    @Transactional
    public Long create(Long postId, CommentCreateRequest request) {
        Long userId = currentUserProvider.currentUserId();
        FeedPost post = visiblePost(postId);
        User author = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.UNAUTHENTICATED));

        PostComment comment = PostComment.create(post, author, request.body());
        return postCommentRepository.save(comment).getId();
    }

    @Transactional(readOnly = true)
    public CommentListResponse list(Long postId, Long cursor, Integer size) {
        Long viewerId = currentUserProvider.currentUserId();
        visiblePost(postId);
        int pageSize = pageSize(size);

        // 한 건 더 읽어 다음 페이지가 있는지 본다(글 목록과 같은 방식).
        List<PostComment> found = postCommentRepository.findPage(
                postId, PostComment.Status.VISIBLE, cursor, Limit.of(pageSize + 1));
        boolean hasNext = found.size() > pageSize;
        List<PostComment> comments = hasNext ? found.subList(0, pageSize) : found;

        if (comments.isEmpty()) {
            return new CommentListResponse(List.of(), null, false);
        }

        List<Long> commentIds = comments.stream().map(PostComment::getId).toList();
        Map<Long, Long> likeCounts = countsById(commentLikeRepository.countByCommentIds(commentIds));
        Set<Long> liked =
                new HashSet<>(commentLikeRepository.findLikedCommentIds(viewerId, commentIds));

        List<CommentListItemResponse> items = comments.stream()
                .map(comment -> new CommentListItemResponse(
                        comment.getId(),
                        comment.getBody(),
                        AuthorResponse.from(comment.getUser()),
                        likeCounts.getOrDefault(comment.getId(), 0L),
                        liked.contains(comment.getId()),
                        comment.getCreatedAt()))
                .toList();

        // 오래된 순이라 다음 커서는 마지막(가장 최근) 항목이다.
        Long nextCursor = hasNext ? comments.get(comments.size() - 1).getId() : null;
        return new CommentListResponse(items, nextCursor, hasNext);
    }

    @Transactional
    public void likePost(Long postId) {
        Long userId = currentUserProvider.currentUserId();
        FeedPost post = visiblePost(postId);

        if (postLikeRepository.existsByPostIdAndUserId(postId, userId)) {
            throw new BusinessException(ErrorCode.DUPLICATE_LIKE);
        }
        User user = currentUser(userId);
        save(() -> postLikeRepository.save(PostLike.create(post, user)));
    }

    /** 없던 공감의 취소도 204 다. 명세에 404 가 없고, 두 번 눌러도 결과가 같은 게 화면에 맞다. */
    @Transactional
    public void unlikePost(Long postId) {
        Long userId = currentUserProvider.currentUserId();
        visiblePost(postId);
        postLikeRepository.deleteByPostIdAndUserId(postId, userId);
    }

    @Transactional
    public void likeComment(Long commentId) {
        Long userId = currentUserProvider.currentUserId();
        PostComment comment = visibleComment(commentId);

        if (commentLikeRepository.existsByCommentIdAndUserId(commentId, userId)) {
            throw new BusinessException(ErrorCode.DUPLICATE_LIKE);
        }
        User user = currentUser(userId);
        save(() -> commentLikeRepository.save(CommentLike.create(comment, user)));
    }

    @Transactional
    public void unlikeComment(Long commentId) {
        Long userId = currentUserProvider.currentUserId();
        visibleComment(commentId);
        commentLikeRepository.deleteByCommentIdAndUserId(commentId, userId);
    }

    /**
     * UQ 위반도 409 로 바꾼다. 위의 exists 검사와 저장 사이에 같은 사용자의 두 번째 요청이
     * 끼어들면(따로 열린 트랜잭션) 검사를 둘 다 통과한다 — 그 창을 막는 건 DB 제약뿐이고,
     * 남은 일은 예외를 같은 응답으로 번역하는 것이다.
     */
    private void save(Runnable saving) {
        try {
            saving.run();
        } catch (DataIntegrityViolationException e) {
            throw new BusinessException(ErrorCode.DUPLICATE_LIKE);
        }
    }

    /** BLOCKED 글은 없는 것처럼 다룬다 — 글 상세(02)와 같은 이유로 403 이 아니라 404 다. */
    private FeedPost visiblePost(Long postId) {
        FeedPost post = feedPostRepository.findById(postId)
                .orElseThrow(() -> new BusinessException(ErrorCode.POST_NOT_FOUND, "postId"));
        if (post.isBlocked()) {
            throw new BusinessException(ErrorCode.POST_NOT_FOUND, "postId");
        }
        return post;
    }

    /** BLOCKED 댓글도 같다. 가려진 댓글에 좋아요를 눌러 존재를 확인할 수 있으면 안 된다. */
    private PostComment visibleComment(Long commentId) {
        PostComment comment = postCommentRepository.findById(commentId)
                .orElseThrow(() -> new BusinessException(ErrorCode.COMMENT_NOT_FOUND, "commentId"));
        if (comment.isBlocked()) {
            throw new BusinessException(ErrorCode.COMMENT_NOT_FOUND, "commentId");
        }
        return comment;
    }

    private User currentUser(Long userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.UNAUTHENTICATED));
    }

    /** {@code [id, count]} 행들을 맵으로. 집계에 없는 대상은 호출부가 0으로 채운다. */
    private Map<Long, Long> countsById(Collection<Object[]> rows) {
        Map<Long, Long> counts = new HashMap<>();
        for (Object[] row : rows) {
            counts.put((Long) row[0], (Long) row[1]);
        }
        return counts;
    }

    private int pageSize(Integer size) {
        if (size == null || size <= 0) {
            return DEFAULT_PAGE_SIZE;
        }
        return Math.min(size, MAX_PAGE_SIZE);
    }
}
