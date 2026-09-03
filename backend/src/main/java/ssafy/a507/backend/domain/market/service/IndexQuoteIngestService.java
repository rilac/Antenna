package ssafy.a507.backend.domain.market.service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import ssafy.a507.backend.domain.market.client.KoreaeximFxClient;
import ssafy.a507.backend.domain.market.client.KoreaeximProperties;
import ssafy.a507.backend.domain.market.client.MarketIndexProperties;
import ssafy.a507.backend.domain.market.client.PublicDataIndexClient;
import ssafy.a507.backend.domain.market.client.PublicDataProperties;
import ssafy.a507.backend.domain.market.dto.IndexQuoteUpsert;
import ssafy.a507.backend.domain.market.entity.IndexQuote.IndexCode;
import ssafy.a507.backend.domain.market.repository.IndexQuoteRepository;
import ssafy.a507.backend.domain.market.repository.MarketUpsertRepository;

/**
 * 지수·환율 수집 배치의 본체(ANT-DATA-04). 코스피·코스닥은 포털에서, 원/달러는 수출입은행에서
 * 받아 {@code index_quotes} 한 표에 넣는다.
 *
 * <p><b>회차 기록(ingest_runs)을 두지 않는 이유.</b> 일봉은 날짜별 호출이라 "어느 날을 받았는가"를
 * 따로 적어야 했지만, 지수는 구간 호출이라 매 회차 창 전체를 다시 받으면 그만이고(멱등 upsert),
 * 환율은 코스피 거래일 달력과 이미 있는 환율 날짜의 차집합이 곧 대상이다. DB 가 진실이고
 * 종결 판정이 따로 없다 — 공휴일은 코스피가 없으니 애초에 대상에 오르지 않는다.
 *
 * <p><b>백필도 이 한 경로다.</b> DB 가 비어 있으면 지수는 포털 제공 시작일(2020-01-02)부터
 * 어제까지 한 번에 받고(페이지 300장쯤), 환율은 그 거래일 전부가 대상이 되어 일 1,000콜
 * 한도만큼 받다가 멈추고 다음 회차가 이어받는다. 이틀이면 끝난다. 별도 스위치가 없다.
 *
 * <p>알려진 구멍 — 거래소는 열렸는데 은행이 쉰 날(드물다)은 환율이 영영 비어, 매 회차 그 날짜를
 * 한 번씩 더 두드린다. 그런 날이 1년에 한둘이라 감수한다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class IndexQuoteIngestService {

    private final PublicDataIndexClient indexClient;
    private final KoreaeximFxClient fxClient;
    private final MarketUpsertRepository upsertRepository;
    private final IndexQuoteRepository indexQuoteRepository;
    private final PublicDataProperties portal;
    private final MarketIndexProperties properties;
    private final KoreaeximProperties fx;

    /**
     * 어제까지의 지수·환율을 받는다. 스케줄러가 부르는 진입점이다.
     *
     * <p>지수가 먼저다 — 환율의 대상 날짜가 코스피 거래일에서 나오기 때문이다. 지수 수집이
     * 실패해도 환율은 이미 들어와 있는 거래일만큼은 계속 받는다.
     *
     * @param today 오늘(KST) · 회차가 도는 시각에는 오늘 종가가 아직 없어 어제가 마지막 대상이다
     */
    public void ingestPending(LocalDate today) {
        LocalDate lastTarget = today.minusDays(1);
        ingestIndices(lastTarget);
        ingestFx(lastTarget);
    }

    private void ingestIndices(LocalDate to) {
        if (!portal.isConfigured()) {
            log.warn("PUBLIC_DATA_SERVICE_KEY 가 비어 있어 지수 수집을 건너뛴다.");
            return;
        }

        // 마지막 날짜에서 lookback 만큼 되돌아가 다시 받는다 — 연휴와 실패 회차, 포털이 늦게
        // 공개한 날을 함께 덮는다. 한 건도 없으면 포털 제공 시작일부터다(첫 기동 = 백필).
        LocalDate from = indexQuoteRepository
                .findLatestTradeDate(IndexCode.KOSPI)
                .map(latest -> latest.minusDays(properties.lookbackDays()))
                .filter(start -> start.isAfter(properties.from()))
                .orElse(properties.from());
        if (from.isAfter(to)) {
            return;
        }

        try {
            List<IndexQuoteUpsert> rows = indexClient.fetchRange(from, to);
            int saved = upsertRepository.upsertIndexQuotes(rows);
            log.info("지수 {} ~ {} 적재 {}건", from, to, saved);
        } catch (RuntimeException e) {
            log.warn("지수 {} ~ {} 수집 실패 — 다음 회차에 다시 받는다.", from, to, e);
        }
    }

    private void ingestFx(LocalDate to) {
        if (!fx.isConfigured()) {
            log.warn("KOREAEXIM_AUTH_KEY 가 비어 있어 환율 수집을 건너뛴다.");
            return;
        }

        LocalDate from = properties.from();
        List<LocalDate> tradingDays = indexQuoteRepository.findTradeDates(IndexCode.KOSPI, from, to);
        List<LocalDate> collected = indexQuoteRepository.findTradeDates(IndexCode.USDKRW, from, to);
        List<LocalDate> pending = pendingFxDates(tradingDays, collected);
        if (pending.isEmpty()) {
            log.info("받을 환율이 없다 — {} ~ {}", from, to);
            return;
        }

        int saved = 0;
        int empty = 0;
        for (int i = 0; i < pending.size(); i++) {
            LocalDate date = pending.get(i);
            try {
                Optional<BigDecimal> rate = fxClient.fetchUsd(date);
                if (rate.isPresent()) {
                    // 날짜마다 바로 적재한다 — 한도에 걸리거나 앱이 죽어도 받은 것은 남는다.
                    upsertRepository.upsertIndexQuotes(
                            List.of(new IndexQuoteUpsert(IndexCode.USDKRW, date, rate.get())));
                    saved++;
                } else {
                    empty++;
                }
            } catch (KoreaeximFxClient.DailyLimitExceededException e) {
                // 한도에 걸린 이 날짜도 못 받은 것이다 — 실패한 날짜까지 포함해 남은 수를 센다.
                log.warn("수출입은행 일 한도에 걸려 환율 수집을 멈춘다 — {}일 남음. 다음 회차가 이어받는다.",
                        pending.size() - i);
                break;
            } catch (RuntimeException e) {
                log.warn("{} 환율 수집 실패 — 다음 회차에 다시 받는다.", date, e);
            }
        }
        log.info("환율 적재 {}건 · 고시 없는 날 {}건 · 대상 {}건", saved, empty, pending.size());
    }

    /**
     * 환율을 받아야 할 날짜 — 코스피 거래일 중 환율이 없는 날, 최신부터.
     *
     * <p>최신부터인 이유: 일 한도에 걸려 도중에 멈추더라도 홈 화면이 쓰는 최근 30일이 먼저
     * 채워진다. 과거 구멍은 다음 회차가 메운다.
     */
    static List<LocalDate> pendingFxDates(
            List<LocalDate> tradingDays, Collection<LocalDate> collected) {
        Set<LocalDate> done = Set.copyOf(collected);
        return tradingDays.stream()
                .filter(date -> !done.contains(date))
                .sorted(Comparator.reverseOrder())
                .toList();
    }
}
