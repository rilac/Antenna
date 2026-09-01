package ssafy.a507.backend.domain.community.service;

import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Limit;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ssafy.a507.backend.common.error.BusinessException;
import ssafy.a507.backend.common.error.ErrorCode;
import ssafy.a507.backend.common.security.CurrentUserProvider;
import ssafy.a507.backend.domain.account.entity.User;
import ssafy.a507.backend.domain.account.repository.UserRepository;
import ssafy.a507.backend.domain.community.dto.AuthorResponse;
import ssafy.a507.backend.domain.community.dto.CommentPreviewResponse;
import ssafy.a507.backend.domain.community.dto.PostCreateRequest;
import ssafy.a507.backend.domain.community.dto.PostDetailResponse;
import ssafy.a507.backend.domain.community.dto.PostListItemResponse;
import ssafy.a507.backend.domain.community.dto.PostListResponse;
import ssafy.a507.backend.domain.community.dto.PredictionCardResponse;
import ssafy.a507.backend.domain.community.dto.ReportCardResponse;
import ssafy.a507.backend.domain.community.entity.FeedPost;
import ssafy.a507.backend.domain.community.entity.PostComment;
import ssafy.a507.backend.domain.community.repository.FeedPostRepository;
import ssafy.a507.backend.domain.community.repository.PostCommentRepository;
import ssafy.a507.backend.domain.community.repository.PostLikeRepository;
import ssafy.a507.backend.domain.monetize.entity.Report;
import ssafy.a507.backend.domain.monetize.entity.Subscription;
import ssafy.a507.backend.domain.monetize.repository.ReportRepository;
import ssafy.a507.backend.domain.monetize.repository.SubscriptionRepository;
import ssafy.a507.backend.domain.prediction.entity.Prediction;
import ssafy.a507.backend.domain.prediction.repository.PredictionRepository;

/**
 * 피드 글 작성·조회.
 *
 * <p>글 자체에는 공개 범위가 없다(전체 공개). 잠기는 것은 <b>첨부된 것</b>뿐이다 —
 * 리포스팅한 리포트의 본문, 미판정 예측의 상세. 판정은 원본 소유자의 구독 상태로 하며
 * 리포스팅한 사람과는 무관하다.
 */
@Service
@RequiredArgsConstructor
public class PostService {

    /** 상세 화면에 함께 내려주는 댓글 미리보기 건수. */
    private static final int COMMENT_PREVIEW_SIZE = 3;

    private static final int DEFAULT_PAGE_SIZE = 20;
    private static final int MAX_PAGE_SIZE = 50;

    private final FeedPostRepository feedPostRepository;
    private final PostLikeRepository postLikeRepository;
    private final PostCommentRepository postCommentRepository;
    private final SubscriptionRepository subscriptionRepository;
    private final ReportRepository reportRepository;
    private final PredictionRepository predictionRepository;
    private final UserRepository userRepository;
    private final CurrentUserProvider currentUserProvider;

