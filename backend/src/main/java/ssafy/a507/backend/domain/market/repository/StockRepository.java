package ssafy.a507.backend.domain.market.repository;

import java.util.List;
import org.springframework.data.domain.Limit;
import org.springframework.data.jpa.repository.JpaRepository;
import ssafy.a507.backend.domain.market.entity.Stock;

public interface StockRepository extends JpaRepository<Stock, String> {

    /**
     * 종목 탐색 목록 한 페이지. 커서는 종목코드다 — 불변 자연키라 페이지 사이에 값이 바뀌지
     * 않고, 정렬 키와 커서가 같아 건너뛰거나 겹치는 행이 생기지 않는다.
     *
     * <p>상장폐지 종목은 빼되 행은 남아 있다. 과거 시세와 판정 근거가 그 행에 매달려 있어서다.
     */
    List<Stock> findByListedTrueAndCodeGreaterThanOrderByCode(String cursor, Limit limit);
}
