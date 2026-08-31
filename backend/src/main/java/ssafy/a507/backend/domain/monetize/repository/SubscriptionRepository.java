package ssafy.a507.backend.domain.monetize.repository;

import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import ssafy.a507.backend.domain.monetize.entity.Subscription;

public interface SubscriptionRepository extends JpaRepository<Subscription, Long> {

    /**
     * 내가 ACTIVE 로 구독 중인 발행자 id 들. 피드 한 페이지의 잠금 판정에 쓴다.
     *
     * <p>글마다 "이 작성자를 구독했나"를 묻지 않고 한 번에 받아오는 이유는 목록 크기만큼
     * 쿼리가 늘어나기 때문이다. PENDING(결제 대기)은 아직 열람 권한이 아니다.
     */
    @Query("""
            select s.publisher.id
              from Subscription s
             where s.subscriber.id = :subscriberId
               and s.publisher.id in :publisherIds
               and s.status = :status
            """)
    List<Long> findSubscribedPublisherIds(
            @Param("subscriberId") Long subscriberId,
            @Param("publisherIds") Collection<Long> publisherIds,
            @Param("status") Subscription.Status status);
}
