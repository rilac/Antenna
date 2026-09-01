package ssafy.a507.backend.domain.market.repository;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import ssafy.a507.backend.domain.market.entity.DailyQuote;

public interface DailyQuoteRepository extends JpaRepository<DailyQuote, Long> {

    /**
     * (종목, 영업일) 단건 조회. UQ(stock_code, trade_date) 를 그대로 타므로 이 표의 기본
     * 접근 경로다. 수집 배치가 이미 받아 둔 날짜인지 확인할 때도 같은 길을 쓴다.
     */
    Optional<DailyQuote> findByStock_CodeAndTradeDate(String stockCode, LocalDate tradeDate);

    /**
     * 수집된 마지막 영업일. 목록 응답의 "전일 종가"가 어느 날짜 기준인지를 이 값 하나로 정한다.
     * 행마다 제각각 최신 날짜를 찾으면 화면이 한 날짜를 표기할 수 없다.
     */
    @Query("select max(q.tradeDate) from DailyQuote q")
    Optional<LocalDate> findLatestTradeDate();

    /**
     * 한 영업일의 종가를 종목 묶음으로 한 번에 읽는다. 행마다 단건 조회를 돌리면 N+1 이다.
     *
     * <p>그날 거래가 정지된 종목은 행이 없어 결과에서 빠진다 — 목록에서는 종가가 빈 칸이 된다.
     */
    List<DailyQuote> findByTradeDateAndStock_CodeIn(
            LocalDate tradeDate, Collection<String> stockCodes);

    /** 시세 시계열 구간 조회. 미니차트·리서치 차트가 공유하는 재료다. */
    List<DailyQuote> findByStock_CodeAndTradeDateBetweenOrderByTradeDate(
            String stockCode, LocalDate from, LocalDate to);
}
