package ssafy.a507.backend.domain.community.repository;

import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
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
}
