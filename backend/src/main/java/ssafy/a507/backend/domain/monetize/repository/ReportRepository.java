package ssafy.a507.backend.domain.monetize.repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Limit;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import ssafy.a507.backend.domain.monetize.entity.Report;
import ssafy.a507.backend.domain.monetize.entity.Subscription;

public interface ReportRepository extends JpaRepository<Report, Long> {

    /**
     * 통합 피드 한 페이지 — 최신순. 커서는 id 하나다.
     *
     * <p>{@code subscriberId} 가 null 이면 scope=ALL, 값이 있으면 scope=SUBSCRIBED 다.
     * 구독 필터를 id 목록(<code>in :ids</code>)이 아니라 exists 서브쿼리로 쓴 이유는, 구독이
     * 하나도 없는 사용자에게 빈 목록을 바인딩해야 해서다 — 빈 컬렉션의 IN 은 DB·드라이버마다
     * 다르게 렌더된다. 이 형태는 파라미터가 항상 스칼라라 그 경우가 생기지 않는다.
     *
     * <p>작성자를 함께 끌어오는 것은 응답에 닉네임이 들어가기 때문이다. LAZY 로 두면 목록
     * 크기만큼 쿼리가 늘어난다.
     */
    @Query("""
            select r from Report r
              join fetch r.user
            where (:subscriberId is null
                   or exists (select 1 from Subscription s
                               where s.subscriber.id = :subscriberId
                                 and s.publisher.id = r.user.id
                                 and s.status = :activeStatus
                                 and (s.expiresAt is null or s.expiresAt > :now)))
              and (:cursorId is null or r.id < :cursorId)
            order by r.id desc
            """)
    List<Report> findFeedPageRecent(
            @Param("subscriberId") Long subscriberId,
            @Param("activeStatus") Subscription.Status activeStatus,
            @Param("now") Instant now,
            @Param("cursorId") Long cursorId,
            Limit limit);

    /**
     * 통합 피드 한 페이지 — 인기순(열람 수). 커서가 {@code (viewCount, id)} 복합이다.
     *
     * <p>열람 수는 값이 겹치고 시간이 지나면 변한다. id 하나만 커서로 쓰면 같은 열람 수 구간
     * 에서 다음 페이지가 어디서 시작해야 하는지 정해지지 않아 건너뛰거나 중복된다. 정렬 키를
     * 커서에 그대로 담고 뒤에 id 로 tie-break 하는 것이 커서 페이징의 일반형이다.
     */
    @Query("""
            select r from Report r
              join fetch r.user
            where (:subscriberId is null
                   or exists (select 1 from Subscription s
                               where s.subscriber.id = :subscriberId
                                 and s.publisher.id = r.user.id
                                 and s.status = :activeStatus
                                 and (s.expiresAt is null or s.expiresAt > :now)))
              and (:cursorViewCount is null
                   or r.viewCount < :cursorViewCount
                   or (r.viewCount = :cursorViewCount and r.id < :cursorId))
            order by r.viewCount desc, r.id desc
            """)
    List<Report> findFeedPagePopular(
            @Param("subscriberId") Long subscriberId,
            @Param("activeStatus") Subscription.Status activeStatus,
            @Param("now") Instant now,
            @Param("cursorViewCount") Integer cursorViewCount,
            @Param("cursorId") Long cursorId,
            Limit limit);

    /** 채널 리포트 목록 한 페이지. 최신순 고정이고 비공개 리포트도 제목까지는 내려간다. */
    @Query("""
            select r from Report r
              join fetch r.user
            where r.user.id = :userId
              and (:cursorId is null or r.id < :cursorId)
            order by r.id desc
            """)
    List<Report> findChannelPage(
            @Param("userId") Long userId, @Param("cursorId") Long cursorId, Limit limit);

    /** 상세 조회. 목록과 같은 이유로 작성자를 함께 끌어온다. */
    @Query("""
            select r from Report r
              join fetch r.user
            where r.id = :id
            """)
    Optional<Report> findDetailById(@Param("id") Long id);

    /**
     * 열람 수 +1. 엔티티를 읽어 더하지 않고 DB 에서 증가시킨다 — 두 사람이 같은 리포트를 동시에
     * 열면 읽기-수정-쓰기 방식은 한쪽 증가분을 덮어써 잃는다.
     */
    @Modifying
    @Query("update Report r set r.viewCount = r.viewCount + 1 where r.id = :id")
    void incrementViewCount(@Param("id") Long id);
}
