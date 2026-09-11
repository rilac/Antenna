package ssafy.a507.backend.domain.prediction.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import ssafy.a507.backend.domain.prediction.dto.ChannelPredictionListResponse;
import ssafy.a507.backend.domain.prediction.service.MyPredictionQueryService;
import ssafy.a507.backend.domain.prediction.service.PredictionViewService;

/**
 * 채널 예측 목록 (ANT-PRED-05, 화면 E-02). {@code ChannelReportController} 처럼 경로가 {@code /channels} 아래라 따로 둔다.
 *
 * <p>필터 이름은 명세대로 {@code status} 다(프론트 요청문의 {@code phase} 가 아니다). 어휘는 {@code /predictions/me} 와 같은
 * {@code ALL|PENDING|HIT|MISS|JUDGED}, 어휘 밖 값은 400.
 */
@RestController
@RequestMapping("/api/v1/channels")
@RequiredArgsConstructor
public class ChannelPredictionController {

    private final PredictionViewService predictionViewService;

    @GetMapping("/{userId}/predictions")
    public ChannelPredictionListResponse list(
            @PathVariable Long userId,
            @RequestParam(required = false) MyPredictionQueryService.StatusFilter status,
            @RequestParam(required = false) Long cursor,
            @RequestParam(required = false) Integer size) {
        return predictionViewService.channelPredictions(userId, status, cursor, size);
    }
}
