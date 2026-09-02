package ssafy.a507.backend.domain.market.service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Limit;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ssafy.a507.backend.common.error.BusinessException;
import ssafy.a507.backend.common.error.ErrorCode;
import ssafy.a507.backend.domain.market.dto.StockListItemResponse;
import ssafy.a507.backend.domain.market.dto.StockListResponse;
import ssafy.a507.backend.domain.market.dto.StockPriceListResponse;
import ssafy.a507.backend.domain.market.dto.StockPricePoint;
import ssafy.a507.backend.domain.market.entity.DailyQuote;
import ssafy.a507.backend.domain.market.entity.Stock;
import ssafy.a507.backend.domain.market.repository.DailyQuoteRepository;
import ssafy.a507.backend.domain.market.repository.StockRepository;

/**
 * 종목 목록·시세 조회.
 *
 * <p>실전 시세로 내려가는 값은 전부 <b>직전 영업일 종가</b>다. 현재가는 시스템 어디에도 없다 —
 * 실시간 시세 제공은 법적 제약이라 애초에 수집하지 않는다(명세 §1, 화면설계 D17).
 *
 * <p>이 단계의 목록은 최소판이다. 명세의 GET /stocks 는 PER 범위·예측 심리 집계·섹터 필터까지
 * 받지만 그 재료(corp_financials·predictions)가 아직 없다. 필터와 집계는 그 표들이 생기는
 * 스토리에서 얹는다 — 지금 빈 값을 내려 두면 화면이 그것을 진짜 0 으로 읽는다.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class StockService {

    private static final int DEFAULT_PAGE_SIZE = 20;
    private static final int MAX_PAGE_SIZE = 100;

    /** from 을 주지 않았을 때의 기본 구간. 관심 종목 미니차트가 최근 30일을 쓴다. */
    private static final int DEFAULT_PRICE_DAYS = 30;

    /** 커서가 없을 때의 시작점. 모든 종목코드가 이 값보다 크다. */
    private static final String FIRST_PAGE_CURSOR = "";

    private final StockRepository stockRepository;
    private final DailyQuoteRepository dailyQuoteRepository;

    public StockListResponse list(String cursor, Integer size) {
        int pageSize = pageSize(size);

        // 한 건 더 읽어 다음 페이지가 있는지 본다. count 쿼리를 따로 돌리지 않는 흔한 방법이다.
        List<Stock> found = stockRepository.findByListedTrueAndCodeGreaterThanOrderByCode(
                cursor == null ? FIRST_PAGE_CURSOR : cursor, Limit.of(pageSize + 1));
        boolean hasNext = found.size() > pageSize;
        List<Stock> stocks = hasNext ? found.subList(0, pageSize) : found;

        if (stocks.isEmpty()) {
            return new StockListResponse(List.of(), null, null, false);
        }

        LocalDate baseDate = dailyQuoteRepository.findLatestTradeDate().orElse(null);
        Map<String, BigDecimal> closes = closesOn(baseDate, stocks);

        List<StockListItemResponse> items = stocks.stream()
                .map(stock -> new StockListItemResponse(
                        stock.getCode(),
                        stock.getName(),
                        stock.getSector(),
                        stock.getMarket(),
                        closes.get(stock.getCode())))
                .toList();

        String nextCursor = hasNext ? stocks.get(stocks.size() - 1).getCode() : null;
        return new StockListResponse(items, baseDate, nextCursor, hasNext);
    }

    /**
     * 구간 시세. 없는 종목은 404 다 — 구간에 시세가 없는 것(빈 목록)과 구분되지 않으면
     * 프론트가 종목코드 오타와 휴장 구간을 같은 화면으로 보여주게 된다.
     *
     * @param from 없으면 to 에서 30일 전
     * @param to 없으면 수집된 마지막 영업일
     */
    public StockPriceListResponse prices(String code, LocalDate from, LocalDate to) {
        if (!stockRepository.existsById(code)) {
            throw new BusinessException(ErrorCode.STOCK_NOT_FOUND, "code");
        }

        LocalDate end = to != null ? to : dailyQuoteRepository.findLatestTradeDate().orElse(null);
        if (end == null) {
            // 아직 한 건도 수집되지 않았다. 기준 삼을 날짜가 없으니 빈 구간으로 답한다.
            return new StockPriceListResponse(List.of());
        }
        LocalDate start = from != null ? from : end.minusDays(DEFAULT_PRICE_DAYS);
        if (start.isAfter(end)) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST, "from");
        }

        List<StockPricePoint> items =
                dailyQuoteRepository
                        .findByStock_CodeAndTradeDateBetweenOrderByTradeDate(code, start, end)
                        .stream()
                        .map(quote -> new StockPricePoint(quote.getTradeDate(), quote.getClose()))
                        .toList();
        return new StockPriceListResponse(items);
    }

    /** 한 영업일의 종가를 종목코드로 찾아 쓸 수 있게 모아 둔다. 없는 종목은 키가 없다. */
    private Map<String, BigDecimal> closesOn(LocalDate baseDate, List<Stock> stocks) {
        if (baseDate == null) {
            return Map.of();
        }
        List<String> codes = stocks.stream().map(Stock::getCode).toList();
        // getStock().getCode() 는 프록시를 깨우지 않는다 — 식별자 게터라 FK 값을 그대로 준다.
        return dailyQuoteRepository.findByTradeDateAndStock_CodeIn(baseDate, codes).stream()
                .collect(Collectors.toMap(
                        quote -> quote.getStock().getCode(),
                        DailyQuote::getClose,
                        (first, second) -> first));
    }

    private int pageSize(Integer size) {
        if (size == null || size <= 0) {
            return DEFAULT_PAGE_SIZE;
        }
        return Math.min(size, MAX_PAGE_SIZE);
    }
}
