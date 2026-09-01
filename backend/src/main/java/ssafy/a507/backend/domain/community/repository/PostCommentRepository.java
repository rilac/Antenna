package ssafy.a507.backend.domain.community.repository;

import java.util.Collection;
import java.util.List;
import org.springframework.data.domain.Limit;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import ssafy.a507.backend.domain.community.entity.PostComment;

public interface PostCommentRepository extends JpaRepository<PostComment, Long> {

    /**
     * 글 여러 건의 댓글 수를 한 쿼리로 센다. BLOCKED 댓글은 세지 않는다 — 목록에서
     * 빠지는 댓글을 개수에 넣으면 "댓글 3개"를 눌렀는데 1개만 보이는 화면이 된다.
     * 댓글이 0인 글은 결과에 없으므로 호출부가 0으로 채운다.
     */
    @Query("""
            select c.post.id, count(c)
              from PostComment c
             where c.post.id in :postIds
               and c.status = :status
             group by c.post.id
            """)
    List<Object[]> countByPostIds(
            @Param("postIds") Collection<Long> postIds, @Param("status") PostComment.Status status);

    /** 상세 화면의 댓글 미리보기. 오래된 순으로 앞 몇 건 — 대화 흐름이 위에서 아래로 읽힌다. */
    @Query("""
            select c from PostComment c
              join fetch c.user
             where c.post.id = :postId
               and c.status = :status
             order by c.id asc
            """)
    List<PostComment> findPreview(
            @Param("postId") Long postId, @Param("status") PostComment.Status status, Limit limit);

    /**
     * 댓글 목록 한 페이지. 오래된 순이라 커서는 {@code id >} 로 나아간다 — 목록 순서와
     * 커서 방향이 어긋나면 다음 페이지가 앞쪽을 다시 준다. BLOCKED 는 제외한다.
     */
    @Query("""
            select c from PostComment c
              join fetch c.user
             where c.post.id = :postId
               and c.status = :status
               and (:cursor is null or c.id > :cursor)
             order by c.id asc
            """)
    List<PostComment> findPage(
            @Param("postId") Long postId,
            @Param("status") PostComment.Status status,
            @Param("cursor") Long cursor,
            Limit limit);
}
