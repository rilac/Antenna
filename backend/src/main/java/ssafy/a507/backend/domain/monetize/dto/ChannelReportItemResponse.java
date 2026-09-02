package ssafy.a507.backend.domain.monetize.dto;

import java.time.Instant;
import ssafy.a507.backend.domain.monetize.entity.Report;

/**
 * GET /api/v1/channels/{userId}/reports 목록 한 줄 — 제목·발행일·공개 여부.
 *
 * <p>{@code visibility} 와 {@code locked} 는 다른 것을 말한다. {@code visibility} 는 리포트
 * 자체의 속성(전체 공개인가)이고 {@code locked} 는 <b>이 요청자가</b> 본문을 볼 수 있는가다.
 * 구독자에게는 {@code visibility=false, locked=false} 가 나온다 — 화면은 잠금 배지에
 * {@code locked} 를, "구독자 전용" 배지에 {@code visibility} 를 쓴다.
 */
public record ChannelReportItemResponse(
        Long id,
        String title,
        boolean visibility,
        boolean locked,
        Instant publishedAt,
        int viewCount) {

    public static ChannelReportItemResponse of(Report report, boolean locked) {
        return new ChannelReportItemResponse(
                report.getId(),
                report.getTitle(),
                report.isPublic(),
                locked,
                report.getCreatedAt(),
                report.getViewCount());
    }
}
