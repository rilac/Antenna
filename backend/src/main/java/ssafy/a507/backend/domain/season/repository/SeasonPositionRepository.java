package ssafy.a507.backend.domain.season.repository;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import ssafy.a507.backend.domain.season.entity.SeasonPosition;

public interface SeasonPositionRepository extends JpaRepository<SeasonPosition, Long> {
    Optional<SeasonPosition> findByParticipant_IdAndTicker_Id(Long participantId, Long tickerId);

    /** 회차의 보유 전부. 종목을 함께 끌어온다 — 행마다 종목명을 읽으면 N+1 이다. */
    @EntityGraph(attributePaths = "ticker")
    List<SeasonPosition> findByParticipant_IdOrderByIdAsc(Long participantId);
}
