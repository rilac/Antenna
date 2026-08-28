package ssafy.a507.backend.domain.season.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import ssafy.a507.backend.domain.season.entity.Season;

public interface SeasonRepository extends JpaRepository<Season, Long> {}
