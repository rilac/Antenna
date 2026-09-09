package ssafy.a507.backend.domain.season.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Limit;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import ssafy.a507.backend.common.ai.AiException;
import ssafy.a507.backend.common.error.BusinessException;
import ssafy.a507.backend.common.error.ErrorCode;
import ssafy.a507.backend.domain.account.entity.User;
import ssafy.a507.backend.domain.account.repository.UserRepository;
import ssafy.a507.backend.domain.season.dto.MyPositionResponse;
import ssafy.a507.backend.domain.season.dto.MySeasonStatusResponse;
import ssafy.a507.backend.domain.season.dto.SeasonAdvanceResponse;
import ssafy.a507.backend.domain.season.dto.SeasonFinishResponse;
import ssafy.a507.backend.domain.season.dto.SeasonOrderRequest;
import ssafy.a507.backend.domain.season.dto.SeasonOrderResponse;
import ssafy.a507.backend.domain.season.dto.SeasonResultResponse;
import ssafy.a507.backend.domain.season.dto.SeasonTradeItemResponse;
import ssafy.a507.backend.domain.season.dto.SeasonTradeListResponse;
import ssafy.a507.backend.domain.season.entity.Season;
import ssafy.a507.backend.domain.season.entity.SeasonParticipant;
import ssafy.a507.backend.domain.season.entity.SeasonPosition;
import ssafy.a507.backend.domain.season.entity.SeasonPrice;
import ssafy.a507.backend.domain.season.entity.SeasonResult;
import ssafy.a507.backend.domain.season.entity.SeasonTicker;
import ssafy.a507.backend.domain.season.entity.SeasonTrade;
import ssafy.a507.backend.domain.season.repository.SeasonParticipantRepository;
import ssafy.a507.backend.domain.season.repository.SeasonPositionRepository;
import ssafy.a507.backend.domain.season.repository.SeasonPriceRepository;
import ssafy.a507.backend.domain.season.repository.SeasonRepository;
import ssafy.a507.backend.domain.season.repository.SeasonResultRepository;
import ssafy.a507.backend.domain.season.repository.SeasonTickerRepository;
import ssafy.a507.backend.domain.season.repository.SeasonTradeRepository;

