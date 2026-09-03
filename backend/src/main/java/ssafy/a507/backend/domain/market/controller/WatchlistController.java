package ssafy.a507.backend.domain.market.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import ssafy.a507.backend.common.security.CurrentUserProvider;
import ssafy.a507.backend.domain.market.dto.WatchlistAddRequest;
import ssafy.a507.backend.domain.market.dto.WatchlistAddResponse;
import ssafy.a507.backend.domain.market.dto.WatchlistResponse;
import ssafy.a507.backend.domain.market.service.WatchlistService;

/** 관심 종목 — ANT-DATA-06. 명세 §홈·시세 {@code /watchlist}. 전부 내 것만 다룬다. */
@RestController
@RequestMapping("/api/v1/watchlist")
@RequiredArgsConstructor
public class WatchlistController {

    private final WatchlistService watchlistService;
    private final CurrentUserProvider currentUserProvider;

    @GetMapping
    public WatchlistResponse list() {
        return watchlistService.list(currentUserProvider.currentUserId());
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public WatchlistAddResponse add(@Valid @RequestBody WatchlistAddRequest request) {
        return watchlistService.add(currentUserProvider.currentUserId(), request.stockCode());
    }

    /** 없는 것을 빼도 204 — 두 번 눌러도 결과가 같다. */
    @DeleteMapping("/{stockCode}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void remove(@PathVariable String stockCode) {
        watchlistService.remove(currentUserProvider.currentUserId(), stockCode);
    }
}
