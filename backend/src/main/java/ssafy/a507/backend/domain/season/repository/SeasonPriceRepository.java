package ssafy.a507.backend.domain.season.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import ssafy.a507.backend.domain.season.entity.SeasonPrice;

public interface SeasonPriceRepository extends JpaRepository<SeasonPrice, Long> {}
