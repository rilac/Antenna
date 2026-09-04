package ssafy.a507.backend.domain.market.service;

import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import jakarta.persistence.criteria.Subquery;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ssafy.a507.backend.common.error.BusinessException;
import ssafy.a507.backend.common.error.ErrorCode;
import ssafy.a507.backend.domain.account.repository.WatchlistItemRepository;
import ssafy.a507.backend.domain.market.dto.SectorSummaryResponse;
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
 * <p>목록은 명세의 GET /stocks 중 재료가 있는 것만 낸다 — 섹터·시장·관심 필터, 행별 등락률·관심
 * 여부·PER·PBR(배치가 stocks 에 써 둔 파생 컬럼을 읽기만 한다). PER 범위·예측 심리 필터와 정렬
 * 5종은 predictions 가 아직 없어 받아도 무시한다(400 이 아니다 — 화면 컨트롤이 이미 있고, 재료가
 * 생기면 서버만 바꾸면 된다). 그 값들은 키를 남긴 채 null · 0 으로 내린다 — 키가 없으면 화면이
 * undefined 를 만나 죽는다.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class StockService {

    private static final int DEFAULT_PAGE_SIZE = 20;
    private static final int MAX_PAGE_SIZE = 100;
    private static final int RATE_SCALE = 2;

    /** from 을 주지 않았을 때의 기본 구간. 관심 종목 미니차트가 최근 30일을 쓴다. */
    private static final int DEFAULT_PRICE_DAYS = 30;

    /** 커서가 없을 때의 시작점. 모든 종목코드가 이 값보다 크다. */
    private static final String FIRST_PAGE_CURSOR = "";

    /**
     * "지금 우리 범위 안" 판정 창(달력 일). 수집 범위(KOSPI 시가총액 상위 300)를 날짜마다 다시
     * 고르므로 한 번이라도 들었던 종목이 stocks 에 남는다 — 2020년에 잠깐 들었다 빠진 종목은
     * 시세가 그때 끊겨 목록에 종가 없는 줄로 뜬다. 마지막 수집일 기준 이 창 안에 시세가 한 점도
     * 없으면 목록·요약에서 뺀다. 하루 이틀 거래정지는 창 안이라 남는다.
     */
    private static final int ACTIVE_WINDOW_DAYS = 7;

    private final StockRepository stockRepository;
    private final DailyQuoteRepository dailyQuoteRepository;
    private final WatchlistItemRepository watchlistItemRepository;

    /**
     * 탐색 목록 한 페이지.
     *
     * @param sector 섹터 필터 · null 이면 전체
     * @param market 시장 필터 · null 이면 전체
     * @param watchedOnly 내 관심 종목만
     */
    public StockListResponse list(
            Long userId,
            String sector,
            Stock.Market market,
            boolean watchedOnly,
            String cursor,
            Integer size) {
        int pageSize = pageSize(size);
        LocalDate baseDate = dailyQuoteRepository.findLatestTradeDate().orElse(null);
        if (baseDate == null) {
            // 시세가 한 건도 없다. 종가 없는 종목 목록은 화면이 그릴 것이 없다.
            return new StockListResponse(List.of(), null, null, false);
        }
        Set<String> watched = new HashSet<>(watchlistItemRepository.findWatchedCodes(userId));
        if (watchedOnly && watched.isEmpty()) {
            return new StockListResponse(List.of(), baseDate, null, false);
        }

        LocalDate activeSince = baseDate.minusDays(ACTIVE_WINDOW_DAYS);
        Specification<Stock> spec = (root, query, cb) -> {
            List<Predicate> where = new ArrayList<>();
            where.add(cb.isTrue(root.get("listed")));
            where.add(cb.greaterThan(root.get("code"), cursor == null ? FIRST_PAGE_CURSOR : cursor));
            // 창 안에 시세가 있는 종목만 — 범위 밖으로 밀린 옛 종목을 거른다.
            Subquery<String> active = query.subquery(String.class);
            Root<DailyQuote> quote = active.from(DailyQuote.class);
            active.select(quote.get("stock").get("code"))
                    .where(cb.greaterThanOrEqualTo(quote.get("tradeDate"), activeSince));
            where.add(root.get("code").in(active));
            if (sector != null) {
                where.add(cb.equal(root.get("sector"), sector));
            }
            if (market != null) {
                where.add(cb.equal(root.get("market"), market));
            }
            if (watchedOnly) {
                where.add(root.get("code").in(watched));
            }
            return cb.and(where.toArray(Predicate[]::new));
        };
        // 한 건 더 읽어 다음 페이지가 있는지 본다. count 쿼리를 따로 돌리지 않는 흔한 방법이다.
        List<Stock> found = stockRepository.findBy(
                spec, q -> q.sortBy(Sort.by("code")).limit(pageSize + 1).all());
        boolean hasNext = found.size() > pageSize;
        List<Stock> stocks = hasNext ? found.subList(0, pageSize) : found;

        if (stocks.isEmpty()) {
            return new StockListResponse(List.of(), baseDate, null, false);
        }

        LocalDate previousDate = previousTradeDate(baseDate);
        List<String> codes = stocks.stream().map(Stock::getCode).toList();
        Map<String, BigDecimal> closes = closesOn(baseDate, codes);
        Map<String, BigDecimal> previousCloses = closesOn(previousDate, codes);

        List<StockListItemResponse> items = stocks.stream()
                .map(stock -> {
                    BigDecimal close = closes.get(stock.getCode());
                    return new StockListItemResponse(
                            stock.getCode(),
                            stock.getName(),
                            stock.getSector(),
                            stock.getMarket(),
                            close,
                            changeRate(close, previousCloses.get(stock.getCode())),
                            stock.getPer(),
                            stock.getPbr(),
                            0,
                            null,
                            watched.contains(stock.getCode()));
                })
                .toList();

        String nextCursor = hasNext ? stocks.get(stocks.size() - 1).getCode() : null;
        return new StockListResponse(items, baseDate, nextCursor, hasNext);
    }

    /**
     * 섹터 요약 칩. 전체 행이 먼저, 섹터는 종목 수 내림차순(같으면 이름순). 300개 안팎이라 전부
     * 읽어 메모리에서 묶는다 — 표가 커지면 그때 집계 쿼리로 바꾼다.
     */
    public SectorSummaryResponse sectors() {
        LocalDate baseDate = dailyQuoteRepository.findLatestTradeDate().orElse(null);
        if (baseDate == null) {
            return new SectorSummaryResponse(List.of());
        }
        List<Stock> listed = activeStocks(baseDate);
        LocalDate previousDate = previousTradeDate(baseDate);
        List<String> codes = listed.stream().map(Stock::getCode).toList();
        Map<String, BigDecimal> closes = closesOn(baseDate, codes);
        Map<String, BigDecimal> previousCloses = closesOn(previousDate, codes);

        List<BigDecimal> allRates = new ArrayList<>();
        Map<String, List<Stock>> bySector = new LinkedHashMap<>();
        Map<String, List<BigDecimal>> ratesBySector = new LinkedHashMap<>();
        for (Stock stock : listed) {
            BigDecimal rate = changeRate(closes.get(stock.getCode()), previousCloses.get(stock.getCode()));
            if (rate != null) {
                allRates.add(rate);
            }
            if (stock.getSector() == null) {
                continue;
            }
            bySector.computeIfAbsent(stock.getSector(), k -> new ArrayList<>()).add(stock);
            if (rate != null) {
                ratesBySector.computeIfAbsent(stock.getSector(), k -> new ArrayList<>()).add(rate);
            }
        }

        List<SectorSummaryResponse.Item> items = new ArrayList<>();
        items.add(new SectorSummaryResponse.Item(null, listed.size(), average(allRates)));
        bySector.entrySet().stream()
                .sorted(Comparator.comparingInt((Map.Entry<String, List<Stock>> e) -> e.getValue().size())
                        .reversed()
                        .thenComparing(Map.Entry::getKey))
                .forEach(e -> items.add(new SectorSummaryResponse.Item(
                        e.getKey(),
                        e.getValue().size(),
                        average(ratesBySector.getOrDefault(e.getKey(), List.of())))));
        return new SectorSummaryResponse(items);
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

    /** 상장 종목 중 창 안에 시세가 있는 것만 — 목록의 같은 판정을 요약에도 쓴다. */
    private List<Stock> activeStocks(LocalDate baseDate) {
        List<Stock> listed = stockRepository.findByListedTrue();
        Set<String> active = dailyQuoteRepository
                .findByStock_CodeInAndTradeDateGreaterThanEqualOrderByStock_CodeAscTradeDateAsc(
                        listed.stream().map(Stock::getCode).toList(),
                        baseDate.minusDays(ACTIVE_WINDOW_DAYS))
                .stream()
                .map(quote -> quote.getStock().getCode())
                .collect(Collectors.toSet());
        return listed.stream().filter(stock -> active.contains(stock.getCode())).toList();
    }

    private LocalDate previousTradeDate(LocalDate baseDate) {
        return baseDate == null ? null : dailyQuoteRepository.findPreviousTradeDate(baseDate).orElse(null);
    }

    /** 어느 한쪽 종가가 없으면 null — 0 으로 그리면 "변동 없음" 으로 읽힌다. */
    private static BigDecimal changeRate(BigDecimal close, BigDecimal previous) {
        if (close == null || previous == null) {
            return null;
        }
        return MarketIndexService.changeRate(close, previous);
    }

    private static BigDecimal average(List<BigDecimal> rates) {
        if (rates.isEmpty()) {
            return BigDecimal.ZERO.setScale(RATE_SCALE, RoundingMode.HALF_UP);
        }
        BigDecimal sum = rates.stream().reduce(BigDecimal.ZERO, BigDecimal::add);
        return sum.divide(BigDecimal.valueOf(rates.size()), RATE_SCALE, RoundingMode.HALF_UP);
    }

    /** 한 영업일의 종가를 종목코드로 찾아 쓸 수 있게 모아 둔다. 없는 종목은 키가 없다. */
    private Map<String, BigDecimal> closesOn(LocalDate date, Collection<String> codes) {
        if (date == null || codes.isEmpty()) {
            return Map.of();
        }
        // getStock().getCode() 는 프록시를 깨우지 않는다 — 식별자 게터라 FK 값을 그대로 준다.
        return dailyQuoteRepository.findByTradeDateAndStock_CodeIn(date, codes).stream()
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
