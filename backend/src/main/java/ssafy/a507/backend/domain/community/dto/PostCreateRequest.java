package ssafy.a507.backend.domain.community.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

/**
 * POST /api/v1/posts 요청 본문.
 *
 * <p>명세의 {@code imageFileIds} 는 받지 않는다 — 그 값을 발급하는 POST /uploads 스토리가
 * 아직 없다(담당 에픽 미정). 외부 URL 을 대신 받는 우회는 두지 않았다. 명세 §첨부·업로드가
 * 그것을 금지하는 이유가 셋이다: 서버 프리뷰 시 SSRF, 추적 픽셀로 열람자 IP 수집,
 * 승인 후 URL 내용 바꿔치기.
 */
public record PostCreateRequest(

        @NotBlank(message = "본문을 입력해야 합니다.")
        @Size(max = 5000, message = "본문은 5000자를 넘을 수 없습니다.")
        String body,

        @Positive(message = "리포트 ID가 올바르지 않습니다.")
        Long reportId,

        @Positive(message = "예측 ID가 올바르지 않습니다.")
        Long predictionId
) {
}
