package ssafy.a507.backend.domain.season.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import ssafy.a507.backend.domain.season.entity.SeasonTicker;

public interface SeasonTickerRepository extends JpaRepository<SeasonTicker, Long> {}
