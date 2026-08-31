package ssafy.a507.backend.domain.community.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.time.Instant;
import ssafy.a507.backend.domain.monetize.entity.Report;

/**
 * 리포스팅된 원본 리포트 카드.
 *
 * <p>명세: 제목은 항상 공개다(구독 유인). {@code locked} 가 true 면 본문을 볼 수 없다는
 * 뜻이고, 카드에는 원래 본문을 싣지 않으므로 화면은 잠금 배지와 구독 유도만 그린다.
 * 잠금 판정은 원본 리포트에서 한다 — 리포스팅한 사람의 구독 상태와 무관하다.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ReportCardResponse(
        Long id, String title, AuthorResponse author, boolean locked, Instant publishedAt) {

    public static ReportCardResponse of(Report report, boolean locked) {
        return new ReportCardResponse(
                report.getId(),
                report.getTitle(),
                AuthorResponse.from(report.getUser()),
                locked,
                report.getCreatedAt());
    }
}
