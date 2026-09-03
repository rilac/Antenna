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

    /**
     * 한 분기 구분의 재무만 최신 연도부터. 호출 쪽은 전부 {@link CorpFinancial#ANNUAL_QUARTER}(연간)를
     * 넣는다 — 분기 수집이 붙으면 지금 메모리에서 걸러 내는 쪽이 4배를 읽어 3/4를 버리게 되고, 한쪽만
     * 고쳐지면 브리핑 문장과 재무 탭이 서로 다른 "최신 연간"을 말한다.
     */
    List<CorpFinancial> findByStockCodeAndQuarterOrderByFiscalYearDesc(String stockCode, int quarter);
}
