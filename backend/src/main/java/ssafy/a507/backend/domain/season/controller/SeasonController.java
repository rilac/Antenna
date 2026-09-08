package ssafy.a507.backend.domain.season.controller;

import jakarta.validation.Valid;
import java.math.BigDecimal;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import ssafy.a507.backend.common.idempotency.IdempotencyStore;
import ssafy.a507.backend.common.security.CurrentUserProvider;
import ssafy.a507.backend.domain.season.dto.MySeasonListResponse;
import ssafy.a507.backend.domain.season.dto.MySeasonStatus;
import ssafy.a507.backend.domain.season.dto.MySeasonStatusResponse;
import ssafy.a507.backend.domain.season.dto.SeasonDetailResponse;
import ssafy.a507.backend.domain.season.dto.SeasonJoinResponse;
import ssafy.a507.backend.domain.season.dto.SeasonListResponse;
import ssafy.a507.backend.domain.season.dto.SeasonOrderRequest;
import ssafy.a507.backend.domain.season.dto.SeasonOrderResponse;
import ssafy.a507.backend.domain.season.dto.SeasonPriceListResponse;
import ssafy.a507.backend.domain.season.dto.SeasonTickerListResponse;
import ssafy.a507.backend.domain.season.dto.SeasonTradeListResponse;
import ssafy.a507.backend.domain.season.entity.Season;
import ssafy.a507.backend.domain.season.entity.SeasonTrade;
import ssafy.a507.backend.domain.season.service.SeasonJoinService;
import ssafy.a507.backend.domain.season.service.SeasonPlayService;
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

    /** 멱등 저장소의 엔드포인트 식별자. 같은 키를 다른 API 에 써도 서로 간섭하지 않게 한다. */
    private static final String JOIN_ENDPOINT = "POST /seasons/{id}/join";
    private static final String ORDER_ENDPOINT = "POST /seasons/{id}/orders";

    private final SeasonQueryService seasonQueryService;
    private final SeasonJoinService seasonJoinService;
    private final SeasonPlayService seasonPlayService;
    private final IdempotencyStore idempotencyStore;
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

    /**
     * 시즌 참가 — 연습·시연은 즉시 201. 명세 §1 의 멱등성 필수 대상이라 Idempotency-Key 가
     * 없으면 400, 같은 키의 재요청은 처리 없이 첫 응답을 돌려준다 — 타임아웃 뒤 재시도가
     * 회차 둘을 만들지 않는다.
     *
     * <p>본문이 없는 요청이라 해시는 빈 문자열이다. 저장하는 값은 응답을 다시 만들 수 있는
     * 세 숫자뿐이다.
     */
    @PostMapping("/{seasonId}/join")
    public ResponseEntity<SeasonJoinResponse> join(
            @PathVariable Long seasonId,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey) {
        Long userId = currentUserProvider.currentUserId();
        String stored = idempotencyStore.execute(
                userId,
                JOIN_ENDPOINT + ":" + seasonId,
                idempotencyKey,
                IdempotencyStore.hash(""),
                () -> {
                    SeasonJoinService.Joined joined = seasonJoinService.join(userId, seasonId);
                    return joined.participantId() + ":" + joined.attemptNo() + ":" + joined.currentDay();
                });
        String[] parts = stored.split(":");
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(new SeasonJoinResponse(
                        Long.valueOf(parts[0]), Integer.parseInt(parts[1]), Integer.parseInt(parts[2])));
    }

    /** 내 현황·포트폴리오. 내 마지막 회차 기준이다. */
    @GetMapping("/{seasonId}/me")
    public MySeasonStatusResponse me(@PathVariable Long seasonId) {
        return seasonPlayService.me(currentUserProvider.currentUserId(), seasonId);
    }

    /**
     * 매수·매도 주문 — 명세 §1 의 멱등성 필수 대상. 같은 키의 재요청은 체결을 다시 만들지
     * 않고 첫 응답을 돌려준다. 같은 키에 다른 본문이면 409 IDEMPOTENCY_KEY_REUSED 다.
     */
    @PostMapping("/{seasonId}/orders")
    public ResponseEntity<SeasonOrderResponse> order(
            @PathVariable Long seasonId,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
            @Valid @RequestBody SeasonOrderRequest request) {
        Long userId = currentUserProvider.currentUserId();
        String stored = idempotencyStore.execute(
                userId,
                ORDER_ENDPOINT + ":" + seasonId,
                idempotencyKey,
                // record 의 toString 은 모든 구성요소를 순서대로 담아 같은 입력에 같은 문자열을 준다.
                IdempotencyStore.hash(request.toString()),
                () -> {
                    SeasonOrderResponse r = seasonPlayService.order(userId, seasonId, request);
                    return r.tradeId() + ":" + r.price().toPlainString() + ":" + r.gameDay();
                });
        String[] parts = stored.split(":");
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(new SeasonOrderResponse(
                        Long.valueOf(parts[0]), new BigDecimal(parts[1]), Integer.parseInt(parts[2])));
    }

    /** 체결 내역(매매일지). 최근 체결이 먼저고 커서는 id 다. */
    @GetMapping("/{seasonId}/trades")
    public SeasonTradeListResponse trades(
            @PathVariable Long seasonId,
            @RequestParam(required = false) Long tickerId,
            @RequestParam(required = false) SeasonTrade.Side side,
            @RequestParam(required = false) Long cursor,
            @RequestParam(required = false) Integer size) {
        return seasonPlayService.trades(
                currentUserProvider.currentUserId(), seasonId, tickerId, side, cursor, size);
    }
}
