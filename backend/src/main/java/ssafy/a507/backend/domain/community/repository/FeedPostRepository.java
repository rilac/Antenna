package ssafy.a507.backend.domain.community.repository;

import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Limit;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import ssafy.a507.backend.domain.community.entity.FeedPost;

public interface FeedPostRepository extends JpaRepository<FeedPost, Long> {

    /**
     * 피드 목록 한 페이지. BLOCKED 는 제외하고 최신순이다.
     *
     * <p>{@code cursor} 가 null 이면 첫 페이지다. 작성자·리포트·예측을 함께 끌어오는 이유는
     * 응답에 작성자 닉네임과 카드 정보가 들어가는데, LAZY 로 두면 글 수만큼 추가 쿼리가
     * 나가기 때문이다. 전부 단일값 연관이라 fetch join 과 페이징을 같이 써도 안전하다.
     *
     * <p>ponytail: 정렬 키는 명세상 작성 시각인데 커서는 id 를 쓴다. id 가 IDENTITY 라
     * 삽입 순서와 단조 일치하고, created_at 은 같은 시각에 여러 건이 들어오면 tie-break 가
     * 필요해 커서가 복합키가 된다. 대량 이관으로 id 순서와 시각 순서가 어긋나면 그때
     * (created_at, id) 복합 커서로 바꾼다.
     */
    @Query("""
            select p from FeedPost p
              join fetch p.user
              left join fetch p.report r
              left join fetch r.user
              left join fetch p.prediction pr
              left join fetch pr.user
              left join fetch pr.stock
            where p.status = :status
              and (:cursor is null or p.id < :cursor)
            order by p.id desc
            """)
    List<FeedPost> findPage(
            @Param("status") FeedPost.Status status, @Param("cursor") Long cursor, Limit limit);

    /** 상세 조회. 목록과 같은 이유로 연관을 함께 끌어온다. BLOCKED 여부는 서비스가 판단한다. */
    @Query("""
            select p from FeedPost p
              join fetch p.user
              left join fetch p.report r
              left join fetch r.user
              left join fetch p.prediction pr
              left join fetch pr.user
              left join fetch pr.stock
            where p.id = :id
            """)
    Optional<FeedPost> findDetailById(@Param("id") Long id);
}
