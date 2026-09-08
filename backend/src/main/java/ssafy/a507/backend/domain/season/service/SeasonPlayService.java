package ssafy.a507.backend.domain.season.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Limit;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ssafy.a507.backend.common.error.BusinessException;
import ssafy.a507.backend.common.error.ErrorCode;
import ssafy.a507.backend.domain.account.entity.User;
import ssafy.a507.backend.domain.account.repository.UserRepository;
import ssafy.a507.backend.domain.season.dto.MyPositionResponse;
import ssafy.a507.backend.domain.season.dto.MySeasonStatusResponse;
import ssafy.a507.backend.domain.season.dto.SeasonOrderRequest;
import ssafy.a507.backend.domain.season.dto.SeasonOrderResponse;
import ssafy.a507.backend.domain.season.dto.SeasonTradeItemResponse;
import ssafy.a507.backend.domain.season.dto.SeasonTradeListResponse;
import ssafy.a507.backend.domain.season.entity.Season;
import ssafy.a507.backend.domain.season.entity.SeasonParticipant;
import ssafy.a507.backend.domain.season.entity.SeasonPosition;
import ssafy.a507.backend.domain.season.entity.SeasonPrice;
import ssafy.a507.backend.domain.season.entity.SeasonTicker;
import ssafy.a507.backend.domain.season.entity.SeasonTrade;
import ssafy.a507.backend.domain.season.repository.SeasonParticipantRepository;
import ssafy.a507.backend.domain.season.repository.SeasonPositionRepository;
import ssafy.a507.backend.domain.season.repository.SeasonPriceRepository;
import ssafy.a507.backend.domain.season.repository.SeasonRepository;
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
    private final UserRepository userRepository;

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
        SeasonParticipant me = participantRepository
                .lockById(myAttempt(userId, seasonId).getId())
                .orElseThrow(() -> new BusinessException(ErrorCode.SEASON_NOT_JOINED));
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
