package ssafy.a507.backend.domain.season.service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ssafy.a507.backend.domain.market.entity.DailyQuote;
import ssafy.a507.backend.domain.market.entity.Stock;
import ssafy.a507.backend.domain.market.repository.DailyQuoteRepository;
import ssafy.a507.backend.domain.market.repository.StockRepository;
import ssafy.a507.backend.domain.season.entity.Season;
import ssafy.a507.backend.domain.season.entity.SeasonPrice;
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
 * <p><b>무엇을 숨기는가.</b> {@code seasons.base_date} 는 서버만 갖고 어떤 응답에도 나가지
 * 않는다. 종목을 가릴지는 시즌마다 다르다({@link SeasonSpec#blind}) — <b>연습은 실명,
 * 대회는 가명</b>이다.
 *
 * <p>실명 시즌에서 시기 은닉은 형식이다. 실제 주가를 쓰므로 종목명과 종가가 함께 나가면
 * 검색 한 번에 날짜가 나온다. 그래도 날짜를 응답에 담지 않는 이유는 둘이다 — 대회가 같은
 * 코드 경로를 쓰고, 화면 축이 날짜가 아니라 game_day 라서다.
 *
 * <p><b>왜 seed 로 뽑는가.</b> 같은 seed·theme·baseDate 면 언제 만들어도 같은 종목이
 * 뽑힌다. 대회에서 참가자가 서로 다른 종목을 받으면 순위가 의미를 잃는다.
 *
 * <p><b>워밍업 구간.</b> 플레이 구간(game_day 1..N) 앞에 시즌 시작 전 구간을
 * {@code game_day 0, -1, -2 …} 로 함께 담는다. 이게 없으면 첫날 캔들이 한 개라 이동평균도
 * MACD 도 값이 없다 — 60게임일 시즌이면 MA60 은 마지막 하루에만, MACD 시그널은 34번째
 * 봉부터 나온다. 근거 없이 매수하는 화면이 되므로 게임이 성립하지 않는다.
 *
 * <p>워밍업을 보여주는 것은 커닝이 아니다. 시즌 <b>시작 전</b> 구간이고 날짜가 없다.
 * 진행일 상한(game_day ≤ 진행일)은 그대로 걸리므로 앞으로 볼 수는 없다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SeasonCreateService {

    private final StockRepository stockRepository;
    private final DailyQuoteRepository dailyQuoteRepository;
    private final SeasonRepository seasonRepository;
    private final SeasonTickerRepository seasonTickerRepository;
    private final SeasonPriceRepository seasonPriceRepository;

    /**
     * 가명은 A사부터 붙인다. 블라인드 시즌에만 쓴다 — 26개를 넘길 시즌은 없고, 넘으면
     * 만들지 않고 막는다.
     */
    private static final String ALPHABET = "ABCDEFGHIJKLMNOPQRSTUVWXYZ";

    /**
     * @param spec 무엇을 만들지 — 성격·섹터·시작 영업일·종목 수·기간·예수금·seed
     * @return 만들어진 시즌
     * @throws IllegalStateException 재료가 부족할 때. 시즌을 반쯤 만들어 두지 않는다
     */
    @Transactional
    public Season create(SeasonSpec spec) {
        List<LocalDate> playDays = tradeDays(spec);
        List<LocalDate> warmupDays = warmupDays(spec, playDays.get(0));

        // 워밍업이 앞, 플레이가 뒤. 이 순서가 곧 game_day 순서다.
        List<LocalDate> allDays = new ArrayList<>(warmupDays);
        allDays.addAll(playDays);

        List<Stock> picked = pick(spec, allDays);

        Season season = seasonRepository.save(Season.practice(
                spec.mode(),
                spec.title(),
                spec.note(),
                spec.theme(),
                playDays.get(0),
                playDays.size(),
                spec.initialCash(),
                spec.seed()));

        List<SeasonTicker> tickers = new ArrayList<>(picked.size());
        for (int i = 0; i < picked.size(); i++) {
            Stock stock = picked.get(i);
            String shown = spec.blind() ? ALPHABET.charAt(i) + "사" : stock.getName();
            tickers.add(SeasonTicker.of(season, shown, stock, stock.getSector()));
        }
        seasonTickerRepository.saveAll(tickers);

        int rows = copyPrices(tickers, allDays, warmupDays.size());

        log.info(
                "시즌 생성 — id={} \"{}\" theme={} 종목 {}개 · 플레이 {}일 + 워밍업 {}일 · 가격 {}행",
                season.getId(),
                season.getTitle(),
                season.getTheme(),
                tickers.size(),
                playDays.size(),
                warmupDays.size(),
                rows);
        return season;
    }

    /**
     * 플레이 구간 직전 영업일들. 오름차순으로 돌려준다.
     *
     * <p>부족해도 만든다 — 수집 시작점(2020-01-02)에 가까운 시즌은 앞이 짧다. 플레이
     * 구간은 요청한 만큼 다 있어야 하지만 워밍업은 있는 만큼만 담으면 되고, 짧으면
     * 초반에 지표가 덜 보일 뿐 게임은 돈다.
     */
    private List<LocalDate> warmupDays(SeasonSpec spec, LocalDate firstPlayDay) {
        if (spec.warmupDays() <= 0) {
            return List.of();
        }
        List<LocalDate> desc = dailyQuoteRepository.findTradeDatesBefore(
                firstPlayDay, PageRequest.of(0, spec.warmupDays()));
        List<LocalDate> asc = new ArrayList<>(desc);
        Collections.reverse(asc);
        if (asc.size() < spec.warmupDays()) {
            log.info(
                    "워밍업이 {}일 요청인데 {}일만 있다 — 수집 시작점에 가까운 구간이다",
                    spec.warmupDays(),
                    asc.size());
        }
        return asc;
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
     * 후보 중에서 seed 로 종목을 뽑는다. <b>워밍업까지 포함한</b> 구간 전체에 시세가 있는
     * 종목만 후보다 — 중간에 상장폐지·거래정지가 끼면 게임일에 구멍이 생기고, 워밍업에
     * 구멍이 있으면 이동평균이 잘못된 값을 낸다.
     */
    private List<Stock> pick(SeasonSpec spec, List<LocalDate> gameDays) {
        if (spec.blind() && spec.tickerCount() > ALPHABET.length()) {
            throw new IllegalStateException(
                    "가명이 A~Z 뿐이라 블라인드 시즌의 종목은 %d 개까지다".formatted(ALPHABET.length()));
        }

        List<Stock> inSector = stockRepository.findBySectorAndListedIsTrueOrderByCodeAsc(spec.theme()).stream()
                .filter(SeasonCreateService::isCommonShare)
                .toList();
        if (inSector.isEmpty()) {
            throw new IllegalStateException("섹터 \"%s\" 에 후보 종목이 없다".formatted(spec.theme()));
        }

        List<String> full = dailyQuoteRepository.findCodesWithFullHistory(
                inSector.stream().map(Stock::getCode).toList(),
                gameDays.get(0),
                gameDays.get(gameDays.size() - 1),
                gameDays.size());

        // 코드 순으로 세워 두고 섞는다. 조회 순서가 흔들려도 같은 seed 가 같은 결과를 낸다.
        List<Stock> candidates = inSector.stream()
                .filter(s -> full.contains(s.getCode()))
                .sorted((a, b) -> a.getCode().compareTo(b.getCode()))
                .collect(Collectors.toCollection(ArrayList::new));

        if (candidates.size() < spec.tickerCount()) {
            throw new IllegalStateException(
                    "섹터 \"%s\" 에서 구간 전체 시세가 있는 종목이 %d 개뿐이라 %d 개를 뽑을 수 없다"
                            .formatted(spec.theme(), candidates.size(), spec.tickerCount()));
        }

        Collections.shuffle(candidates, new Random(spec.seed()));
        return candidates.subList(0, spec.tickerCount());
    }

    /**
     * 보통주인가. KRX 종목코드 여섯 자리 중 끝자리가 0 이면 보통주고, 그 밖(5·7·9·K)은
     * 우선주다 — 삼성전자 005930 과 삼성전자우 005935 는 코드 끝자리만 다르다.
     *
     * <p>우선주를 후보에서 뺀다. 보통주와 거의 같이 움직이므로 둘이 함께 뽑히면 5종목 중
     * 둘이 사실상 같은 종목이 되어 분산이 무의미해진다. 참가자에게는 "A사"·"E사" 로만
     * 보이니 같은 회사인지 알 방법도 없다.
     */
    private static boolean isCommonShare(Stock stock) {
        String code = stock.getCode();
        return code != null && code.endsWith("0");
    }

    /**
     * 구간 시세를 game_day 로 바꿔 옮긴다. 종목마다 한 번씩 읽고 날짜→인덱스 표로 바꾼다.
     * 종목 5개 × (워밍업 120 + 플레이 60)이면 900행이라 한 트랜잭션에서 끝난다.
     */
    private int copyPrices(
            List<SeasonTicker> tickers, List<LocalDate> gameDays, int warmupCount) {
        /* 워밍업 마지막 날이 game_day 0 이고 플레이 첫날이 1 이다. 그래서 인덱스에서
           워밍업 개수를 빼고 1 을 더한다 — 워밍업이 없으면 그대로 1..N 이다. */
        Map<LocalDate, Integer> dayIndex = new HashMap<>();
        for (int i = 0; i < gameDays.size(); i++) {
            dayIndex.put(gameDays.get(i), i - warmupCount + 1);
        }
        LocalDate from = gameDays.get(0);
        LocalDate to = gameDays.get(gameDays.size() - 1);

        List<SeasonPrice> prices = new ArrayList<>(tickers.size() * gameDays.size());
        for (SeasonTicker ticker : tickers) {
            List<DailyQuote> quotes =
                    dailyQuoteRepository.findByStock_CodeAndTradeDateBetweenOrderByTradeDate(
                            ticker.getRealStock().getCode(), from, to);
            for (DailyQuote q : quotes) {
                Integer gameDay = dayIndex.get(q.getTradeDate());
                if (gameDay == null) {
                    // 구간 안이지만 우리 영업일 목록에 없는 날. 매핑이 없으면 담지 않는다.
                    continue;
                }
                prices.add(SeasonPrice.of(
                        ticker,
                        gameDay,
                        q.getOpen(),
                        q.getHigh(),
                        q.getLow(),
                        q.getClose(),
                        q.getVolume()));
            }
        }
        seasonPriceRepository.saveAll(prices);
        return prices.size();
    }

    /** 스펙을 그대로 담는 그릇. 관리자 생성 API 가 들어오면 그 요청 본문이 이걸 채운다. */
    public record SeasonSpec(
            Season.Mode mode,
            String title,
            String note,
            String theme,
            LocalDate baseDate,
            int tickerCount,
            int lengthDays,
            /** 시즌 시작 전에 함께 담을 봉 수. game_day 0 이하로 들어간다. 0 이면 안 담는다 */
            int warmupDays,
            /**
             * 종목을 가릴 것인가. true 면 "A사"·"B사", false 면 실제 종목명이 들어간다.
             *
             * <p><b>연습은 false, 대회는 true 로 만든다.</b> 목적이 달라서다 — 연습은 배우는
             * 자리라 "삼성전자가 이때 이랬구나" 가 곧 학습이고, 대회는 순위가 걸려 있어
             * 그 구간을 기억하는 사람이 유리하면 순위가 뜻을 잃는다.
             *
             * <p>실명이면 시기가 사실상 드러난다. 실제 주가를 쓰기 때문이다 — 종목명과
             * 종가가 함께 나가면 검색 한 번에 날짜가 나온다. 연습에서는 그걸 받아들인다.
             */
            boolean blind,
            BigDecimal initialCash,
            long seed) {}
}
