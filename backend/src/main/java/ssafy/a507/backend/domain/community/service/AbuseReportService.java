package ssafy.a507.backend.domain.community.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ssafy.a507.backend.common.error.BusinessException;
import ssafy.a507.backend.common.error.ErrorCode;
import ssafy.a507.backend.common.security.CurrentUserProvider;
import ssafy.a507.backend.domain.account.entity.User;
import ssafy.a507.backend.domain.account.repository.UserRepository;
import ssafy.a507.backend.domain.community.entity.AbuseReport;
import ssafy.a507.backend.domain.community.entity.FeedPost;
import ssafy.a507.backend.domain.community.entity.PostComment;
import ssafy.a507.backend.domain.community.dto.AbuseReportCreateRequest;
import ssafy.a507.backend.domain.community.repository.AbuseReportRepository;
import ssafy.a507.backend.domain.community.repository.FeedPostRepository;
import ssafy.a507.backend.domain.community.repository.PostCommentRepository;

/**
 * 신고 접수. 접수까지만 하고 블라인드 처리는 하지 않는다 —
 * 대상을 BLOCKED 로 내리는 것은 관리자 몫이다(ANT-ADMIN-01).
 */
@Service
@RequiredArgsConstructor
public class AbuseReportService {

    private final AbuseReportRepository abuseReportRepository;
    private final FeedPostRepository feedPostRepository;
    private final PostCommentRepository postCommentRepository;
    private final UserRepository userRepository;
    private final CurrentUserProvider currentUserProvider;

    @Transactional
    public Long report(AbuseReportCreateRequest request) {
        Long reporterId = currentUserProvider.currentUserId();
        User reporter = userRepository.findById(reporterId)
                .orElseThrow(() -> new BusinessException(ErrorCode.UNAUTHENTICATED));

        AbuseReport report = switch (request.targetType()) {
            case POST -> againstPost(reporter, request);
            case COMMENT -> againstComment(reporter, request);
            case USER -> againstUser(reporter, request);
        };
        return abuseReportRepository.save(report).getId();
    }

    private AbuseReport againstPost(User reporter, AbuseReportCreateRequest request) {
        FeedPost post = feedPostRepository.findById(request.targetId())
                .orElseThrow(() -> new BusinessException(ErrorCode.TARGET_NOT_FOUND, "targetId"));
        rejectSelf(reporter.getId(), post.getUser().getId());
        rejectDuplicate(abuseReportRepository.existsByReporterIdAndTargetPostIdAndStatus(
                reporter.getId(), post.getId(), AbuseReport.Status.PENDING));
        return AbuseReport.againstPost(reporter, post, request.reason(), request.detail());
    }

    private AbuseReport againstComment(User reporter, AbuseReportCreateRequest request) {
        PostComment comment = postCommentRepository.findById(request.targetId())
                .orElseThrow(() -> new BusinessException(ErrorCode.TARGET_NOT_FOUND, "targetId"));
        rejectSelf(reporter.getId(), comment.getUser().getId());
        rejectDuplicate(abuseReportRepository.existsByReporterIdAndTargetCommentIdAndStatus(
                reporter.getId(), comment.getId(), AbuseReport.Status.PENDING));
        return AbuseReport.againstComment(reporter, comment, request.reason(), request.detail());
    }

    private AbuseReport againstUser(User reporter, AbuseReportCreateRequest request) {
        User target = userRepository.findById(request.targetId())
                .orElseThrow(() -> new BusinessException(ErrorCode.TARGET_NOT_FOUND, "targetId"));
        rejectSelf(reporter.getId(), target.getId());
        rejectDuplicate(abuseReportRepository.existsByReporterIdAndTargetUserIdAndStatus(
                reporter.getId(), target.getId(), AbuseReport.Status.PENDING));
        return AbuseReport.againstUser(reporter, target, request.reason(), request.detail());
    }

    private void rejectSelf(Long reporterId, Long targetOwnerId) {
        if (reporterId.equals(targetOwnerId)) {
            throw new BusinessException(ErrorCode.SELF_REPORT, "targetId");
        }
    }

    private void rejectDuplicate(boolean exists) {
        if (exists) {
            throw new BusinessException(ErrorCode.DUPLICATE_REPORT, "targetId");
        }
    }
}
