package ssafy.a507.backend.domain.market.service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ssafy.a507.backend.common.error.BusinessException;
import ssafy.a507.backend.common.error.ErrorCode;
import ssafy.a507.backend.domain.account.entity.WatchlistItem;
import ssafy.a507.backend.domain.account.repository.UserRepository;
import ssafy.a507.backend.domain.account.repository.WatchlistItemRepository;
import ssafy.a507.backend.domain.market.dto.WatchlistAddResponse;
import ssafy.a507.backend.domain.market.dto.WatchlistItemResponse;
import ssafy.a507.backend.domain.market.dto.WatchlistResponse;
import ssafy.a507.backend.domain.market.entity.DailyQuote;
import ssafy.a507.backend.domain.market.entity.Stock;
import ssafy.a507.backend.domain.market.repository.DailyQuoteRepository;
import ssafy.a507.backend.domain.market.repository.StockRepository;

/**
 * 관심 종목(ANT-DATA-06). 담기·빼기·목록. 시세는 저장하지 않고 daily_quotes 에서 그때그때 파생한다.
 *
 * <p>목록의 시세 규칙은 시장 Overview({@link MarketIndexService})와 같다 — 최근 30 <b>영업일</b> 점,
 * 오래된 순, 등락률은 마지막 두 점으로. 화면이 지수 카드와 관심 종목 카드를 같은 미니차트로 그리므로
 * 두 응답이 한 규칙이어야 한다.
 *
 * <p>담기는 중복이면 409, 빼기는 없어도 204 다 — 좋아요와 같은 정책이다. 담기는 사용자가 "이미
 * 있었네" 를 알아야 하고, 빼기는 두 번 눌러도 결과가 같아 알릴 것이 없다.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class WatchlistService {

    static final int SERIES_POINTS = 30;

    /** 30 영업일을 달력으로 덮는 여유. 연휴가 겹쳐도 점이 모자라지 않게 넉넉히 잡는다. */
    private static final int SERIES_WINDOW_DAYS = 60;

    private final WatchlistItemRepository watchlistItemRepository;
    private final StockRepository stockRepository;
    private final DailyQuoteRepository dailyQuoteRepository;
    private final UserRepository userRepository;

    public WatchlistResponse list(Long userId) {
        List<WatchlistItem> items = watchlistItemRepository.findAllWithStock(userId);
        if (items.isEmpty()) {
            return new WatchlistResponse(List.of());
        }

        List<String> codes = items.stream().map(item -> item.getStock().getCode()).toList();
        Map<String, List<BigDecimal>> seriesByCode = recentCloses(codes);

        List<WatchlistItemResponse> rows = new ArrayList<>(items.size());
        for (WatchlistItem item : items) {
            Stock stock = item.getStock();
            List<BigDecimal> series = seriesByCode.getOrDefault(stock.getCode(), List.of());
            // 수집 범위 밖으로 밀린 종목은 점이 없다. 값을 지어내지 않고 빈 채로 내린다.
            BigDecimal close = series.isEmpty() ? null : series.get(series.size() - 1);
            BigDecimal previous = series.size() > 1 ? series.get(series.size() - 2) : null;
            rows.add(new WatchlistItemResponse(
                    stock.getCode(),
                    stock.getName(),
                    close,
                    MarketIndexService.changeRate(close, previous),
                    series));
        }
        return new WatchlistResponse(rows);
    }

    @Transactional
    public WatchlistAddResponse add(Long userId, String stockCode) {
        Stock stock = stockRepository
                .findById(stockCode)
                .orElseThrow(() -> new BusinessException(ErrorCode.STOCK_NOT_FOUND, "stockCode"));
        if (watchlistItemRepository.existsByUser_IdAndStock_Code(userId, stockCode)) {
            throw new BusinessException(ErrorCode.DUPLICATE_WATCHLIST_ITEM, "stockCode");
        }
        try {
            // 회원 행을 읽지 않는다 — FK 값만 필요하고, 호출자는 이미 인증된 사용자다.
            watchlistItemRepository.save(
                    WatchlistItem.of(userRepository.getReferenceById(userId), stock));
        } catch (DataIntegrityViolationException e) {
            // 두 탭에서 동시에 담으면 exists 검사는 둘 다 지나고 INSERT 에서 한쪽이 UQ 에 걸린다.
            // 그것도 "이미 있다" 다 — 500 으로 새지 않게 같은 409 로 돌린다.
            throw new BusinessException(ErrorCode.DUPLICATE_WATCHLIST_ITEM, "stockCode");
        }
        return new WatchlistAddResponse(stockCode);
    }

    @Transactional
    public void remove(Long userId, String stockCode) {
        watchlistItemRepository.deleteByUserIdAndStockCode(userId, stockCode);
    }

    /**
     * 종목별 최근 30 영업일 종가, 오래된 순. 한 쿼리로 받아 종목별로 끊는다.
     *
     * <p>창은 마지막 수집 영업일에서 달력 60일 — 30 영업일을 덮고도 남는다. 종목마다 마지막 점이
     * 다를 수 있다(거래정지·수집 범위 이탈). 그 종목은 자기 마지막 점을 종가로 삼는다.
     */
    private Map<String, List<BigDecimal>> recentCloses(List<String> codes) {
        LocalDate latest = dailyQuoteRepository.findLatestTradeDate().orElse(null);
        if (latest == null) {
            return Map.of();
        }
        List<DailyQuote> quotes =
                dailyQuoteRepository.findByStock_CodeInAndTradeDateGreaterThanEqualOrderByStock_CodeAscTradeDateAsc(
                        codes, latest.minusDays(SERIES_WINDOW_DAYS));

        Map<String, List<BigDecimal>> byCode = new HashMap<>();
        for (DailyQuote quote : quotes) {
            // getStock().getCode() 는 프록시를 깨우지 않는다 — 식별자 게터라 FK 값을 그대로 준다.
            byCode.computeIfAbsent(quote.getStock().getCode(), k -> new ArrayList<>()).add(quote.getClose());
        }
        byCode.replaceAll((code, closes) -> closes.size() <= SERIES_POINTS
                ? closes
                : new ArrayList<>(closes.subList(closes.size() - SERIES_POINTS, closes.size())));
        return byCode;
    }
}
