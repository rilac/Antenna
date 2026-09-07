package ssafy.a507.backend.domain.season.repository;

import java.time.LocalDate;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import ssafy.a507.backend.domain.season.entity.SeasonPrice;

public interface SeasonPriceRepository extends JpaRepository<SeasonPrice, Long> {

    /**
     * 시즌 종목의 구간 시세를 {@code daily_quotes} 에서 한 문장으로 옮긴다.
     *
     * <p>영업일을 날짜순으로 세어 {@code game_day} 를 만든다 — 앞의 {@code warmup} 일은
     * {@code 0, -1, -2 …} 로 내려가고 그다음 날이 {@code 1} 이다. 종목마다 엔티티를 만들어
     * 저장하면 200종목 × 90일 = 1만 8천 번의 INSERT 라 기동이 수십 초 늘어난다.
     *
     * <p>SQL 은 PostgreSQL 과 H2(MODE=PostgreSQL) 양쪽에서 같은 문장으로 돈다 — 윈도 함수와
     * CROSS JOIN 만 쓴다.
     *
     * @param from 워밍업의 첫 날(워밍업이 없으면 첫 게임일)
     * @param to 마지막 게임일
     * @param warmup 워밍업 일수. {@code from} 부터 세어 이 수만큼이 game_day 0 이하다
     * @return 담긴 행 수
     */
    @Modifying(flushAutomatically = true)
    @Query(
            value = """
                    INSERT INTO season_prices (ticker_id, game_day, open, high, low, close, volume)
                    SELECT t.id, d.game_day, q.open, q.high, q.low, q.close, q.volume
                    FROM season_tickers t
                    CROSS JOIN (
                        SELECT x.trade_date, ROW_NUMBER() OVER (ORDER BY x.trade_date) - :warmup AS game_day
                        FROM (SELECT DISTINCT trade_date FROM daily_quotes
                              WHERE trade_date BETWEEN :from AND :to) x
                    ) d
                    JOIN daily_quotes q ON q.stock_code = t.real_stock_code AND q.trade_date = d.trade_date
                    WHERE t.season_id = :seasonId
                    """,
            nativeQuery = true)
    int copyFromDailyQuotes(
            @Param("seasonId") Long seasonId,
            @Param("from") LocalDate from,
            @Param("to") LocalDate to,
            @Param("warmup") int warmup);

    /** 시즌의 가격 전부. 시즌을 지울 때 종목보다 먼저 지운다(FK). */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("delete from SeasonPrice p where p.ticker in"
            + " (select t from SeasonTicker t where t.season.id = :seasonId)")
    void deleteBySeasonId(@Param("seasonId") Long seasonId);
}
