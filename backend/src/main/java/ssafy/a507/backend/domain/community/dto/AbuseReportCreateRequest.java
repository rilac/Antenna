package ssafy.a507.backend.domain.community.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import ssafy.a507.backend.domain.community.entity.AbuseReport;

/** POST /api/abuse-reports 요청 본문. */
public record AbuseReportCreateRequest(

        @NotNull(message = "신고 대상 종류를 지정해야 합니다.")
        TargetType targetType,

        @NotNull(message = "신고 대상 ID를 지정해야 합니다.")
        @Positive(message = "신고 대상 ID가 올바르지 않습니다.")
        Long targetId,

        @NotNull(message = "신고 사유를 지정해야 합니다.")
        AbuseReport.Reason reason,

        @Size(max = 300, message = "상세 사유는 300자를 넘을 수 없습니다.")
        String detail
) {
    public enum TargetType {
        POST,
        COMMENT,
        USER
    }
}
