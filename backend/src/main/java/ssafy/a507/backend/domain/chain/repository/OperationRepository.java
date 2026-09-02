package ssafy.a507.backend.domain.chain.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import ssafy.a507.backend.domain.chain.entity.Operation;

public interface OperationRepository extends JpaRepository<Operation, String> {}
