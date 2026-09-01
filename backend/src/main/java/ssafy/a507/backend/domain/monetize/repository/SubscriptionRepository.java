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

    /**
     * 이 발행자를 ACTIVE 로 구독 중인 사람들. 리포트 발행 알림 대상이다.
     *
     * <p>User 엔티티가 아니라 id 만 받는다 — 알림 행의 FK 에 필요한 것은 id 뿐이고, 구독자
     * 수만큼 User 를 적재하면 발행 한 번에 그만큼 메모리를 쓴다.
     */
    @Query("""
            select s.subscriber.id
              from Subscription s
             where s.publisher.id = :publisherId
               and s.status = :status
            """)
    List<Long> findSubscriberIds(
            @Param("publisherId") Long publisherId, @Param("status") Subscription.Status status);
}
