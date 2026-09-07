package ssafy.a507.backend.domain.prediction.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import ssafy.a507.backend.common.security.CurrentUserProvider;
import ssafy.a507.backend.domain.prediction.dto.MyPredictionListResponse;
import ssafy.a507.backend.domain.prediction.service.MyPredictionQueryService;

/**
 * 내 예측 목록 — ANT-PRED-06, 화면 C-02. 명세 §1 의 {@code Base /api/v1} 을 경로에 직접 적는다.
 *
 * <p>같은 스토리의 슬롯·포트폴리오·심리 통계 세 API 는 아직 없다. 화면 C-02 가 막혀 있어 목록만 먼저 낸다.
 */
@RestController
@RequestMapping("/api/v1/predictions")
@RequiredArgsConstructor
public class MyPredictionController {

    private final MyPredictionQueryService myPredictionQueryService;
    private final CurrentUserProvider currentUserProvider;

    @GetMapping("/me")
    public MyPredictionListResponse mine(
            @RequestParam(required = false) MyPredictionQueryService.StatusFilter status,
            @RequestParam(required = false) Long cursor,
            @RequestParam(required = false) Integer size) {
        return myPredictionQueryService.list(
                currentUserProvider.currentUserId(), status, cursor, size);
    }
}
