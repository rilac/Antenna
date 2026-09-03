package ssafy.a507.backend.domain.market.repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Limit;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import ssafy.a507.backend.domain.market.entity.IndexQuote;
import ssafy.a507.backend.domain.market.entity.IndexQuote.IndexCode;

public interface IndexQuoteRepository extends JpaRepository<IndexQuote, Long> {

    /** 한 지수의 최근 N개 점 — 최신이 먼저다. 홈 미니차트와 등락률 계산이 이것으로 끝난다. */
    List<IndexQuote> findByIndexCodeOrderByTradeDateDesc(IndexCode indexCode, Limit limit);

    /** 한 지수가 어디까지 들어와 있는가. 수집 창의 시작점을 정한다. 한 건도 없으면 빈 값. */
    @Query("select max(q.tradeDate) from IndexQuote q where q.indexCode = :code")
    Optional<LocalDate> findLatestTradeDate(@Param("code") IndexCode code);

    /**
     * 구간 안에서 값이 있는 날짜들. 코스피 것은 거래일 달력이 되고, 환율 것은 이미 받은 날이 된다 —
     * 둘의 차집합이 환율 수집 대상이다.
     */
    @Query(
            """
            select q.tradeDate from IndexQuote q
            where q.indexCode = :code and q.tradeDate between :from and :to
            order by q.tradeDate
            """)
    List<LocalDate> findTradeDates(
            @Param("code") IndexCode code,
            @Param("from") LocalDate from,
            @Param("to") LocalDate to);
}
