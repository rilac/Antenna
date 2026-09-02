package ssafy.a507.backend.domain.monetize.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.time.Instant;
import java.util.Arrays;
import ssafy.a507.backend.domain.community.dto.AuthorResponse;
import ssafy.a507.backend.domain.monetize.entity.Report;

/**
 * GET /api/v1/reports/{id} 200 응답. 잠기지 않았으면 {@code body}(전문), 잠겼으면
 * {@code preview}(앞 3줄)가 실린다 — 둘 중 하나만 나가고 나머지 키는 생략된다.
 *
 * <p>키를 바꿔 내리는 이유는 전문과 미리보기를 같은 필드에 담으면 프론트가 잘린 본문을
 * 전문으로 착각해 그리기 때문이다. 명세도 잠금 응답을
 * {@code { preview: "앞 3줄", locked: true }} 로 적고 있다.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ReportDetailResponse(
        Long id,
        String title,
        AuthorResponse author,
        boolean visibility,
        boolean locked,
        String body,
        String preview,
        Instant publishedAt,
        int viewCount) {

    /** 미리보기 줄 수. 명세가 "앞 3줄"로 못박은 값이다. */
    private static final int PREVIEW_LINES = 3;

    /**
     * 미리보기 글자 수 상한.
     *
     * <p>줄 수만으로 자르면 잠금이 무의미해진다 — 개행 없이 20000자를 쓴 본문은 1줄이라
     * "앞 3줄"이 전문과 같아진다. 명세에 없는 값이고 이 구현에서 정했다.
     */
    private static final int PREVIEW_MAX_CHARS = 300;

    /** 전문 공개. {@code viewCount} 는 이번 열람을 반영한 값을 호출부가 넘긴다. */
    public static ReportDetailResponse full(Report report, int viewCount) {
        return new ReportDetailResponse(
                report.getId(),
                report.getTitle(),
                AuthorResponse.from(report.getUser()),
                report.isPublic(),
                false,
                report.getBody(),
                null,
                report.getCreatedAt(),
                viewCount);
    }

    /** 잠금. 제목·작성자·통계는 그대로 공개하고 본문만 앞 3줄로 줄인다(구독 유인). */
    public static ReportDetailResponse locked(Report report, int viewCount) {
        return new ReportDetailResponse(
                report.getId(),
                report.getTitle(),
                AuthorResponse.from(report.getUser()),
                report.isPublic(),
                true,
                null,
                preview(report.getBody()),
                report.getCreatedAt(),
                viewCount);
    }

    private static String preview(String body) {
        if (body == null) {
            return null;
        }
        // limit 을 PREVIEW_LINES + 1 로 두면 앞 3줄까지만 쪼개고 나머지는 마지막 조각에 남는다.
        // 개행은 \r?\n 으로 본다 — \n 만 보면 CRLF 본문의 각 줄 끝에 \r 이 남는다.
        String[] lines = body.split("\r?\n", PREVIEW_LINES + 1);
        int take = Math.min(lines.length, PREVIEW_LINES);
        String head = String.join("\n", Arrays.copyOf(lines, take));
        if (head.length() <= PREVIEW_MAX_CHARS) {
            return head;
        }
        return cut(head, PREVIEW_MAX_CHARS);
    }

    /**
     * 글자 수로 자르되 서로게이트 쌍을 쪼개지 않는다.
     *
     * <p>이모지는 UTF-16 에서 두 칸을 쓴다. 경계가 그 사이에 걸리면 앞쪽 절반만 남고, Jackson
     * 은 짝 없는 이스케이프를 내보낸다 — 화면에는 깨진 글자가, 엄격한 JSON 파서에는 오류가 된다.
     * 한 칸 물러서면 이모지 하나가 빠지는 것으로 끝난다.
     */
    static String cut(String value, int max) {
        int end = Character.isHighSurrogate(value.charAt(max - 1)) ? max - 1 : max;
        return value.substring(0, end);
    }
}
