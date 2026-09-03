package ssafy.a507.backend.domain.research.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import ssafy.a507.backend.domain.research.dto.CorpFinancialListResponse;
import ssafy.a507.backend.domain.research.dto.CorpProfileResponse;
import ssafy.a507.backend.domain.research.dto.PeerListResponse;
import ssafy.a507.backend.domain.research.dto.ValuationResponse;
import ssafy.a507.backend.domain.research.service.CorpInfoService;

/**
 * 종목 리서치 탭의 기업개요·재무·밸류에이션·경쟁사 — ANT-RESEARCH-05 (명세 -216). 차트 탭은
 * {@code GET /stocks/{code}/prices}(market) 를 그대로 쓴다.
 */
@RestController
@RequestMapping("/api/v1/stocks")
@RequiredArgsConstructor
public class CorpInfoController {

    private final CorpInfoService corpInfoService;

    @GetMapping("/{code}/profile")
    public CorpProfileResponse profile(@PathVariable String code) {
        return corpInfoService.profile(code);
    }

    /** @param years 조회 연수 · 기본 3, 상한 10 */
    @GetMapping("/{code}/financials")
    public CorpFinancialListResponse financials(
            @PathVariable String code, @RequestParam(required = false) Integer years) {
        return corpInfoService.financials(code, years);
    }

    @GetMapping("/{code}/valuation")
    public ValuationResponse valuation(@PathVariable String code) {
        return corpInfoService.valuation(code);
    }

    @GetMapping("/{code}/peers")
    public PeerListResponse peers(@PathVariable String code) {
        return corpInfoService.peers(code);
    }
}
