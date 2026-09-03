package ssafy.a507.backend.domain.research.repository;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import ssafy.a507.backend.domain.research.entity.CorpFinancial;

public interface CorpFinancialRepository extends JpaRepository<CorpFinancial, Long> {

    Optional<CorpFinancial> findByStockCodeAndFiscalYearAndQuarter(
            String stockCode, int fiscalYear, int quarter);

    /** 최신 연도가 위로 온다. 지금은 적재 결과를 되읽어 검증하는 테스트가 쓴다. */
    List<CorpFinancial> findByStockCodeOrderByFiscalYearDescQuarterDesc(String stockCode);
}
