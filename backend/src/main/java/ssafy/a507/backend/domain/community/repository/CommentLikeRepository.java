package ssafy.a507.backend.domain.community.repository;

import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import ssafy.a507.backend.domain.community.entity.CommentLike;

public interface CommentLikeRepository extends JpaRepository<CommentLike, Long> {

    /** 댓글 여러 건의 좋아요 수를 한 쿼리로. 0인 댓글은 결과에 없으므로 호출부가 채운다. */
    @Query("""
            select cl.comment.id, count(cl)
              from CommentLike cl
             where cl.comment.id in :commentIds
             group by cl.comment.id
            """)
    List<Object[]> countByCommentIds(@Param("commentIds") Collection<Long> commentIds);

    boolean existsByCommentIdAndUserId(Long commentId, Long userId);

    /** 한 댓글의 좋아요 수. */
    long countByCommentId(Long commentId);

    /** 이 페이지에서 내가 이미 좋아요한 댓글들. */
    @Query("""
            select cl.comment.id
              from CommentLike cl
             where cl.user.id = :userId
               and cl.comment.id in :commentIds
            """)
    List<Long> findLikedCommentIds(
            @Param("userId") Long userId, @Param("commentIds") Collection<Long> commentIds);

    @Modifying(clearAutomatically = true)
    @Query("delete from CommentLike cl where cl.comment.id = :commentId and cl.user.id = :userId")
    int deleteByCommentIdAndUserId(
            @Param("commentId") Long commentId, @Param("userId") Long userId);
}
