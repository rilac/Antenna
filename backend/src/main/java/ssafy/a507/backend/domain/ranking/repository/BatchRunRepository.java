package ssafy.a507.backend.domain.ranking.repository;

import java.time.LocalDate;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import ssafy.a507.backend.domain.ranking.entity.BatchRun;

public interface BatchRunRepository extends JpaRepository<BatchRun, Long> {

    /**
     * 그날 회차. {@code business_date} 가 UNIQUE 라 하루에 한 행뿐이다.
     *
     * <p>판정 배치(ANT-PRED-03·04)가 같은 날 다시 돌 때 쓴다 — 새 행을 만들면 UQ 위반이라, 있으면 카운터를 더한다.
     */
    Optional<BatchRun> findByBusinessDate(LocalDate businessDate);
}
