package ssafy.a507.backend.domain.market.controller;

import java.time.LocalDate;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import ssafy.a507.backend.domain.market.dto.StockListResponse;
import ssafy.a507.backend.domain.market.dto.StockPriceListResponse;
import ssafy.a507.backend.domain.market.service.StockService;

/**
 * 종목 목록·시세 — ANT-DATA-03. 명세 §1 의 {@code Base /api/v1} 을 경로에 직접 적는다.
 *
 * <p>경로가 지라 티켓의 {@code /api/stocks/{code}/quotes} 가 아니라 명세서의
 * {@code /stocks/{code}/prices} 인 이유 — 프론트 api/client.ts 와 §1 Base 가 그쪽으로 굳어
 * 있고, 경로 계약 테스트가 {@code /api/v1} 밖의 경로를 실패시킨다.
 */
@RestController
@RequestMapping("/api/v1/stocks")
@RequiredArgsConstructor
public class StockController {

    private final StockService stockService;

    @GetMapping
    public StockListResponse list(
            @RequestParam(required = false) String cursor,
            @RequestParam(required = false) Integer size) {
        return stockService.list(cursor, size);
    }

    /**
     * 실전 일봉 시계열 — 종가만. 구간을 주지 않으면 마지막 영업일까지의 최근 30일이다.
     */
    @GetMapping("/{code}/prices")
    public StockPriceListResponse prices(
            @PathVariable String code,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
                    LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
                    LocalDate to) {
        return stockService.prices(code, from, to);
    }
}
