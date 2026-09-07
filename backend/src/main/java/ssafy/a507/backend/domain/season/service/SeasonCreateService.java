package ssafy.a507.backend.domain.season.service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ssafy.a507.backend.domain.market.entity.Stock;
import ssafy.a507.backend.domain.market.repository.DailyQuoteRepository;
import ssafy.a507.backend.domain.market.repository.StockRepository;
import ssafy.a507.backend.domain.season.entity.Season;
import ssafy.a507.backend.domain.season.entity.SeasonTicker;
import ssafy.a507.backend.domain.season.repository.SeasonPriceRepository;
import ssafy.a507.backend.domain.season.repository.SeasonRepository;
import ssafy.a507.backend.domain.season.repository.SeasonTickerRepository;

/**
 * 실제 과거 시세로 시즌을 만든다.
 *
 * <p>가격을 합성하지 않는다 — {@code daily_quotes} 의 한 구간을 {@code season_prices} 로
 * 옮기고 실제 날짜만 {@code game_day} 인덱스로 바꾼다(ERD v0.6). 뉴스가 실제 사건인데
 * 가격이 난수면 뉴스를 읽어도 소용이 없으므로 둘의 출처가 같아야 한다.
 *
 * <p><b>종목은 실명이다(ERD v0.8).</b> "A사" 가명은 걷어냈다 — 종목이 누구인지 모르면 업종
 * 사이의 연관이나 실적 같은 공부가 성립하지 않는다. 숨기는 것은 실제 날짜 하나뿐이다.
 * {@code seasons.base_date} 는 서버만 갖고 응답에는 game_day 만 나간다.
 *
 * <p><b>종목 범위는 그 구간의 대형주 전부다.</b> 첫 게임일 종가 × 상장주식수
 * ({@code stocks.listed_shares})로 시가총액을 근사해 큰 순서로 {@code tickerCount} 개까지.
 * 주제({@code theme})는 종목을 거르지 않는다 — 카드가 말하는 대표 업종일 뿐이고, 주제 업종
 * 밖의 종목이 같은 장에서 어떻게 움직였는지 보는 것도 공부다.
 *
 * <p><b>워밍업.</b> 첫 게임일 앞의 {@value #WARMUP_DAYS}영업일을 {@code game_day <= 0} 으로
 * 함께 담는다. D+1 화면이 봉 하나로 시작하지 않게 하려는 것이고, 진행일 절단
 * ({@code game_day <= currentDay})이 그대로 미래를 막는다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SeasonCreateService {

    /** 첫 게임일 앞에 함께 담는 과거 봉 수. 수집 시작(2020-01-02) 앞이면 있는 만큼만 담는다. */
    public static final int WARMUP_DAYS = 30;

    private final StockRepository stockRepository;
    private final DailyQuoteRepository dailyQuoteRepository;
    private final SeasonRepository seasonRepository;
    private final SeasonTickerRepository seasonTickerRepository;
    private final SeasonPriceRepository seasonPriceRepository;

    /**
     * @param spec 무엇을 만들지 — 성격·대표 업종·시작 영업일·종목 수 상한·기간·예수금·seed
     * @return 만들어진 시즌
     * @throws IllegalStateException 재료가 부족할 때. 시즌을 반쯤 만들어 두지 않는다
     */
    @Transactional
    public Season create(SeasonSpec spec) {
        List<LocalDate> gameDays = tradeDays(spec);
        List<LocalDate> warmup = dailyQuoteRepository.findTradeDatesBefore(
                gameDays.get(0), PageRequest.of(0, WARMUP_DAYS));
        List<Stock> picked = pick(spec, gameDays);

        Season season = seasonRepository.save(Season.practice(
                spec.mode(),
                spec.title(),
                spec.note(),
                spec.theme(),
                gameDays.get(0),
                gameDays.size(),
                spec.initialCash(),
                spec.seed()));

        List<SeasonTicker> tickers = picked.stream()
                .map(stock -> SeasonTicker.of(season, stock.getName(), stock, stock.getSector()))
                .toList();
        seasonTickerRepository.saveAll(tickers);

        // findTradeDatesBefore 는 최근 날짜부터 내려오므로 마지막 원소가 가장 이른 날이다.
        LocalDate from = warmup.isEmpty() ? gameDays.get(0) : warmup.get(warmup.size() - 1);
        int rows = seasonPriceRepository.copyFromDailyQuotes(
                season.getId(), from, gameDays.get(gameDays.size() - 1), warmup.size());

        log.info(
                "시즌 생성 — id={} \"{}\" theme={} 종목 {}개 · {}게임일(+워밍업 {}) · 가격 {}행",
                season.getId(),
                season.getTitle(),
                season.getTheme(),
                tickers.size(),
                gameDays.size(),
                warmup.size(),
                rows);
        return season;
    }

    /**
     * 시즌과 그 종목·가격을 지운다. 참가자가 있는 시즌은 부르지 않는다 — 호출부가 먼저 확인한다.
     * 만든 곳이 지우는 이유는 트랜잭션 경계를 한 곳에 두려는 것이다.
     */
    @Transactional
    public void delete(Long seasonId) {
        seasonPriceRepository.deleteBySeasonId(seasonId);
        seasonTickerRepository.deleteBySeason_Id(seasonId);
        seasonRepository.deleteById(seasonId);
    }

    /**
     * game_day ↔ 실제 영업일 매핑. 수집된 날짜가 곧 영업일이라 공휴일 표를 들지 않는다.
     * 요청한 기준일이 휴일이면 그다음 영업일부터 시작한다 — 그래서 시즌에 남는
     * {@code base_date} 는 요청값이 아니라 <b>첫 게임일의 실제 영업일</b>이다.
     * 요청한 기간만큼 없으면 만들지 않는다 — 짧은 시즌을 조용히 만들면 화면이 "총 120일"
     * 이라고 적어 둔 것과 어긋난다.
     */
    private List<LocalDate> tradeDays(SeasonSpec spec) {
        List<LocalDate> days = dailyQuoteRepository.findTradeDatesFrom(
                spec.baseDate(), PageRequest.of(0, spec.lengthDays()));
        if (days.size() < spec.lengthDays()) {
            throw new IllegalStateException(
                    "%s 부터 %d 영업일이 필요한데 %d 일만 수집돼 있다 — 백필을 먼저 돌려야 한다"
                            .formatted(spec.baseDate(), spec.lengthDays(), days.size()));
        }
        return days;
    }

    /**
     * 첫 게임일 시가총액 큰 순서로 {@code tickerCount} 개까지. 후보는 셋을 다 만족해야 한다 —
     * 보통주 · 상장주식수가 있음 · 구간 전체에 시세가 빠짐없이 있음(중간에 거래정지가 끼면
     * 게임일에 구멍이 생긴다).
     *
     * <p>{@code tickerCount} 는 상한이다. 후보가 그보다 적으면 있는 만큼 만든다 — 수집 범위가
     * 상위 300 이고 상장주식수는 마지막 수집일 기준이라, 오래된 구간일수록 후보가 줄어든다.
     * 응답의 {@code tickerCount} 는 실제로 담긴 수라 화면과 어긋나지 않는다.
     */
    private List<Stock> pick(SeasonSpec spec, List<LocalDate> gameDays) {
        LocalDate first = gameDays.get(0);
        LocalDate last = gameDays.get(gameDays.size() - 1);

        List<String> ranked = dailyQuoteRepository.findCodesByCapDescOn(first).stream()
                .filter(SeasonCreateService::isCommonShare)
                .toList();
        if (ranked.isEmpty()) {
            throw new IllegalStateException(
                    "%s 의 시가총액을 구할 종목이 없다 — stocks.listed_shares 가 비어 있다".formatted(first));
        }

        Set<String> full = new HashSet<>(
                dailyQuoteRepository.findCodesWithFullHistory(ranked, first, last, gameDays.size()));
        List<String> codes = ranked.stream()
                .filter(full::contains)
                .limit(spec.tickerCount())
                .toList();
        if (codes.isEmpty()) {
            throw new IllegalStateException(
                    "%s ~ %s 구간 전체 시세가 있는 종목이 없다".formatted(first, last));
        }

        // 시총 순서를 그대로 지킨다 — findAllById 는 순서를 보장하지 않는다.
        Map<String, Stock> byCode = stockRepository.findAllById(codes).stream()
                .collect(Collectors.toMap(Stock::getCode, Function.identity()));
        return codes.stream().map(byCode::get).toList();
    }

    /**
     * 보통주인가. KRX 종목코드 여섯 자리 중 끝자리가 0 이면 보통주고, 그 밖(5·7·9·K)은
     * 우선주다 — 삼성전자 005930 과 삼성전자우 005935 는 코드 끝자리만 다르다.
     *
     * <p>우선주를 후보에서 뺀다. 보통주와 거의 같이 움직여 같은 회사가 두 줄로 잡히고,
     * 지수 구성 종목(KOSPI 200)에도 우선주는 없다.
     */
    private static boolean isCommonShare(String code) {
        return code != null && code.endsWith("0");
    }

    /**
     * 스펙을 그대로 담는 그릇. 관리자 생성 API 가 들어오면 그 요청 본문이 이걸 채운다.
     *
     * @param theme 카드가 말하는 대표 업종. 종목을 거르는 조건이 아니다
     * @param tickerCount 종목 수 상한
     * @param seed 시즌 식별값. 같은 (mode, theme, seed) 는 같은 시즌으로 본다 — 시더가
     *     다시 돌아도 겹쳐 만들지 않는 근거. 종목 선정은 시총 순이라 seed 와 무관하다
     */
    public record SeasonSpec(
            Season.Mode mode,
            String title,
            String note,
            String theme,
            LocalDate baseDate,
            int tickerCount,
            int lengthDays,
            BigDecimal initialCash,
            long seed) {}
}
