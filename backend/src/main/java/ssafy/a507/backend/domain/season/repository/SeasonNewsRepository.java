package ssafy.a507.backend.domain.season.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import ssafy.a507.backend.domain.season.entity.SeasonNews;

public interface SeasonNewsRepository extends JpaRepository<SeasonNews, Long> {}
