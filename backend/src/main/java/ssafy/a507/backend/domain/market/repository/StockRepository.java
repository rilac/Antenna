package ssafy.a507.backend.domain.market.repository;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import ssafy.a507.backend.domain.market.entity.Stock;

/**
 * 종목 마스터. 탐색 목록은 필터 조합이 많아 파생 쿼리 대신 {@link JpaSpecificationExecutor} 로
 * 조건을 조립한다 — 커서는 종목코드다. 불변 자연키라 페이지 사이에 값이 바뀌지 않고, 정렬 키와
 * 커서가 같아 건너뛰거나 겹치는 행이 생기지 않는다.
 */
public interface StockRepository extends JpaRepository<Stock, String>, JpaSpecificationExecutor<Stock> {

    /** 상장 종목 전부 — 섹터 요약 재료. 300개 안팎이라 한 번에 읽는다. */
    List<Stock> findByListedTrue();
}
