package ssafy.a507.backend.domain.market.service;

import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import ssafy.a507.backend.domain.market.client.PublicDataProperties;
import ssafy.a507.backend.domain.market.client.PublicDataStockClient;
import ssafy.a507.backend.domain.market.client.StockPriceRow;
import ssafy.a507.backend.domain.market.dto.DailyQuoteUpsert;
import ssafy.a507.backend.domain.market.dto.StockUpsert;
import ssafy.a507.backend.domain.market.entity.IngestRun;
import ssafy.a507.backend.domain.market.repository.IngestRunRepository;
import ssafy.a507.backend.domain.market.repository.MarketUpsertRepository;

/**
 * 일봉 수집 배치의 본체. 날짜 하나를 받아 오는 일과, 아직 못 받은 날짜들을 고르는 일을 한다.
 *
 * <p><b>역주행 보정이 별도 로직이 아닌 이유.</b> "창 안에서 SUCCESS 가 아닌 영업일을 오래된
 * 것부터 다시 집는다" 하나로 세 가지가 동시에 해결된다 — 어제치 정기 수집, 실패한 회차의
 * 재시도, 주말·연휴로 건너뛴 구간의 소급 수집. 월요일 13시에 도는 회차는 금요일치가 창 안에
 * SUCCESS 로 없으면 그것부터 집는다.
 *
 * <p>날짜 하나가 실패해도 나머지는 계속 간다. 실패한 날짜는 SUCCESS 가 아닌 채로 남아 다음
 * 회차가 자연히 다시 집는다 — 재시도 큐를 따로 두지 않는 이유다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DailyQuoteIngestService {

    private final PublicDataStockClient client;
    private final MarketUpsertRepository marketUpsertRepository;
    private final IngestRunRepository ingestRunRepository;
    private final PublicDataProperties properties;

    /** 한 회차가 집을 날짜 수에 상한을 두지 않는다는 표시. */
    private static final int NO_LIMIT = 0;

    /**
     * 아직 못 받은 영업일을 오래된 것부터 메운다. 13시 스케줄러가 부르는 진입점이다.
     *
     * @param today 오늘(KST)
     * @return 이번 회차가 건드린 날짜별 기록
     */
    public List<IngestRun> ingestPending(LocalDate today) {
        // 배치가 도는 13시에는 오늘 종가가 아직 없다(장 마감 15:30). 어제가 마지막 대상이다.
        LocalDate lastTarget = today.minusDays(1);
        return ingestMissing(lastTarget.minusDays(properties.lookbackDays()), lastTarget, NO_LIMIT);
    }

    /**
     * 3년치 백필. 정기 수집과 같은 길을 쓰되 창만 넓히고, 한 회차가 집는 날짜 수를 자른다.
     *
     * <p><b>왜 한 번에 740일을 돌지 않는가.</b> 회차를 잘게 나누면 세 가지가 공짜로 딸려온다 —
     * 포털에 몰아치지 않고(회차당 chunkSize×3콜), 중간에 앱이 죽어도 ingest_runs 에 남은
     * 진행 상황에서 이어지며, 일 1만 콜 한도를 회차 간격으로 조절할 수 있다.
     *
     * @param from 백필 시작일
     * @param chunkSize 이번 회차가 집을 영업일 수
     */
    public List<IngestRun> backfill(LocalDate today, LocalDate from, int chunkSize) {
        return ingestMissing(from, today.minusDays(1), chunkSize);
    }

    private List<IngestRun> ingestMissing(LocalDate from, LocalDate to, int limit) {
        if (!properties.isConfigured()) {
            log.warn("PUBLIC_DATA_SERVICE_KEY 가 비어 있어 일봉 수집을 건너뛴다.");
            return List.of();
        }
        if (from.isAfter(to)) {
            return List.of();
        }

        Set<LocalDate> collected = ingestRunRepository.findByBaseDateBetween(from, to).stream()
                .filter(IngestRun::isCollected)
                .map(IngestRun::getBaseDate)
                .collect(Collectors.toSet());

        List<LocalDate> targets = pendingDates(from, to, collected);
        if (targets.isEmpty()) {
            log.info("메울 영업일이 없다 — {} ~ {}", from, to);
            return List.of();
        }

        if (limit > NO_LIMIT && targets.size() > limit) {
            log.info("남은 {}일 중 {}일만 이번 회차에 집는다.", targets.size(), limit);
            targets = targets.subList(0, limit);
        }

        log.info("일봉 수집 시작 — {} ~ {} 중 {}일", from, to, targets.size());
        List<IngestRun> runs = new ArrayList<>(targets.size());
        for (LocalDate target : targets) {
            runs.add(ingestDate(target));
        }
        return runs;
    }

    /**
     * 날짜 하나를 받아 적재한다. 성공·빈 날·실패 어느 쪽이든 회차 기록을 남기고 돌아온다 —
     * 예외를 밖으로 던지지 않는 이유는 한 날짜의 실패가 나머지 날짜를 멈추면 안 되기 때문이다.
     */
    public IngestRun ingestDate(LocalDate baseDate) {
        Instant startedAt = Instant.now();
        IngestRun run = ingestRunRepository
                .findByBaseDate(baseDate)
                .orElseGet(() -> IngestRun.of(baseDate));
        run.start(startedAt);
        // 시작을 먼저 남긴다. 회차가 도중에 죽으면 RUNNING 인 채로 남아 흔적이 된다.
        ingestRunRepository.save(run);

        try {
            List<StockPriceRow> rows = client.fetchDay(baseDate);

            if (rows.isEmpty()) {
                // 공휴일·휴장. 오류가 아니다.
                log.info("{} 는 받을 데이터가 없다(공휴일·휴장 또는 공개 전).", baseDate);
                run.markEmpty(Instant.now());
            } else {
                // 일봉의 stock_code 가 stocks 를 참조하므로 종목이 먼저다.
                marketUpsertRepository.upsertStocks(toStocks(rows));
                // 한 회차가 같은 수집 시각을 공유해야 나중에 회차 단위로 묶어 볼 수 있다.
                marketUpsertRepository.upsertDailyQuotes(toQuotes(rows), startedAt);
                log.info("{} 일봉 {}건 적재", baseDate, rows.size());
                run.succeed(rows.size(), Instant.now());
            }
        } catch (RuntimeException e) {
            log.warn("{} 수집 실패 — 다음 회차에 다시 시도한다.", baseDate, e);
            run.fail(e.getMessage(), Instant.now());
        }
        return ingestRunRepository.save(run);
    }

    /**
     * 창 안에서 아직 받지 못한 영업일을 오래된 것부터 돌려준다.
     *
     * <p>토·일은 애초에 장이 열리지 않아 부를 이유가 없다. 공휴일은 달력을 갖고 있지 않아
     * 미리 걸러내지 못하고, 불러 보고 빈 응답이면 EMPTY 로 남긴다.
     */
    static List<LocalDate> pendingDates(LocalDate from, LocalDate to, Set<LocalDate> collected) {
        List<LocalDate> targets = new ArrayList<>();
        for (LocalDate date = from; !date.isAfter(to); date = date.plusDays(1)) {
            if (isWeekend(date) || collected.contains(date)) {
                continue;
            }
            targets.add(date);
        }
        return targets;
    }

    private static boolean isWeekend(LocalDate date) {
        DayOfWeek day = date.getDayOfWeek();
        return day == DayOfWeek.SATURDAY || day == DayOfWeek.SUNDAY;
    }

    private List<StockUpsert> toStocks(List<StockPriceRow> rows) {
        return rows.stream()
                .map(row -> new StockUpsert(row.stockCode(), row.stockName(), row.market()))
                .toList();
    }

    private List<DailyQuoteUpsert> toQuotes(List<StockPriceRow> rows) {
        return rows.stream()
                .map(row -> new DailyQuoteUpsert(
                        row.stockCode(),
                        row.tradeDate(),
                        row.open(),
                        row.high(),
                        row.low(),
                        row.close(),
                        row.volume()))
                .toList();
    }
}
