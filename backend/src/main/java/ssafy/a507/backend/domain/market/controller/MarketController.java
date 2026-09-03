package ssafy.a507.backend.domain.market.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import ssafy.a507.backend.domain.market.dto.MarketIndexListResponse;
import ssafy.a507.backend.domain.market.service.MarketIndexService;

/** 시장 Overview — ANT-DATA-04. 명세 §홈·시세 {@code GET /market/indices}. */
@RestController
@RequestMapping("/api/v1/market")
@RequiredArgsConstructor
public class MarketController {

    private final MarketIndexService marketIndexService;

    /**
     * 지수 3종(KOSPI · KOSDAQ · USDKRW)의 최신 종가·등락률과 미니차트용 시계열.
     *
     * @param days 시계열 점 수 · 기본 30
     */
    @GetMapping("/indices")
    public MarketIndexListResponse indices(@RequestParam(required = false) Integer days) {
        return marketIndexService.indices(days);
    }
}
