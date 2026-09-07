package ssafy.a507.backend.domain.season.repository;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import ssafy.a507.backend.domain.season.entity.SeasonParticipant;

public interface SeasonParticipantRepository extends JpaRepository<SeasonParticipant, Long> {

    /**
     * 이 시즌에서 내 마지막 회차. 연습·시연은 회차를 늘려 여러 번 다시 할 수 있으므로
     * (시즌, 사용자) 로는 여러 행이 나온다 — 화면이 이어서 할 대상은 가장 최근 회차다.
     */
    Optional<SeasonParticipant> findFirstBySeason_IdAndUser_IdOrderByAttemptNoDesc(
            Long seasonId, Long userId);

    /**
     * 내 참가 전부. 시즌을 함께 끌어온다 — 행마다 lengthDays 를 읽으면 N+1 이다.
     * 최근 참가가 먼저 온다(id 역순 = 참가한 순서의 역순).
     */
    @EntityGraph(attributePaths = "season")
    List<SeasonParticipant> findByUser_IdOrderByIdDesc(Long userId);

    /** 누구든 참가한 적이 있는가 — 시더가 옛 시즌을 지워도 되는지 가르는 기준. */
    boolean existsBySeason_Id(Long seasonId);
}
