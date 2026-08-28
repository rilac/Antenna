package ssafy.a507.backend.domain.account.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import ssafy.a507.backend.domain.account.entity.UserInterest;

public interface UserInterestRepository extends JpaRepository<UserInterest, Long> {}
