package ssafy.a507.backend.domain.season.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import ssafy.a507.backend.domain.season.entity.SeasonParticipant;

public interface SeasonParticipantRepository extends JpaRepository<SeasonParticipant, Long> {}