    @Transactional
    public Long create(PostCreateRequest request) {
        Long userId = currentUserProvider.currentUserId();
        User author = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.UNAUTHENTICATED));

        Report report = null;
        if (request.reportId() != null) {
            report = reportRepository.findById(request.reportId())
                    .orElseThrow(() -> new BusinessException(ErrorCode.REPORT_NOT_FOUND, "reportId"));
        }

        Prediction prediction = null;
        if (request.predictionId() != null) {
            prediction = predictionRepository.findById(request.predictionId())
                    .orElseThrow(
                            () -> new BusinessException(ErrorCode.PREDICTION_NOT_FOUND, "predictionId"));
        }

        FeedPost post = FeedPost.create(author, request.body(), report, prediction);
        return feedPostRepository.save(post).getId();
    }

    @Transactional(readOnly = true)
    public PostListResponse list(Long cursor, Integer size) {
        Long viewerId = currentUserProvider.currentUserId();
        int pageSize = pageSize(size);

        // 한 건 더 읽어 다음 페이지가 있는지 본다. count 쿼리를 따로 돌리지 않는 흔한 방법이다.
        List<FeedPost> found = feedPostRepository.findPage(
                FeedPost.Status.VISIBLE, cursor, Limit.of(pageSize + 1));
        boolean hasNext = found.size() > pageSize;
        List<FeedPost> posts = hasNext ? found.subList(0, pageSize) : found;

        if (posts.isEmpty()) {
            return new PostListResponse(List.of(), null, false);
        }

        List<Long> postIds = posts.stream().map(FeedPost::getId).toList();
        Map<Long, Long> likeCounts = countsByPostId(postLikeRepository.countByPostIds(postIds));
        Map<Long, Long> commentCounts = countsByPostId(
                postCommentRepository.countByPostIds(postIds, PostComment.Status.VISIBLE));
        Set<Long> liked = new HashSet<>(postLikeRepository.findLikedPostIds(viewerId, postIds));
        Set<Long> subscribed = subscribedPublisherIds(viewerId, posts);

        List<PostListItemResponse> items = posts.stream()
                .map(post -> new PostListItemResponse(
                        post.getId(),
                        post.getBody(),
                        AuthorResponse.from(post.getUser()),
                        reportCard(post.getReport(), viewerId, subscribed),
                        predictionCard(post.getPrediction(), viewerId, subscribed),
                        likeCounts.getOrDefault(post.getId(), 0L),
                        liked.contains(post.getId()),
                        commentCounts.getOrDefault(post.getId(), 0L),
                        post.getCreatedAt()))
                .toList();

        Long nextCursor = hasNext ? posts.get(posts.size() - 1).getId() : null;
        return new PostListResponse(items, nextCursor, hasNext);
    }

    @Transactional(readOnly = true)
    public PostDetailResponse detail(Long postId) {
        Long viewerId = currentUserProvider.currentUserId();

        FeedPost post = feedPostRepository.findDetailById(postId)
                .orElseThrow(() -> new BusinessException(ErrorCode.POST_NOT_FOUND, "postId"));
        // 블라인드된 글은 없는 것처럼 다룬다. 403 을 주면 "가려진 글이 여기 있었다"를 알려준다.
        if (post.isBlocked()) {
            throw new BusinessException(ErrorCode.POST_NOT_FOUND, "postId");
        }

        List<Long> postIds = List.of(post.getId());
        long likeCount = countsByPostId(postLikeRepository.countByPostIds(postIds))
                .getOrDefault(post.getId(), 0L);
        long commentCount = countsByPostId(
                        postCommentRepository.countByPostIds(postIds, PostComment.Status.VISIBLE))
                .getOrDefault(post.getId(), 0L);
        boolean liked = postLikeRepository.existsByPostIdAndUserId(post.getId(), viewerId);
        Set<Long> subscribed = subscribedPublisherIds(viewerId, List.of(post));

        List<CommentPreviewResponse> comments = postCommentRepository
                .findPreview(post.getId(), PostComment.Status.VISIBLE, Limit.of(COMMENT_PREVIEW_SIZE))
                .stream()
                .map(CommentPreviewResponse::from)
                .toList();

        return new PostDetailResponse(
                post.getId(),
                post.getBody(),
                AuthorResponse.from(post.getUser()),
                reportCard(post.getReport(), viewerId, subscribed),
                predictionCard(post.getPrediction(), viewerId, subscribed),
                likeCount,
                liked,
                commentCount,
                post.getCreatedAt(),
                comments);
    }

    /**
     * 리포트 카드. 비공개 리포트를 미구독자가 보면 잠근다 — 제목·작성자는 그대로 공개다
     * (명세: 제목은 항상 공개, 구독 유인).
     */
    private ReportCardResponse reportCard(Report report, Long viewerId, Set<Long> subscribed) {
        if (report == null) {
            return null;
        }
        boolean locked = !report.isPublic() && !canRead(report.getUser().getId(), viewerId, subscribed);
        return ReportCardResponse.of(report, locked);
    }

    /** 예측 카드. 미판정 예측을 미구독자가 보면 잠근다. 판정 완료는 전체 공개라 잠기지 않는다. */
    private PredictionCardResponse predictionCard(
            Prediction prediction, Long viewerId, Set<Long> subscribed) {
        if (prediction == null) {
            return null;
        }
        boolean locked = PredictionCardResponse.isGated(prediction)
                && !canRead(prediction.getUser().getId(), viewerId, subscribed);
        return PredictionCardResponse.of(prediction, locked);
    }

    /** 본인이거나 그 채널을 ACTIVE 로 구독 중이면 잠기지 않는다. */
    private boolean canRead(Long ownerId, Long viewerId, Set<Long> subscribed) {
        return ownerId.equals(viewerId) || subscribed.contains(ownerId);
    }

    /**
     * 이 페이지에 등장하는 첨부물 소유자 중 내가 구독 중인 사람들. 글마다 구독 여부를 묻지
     * 않고 한 번에 받아온다.
     */
    private Set<Long> subscribedPublisherIds(Long viewerId, List<FeedPost> posts) {
        Set<Long> ownerIds = new HashSet<>();
        for (FeedPost post : posts) {
            if (post.getReport() != null) {
                ownerIds.add(post.getReport().getUser().getId());
            }
            if (post.getPrediction() != null) {
                ownerIds.add(post.getPrediction().getUser().getId());
            }
        }
        ownerIds.remove(viewerId);
        if (ownerIds.isEmpty()) {
            return Set.of();
        }
        return new HashSet<>(subscriptionRepository.findSubscribedPublisherIds(
                viewerId, ownerIds, Subscription.Status.ACTIVE));
    }

    /** {@code [postId, count]} 행들을 맵으로. 집계에 없는 글은 호출부가 0으로 채운다. */
    private Map<Long, Long> countsByPostId(Collection<Object[]> rows) {
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
