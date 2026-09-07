package ssafy.a507.backend.domain.market.repository;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
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

    /** 기준일 직전 영업일. 목록의 "전일 대비" 가 어느 날과 비교한 것인지를 이 값 하나로 정한다. */
    @Query("select max(q.tradeDate) from DailyQuote q where q.tradeDate < :date")
    Optional<LocalDate> findPreviousTradeDate(@Param("date") LocalDate date);

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

    /**
     * 여러 종목의 최근 시세를 한 번에 읽는다 — 관심 종목 미니차트 재료. 종목마다 구간 조회를 돌리면
     * N+1 이다. 종목·날짜 오름차순이라 호출 쪽이 종목별로 끊어 담기만 하면 된다.
     */
    List<DailyQuote> findByStock_CodeInAndTradeDateGreaterThanEqualOrderByStock_CodeAscTradeDateAsc(
            Collection<String> stockCodes, LocalDate from);

    /**
     * 기준일부터의 영업일 목록. 수집된 날짜가 곧 영업일이라 달력이나 공휴일 표가 필요 없다.
     *
     * <p>모의투자 시즌이 game_day ↔ 실제 영업일 매핑을 이걸로 만든다. 반환 순서가
     * game_day 1..N 순서다.
     */
    @Query("select distinct q.tradeDate from DailyQuote q where q.tradeDate >= :from order by q.tradeDate")
    List<LocalDate> findTradeDatesFrom(@Param("from") LocalDate from, Pageable pageable);

    /**
     * 기준일 직전 영업일들 — 시즌 워밍업 봉 재료. <b>최근 날짜가 먼저</b> 온다(내림차순).
     * 수집 시작보다 앞이면 있는 만큼만 돌아온다.
     */
    @Query("select distinct q.tradeDate from DailyQuote q where q.tradeDate < :before order by q.tradeDate desc")
    List<LocalDate> findTradeDatesBefore(@Param("before") LocalDate before, Pageable pageable);

    /**
     * 그날 시가총액 큰 순서의 종목코드 — 시즌 종목 후보. 종가 × 상장주식수로 근사한다.
     * 상장주식수는 마지막 수집일 기준 현재값이라 과거 구간에서는 근사치다.
     * 상장주식수가 없는 종목은 순위를 매길 수 없어 빠진다. 동률은 코드순.
     */
    @Query("select q.stock.code from DailyQuote q join q.stock s"
            + " where q.tradeDate = :day and s.listed = true and s.listedShares is not null"
            + " order by (q.close * s.listedShares) desc, s.code asc")
    List<String> findCodesByCapDescOn(@Param("day") LocalDate day);

    /**
     * 그 구간에 시세가 빠짐없이 있는 종목만. 중간에 상장폐지·거래정지가 있으면 게임일에
     * 구멍이 생겨 시즌으로 쓸 수 없다 — 구간 길이와 행 수가 같은 종목만 고른다.
     */
    @Query("select q.stock.code from DailyQuote q"
            + " where q.tradeDate between :from and :to and q.stock.code in :codes"
            + " group by q.stock.code having count(q) = :days")
    List<String> findCodesWithFullHistory(
            @Param("codes") Collection<String> codes,
            @Param("from") LocalDate from,
            @Param("to") LocalDate to,
            @Param("days") long days);
}
