package ssafy.a507.backend.domain.market.repository;

import java.time.LocalDate;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import ssafy.a507.backend.domain.market.entity.DailyQuote;

public interface DailyQuoteRepository extends JpaRepository<DailyQuote, Long> {

    /**
     * (종목, 영업일) 단건 조회. UQ(stock_code, trade_date) 를 그대로 타므로 이 표의 기본
     * 접근 경로다. 수집 배치가 이미 받아 둔 날짜인지 확인할 때도 같은 길을 쓴다.
     */
    Optional<DailyQuote> findByStock_CodeAndTradeDate(String stockCode, LocalDate tradeDate);
}
