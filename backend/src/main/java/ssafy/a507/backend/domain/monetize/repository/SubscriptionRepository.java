package ssafy.a507.backend.domain.monetize.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import ssafy.a507.backend.domain.monetize.entity.Subscription;

public interface SubscriptionRepository extends JpaRepository<Subscription, Long> {}
