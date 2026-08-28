package ssafy.a507.backend.domain.ranking.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import ssafy.a507.backend.domain.ranking.entity.Ranking;

public interface RankingRepository extends JpaRepository<Ranking, Long> {}
