package ssafy.a507.backend.domain.market.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import ssafy.a507.backend.domain.market.entity.Stock;

public interface StockRepository extends JpaRepository<Stock, String> {}
