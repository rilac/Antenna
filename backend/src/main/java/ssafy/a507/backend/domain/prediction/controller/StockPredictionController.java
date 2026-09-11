package ssafy.a507.backend.domain.prediction.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import ssafy.a507.backend.domain.prediction.dto.PredictionDistributionResponse;
import ssafy.a507.backend.domain.prediction.dto.SettledTodayResponse;
import ssafy.a507.backend.domain.prediction.service.StockPredictionService;

/**
 * 종목 상세의 예측 블록 (ANT-PRED-07). 경로는 {@code /stocks} 아래지만 재료가 전부 예측이라 prediction 도메인에 둔다 —
 * 리서치 컨트롤러들이 같은 base path 를 나눠 쓰는 것과 같은 방식이다.
 */
@RestController
@RequestMapping("/api/v1/stocks")
@RequiredArgsConstructor
public class StockPredictionController {

    private final StockPredictionService stockPredictionService;

    /** 판정 대기 예측의 목표가 분포(호가창). 개인은 없고 구간별 인원만 있다. */
    @GetMapping("/{code}/predictions/distribution")
    public PredictionDistributionResponse distribution(@PathVariable String code) {
        return stockPredictionService.distribution(code);
    }

    /** 오늘 판정된 이 종목의 예측. 판정 후라 전체 공개다. */
    @GetMapping("/{code}/predictions/settled-today")
    public SettledTodayResponse settledToday(@PathVariable String code) {
        return stockPredictionService.settledToday(code);
    }
}
