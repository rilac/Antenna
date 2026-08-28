package ssafy.a507.backend.domain.research.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import ssafy.a507.backend.domain.research.entity.ResearchPoint;

public interface ResearchPointRepository extends JpaRepository<ResearchPoint, Long> {}
