package ssafy.a507.backend.domain.community.repository;

import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import ssafy.a507.backend.domain.community.entity.PostLike;

public interface PostLikeRepository extends JpaRepository<PostLike, Long> {

    /**
     * 글 여러 건의 공감 수를 한 쿼리로 센다. 글마다 count 를 부르면 목록 크기만큼
     * 쿼리가 늘어난다. 공감이 0인 글은 결과에 없으므로 호출부가 0으로 채운다.
     */
    @Query("""
            select l.post.id, count(l)
              from PostLike l
             where l.post.id in :postIds
             group by l.post.id
            """)
    List<Object[]> countByPostIds(@Param("postIds") Collection<Long> postIds);

    boolean existsByPostIdAndUserId(Long postId, Long userId);

    /**
     * 이 페이지에서 내가 이미 공감한 글들. 글마다 exists 를 묻지 않고 한 번에 받아온다
     * (구독 여부를 한 번에 받아오는 것과 같은 이유).
     */
    @Query("""
            select l.post.id
              from PostLike l
             where l.user.id = :userId
               and l.post.id in :postIds
            """)
    List<Long> findLikedPostIds(
            @Param("userId") Long userId, @Param("postIds") Collection<Long> postIds);

    /**
     * 공감 취소. 엔티티를 읽어와 삭제하면 select 가 한 번 더 나가므로 delete 한 문장으로 지운다.
     * 지운 건수를 돌려주지만 호출부는 쓰지 않는다 — 없던 공감의 취소도 204 다(멱등).
     */
    @Modifying(clearAutomatically = true)
    @Query("delete from PostLike l where l.post.id = :postId and l.user.id = :userId")
    int deleteByPostIdAndUserId(@Param("postId") Long postId, @Param("userId") Long userId);
}