/**
 * 시즌 진행 — 내 현황 · 주문 · 체결 내역(ANT-SEASON-03). 전부 <b>내 마지막 회차</b>가 기준이다.
 *
 * <p><b>체결 규칙(명세 §모의투자).</b> 게임일 종가 단일가, 부분 체결·슬리피지 없음. 체결가는
 * 서버가 내 진행일 봉에서 읽는다 — 요청에 가격이 없으니 커닝할 자리도 없다.
 *
 * <p><b>예수금은 로컬 원장이다.</b> 금융망 출금·입금(ANT-SEASON-06)은 붙이지 않는다.
 * {@code season_participants.cash} 를 바로 더하고 뺀다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SeasonPlayService {

    private static final int DEFAULT_PAGE_SIZE = 20;
    private static final int MAX_PAGE_SIZE = 100;
    private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);

    private final SeasonRepository seasonRepository;
    private final SeasonParticipantRepository participantRepository;
    private final SeasonTickerRepository seasonTickerRepository;
    private final SeasonPriceRepository seasonPriceRepository;
    private final SeasonPositionRepository positionRepository;
    private final SeasonTradeRepository tradeRepository;
    private final SeasonResultRepository resultRepository;
    private final UserRepository userRepository;
    private final SeasonReviewService reviewService;
    /** finish 가 트랜잭션을 손으로 나누는 데 쓴다 — LLM 호출을 트랜잭션 밖에 두려고. */
    private final TransactionTemplate tx;

    /** 내 현황. 보유 종목은 내 진행일 종가로 평가한다. */
    @Transactional(readOnly = true)
    public MySeasonStatusResponse me(Long userId, Long seasonId) {
        SeasonParticipant me = myAttempt(userId, seasonId);
        List<SeasonPosition> held = positionRepository.findByParticipant_IdOrderByIdAsc(me.getId());

        Map<Long, BigDecimal> closes = seasonPriceRepository
                .findByTicker_IdInAndGameDay(
                        held.stream().map(p -> p.getTicker().getId()).toList(), me.getCurrentDay())
                .stream()
                .collect(Collectors.toMap(
                        p -> p.getTicker().getId(), SeasonPrice::getClose));

        BigDecimal stockValue = BigDecimal.ZERO;
        for (SeasonPosition p : held) {
            stockValue = stockValue.add(valueOf(p, closes));
        }
        BigDecimal totalAsset = me.getCash().add(stockValue);
        BigDecimal initialCash = me.getSeason().getInitialCash();
        BigDecimal pnl = totalAsset.subtract(initialCash);

        List<MyPositionResponse> positions = held.stream()
                .map(p -> {
                    BigDecimal value = valueOf(p, closes);
                    BigDecimal cost = p.getAvgPrice().multiply(BigDecimal.valueOf(p.getQty()));
                    return new MyPositionResponse(
                            p.getTicker().getId(),
                            p.getTicker().getDisplayName(),
                            p.getQty(),
                            p.getAvgPrice(),
                            value,
                            value.subtract(cost),
                            percent(value, totalAsset));
                })
                .toList();

        return new MySeasonStatusResponse(
                me.getCash(), totalAsset, stockValue, pnl, percent(pnl, initialCash),
                me.getCurrentDay(), positions);
    }

    /**
     * 주문. 매수는 예수금, 매도는 보유 수량을 검사하고 409 로 거절한다.
     * 포지션은 수량 가중 평단으로 갱신하고 0 이 되면 지운다. 체결은 append 다.
     */
    @Transactional
    public SeasonOrderResponse order(Long userId, Long seasonId, SeasonOrderRequest request) {
        SeasonParticipant me = lockedOngoing(userId, seasonId);
        SeasonTicker ticker = seasonTickerRepository
                .findByIdAndSeason_Id(request.tickerId(), seasonId)
                .orElseThrow(() -> new BusinessException(ErrorCode.SEASON_TICKER_NOT_FOUND));
        BigDecimal price = seasonPriceRepository
                .findByTicker_IdAndGameDay(ticker.getId(), me.getCurrentDay())
                .map(SeasonPrice::getClose)
                .orElseThrow(() -> new BusinessException(ErrorCode.SEASON_PRICE_NOT_FOUND));

        int qty = request.qty();
        BigDecimal amount = price.multiply(BigDecimal.valueOf(qty));
        BigDecimal realizedPnl = null;

        if (request.side() == SeasonTrade.Side.BUY) {
            if (me.getCash().compareTo(amount) < 0) {
                throw new BusinessException(ErrorCode.INSUFFICIENT_BALANCE);
            }
            me.debit(amount);
            positionRepository
                    .findByParticipant_IdAndTicker_Id(me.getId(), ticker.getId())
                    .ifPresentOrElse(
                            p -> p.buy(qty, price),
                            () -> positionRepository.save(SeasonPosition.open(me, ticker, qty, price)));
        } else {
            SeasonPosition held = positionRepository
                    .findByParticipant_IdAndTicker_Id(me.getId(), ticker.getId())
                    .filter(p -> p.getQty() >= qty)
                    .orElseThrow(() -> new BusinessException(ErrorCode.SEASON_INSUFFICIENT_QTY));
            realizedPnl = price.subtract(held.getAvgPrice()).multiply(BigDecimal.valueOf(qty));
            me.credit(amount);
            held.sell(qty);
            if (held.getQty() == 0) {
                positionRepository.delete(held);
            }
        }

        SeasonTrade trade = tradeRepository.save(SeasonTrade.of(
                me, ticker, request.side(), qty, price, me.getCurrentDay(), realizedPnl));
        return new SeasonOrderResponse(trade.getId(), price, trade.getGameDay());
    }

    /** 체결 내역. 최근 체결이 먼저고 커서는 id 다. */
    @Transactional(readOnly = true)
    public SeasonTradeListResponse trades(
            Long userId, Long seasonId, Long tickerId, SeasonTrade.Side side,
            Long cursor, Integer size) {
        SeasonParticipant me = myAttempt(userId, seasonId);
        int pageSize = pageSize(size);

        // 한 건 더 읽어 다음 페이지가 있는지 본다(댓글 목록과 같은 방식).
        List<SeasonTrade> found = tradeRepository.findPage(
                me.getId(), tickerId, side, cursor, Limit.of(pageSize + 1));
        boolean hasNext = found.size() > pageSize;
        List<SeasonTrade> page = hasNext ? found.subList(0, pageSize) : found;

        List<SeasonTradeItemResponse> items = page.stream()
                .map(t -> new SeasonTradeItemResponse(
                        t.getId(),
                        t.getTicker().getId(),
                        t.getTicker().getDisplayName(),
                        t.getSide(),
                        t.getQty(),
                        t.getPrice(),
                        t.getPrice().multiply(BigDecimal.valueOf(t.getQty())),
                        t.getGameDay(),
                        t.getRealizedPnl()))
                .toList();
        Long nextCursor = hasNext ? page.get(page.size() - 1).getId() : null;
        return new SeasonTradeListResponse(items, nextCursor, hasNext);
    }

    /**
     * 다음 게임일(ANT-SEASON-04). {@code expectedDay} 는 낙관적 잠금이다 — 서버의 진행일과 다르면
     * 넘기지 않는다. 두 번 눌러도, 탭이 둘이어도 하루만 간다. 마지막 게임일에서는 더 가지 않고
     * 409 로 종료를 가리킨다.
     */
    @Transactional
    public SeasonAdvanceResponse advance(Long userId, Long seasonId, int expectedDay) {
        SeasonParticipant me = lockedOngoing(userId, seasonId);
        Season season = me.getSeason();
        if (season.getMode() == Season.Mode.COMPETITION) {
            throw new BusinessException(ErrorCode.SEASON_ADVANCE_NOT_ALLOWED);
        }
        if (me.getCurrentDay() != expectedDay) {
            throw new BusinessException(ErrorCode.DAY_MISMATCH);
        }
        if (me.getCurrentDay() >= season.getLengthDays()) {
            throw new BusinessException(ErrorCode.SEASON_LAST_DAY);
        }
        me.advance();
        return new SeasonAdvanceResponse(me.getCurrentDay(), me.getCurrentDay() == season.getLengthDays());
    }

    /**
     * 종료 — 마지막 게임일에서만. 회차를 DONE 으로 굳히고 {@code season_results} 를 1회 만든다.
     * 성적표와 함께 AI 복기(ANT-SEASON-09)를 만들어 같이 저장한다. 이미 끝난 회차면 저장된
     * 결과를 그대로 준다(멱등) — 단 복기가 비어 있고 키가 있으면 그때 채운다.
     *
     * <p>트랜잭션을 셋으로 나눈다. GMS 호출은 길면 수십 초라 그동안 DB 커넥션을 잡고 있으면
     * 안 된다. ① 읽기 — 상태 검증 · 지표 계산 · 프롬프트 재료 ② 트랜잭션 밖 — GMS ③ 쓰기 —
     * 잠그고 다시 검증한 뒤 지표를 다시 계산해 복기와 함께 저장. 복기 생성이 실패하면 아무것도
     * 저장하지 않는다(503 SEASON_REVIEW_FAILED) — 회차는 ONGOING 그대로라 다시 시도할 수 있다.
     * 지표를 ③에서 다시 계산하는 이유는 ①과 ③ 사이에 주문이 끼어들 수 있어서다.
     */
    public SeasonFinishResponse finish(Long userId, Long seasonId) {
        Prepared p = tx.execute(status -> prepare(userId, seasonId));
        if (p.done()) {
            return p.response();
        }
        String review;
        try {
            review = reviewService.review(p.aiInput());
        } catch (AiException e) {
            log.warn("[SEASON-09] 복기 생성 실패 user={} season={} — {}", userId, seasonId, e.getMessage());
            throw new BusinessException(ErrorCode.SEASON_REVIEW_FAILED);
        }
        return tx.execute(status -> commit(userId, seasonId, review));
    }

    /** ①의 결과. done 이면 더 할 일이 없어 response 를 그대로 준다. 아니면 aiInput 이 있다. */
    private record Prepared(boolean done, SeasonFinishResponse response, String aiInput) {}

    private Prepared prepare(Long userId, Long seasonId) {
        SeasonParticipant me = myAttempt(userId, seasonId);
        Season season = me.getSeason();
        if (me.getStatus() == SeasonParticipant.Status.DONE) {
            SeasonResult saved = resultRepository.findById(me.getId())
                    .orElseThrow(() -> new BusinessException(ErrorCode.SEASON_ATTEMPT_ENDED));
            if (saved.getReviewBody() != null || !reviewService.hasKey()) {
                return new Prepared(true, toResponse(saved), null);
            }
            // 복기만 비어 있는 끝난 회차 — 저장된 지표로 재료를 만들어 채우러 간다
            List<SeasonTrade> trades = tradeRepository.findByParticipant_IdOrderByIdAsc(me.getId());
            return new Prepared(false, null, SeasonReviewService.input(season, trades, toResult(saved)));
        }
        if (!me.isOngoing()) {
            throw new BusinessException(ErrorCode.SEASON_ATTEMPT_ENDED);
        }
        if (me.getCurrentDay() < season.getLengthDays()) {
            throw new BusinessException(ErrorCode.SEASON_NOT_LAST_DAY);
        }
        List<SeasonTrade> trades = tradeRepository.findByParticipant_IdOrderByIdAsc(me.getId());
        return new Prepared(false, null, SeasonReviewService.input(season, trades, compute(season, trades)));
    }

    private SeasonFinishResponse commit(Long userId, Long seasonId, String review) {
        SeasonParticipant me = participantRepository
                .lockById(myAttempt(userId, seasonId).getId())
                .orElseThrow(() -> new BusinessException(ErrorCode.SEASON_NOT_JOINED));
        if (me.getStatus() == SeasonParticipant.Status.DONE) {
            // ①과 ③ 사이에 다른 요청이 먼저 끝냈거나, 복기만 채우러 온 경우
            SeasonResult saved = resultRepository.findById(me.getId())
                    .orElseThrow(() -> new BusinessException(ErrorCode.SEASON_ATTEMPT_ENDED));
            if (saved.getReviewBody() == null && review != null) {
                saved.review(review, reviewService.promptVersion());
            }
            return toResponse(saved);
        }
        if (!me.isOngoing()) {
            throw new BusinessException(ErrorCode.SEASON_ATTEMPT_ENDED);
        }
        Season season = me.getSeason();
        if (me.getCurrentDay() < season.getLengthDays()) {
            throw new BusinessException(ErrorCode.SEASON_NOT_LAST_DAY);
        }
        List<SeasonTrade> trades = tradeRepository.findByParticipant_IdOrderByIdAsc(me.getId());
        SeasonResultCalculator.Result r = compute(season, trades);

        me.finish();
        SeasonResult result = SeasonResult.of(
                me, r.finalAsset(), r.returnRate(), r.benchmarkReturn(), r.maxDrawdown(),
                r.winRate(), r.profitFactor(), r.avgHoldingDays());
        if (review != null) {
            result.review(review, reviewService.promptVersion());
        }
        return toResponse(resultRepository.save(result));
    }

    /** 성과 지표 — 체결과 거래한 종목의 종가에서 계산한다. */
    private SeasonResultCalculator.Result compute(Season season, List<SeasonTrade> trades) {
        int lastDay = season.getLengthDays();
        List<Long> traded = trades.stream().map(tr -> tr.getTicker().getId()).distinct().toList();
        Map<Long, TreeMap<Integer, BigDecimal>> closes = new HashMap<>();
        if (!traded.isEmpty()) {
            for (SeasonPrice p : seasonPriceRepository
                    .findByTicker_IdInAndGameDayBetweenOrderByGameDayAsc(traded, 1, lastDay)) {
                closes.computeIfAbsent(p.getTicker().getId(), k -> new TreeMap<>())
                        .put(p.getGameDay(), p.getClose());
            }
        }
        return SeasonResultCalculator.compute(
                season.getInitialCash(),
                lastDay,
                trades,
                (tickerId, day) -> {
                    Map.Entry<Integer, BigDecimal> e =
                            closes.getOrDefault(tickerId, new TreeMap<>()).floorEntry(day);
                    if (e == null) {
                        throw new BusinessException(ErrorCode.SEASON_PRICE_NOT_FOUND);
                    }
                    return e.getValue();
                },
                closesOf(season.getId(), 1),
                closesOf(season.getId(), lastDay));
    }

    private static SeasonResultCalculator.Result toResult(SeasonResult s) {
        return new SeasonResultCalculator.Result(
                s.getFinalAsset(), s.getReturnRate(), s.getBenchmarkReturn(), s.getMaxDrawdown(),
                s.getWinRate(), s.getProfitFactor(), s.getAvgHoldingDays());
    }

    /** 내 마지막 회차의 결과. 끝나지 않았으면 404 — 결과는 finish 가 만든다. */
    @Transactional(readOnly = true)
    public SeasonResultResponse result(Long userId, Long seasonId) {
        SeasonParticipant me = myAttempt(userId, seasonId);
        SeasonResult r = resultRepository.findById(me.getId())
                .orElseThrow(() -> new BusinessException(ErrorCode.SEASON_RESULT_NOT_FOUND));
        return new SeasonResultResponse(
                r.getParticipantId(), r.getFinalAsset(), r.getReturnRate(), r.getBenchmarkReturn(),
                r.getMaxDrawdown(), r.getWinRate(), r.getProfitFactor(), r.getAvgHoldingDays(),
                r.getScore(), r.getGrade(), r.getReviewBody(), r.getClosedAt());
    }

    private Map<Long, BigDecimal> closesOf(Long seasonId, int gameDay) {
        return seasonPriceRepository.findByTicker_Season_IdAndGameDay(seasonId, gameDay).stream()
                .collect(Collectors.toMap(p -> p.getTicker().getId(), SeasonPrice::getClose));
    }

    private static SeasonFinishResponse toResponse(SeasonResult r) {
        return new SeasonFinishResponse(
                r.getParticipantId(), r.getFinalAsset(), r.getReturnRate(), r.getBenchmarkReturn(),
                r.getMaxDrawdown(), r.getWinRate(), r.getProfitFactor(), r.getAvgHoldingDays());
    }

    /** 주문·진행용 — 내 마지막 회차를 잠그고 읽는다. 끝난 회차면 409. */
    private SeasonParticipant lockedOngoing(Long userId, Long seasonId) {
        SeasonParticipant me = participantRepository
                .lockById(myAttempt(userId, seasonId).getId())
                .orElseThrow(() -> new BusinessException(ErrorCode.SEASON_NOT_JOINED));
        if (!me.isOngoing()) {
            throw new BusinessException(ErrorCode.SEASON_ATTEMPT_ENDED);
        }
        return me;
    }

    /** 볼 수 있는 시즌의 내 마지막 회차. 참가한 적이 없으면 409 다. */
    private SeasonParticipant myAttempt(Long userId, Long seasonId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
        Season season = seasonRepository.findById(seasonId)
                .filter(s -> s.getMode() != Season.Mode.DEMO || user.getRole() == User.Role.ADMIN)
                .orElseThrow(() -> new BusinessException(ErrorCode.SEASON_NOT_FOUND));
        return participantRepository
                .findFirstBySeason_IdAndUser_IdOrderByAttemptNoDesc(season.getId(), userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.SEASON_NOT_JOINED));
    }

    private static BigDecimal valueOf(SeasonPosition p, Map<Long, BigDecimal> closes) {
        BigDecimal close = closes.get(p.getTicker().getId());
        if (close == null) {
            throw new BusinessException(ErrorCode.SEASON_PRICE_NOT_FOUND);
        }
        return close.multiply(BigDecimal.valueOf(p.getQty()));
    }

    /** part / whole × 100, 소수 둘째 자리. whole 이 0 이면 0 이다. */
    private static BigDecimal percent(BigDecimal part, BigDecimal whole) {
        if (whole.signum() == 0) {
            return BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        }
        return part.multiply(HUNDRED).divide(whole, 2, RoundingMode.HALF_UP);
    }

    private static int pageSize(Integer size) {
        if (size == null || size <= 0) {
            return DEFAULT_PAGE_SIZE;
        }
        return Math.min(size, MAX_PAGE_SIZE);
    }
}
