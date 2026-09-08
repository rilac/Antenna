package ssafy.a507.backend.domain.season.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import ssafy.a507.backend.common.security.CurrentUserProvider;
import ssafy.a507.backend.domain.season.dto.MySeasonListResponse;
import ssafy.a507.backend.domain.season.dto.MySeasonStatus;
import ssafy.a507.backend.domain.season.dto.SeasonDetailResponse;
import ssafy.a507.backend.domain.season.dto.SeasonListResponse;
import ssafy.a507.backend.domain.season.dto.SeasonPriceListResponse;
import ssafy.a507.backend.domain.season.dto.SeasonTickerListResponse;
import ssafy.a507.backend.domain.season.entity.Season;
import ssafy.a507.backend.domain.season.service.SeasonQueryService;

/**
 * 모의투자 시즌 조회 — 명세 §모의투자 {@code /seasons}. G-01 홈 · G-02a 연습하기 ·
 * G-03 시즌 상세가 쓰는 세 경로다.
 *
 * <p>{@code /me} 를 {@code /{seasonId}} 보다 위에 둔다. 아래에 두면 "me" 가 경로 변수로
 * 잡혀 숫자 변환에서 400 이 난다.
 */
@RestController
@RequestMapping("/api/v1/seasons")
@RequiredArgsConstructor
public class SeasonController {

    private final SeasonQueryService seasonQueryService;
    private final CurrentUserProvider currentUserProvider;

    @GetMapping
    public SeasonListResponse list(
            @RequestParam(required = false) Season.Mode mode,
            @RequestParam(required = false) Season.Status status) {
        return seasonQueryService.list(currentUserProvider.currentUserId(), mode, status);
    }

    @GetMapping("/me")
    public MySeasonListResponse mine(@RequestParam(required = false) MySeasonStatus status) {
        return seasonQueryService.mine(currentUserProvider.currentUserId(), status);
    }

    @GetMapping("/{seasonId}")
    public SeasonDetailResponse detail(@PathVariable Long seasonId) {
        return seasonQueryService.detail(currentUserProvider.currentUserId(), seasonId);
    }

    @GetMapping("/{seasonId}/tickers")
    public SeasonTickerListResponse tickers(@PathVariable Long seasonId) {
        return seasonQueryService.tickers(currentUserProvider.currentUserId(), seasonId);
    }

    /**
     * {@code uptoDay} 는 상한을 <b>낮추는</b> 데만 쓴다. 진행일보다 크게 넣어도 진행일에서
     * 잘린다 — 요청으로 커닝 상한을 넘길 수 없다.
     */
    @GetMapping("/{seasonId}/tickers/{tickerId}/prices")
    public SeasonPriceListResponse prices(
            @PathVariable Long seasonId,
            @PathVariable Long tickerId,
            @RequestParam(required = false) Integer uptoDay) {
        return seasonQueryService.prices(
                currentUserProvider.currentUserId(), seasonId, tickerId, uptoDay);
    }
}
