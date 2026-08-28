package ssafy.a507.backend.domain.market.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import ssafy.a507.backend.domain.market.entity.DailyQuote;

public interface DailyQuoteRepository extends JpaRepository<DailyQuote, Long> {}
