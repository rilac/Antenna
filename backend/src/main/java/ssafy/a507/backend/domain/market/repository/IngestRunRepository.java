package ssafy.a507.backend.domain.market.repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import ssafy.a507.backend.domain.market.entity.IngestRun;

public interface IngestRunRepository extends JpaRepository<IngestRun, Long> {

    Optional<IngestRun> findByBaseDate(LocalDate baseDate);

    /** 되돌아보는 창 안의 회차들. 여기서 SUCCESS 만 걸러 "이미 받은 날짜"를 만든다. */
    List<IngestRun> findByBaseDateBetween(LocalDate from, LocalDate to);
}
