package ssafy.a507.backend.domain.research.service;

import jakarta.persistence.criteria.Root;
import jakarta.persistence.criteria.Subquery;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ssafy.a507.backend.common.error.BusinessException;
import ssafy.a507.backend.common.error.ErrorCode;
import ssafy.a507.backend.domain.market.entity.DailyQuote;
import ssafy.a507.backend.domain.market.entity.Stock;
import ssafy.a507.backend.domain.market.repository.DailyQuoteRepository;
import ssafy.a507.backend.domain.market.repository.StockRepository;
import ssafy.a507.backend.domain.research.dto.CorpFinancialListResponse;
import ssafy.a507.backend.domain.research.dto.CorpProfileResponse;
import ssafy.a507.backend.domain.research.dto.PeerListResponse;
import ssafy.a507.backend.domain.research.dto.ValuationResponse;
import ssafy.a507.backend.domain.research.entity.CorpFinancial;
import ssafy.a507.backend.domain.research.repository.CorpFinancialRepository;
import ssafy.a507.backend.domain.research.repository.CorpProfileRepository;

/**
 * 리서치 탭의 기업개요·재무·밸류에이션·경쟁사 조회 (ANT-RESEARCH-05). DART 수집분(-01)과 일봉을
 * 읽어 계산만 한다 — 외부 호출도 LLM 도 없다.
 *
 * <p>데이터가 없으면 200 + 빈 값, 종목이 없을 때만 404 다(브리핑·포인트와 같은 규칙). 화면이
 * "종목 없음"과 "아직 미수집"을 구분해야 한다.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CorpInfoService {

    static final int DEFAULT_YEARS = 3;
    static final int MAX_YEARS = 10;

    /**
     * 경쟁사 "활성" 판정 창(달력 일). {@code StockService.ACTIVE_WINDOW_DAYS} 와 같은 값이어야 한다 —
     * 탐색 목록(B-02)에 보이는 종목과 경쟁사 표에 보이는 종목이 같아야 하기 때문이다. 그쪽이
     * private 이라 값을 복제한다(market 도메인 파일을 건드리지 않기 위해).
     */
    static final int ACTIVE_WINDOW_DAYS = 7;

    static final String PER_PBR_NOTE = "상장주식수를 아직 받지 못해 시가총액 지표(PER·PBR)를 계산하지 않는다";

    static final String NO_PRICE_NOTE = "이 종목의 시세가 없어 시가총액 지표(PER·PBR)를 계산하지 않는다";

    static final String NO_FINANCIAL_NOTE = "최신 연간 재무가 없어 시가총액 지표(PER·PBR)를 계산하지 않는다";

    static final String CURRENCY_NOTE = "최신 연간 재무가 원화가 아니라 시가총액 지표(PER·PBR)를 계산하지 않는다";

    static final String LOSS_NOTE = "최신 연간 순이익이 적자라 PER 을 계산하지 않는다";

    static final String IMPAIRED_NOTE = "자본총계가 0 이하라 PBR 을 계산하지 않는다";

    /** 시가총액 지표의 소수 자리. 탐색 목록의 파생 컬럼과 같다. */
    private static final int RATIO_SCALE = 2;

    private final StockRepository stockRepository;
    private final DailyQuoteRepository dailyQuoteRepository;
    private final CorpProfileRepository corpProfileRepository;
    private final CorpFinancialRepository corpFinancialRepository;

    public CorpProfileResponse profile(String code) {
        Stock stock = stock(code);
        return CorpProfileResponse.of(stock, corpProfileRepository.findById(code).orElse(null));
    }

    /** 연간(사업보고서) 재무 최근 {@code years} 개년. 분기 행은 아직 수집하지 않아 여기서도 뺀다. */
    public CorpFinancialListResponse financials(String code, Integer years) {
        requireStock(code);
        int limit = years == null || years <= 0 ? DEFAULT_YEARS : Math.min(years, MAX_YEARS);
        List<CorpFinancialListResponse.Item> items = new ArrayList<>(annual(code).stream()
                .limit(limit)
                .map(CorpFinancialListResponse.Item::of)
                .toList());
        Collections.reverse(items);
        return new CorpFinancialListResponse(items);
    }

    /**
     * ROE·부채비율은 가장 최근 연간 재무로, 종가는 수집된 마지막 영업일로 계산한다. 자본총계가 0
     * 이하(완전자본잠식)면 비율이 뒤집혀 의미가 없어 둘 다 null 이다.
     */
    public ValuationResponse valuation(String code) {
        Stock stock = stock(code);
        // 종가와 그 종가의 날짜를 한 행에서 함께 꺼낸다. 전역 최신 거래일을 쓰면 수집 대상 밖 종목과
        // 거래정지 종목에서 "그날 종가가 없는 날짜"를 기준일이라 말하게 된다 — 화면 머리의 "N일
        // 종가" 와 PER 의 기준이 어긋난다.
        DailyQuote quote = dailyQuoteRepository.findTopByStock_CodeOrderByTradeDateDesc(code).orElse(null);
        LocalDate priceDate = quote == null ? null : quote.getTradeDate();
        BigDecimal prevClose = quote == null ? null : quote.getClose();

        CorpFinancial latest = annual(code).stream().findFirst().orElse(null);
        if (latest == null) {
            return new ValuationResponse(
                    null, null, null, null,
                    new ValuationResponse.BasedOn(priceDate, prevClose, null, null, NO_FINANCIAL_NOTE));
        }

        BigInteger equity = latest.getTotalEquity();
        BigDecimal marketCap = marketCap(stock, prevClose);
        return new ValuationResponse(
                perShareRatio(marketCap, latest, latest.getNetIncome()),
                perShareRatio(marketCap, latest, equity),
                StockMaterials.ratio(latest.getNetIncome(), equity),
                StockMaterials.ratio(latest.getTotalLiabilities(), equity),
                new ValuationResponse.BasedOn(
                        priceDate,
                        prevClose,
                        latest.getFiscalYear(),
                        latest.getFsDiv(),
                        note(stock, latest, prevClose)));
    }

    /** 종가 × 상장주식수. 둘 중 하나라도 없으면 null 이다. */
    private static BigDecimal marketCap(Stock stock, BigDecimal close) {
        if (close == null || stock.getListedShares() == null) {
            return null;
        }
        return close.multiply(BigDecimal.valueOf(stock.getListedShares()));
    }

    /**
     * 시가총액 / 분모. 탐색 목록의 파생 컬럼({@code MarketUpsertRepository.refreshValuations})과 같은
     * 식·같은 자리수라 두 화면이 같은 숫자를 보여 준다.
     *
     * <p>분모가 0 이하면 null 이다 — 음수 PER 은 "싸다" 로 읽혀 더 해롭다. 원화 종가를 달러 재무로
     * 나눌 수 없으므로 통화가 원화가 아닌 재무도 없는 것으로 본다.
     */
    private static BigDecimal perShareRatio(BigDecimal marketCap, CorpFinancial financial, BigInteger denominator) {
        if (marketCap == null || !isKrw(financial) || denominator == null || denominator.signum() <= 0) {
            return null;
        }
        return marketCap.divide(new BigDecimal(denominator), RATIO_SCALE, RoundingMode.HALF_UP);
    }

    private static boolean isKrw(CorpFinancial financial) {
        return financial.getCurrency() == null || "KRW".equals(financial.getCurrency());
    }

    /** 시가총액 지표가 빈 이유. 다 있으면 null — 화면이 사유 줄을 그리지 않는다. */
    private static String note(Stock stock, CorpFinancial latest, BigDecimal close) {
        if (close == null) {
            return NO_PRICE_NOTE;
        }
        if (stock.getListedShares() == null) {
            return PER_PBR_NOTE;
        }
        if (!isKrw(latest)) {
            return CURRENCY_NOTE;
        }
        boolean loss = latest.getNetIncome() == null || latest.getNetIncome().signum() <= 0;
        boolean impaired = latest.getTotalEquity() == null || latest.getTotalEquity().signum() <= 0;
        if (loss && impaired) {
            return LOSS_NOTE + " · " + IMPAIRED_NOTE;
        }
        if (loss) {
            return LOSS_NOTE;
        }
        return impaired ? IMPAIRED_NOTE : null;
    }

    /**
     * 같은 KRX 섹터의 상장·활성 종목(자기 제외), 종목코드순. 섹터가 비어 있거나 시세가 한 건도
     * 없으면 빈 목록이다.
     */
    public PeerListResponse peers(String code) {
        Stock self = stock(code);
        LocalDate priceDate = dailyQuoteRepository.findLatestTradeDate().orElse(null);
        if (self.getSector() == null || priceDate == null) {
            return new PeerListResponse(priceDate, List.of());
        }
        LocalDate activeSince = priceDate.minusDays(ACTIVE_WINDOW_DAYS);
        Specification<Stock> peers = (root, query, cb) -> {
            // 창 안에 시세가 있는 종목만 — StockService.list() 와 같은 서브쿼리다.
            Subquery<String> active = query.subquery(String.class);
            Root<DailyQuote> quote = active.from(DailyQuote.class);
            active.select(quote.get("stock").get("code"))
                    .where(cb.greaterThanOrEqualTo(quote.get("tradeDate"), activeSince));
            return cb.and(
                    cb.isTrue(root.get("listed")),
                    cb.equal(root.get("sector"), self.getSector()),
                    cb.notEqual(root.get("code"), code),
                    root.get("code").in(active));
        };
        List<Stock> candidates = stockRepository.findAll(peers, Sort.by("code"));
        if (candidates.isEmpty()) {
            return new PeerListResponse(priceDate, List.of());
        }
        // 기준일 종가만 한 번에. 그날 거래가 정지된 종목은 행이 없어 종가가 null 로 남는다.
        Map<String, BigDecimal> closes = dailyQuoteRepository
                .findByTradeDateAndStock_CodeIn(priceDate, candidates.stream().map(Stock::getCode).toList())
                .stream()
                .collect(Collectors.toMap(q -> q.getStock().getCode(), DailyQuote::getClose, (a, b) -> a));
        List<PeerListResponse.Item> items = candidates.stream()
                .map(s -> PeerListResponse.Item.of(s, closes.get(s.getCode())))
                .toList();
        return new PeerListResponse(priceDate, items);
    }

    /** 존재만 보는 경로. 엔티티가 필요 없으면 findById 로 행 전체를 읽지 않는다. */
    private void requireStock(String code) {
        if (!stockRepository.existsById(code)) {
            throw new BusinessException(ErrorCode.STOCK_NOT_FOUND, "code");
        }
    }

    private Stock stock(String code) {
        return stockRepository.findById(code)
                .orElseThrow(() -> new BusinessException(ErrorCode.STOCK_NOT_FOUND, "code"));
    }

    /** 최신 연도가 앞. 분기 행은 아직 수집하지 않지만 스키마 자리가 열려 있어 쿼리에서 건다. */
    private List<CorpFinancial> annual(String code) {
        return corpFinancialRepository.findByStockCodeAndQuarterOrderByFiscalYearDesc(
                code, CorpFinancial.ANNUAL_QUARTER);
    }
}
