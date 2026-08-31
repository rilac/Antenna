package ssafy.a507.backend.domain.community.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import ssafy.a507.backend.domain.community.entity.AbuseReport;

public interface AbuseReportRepository extends JpaRepository<AbuseReport, Long> {

    /**
     * 같은 신고자가 같은 대상을 이미 접수해 두었는지.
     * PENDING 만 본다 — 관리자가 처리(ACCEPTED/REJECTED)한 뒤의 재신고는 새 사안으로 받는다.
     */
    boolean existsByReporterIdAndTargetPostIdAndStatus(
            Long reporterId, Long targetPostId, AbuseReport.Status status);

    boolean existsByReporterIdAndTargetCommentIdAndStatus(
            Long reporterId, Long targetCommentId, AbuseReport.Status status);

    boolean existsByReporterIdAndTargetUserIdAndStatus(
            Long reporterId, Long targetUserId, AbuseReport.Status status);
}
