package ssafy.a507.backend.domain.monetize.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * POST /api/v1/reports 요청 본문. 명세 §리포트·글의 세 필드 그대로다.
 *
 * <p>{@code visibility} 를 {@code Boolean} 으로 둔 이유 — {@code boolean} 이면 필드를 아예
 * 빼고 보낸 요청이 false(구독자 전용)로 조용히 처리된다. 공개 여부는 기본값으로 정할 값이
 * 아니라서 누락을 400 으로 잡는다.
 */
public record ReportCreateRequest(

        @NotBlank(message = "제목을 입력해야 합니다.")
        @Size(max = 100, message = "제목은 100자를 넘을 수 없습니다.")
        String title,

        @NotBlank(message = "본문을 입력해야 합니다.")
        @Size(max = 20000, message = "본문은 20000자를 넘을 수 없습니다.")
        String body,

        @NotNull(message = "공개 여부를 지정해야 합니다.")
        Boolean visibility
) {
}
